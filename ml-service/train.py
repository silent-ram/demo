import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score, roc_auc_score
import joblib
import os
import json
from datetime import datetime

from feature_extractor import FeatureExtractor
from config_loader import get_device_profiles_legacy, get_device_type_names
from data_fetcher import get_data_fetcher
from model_version_manager import get_version_manager

MODEL_ROOT = 'model'
WINDOW_SIZE = 10
MOVING_AVG_WINDOW = 5

MIN_WINDOWS_PER_TYPE = 500


def train_model_for_device_type(device_type: str, X: np.ndarray, y: np.ndarray,
                                feature_names: list) -> dict:
    """为指定设备类型训练 Logistic Regression 模型。

    按时间切分：前70%训练，后30%测试（避免时序数据泄露）。
    """
    if len(X) == 0:
        raise ValueError(f'设备类型 {device_type} 无有效训练数据')

    n_train = int(len(X) * 0.7)
    X_train, X_test = X[:n_train], X[n_train:]
    y_train, y_test = y[:n_train], y[n_train:]

    print(f'[{device_type}] 总样本: {len(X)}, 训练集: {len(X_train)}, 测试集: {len(X_test)}')
    print(f'[{device_type}] 正例(故障): {sum(y)}, 负例(正常): {len(y) - sum(y)}')

    if sum(y) < 10:
        print(f'警告: [{device_type}] 故障样本过少，模型可能无法学习故障模式')

    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_test_scaled = scaler.transform(X_test)

    model = LogisticRegression(random_state=42, max_iter=1000, class_weight='balanced')
    model.fit(X_train_scaled, y_train)

    y_pred = model.predict(X_test_scaled)
    y_pred_proba = model.predict_proba(X_test_scaled)[:, 1]

    accuracy = accuracy_score(y_test, y_pred)
    precision = precision_score(y_test, y_pred, zero_division=0)
    recall = recall_score(y_test, y_pred, zero_division=0)
    f1 = f1_score(y_test, y_pred, zero_division=0)

    try:
        roc_auc = roc_auc_score(y_test, y_pred_proba)
    except ValueError:
        roc_auc = 0.0

    coef_dict = {}
    for name, coef in zip(feature_names, model.coef_[0]):
        coef_dict[name] = float(coef)

    metrics = {
        'accuracy': float(accuracy),
        'precision': float(precision),
        'recall': float(recall),
        'f1_score': float(f1),
        'roc_auc': float(roc_auc),
        'training_samples': int(len(X_train)),
        'test_samples': int(len(X_test)),
        'positive_samples': int(sum(y)),
        'negative_samples': int(len(y) - sum(y)),
        'feature_count': int(len(feature_names)),
        'feature_importance': coef_dict,
        'model_loaded': True,
        'trained_at': datetime.now().isoformat()
    }

    print(f'\n[{device_type}] 模型指标:')
    print(f'  准确率: {accuracy:.4f}')
    print(f'  精确率: {precision:.4f}')
    print(f'  召回率: {recall:.4f}')
    print(f'  F1分数: {f1:.4f}')
    print(f'  ROC AUC: {roc_auc:.4f}')

    device_dir = os.path.join(MODEL_ROOT, device_type)
    os.makedirs(device_dir, exist_ok=True)

    model_path = os.path.join(device_dir, 'fault_model.pkl')
    scaler_path = os.path.join(device_dir, 'scaler.pkl')

    joblib.dump(model, model_path)
    joblib.dump(scaler, scaler_path)

    print(f'  模型已保存: {model_path}')

    return metrics


def train_all_with_real_data(min_samples: int = MIN_WINDOWS_PER_TYPE, influx_hours: int = 168):
    """
    统一训练入口：从 InfluxDB 查询真实传感器数据 + fault_probability 标签训练。

    数据流：
    1. 从 InfluxDB 查询 temperature, vibration, pressure, fault_probability
    2. 按设备类型分组
    3. 滑动窗口(10)提取15维特征
    4. 标签：连续5点 >=0.7 → 故障，连续5点 <=0.1 → 正常，其余丢弃
    5. 每种类型 >= 500 窗口才训练
    6. Logistic Regression (70/30 时间切分)
    7. 新模型 vs 旧模型对比（F1 不低于旧模型95%才替换）
    """
    print('=' * 60)
    print('统一训练管道 v3.0 — InfluxDB 真实数据 + fault_probability 标签')
    print('=' * 60)

    fetcher = get_data_fetcher()
    version_manager = get_version_manager()
    all_metrics = {}
    all_results = {}
    feature_names = FeatureExtractor.get_feature_names()

    for device_type in get_device_type_names():
        print(f'\n--- 训练设备类型: {device_type} ---')
        try:
            X, y, info = fetcher.fetch_training_data(
                device_type, hours=influx_hours, min_samples=min_samples
            )

            print(f'  InfluxDB 数据: {info["real_samples"]} 样本, '
                  f'故障: {info["positive"]}, 正常: {info["negative"]}, '
                  f'来源: {info["source"]}')

            if info['source'] == 'insufficient' or len(X) < min_samples:
                print(f'  跳过: 有效样本不足（需要 >= {min_samples}）')
                all_metrics[device_type] = {
                    'error': f'数据不足: 仅 {len(X)} 个有效窗口（需 >= {min_samples}）',
                    'real_samples': info['real_samples']
                }
                all_results[device_type] = {
                    'error': all_metrics[device_type]['error'],
                    'skipped': True
                }
                continue

            # 训练新模型
            new_metrics = train_model_for_device_type(device_type, X, y, feature_names)

            # 版本管理：初始化结构
            version_manager.init_version_structure(device_type)

            # 与旧模型对比
            old_metrics = _load_old_metrics(device_type, version_manager)
            improvement = {'f1': 0.0, 'roc_auc': 0.0}
            should_replace = True

            if old_metrics:
                old_f1 = old_metrics.get('f1_score', 0)
                old_roc = old_metrics.get('roc_auc', 0)
                improvement['f1'] = new_metrics['f1_score'] - old_f1
                improvement['roc_auc'] = new_metrics['roc_auc'] - old_roc

                if new_metrics['f1_score'] < old_f1 * 0.95:
                    should_replace = False
                    print(f'  新模型 F1 ({new_metrics["f1_score"]:.4f}) '
                          f'低于旧模型 ({old_f1:.4f}) 的95%，保留旧模型')

            # 保存新版本
            new_metrics['improvement'] = improvement
            version = version_manager.save_new_version(device_type, None, None, new_metrics)

            if should_replace:
                # 需要重新训练来获取 model 和 scaler 对象用于版本管理
                n_train = int(len(X) * 0.7)
                scaler = StandardScaler()
                X_train_scaled = scaler.fit_transform(X[:n_train])
                model = LogisticRegression(random_state=42, max_iter=1000, class_weight='balanced')
                model.fit(X_train_scaled, y[:n_train])

                version_path = os.path.join(MODEL_ROOT, device_type, version)
                os.makedirs(version_path, exist_ok=True)
                joblib.dump(model, os.path.join(version_path, 'fault_model.pkl'))
                joblib.dump(scaler, os.path.join(version_path, 'scaler.pkl'))

                version_manager.activate_version(device_type, version)
                print(f'  新模型已激活: v{version}')

            all_metrics[device_type] = new_metrics
            all_results[device_type] = {
                'new_metrics': new_metrics,
                'old_metrics': old_metrics,
                'improvement': improvement,
                'should_replace': should_replace,
                'version': version,
                'data_info': info
            }

        except Exception as e:
            print(f'[{device_type}] 训练失败: {e}')
            all_metrics[device_type] = {'error': str(e)}
            all_results[device_type] = {'error': str(e)}

    fetcher.close()

    metadata = {
        'version': '3.0.0',
        'created_at': datetime.now().isoformat(),
        'feature_extractor_version': '2.0',
        'feature_count': FeatureExtractor.get_expected_feature_count(),
        'window_size': WINDOW_SIZE,
        'moving_avg_window': MOVING_AVG_WINDOW,
        'label_strategy': 'fault_probability',
        'label_rules': {
            'fault': f'连续{5}个点 fault_probability >= {0.7}',
            'normal': f'连续{5}个点 fault_probability <= {0.1}',
            'min_windows': MIN_WINDOWS_PER_TYPE
        },
        'device_types': get_device_type_names(),
        'device_metrics': all_metrics,
        'device_results': all_results,
        'data_source': 'influxdb'
    }

    metadata_path = os.path.join(MODEL_ROOT, 'metadata.json')
    with open(metadata_path, 'w', encoding='utf-8') as f:
        json.dump(metadata, f, ensure_ascii=False, indent=2)

    print(f'\n全局元数据已保存: {metadata_path}')
    print('=' * 60)
    print('训练完成')
    print('=' * 60)

    return metadata


def train_single_device_type(device_type: str, min_samples: int = MIN_WINDOWS_PER_TYPE,
                             influx_hours: int = 168) -> dict:
    """训练单个设备类型的模型。

    Returns:
        训练结果 dict（同 train_all_with_real_data 中单个设备的结果结构）
    """
    valid_types = get_device_type_names()
    if device_type not in valid_types:
        return {'error': f'未知的设备类型: {device_type}，支持: {valid_types}'}

    print('=' * 60)
    print(f'单类型训练 — {device_type}')
    print('=' * 60)

    fetcher = get_data_fetcher()
    version_manager = get_version_manager()
    feature_names = FeatureExtractor.get_feature_names()

    result = {}
    try:
        X, y, info = fetcher.fetch_training_data(
            device_type, hours=influx_hours, min_samples=min_samples
        )

        print(f'  数据: {info["real_samples"]} 样本, 故障: {info["positive"]}, 正常: {info["negative"]}')

        if info['source'] == 'insufficient' or len(X) < min_samples:
            result = {
                'error': f'数据不足: 仅 {len(X)} 个有效窗口（需 >= {min_samples}）',
                'data_info': info
            }
        else:
            new_metrics = train_model_for_device_type(device_type, X, y, feature_names)
            version_manager.init_version_structure(device_type)

            old_metrics = _load_old_metrics(device_type, version_manager)
            improvement = {'f1': 0.0, 'roc_auc': 0.0}
            should_replace = True

            if old_metrics:
                improvement['f1'] = new_metrics['f1_score'] - old_metrics.get('f1_score', 0)
                improvement['roc_auc'] = new_metrics['roc_auc'] - old_metrics.get('roc_auc', 0)
                if new_metrics['f1_score'] < old_metrics.get('f1_score', 0) * 0.95:
                    should_replace = False

            new_metrics['improvement'] = improvement
            version = version_manager.save_new_version(device_type, None, None, new_metrics)

            if should_replace:
                n_train = int(len(X) * 0.7)
                scaler = StandardScaler()
                X_train_scaled = scaler.fit_transform(X[:n_train])
                model = LogisticRegression(random_state=42, max_iter=1000, class_weight='balanced')
                model.fit(X_train_scaled, y[:n_train])

                version_path = os.path.join(MODEL_ROOT, device_type, version)
                os.makedirs(version_path, exist_ok=True)
                joblib.dump(model, os.path.join(version_path, 'fault_model.pkl'))
                joblib.dump(scaler, os.path.join(version_path, 'scaler.pkl'))
                version_manager.activate_version(device_type, version)
                print(f'  新模型已激活: v{version}')

            result = {
                'new_metrics': new_metrics,
                'old_metrics': old_metrics,
                'improvement': improvement,
                'should_replace': should_replace,
                'version': version,
                'data_info': info
            }

    except Exception as e:
        print(f'[{device_type}] 训练失败: {e}')
        result = {'error': str(e)}

    fetcher.close()

    # 更新 metadata.json 中该设备类型的结果
    metadata_path = os.path.join(MODEL_ROOT, 'metadata.json')
    if os.path.exists(metadata_path):
        with open(metadata_path, 'r', encoding='utf-8') as f:
            metadata = json.load(f)
    else:
        metadata = {'version': '3.0.0', 'device_types': valid_types, 'device_metrics': {}, 'device_results': {}}

    if 'error' not in result:
        metadata.setdefault('device_metrics', {})[device_type] = result.get('new_metrics', {})
    metadata.setdefault('device_results', {})[device_type] = result
    metadata['updated_at'] = datetime.now().isoformat()

    with open(metadata_path, 'w', encoding='utf-8') as f:
        json.dump(metadata, f, ensure_ascii=False, indent=2)

    print(f'训练完成: {device_type}')
    return result


def _load_old_metrics(device_type: str, version_manager) -> dict | None:
    """加载旧模型的指标用于对比。"""
    try:
        active_version = version_manager.get_active_version(device_type)
        if active_version:
            old_metrics_path = os.path.join(
                MODEL_ROOT, device_type, active_version, 'metrics.json'
            )
            if os.path.exists(old_metrics_path):
                with open(old_metrics_path, 'r', encoding='utf-8') as f:
                    return json.load(f)
    except Exception as e:
        print(f'  加载旧模型指标失败: {e}')
    return None


if __name__ == '__main__':
    train_all_with_real_data(min_samples=500, influx_hours=168)
