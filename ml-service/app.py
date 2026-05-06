from flask import Flask, request, jsonify
from functools import wraps
from flask_limiter import Limiter
from flask_limiter.util import get_remote_address
from collections import deque
import os
import json
import requests

from train import train_all_with_real_data, train_single_device_type
from predict import (
    predictor, predict_fault, predict_with_raw,
    FaultPredictor, FeatureExtractor, DEVICE_PROFILES
)
from chart import generate_trend_chart
from model_version_manager import get_version_manager

app = Flask(__name__)

limiter = Limiter(
    app=app,
    key_func=get_remote_address,
    default_limits=["2000 per day", "500 per hour"]
)

MODEL_ROOT = 'model'
METADATA_FILE = os.path.join(MODEL_ROOT, 'metadata.json')

API_KEY = os.getenv('ML_API_KEY', 'myDefaultMLApiKey')

# 设备传感器历史缓存: {device_id: deque(maxlen=30)}
# 每个设备保留最近30条原始记录（约150秒，5秒采样间隔）
_sensor_history = {}
HISTORY_MAXLEN = 30


def require_api_key(f):
    @wraps(f)
    def decorated_function(*args, **kwargs):
        if not API_KEY:
            return jsonify({
                'success': False,
                'message': 'API密钥未配置',
                'data': None
            }), 500

        api_key = request.headers.get('X-API-Key')
        if not api_key or api_key != API_KEY:
            return jsonify({
                'success': False,
                'message': '无效的API密钥',
                'data': None
            }), 401
        return f(*args, **kwargs)
    return decorated_function


def _update_history(device_id: str, device_type: str,
                    temperature: float, vibration: float, pressure: float):
    """更新设备传感器历史缓存，返回当前历史记录列表。"""
    history = _sensor_history.setdefault(device_id, deque(maxlen=HISTORY_MAXLEN))

    from datetime import datetime
    now = datetime.now().isoformat()

    history.append({'timestamp': now, 'sensor_code': 'temperature', 'value': float(temperature)})
    history.append({'timestamp': now, 'sensor_code': 'vibration', 'value': float(vibration)})
    history.append({'timestamp': now, 'sensor_code': 'pressure', 'value': float(pressure)})

    return list(history)


def _has_new_model():
    """检查是否存在新版多模型结构。"""
    return os.path.exists(METADATA_FILE)


@app.route('/ml/health', methods=['GET'])
def health():
    status = {'status': 'healthy'}

    if _has_new_model():
        # 新版：检查所有设备类型的模型是否加载
        loaded_types = list(predictor._predictors.keys())
        status['model_version'] = '2.0'
        status['loaded_models'] = loaded_types
        status['model_count'] = len(loaded_types)
        status['all_models_loaded'] = len(loaded_types) == len(DEVICE_PROFILES)
    else:
        # 旧版：检查单一模型
        legacy_path = os.path.join(MODEL_ROOT, 'fault_model.pkl')
        status['model_version'] = '1.0'
        status['model_loaded'] = os.path.exists(legacy_path)

    return jsonify({
        'success': True,
        'message': 'ML Service is running',
        'data': status
    })


@app.route('/ml/predict', methods=['POST'])
@limiter.limit("60 per minute")
def predict():
    """主预测接口（v2.0）。

    请求体:
    {
        "device_id": "1",           // 可选，用于历史缓存
        "device_type": "工业机器人", // 可选，默认"工业机器人"
        "temperature": 65.0,
        "vibration": 0.25,
        "pressure": 100.0
    }

    返回:
    {
        "success": true,
        "data": {
            "fault_probability": 0.15,
            "is_fault": false,
            "device_type": "工业机器人",
            "feature_count": 15
        }
    }
    """
    try:
        data = request.get_json()
        if not data:
            return jsonify({'success': False, 'message': '请提供传感器数据', 'data': None}), 400

        device_id = str(data.get('device_id', 'unknown'))
        device_type = data.get('device_type', '工业机器人')
        temperature = data.get('temperature')
        vibration = data.get('vibration')
        pressure = data.get('pressure')

        # 基本参数校验
        if temperature is None or vibration is None or pressure is None:
            return jsonify({'success': False, 'message': '缺少必要的传感器数据', 'data': None}), 400

        # 数值范围校验
        if not (-50 <= float(temperature) <= 200):
            return jsonify({'success': False, 'message': '温度值超出合理范围(-50~200)', 'data': None}), 400
        if not (0 <= float(vibration) <= 100):
            return jsonify({'success': False, 'message': '振动值超出合理范围(0~100)', 'data': None}), 400
        if not (0 <= float(pressure) <= 1000):
            return jsonify({'success': False, 'message': '压力值超出合理范围(0~1000)', 'data': None}), 400

        # 校验设备类型
        if device_type not in DEVICE_PROFILES:
            return jsonify({
                'success': False,
                'message': f'未知的设备类型: {device_type}，支持的类型: {list(DEVICE_PROFILES.keys())}',
                'data': None
            }), 400

        # 更新历史缓存
        history = _update_history(device_id, device_type, temperature, vibration, pressure)

        # 使用新版预测路径（15维特征）
        result = predict_with_raw(device_type, history)

        if 'error' in result:
            app.logger.error('预测失败: %s', result['error'])
            return jsonify({'success': False, 'message': result['error'], 'data': None}), 500

        fault_probability = result['fault_probability']

        return jsonify({
            'success': True,
            'message': '预测成功',
            'data': {
                'fault_probability': fault_probability,
                'is_fault': fault_probability >= 0.7,
                'device_type': device_type,
                'feature_count': result.get('features', []).__len__(),
                'history_length': len(history)
            }
        })

    except Exception as e:
        app.logger.error(f"预测失败: {str(e)}", exc_info=True)
        return jsonify({'success': False, 'message': '服务器内部错误', 'data': None}), 500


@app.route('/ml/predict/dynamic', methods=['POST'])
def predict_dynamic():
    """动态传感器预测接口（v2.0）。

    直接接收完整的传感器历史记录，提取15维特征并预测。
    适用于前端或外部系统已构建好时序数据窗的场景。

    请求体:
    {
        "device_type": "工业机器人",
        "sensor_data": [
            {"timestamp": "2026-04-06T10:00:00", "sensor_code": "temperature", "value": 85},
            ...
        ]
    }
    """
    try:
        data = request.get_json()
        device_type = data.get('device_type', '工业机器人')
        sensor_data = data.get('sensor_data', [])

        if not sensor_data:
            return jsonify({'success': False, 'message': '请提供传感器数据', 'data': None}), 400

        if device_type not in DEVICE_PROFILES:
            return jsonify({
                'success': False,
                'message': f'未知的设备类型: {device_type}',
                'data': None
            }), 400

        result = predict_with_raw(device_type, sensor_data)

        if 'error' in result:
            return jsonify({'success': False, 'message': result['error'], 'data': None}), 400

        return jsonify({
            'success': True,
            'message': '预测成功',
            'data': {
                'faultProbability': result['fault_probability'],
                'deviceType': device_type,
                'featureCount': len(result.get('features', []))
            }
        })

    except Exception as e:
        app.logger.error(f"动态预测失败: {str(e)}", exc_info=True)
        return jsonify({'success': False, 'message': f'服务器内部错误: {str(e)}', 'data': None}), 500


@app.route('/ml/chart/trend', methods=['GET'])
def chart_trend():
    try:
        device_id = request.args.get('deviceId', 1, type=int)
        data_points = request.args.get('points', 100, type=int)

        data = None
        try:
            collector_url = os.getenv('COLLECTOR_SERVICE_URL', 'http://localhost:8083')
            resp = requests.get(f'{collector_url}/collector/metrics/{device_id}', timeout=5)
            if resp.status_code == 200:
                result = resp.json()
                if result.get('code') == 200 and result.get('data'):
                    metrics = result['data']
                    timestamps = []
                    temperature = []
                    vibration = []
                    pressure = []
                    for m in metrics:
                        ts = m.get('timestamp', '')
                        timestamps.append(ts.split('T')[1][:8] if 'T' in ts else ts)
                        temperature.append(m.get('temperature', 0))
                        vibration.append(m.get('vibration', 0))
                        pressure.append(m.get('pressure', 0))
                    if temperature:
                        data = {
                            'temperature': temperature[-data_points:],
                            'vibration': vibration[-data_points:],
                            'pressure': pressure[-data_points:],
                            'timestamps': timestamps[-data_points:]
                        }
        except Exception as e:
            app.logger.error(f'获取传感器数据失败: {e}')

        img_base64 = generate_trend_chart(device_id, data_points, data=data)

        return jsonify({
            'success': True,
            'message': '图表生成成功',
            'data': {'image': img_base64, 'format': 'png'}
        })

    except Exception as e:
        return jsonify({
            'success': False,
            'message': f'图表生成失败: {str(e)}',
            'data': None
        }), 500


@app.route('/ml/model/metrics', methods=['GET'])
def get_model_metrics():
    """获取所有设备类型的模型指标。"""
    try:
        all_metrics = {}

        if _has_new_model():
            for device_type in DEVICE_PROFILES.keys():
                metrics_path = os.path.join(MODEL_ROOT, device_type, 'metrics.json')
                if os.path.exists(metrics_path):
                    with open(metrics_path, 'r', encoding='utf-8') as f:
                        all_metrics[device_type] = json.load(f)
                else:
                    all_metrics[device_type] = {'error': '模型未训练'}

            # 汇总指标
            summary = {
                'model_version': '2.0',
                'device_types': list(DEVICE_PROFILES.keys()),
                'device_metrics': all_metrics
            }
        else:
            # 旧版兼容
            legacy_metrics_path = os.path.join(MODEL_ROOT, 'metrics.json')
            if os.path.exists(legacy_metrics_path):
                with open(legacy_metrics_path, 'r') as f:
                    summary = json.load(f)
                summary['model_version'] = '1.0'
            else:
                return jsonify({
                    'success': False,
                    'message': '模型未训练',
                    'data': None
                }), 404

        return jsonify({
            'success': True,
            'message': '获取模型指标成功',
            'data': summary
        })

    except Exception as e:
        app.logger.error(f"获取模型指标失败: {e}", exc_info=True)
        return jsonify({'success': False, 'message': str(e), 'data': None}), 500


@app.route('/ml/model/retrain', methods=['POST'])
@limiter.limit("1 per hour")
@require_api_key
def retrain_model():
    """重新训练模型。可选指定单个设备类型，不指定则训练全部。

    请求体（可选）:
    {
        "device_type": "工业机器人"  // 不传则训练所有类型
    }
    """
    try:
        data = request.get_json(silent=True) or {}
        device_type = data.get('device_type')

        _sensor_history.clear()

        if device_type:
            result = train_single_device_type(device_type, min_samples=500, influx_hours=168)
            predictor.reload()
            return jsonify({
                'success': True,
                'message': f'{device_type} 模型训练成功',
                'data': {device_type: result}
            })
        else:
            metadata = train_all_with_real_data(min_samples=500, influx_hours=168)
            predictor.reload()
            return jsonify({
                'success': True,
                'message': '全部模型训练成功',
                'data': metadata.get('device_results', {})
            })

    except Exception as e:
        app.logger.error(f"模型训练失败: {e}", exc_info=True)
        return jsonify({
            'success': False,
            'message': f'模型训练失败: {str(e)}',
            'data': None
        }), 500


@app.route('/ml/model/versions', methods=['GET'])
def list_model_versions():
    """查询所有设备类型的模型版本历史。"""
    try:
        version_manager = get_version_manager()
        result = {}
        for dtype in DEVICE_PROFILES.keys():
            result[dtype] = version_manager.list_versions(dtype)
        return jsonify({'success': True, 'data': result})
    except Exception as e:
        app.logger.error(f"获取版本列表失败: {e}", exc_info=True)
        return jsonify({'success': False, 'message': str(e), 'data': None}), 500


@app.route('/ml/model/rollback', methods=['POST'])
@require_api_key
def rollback_model():
    """回滚到指定版本。

    请求体:
    {
        "device_type": "工业机器人",
        "version": "v2"
    }
    """
    try:
        data = request.get_json()
        device_type = data.get('device_type')
        version = data.get('version')

        if not device_type or not version:
            return jsonify({'success': False, 'message': '缺少 device_type 或 version'}), 400

        version_manager = get_version_manager()
        version_manager.rollback(device_type, version)

        # 重新加载模型
        predictor.reload()

        return jsonify({
            'success': True,
            'message': f'已回滚 {device_type} 到 {version}',
            'data': {'device_type': device_type, 'version': version}
        })
    except Exception as e:
        app.logger.error(f"回滚失败: {e}", exc_info=True)
        return jsonify({'success': False, 'message': f'回滚失败: {str(e)}', 'data': None}), 500


@app.route('/ml/chart/fault-probability', methods=['GET'])
def get_fault_probability_chart():
    """生成故障概率趋势图（Matplotlib）。"""
    try:
        from chart import generate_fault_probability_chart

        device_id = request.args.get('deviceId', '1')
        device_name = request.args.get('deviceName', '')
        hours = int(request.args.get('hours', 24))

        # 从 collector-service 获取故障概率历史
        history_data = []
        try:
            collector_url = os.getenv('COLLECTOR_SERVICE_URL', 'http://localhost:8083')
            resp = requests.get(
                f'{collector_url}/collector/fault-probability-history/{device_id}',
                params={'hours': hours},
                timeout=5
            )
            if resp.status_code == 200:
                result = resp.json()
                if result.get('code') == 200 and result.get('data'):
                    history_data = result['data']
        except Exception as e:
            app.logger.warning('获取故障概率历史失败: %s', e)

        img_base64 = generate_fault_probability_chart(device_id, history_data, device_name)

        return jsonify({
            'success': True,
            'data': {
                'image': img_base64,
                'format': 'png'
            }
        })
    except Exception as e:
        app.logger.error(f"生成故障概率图失败: {e}", exc_info=True)
        return jsonify({'success': False, 'message': str(e), 'data': None}), 500


@app.errorhandler(Exception)
def handle_exception(e):
    import traceback
    error_msg = traceback.format_exc()
    app.logger.error(f"Error: {str(e)}\n{error_msg}")

    return jsonify({
        'success': False,
        'message': f'服务器内部错误: {str(e)}',
        'data': None
    }), 500


if __name__ == '__main__':
    print('Starting ML Service v2.0...')
    print(f'Model root: {MODEL_ROOT}')
    print(f'Metadata exists: {os.path.exists(METADATA_FILE)}')
    print(f'Loaded models: {list(predictor._predictors.keys())}')

    debug_mode = os.getenv('FLASK_DEBUG', 'False').lower() == 'true'
    host = os.getenv('FLASK_HOST', '127.0.0.1')
    port = int(os.getenv('FLASK_PORT', 5000))

    print(f'Starting Flask on {host}:{port}')
    app.run(host=host, port=port, debug=debug_mode)
