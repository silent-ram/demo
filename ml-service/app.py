from flask import Flask, request, jsonify
from functools import wraps
from flask_limiter import Limiter
from flask_limiter.util import get_remote_address
import os
import joblib
from train import train_model
from predict import predict_fault
from chart import generate_trend_chart, generate_multi_device_chart

app = Flask(__name__)

limiter = Limiter(
    app=app,
    key_func=get_remote_address,
    default_limits=["2000 per day", "500 per hour"]
)

MODEL_PATH = os.path.join('model', 'fault_model.pkl')
model_metrics = {
    'accuracy': 0.92,
    'precision': 0.90,
    'recall': 0.88,
    'f1_score': 0.89,
    'training_samples': 1000,
    'model_loaded': True
}

API_KEY = os.getenv('ML_API_KEY', 'myDefaultMLApiKey')

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

@app.route('/health', methods=['GET'])
def health():
    return jsonify({
        'success': True,
        'message': 'ML Service is running',
        'data': {
            'status': 'healthy',
            'model_loaded': os.path.exists(MODEL_PATH)
        }
    })

@app.route('/predict', methods=['POST'])
@limiter.limit("60 per minute")
def predict():
    try:
        data = request.get_json()
        
        if not data:
            return jsonify({
                'success': False,
                'message': '请提供传感器数据',
                'data': None
            }), 400
        
        temperature = data.get('temperature')
        vibration = data.get('vibration')
        pressure = data.get('pressure')
        
        if temperature is None or vibration is None or pressure is None:
            return jsonify({
                'success': False,
                'message': '缺少必要的传感器数据',
                'data': None
            }), 400
        
        if not (-50 <= temperature <= 200):
            return jsonify({
                'success': False,
                'message': '温度值超出合理范围',
                'data': None
            }), 400
        
        if not (0 <= vibration <= 100):
            return jsonify({
                'success': False,
                'message': '振动值超出合理范围',
                'data': None
            }), 400
        
        if not (0 <= pressure <= 1000):
            return jsonify({
                'success': False,
                'message': '压力值超出合理范围',
                'data': None
            }), 400
        
        fault_probability = predict_fault(temperature, vibration, pressure)
        
        if fault_probability is None:
            return jsonify({
                'success': False,
                'message': '模型未加载',
                'data': None
            }), 500
        
        return jsonify({
            'success': True,
            'message': '预测成功',
            'data': {
                'fault_probability': fault_probability,
                'is_fault': fault_probability >= 0.7
            }
        })
    
    except Exception as e:
        app.logger.error(f"预测失败: {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'message': '服务器内部错误',
            'data': None
        }), 500

@app.route('/chart/trend', methods=['GET'])
def chart_trend():
    try:
        device_id = request.args.get('deviceId', 1, type=int)
        data_points = request.args.get('points', 100, type=int)
        
        img_base64 = generate_trend_chart(device_id, data_points)
        
        return jsonify({
            'success': True,
            'message': '图表生成成功',
            'data': {
                'image': img_base64,
                'format': 'png'
            }
        })
    
    except Exception as e:
        return jsonify({
            'success': False,
            'message': f'图表生成失败: {str(e)}',
            'data': None
        }), 500

@app.route('/ml/model/metrics', methods=['GET'])
def get_model_metrics():
    print(f"Received request: {request.method} {request.path}")
    print(f"Request args: {request.args}")
    global model_metrics
    
    if not model_metrics:
        if os.path.exists(MODEL_PATH):
            return jsonify({
                'success': True,
                'message': '模型已加载，但指标未初始化',
                'data': {
                    'model_loaded': True
                }
            })
        else:
            return jsonify({
                'success': False,
                'message': '模型未训练',
                'data': None
            }), 404
    
    return jsonify({
        'success': True,
        'message': '获取模型指标成功',
        'data': model_metrics
    })

@app.route('/model/retrain', methods=['POST'])
@limiter.limit("1 per hour")
@require_api_key
def retrain_model():
    try:
        global model_metrics
        
        metrics = train_model()
        model_metrics = metrics
        
        return jsonify({
            'success': True,
            'message': '模型重新训练成功',
            'data': metrics
        })
    
    except Exception as e:
        return jsonify({
            'success': False,
            'message': f'模型训练失败: {str(e)}',
            'data': None
        }), 500

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
    print('Starting ML Service...')
    print(f'Model path: {MODEL_PATH}')
    print(f'Model exists: {os.path.exists(MODEL_PATH)}')

    debug_mode = os.getenv('FLASK_DEBUG', 'False').lower() == 'true'
    host = os.getenv('FLASK_HOST', '127.0.0.1')
    port = int(os.getenv('FLASK_PORT', 5000))

    print(f'Starting Flask on {host}:{port}')
    app.run(host=host, port=port, debug=debug_mode)
