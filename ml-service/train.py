import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score, roc_auc_score
import joblib
import os
import json
from datetime import datetime

from feature_extractor import FeatureExtractor, SENSOR_CODES
from config_loader import get_device_profiles_legacy, get_stable_params
from data_fetcher import get_data_fetcher

MODEL_ROOT = 'model'
WINDOW_SIZE = 10
MOVING_AVG_WINDOW = 5


def generate_device_data(device_type: str, n_samples: int = 5000,
                         window_size: int = WINDOW_SIZE,
                         fault_rate: float = 0.15,
                         random_seed: int = 42):
    """为指定设备类型生成带有渐进故障注入的时序数据。

    Args:
        device_type: 设备类型，如'工业机器人'
        n_samples: 生成的样本数（窗口数）
        window_size: 每个窗口包含的原始点数
        fault_rate: 故障样本比例
        random_seed: 随机种子

    Returns:
        X: 特征矩阵，shape (n_samples, 15)
        y: 标签向量，shape (n_samples,)
        feature_names: 特征名称列表
    """
    np.random.seed(random_seed)
    profile = get_device_profiles_legacy()[device_type]

    # 为每种传感器生成连续时序数据（随机游走）
    # 总原始点数 = n_samples * step + window_size，step=5 保证窗口间有重叠但不完全重复
    step = 5
    total_points = n_samples * step + window_size

    raw_data = {code: [] for code in SENSOR_CODES}
    raw_timestamps = []

    # 初始化基线
    current_values = {code: profile[code]['baseline'] for code in SENSOR_CODES}

    for t in range(total_points):
        timestamp = f'2026-01-01T{t // 3600:02d}:{(t // 60) % 60:02d}:{t % 60:02d}'
        raw_timestamps.append(timestamp)

        for code in SENSOR_CODES:
            baseline = profile[code]['baseline']
            stable_cfg = get_stable_params(device_type, code)
            noise_ratio = stable_cfg.get('noise_ratio', 0.02)
            drift_ratio = stable_cfg.get('drift_ratio', 0.005)
            bound_ratio = stable_cfg.get('bound_ratio', 1.5)
            noise_std = baseline * noise_ratio
            drift = np.random.normal(0, baseline * drift_ratio)

            current_values[code] += drift + np.random.normal(0, noise_std)
            # 边界保护：不低于0，不偏离基线不超过 bound_ratio
            current_values[code] = max(0.0, min(current_values[code], baseline * bound_ratio))

            raw_data[code].append(current_values[code])

    # 为每个时间点决定状态：正常、短暂尖峰、渐进故障
    # 使用连续状态机模拟真实设备运行
    point_states = ['normal'] * total_points  # normal | spike | fault | recovering
    current_state = 'normal'
    state_counter = 0

    for t in range(total_points):
        if current_state == 'normal':
            # 正常状态：小概率进入故障（15%），或短暂尖峰（10%）
            r = np.random.random()
            if r < 0.05:  # 5%概率开始渐进故障
                current_state = 'fault'
                state_counter = np.random.randint(15, 40)  # 故障持续15-40个点
            elif r < 0.12:  # 7%概率短暂尖峰（环境温度骤升）
                current_state = 'spike'
                state_counter = np.random.randint(3, 6)  # 尖峰持续3-5个点
        elif current_state == 'spike':
            state_counter -= 1
            if state_counter <= 0:
                current_state = 'normal'
        elif current_state == 'fault':
            state_counter -= 1
            if state_counter <= 0:
                current_state = 'recovering'
                state_counter = np.random.randint(10, 20)  # 恢复10-20个点
        elif current_state == 'recovering':
            state_counter -= 1
            if state_counter <= 0:
                current_state = 'normal'

        point_states[t] = current_state

    # 根据状态调整原始数据
    for t in range(total_points):
        state = point_states[t]

        if state == 'spike':
            # 短暂尖峰：温度骤升5-10度然后恢复，这是正常波动
            for code in SENSOR_CODES:
                if code == 'temperature':
                    raw_data[code][t] += np.random.uniform(5, 10)
                elif code == 'vibration':
                    raw_data[code][t] += np.random.uniform(0.1, 0.2)
        elif state == 'fault':
            # 渐进故障：每步缓慢劣化，不是瞬间飙升
            # 故障强度随时间递增
            for code in SENSOR_CODES:
                threshold = profile[code]['threshold']
                baseline = profile[code]['baseline']
                # 计算距离阈值的距离，缓慢推进
                gap = threshold - baseline
                # 每步劣化0.5-2.0个单位，累积超过阈值
                deterioration = np.random.uniform(0.5, 2.0)
                # 让故障值在基线+阈值之间渐进上升，偶尔超调
                target_min = baseline + gap * 0.3
                target_max = threshold + gap * 0.4
                # 基于当前值的渐进偏离
                current = raw_data[code][t]
                if current < target_max:
                    raw_data[code][t] = min(current + deterioration, target_max + np.random.uniform(0, 3))
        elif state == 'recovering':
            # 恢复状态：缓慢回到基线
            for code in SENSOR_CODES:
                baseline = profile[code]['baseline']
                current = raw_data[code][t]
                raw_data[code][t] = current * 0.9 + baseline * 0.1 + np.random.normal(0, 1)

    # 构建窗口并提取特征 + 基于时序语义自动标注标签
    extractor = FeatureExtractor(window_size=window_size, moving_avg_window=MOVING_AVG_WINDOW)
    feature_names = extractor.get_feature_names()

    X = []
    y = []

    for i in range(n_samples):
        start_t = i * step
        end_t = start_t + window_size

        # 构建传感器数据列表
        sensor_data = []
        for t_offset in range(start_t, end_t):
            for code in SENSOR_CODES:
                sensor_data.append({
                    'timestamp': raw_timestamps[t_offset],
                    'sensor_code': code,
                    'value': raw_data[code][t_offset]
                })

        # 提取15维特征
        features = extractor.extract_features(sensor_data, device_type)

        if features and len(features) == extractor.get_expected_feature_count():
            # 标签策略：窗口内故障点 >= 3 = 明确故障（持续劣化）
            window_states = point_states[start_t:end_t]
            fault_count = sum(1 for s in window_states if s == 'fault')

            if fault_count >= 3:
                label = 1
            else:
                label = 0

            X.append(features)
            y.append(label)

    return np.array(X), np.array(y), feature_names


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

    print(f'[{device_type}] 特征标准化...')
    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_test_scaled = scaler.transform(X_test)

    print(f'[{device_type}] 训练 LogisticRegression...')
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
        roc_auc = 0.0  # 仅一类样本时无法计算

    # 特征重要性（系数）
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

    # 保存模型
    device_dir = os.path.join(MODEL_ROOT, device_type)
    os.makedirs(device_dir, exist_ok=True)

    model_path = os.path.join(device_dir, 'fault_model.pkl')
    scaler_path = os.path.join(device_dir, 'scaler.pkl')
    metrics_path = os.path.join(device_dir, 'metrics.json')

    joblib.dump(model, model_path)
    joblib.dump(scaler, scaler_path)

    with open(metrics_path, 'w', encoding='utf-8') as f:
        json.dump(metrics, f, ensure_ascii=False, indent=2)

    print(f'  模型已保存: {model_path}')
    print(f'  指标已保存: {metrics_path}')

    return metrics


def train_all_models(n_samples: int = 5000):
    """为所有设备类型训练独立模型。"""
    print('=' * 60)
    print('工业设备故障预测模型训练 v2.0')
    print('=' * 60)

    all_metrics = {}
    all_feature_names = None

    for device_type in get_device_profiles_legacy().keys():
        print(f'\n--- 训练设备类型: {device_type} ---')
        try:
            X, y, feature_names = generate_device_data(
                device_type, n_samples=n_samples, random_seed=hash(device_type) % 10000
            )
            all_feature_names = feature_names

            metrics = train_model_for_device_type(device_type, X, y, feature_names)
            all_metrics[device_type] = metrics

        except Exception as e:
            print(f'[{device_type}] 训练失败: {e}')
            all_metrics[device_type] = {'error': str(e)}

    # 保存全局元数据
    metadata = {
        'version': '2.0.0',
        'created_at': datetime.now().isoformat(),
        'feature_extractor_version': '2.0',
        'feature_count': FeatureExtractor.get_expected_feature_count(),
        'window_size': WINDOW_SIZE,
        'moving_avg_window': MOVING_AVG_WINDOW,
        'sensor_codes': SENSOR_CODES,
        'feature_names': all_feature_names or FeatureExtractor.get_feature_names(),
        'device_types': list(get_device_profiles_legacy().keys()),
        'device_metrics': all_metrics
    }

    metadata_path = os.path.join(MODEL_ROOT, 'metadata.json')
    with open(metadata_path, 'w', encoding='utf-8') as f:
        json.dump(metadata, f, ensure_ascii=False, indent=2)

    print(f'\n全局元数据已保存: {metadata_path}')
    print('=' * 60)
    print('训练完成')
    print('=' * 60)

    return metadata


if __name__ == '__main__':
    train_all_models(n_samples=5000)


def train_all_with_real_data(min_samples: int = 100, influx_hours: int = 168, n_sim_samples: int = 5000):
    """
    优先从 InfluxDB 读取真实数据训练模型，不足时补充模拟数据。

    这是符合毕设要求的核心训练入口：
    "从时序数据库中提取设备正常与故障状态下的特征数据，通过 Logistic 回归训练预测模型"
    """
    print('=' * 60)
    print('工业设备故障预测模型训练 v2.1 - 真实数据驱动')
    print('=' * 60)

    fetcher = get_data_fetcher()
    all_metrics = {}
    all_feature_names = None

    for device_type in get_device_profiles_legacy().keys():
        print(f'\n--- 训练设备类型: {device_type} ---')
        try:
            # 1. 优先从 InfluxDB 读取真实数据
            X_real, y_real, info = fetcher.fetch_training_data(
                device_type, hours=influx_hours, min_samples=min_samples
            )
            print(f'  InfluxDB 数据: {info["real_samples"]} 样本, 来源: {info["source"]}')

            # 2. 如果真实数据不足，补充模拟数据
            if len(X_real) < min_samples:
                print(f'  真实数据不足，补充模拟数据...')
                X_sim, y_sim, feature_names = generate_device_data(
                    device_type, n_samples=n_sim_samples,
                    random_seed=hash(device_type + str(datetime.now().timestamp())) % 10000
                )
                all_feature_names = feature_names

                if len(X_real) > 0:
                    X = np.vstack([X_real, X_sim])
                    y = np.concatenate([y_real, y_sim])
                else:
                    X = X_sim
                    y = y_sim
            else:
                X = X_real
                y = y_real
                # 获取特征名
                all_feature_names = FeatureExtractor.get_feature_names()

            print(f'  总训练样本: {len(X)} (正样本: {sum(y)}, 负样本: {len(y) - sum(y)})')

            # 3. 训练模型
            metrics = train_model_for_device_type(
                device_type, X, y,
                all_feature_names or FeatureExtractor.get_feature_names()
            )
            all_metrics[device_type] = metrics

        except Exception as e:
            print(f'[{device_type}] 训练失败: {e}')
            all_metrics[device_type] = {'error': str(e)}

    fetcher.close()

    # 保存全局元数据
    metadata = {
        'version': '2.1.0',
        'created_at': datetime.now().isoformat(),
        'feature_extractor_version': '2.0',
        'feature_count': FeatureExtractor.get_expected_feature_count(),
        'window_size': WINDOW_SIZE,
        'moving_avg_window': MOVING_AVG_WINDOW,
        'sensor_codes': SENSOR_CODES,
        'feature_names': all_feature_names or FeatureExtractor.get_feature_names(),
        'device_types': list(get_device_profiles_legacy().keys()),
        'device_metrics': all_metrics,
        'data_source': 'influxdb + simulated'
    }

    metadata_path = os.path.join(MODEL_ROOT, 'metadata.json')
    with open(metadata_path, 'w', encoding='utf-8') as f:
        json.dump(metadata, f, ensure_ascii=False, indent=2)

    print(f'\n全局元数据已保存: {metadata_path}')
    print('=' * 60)
    print('训练完成')
    print('=' * 60)

    return metadata
