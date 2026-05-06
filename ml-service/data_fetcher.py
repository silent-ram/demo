"""
InfluxDB 数据提取器 v2.0

从 InfluxDB 查询设备传感器历史数据 + fault_probability 标签，用于模型训练。
标签策略：基于 fault_probability 字段，连续5点 >=0.7 为故障，连续5点 <=0.1 为正常，其余丢弃。
"""

import os
import numpy as np
from datetime import datetime, timedelta
from influxdb_client import InfluxDBClient
import logging

from config_loader import get_sensor_codes, get_device_type_names
from feature_extractor import FeatureExtractor

logger = logging.getLogger(__name__)

INFLUXDB_URL = os.environ.get('INFLUXDB_URL', 'http://localhost:8086')
INFLUXDB_TOKEN = os.environ.get('INFLUXDB_TOKEN', '')
INFLUXDB_ORG = os.environ.get('INFLUXDB_ORG', 'cy')
INFLUXDB_BUCKET = os.environ.get('INFLUXDB_BUCKET', 'sensor_data')
MEASUREMENT = 'sensor_data'

FAULT_THRESHOLD = 0.7
NORMAL_THRESHOLD = 0.1
CONSECUTIVE_POINTS = 5
MIN_WINDOWS_PER_TYPE = 500


def _to_float(val):
    if val is None:
        return None
    try:
        return float(val)
    except (ValueError, TypeError):
        return None


class InfluxDBDataFetcher:
    """从 InfluxDB 提取传感器历史数据用于训练。"""

    def __init__(self):
        self.client = InfluxDBClient(
            url=INFLUXDB_URL,
            token=INFLUXDB_TOKEN,
            org=INFLUXDB_ORG
        )
        self.query_api = self.client.query_api()
        self.extractor = FeatureExtractor()

    def query_device_history(self, device_id: str, hours: int = 168) -> list:
        """
        查询某设备最近 N 小时的传感器数据（含 fault_probability）。

        Returns:
            [{timestamp, temperature, vibration, pressure, fault_probability}, ...]
        """
        flux = self._build_flux_query(device_id=device_id, hours=hours)
        return self._execute_query(flux, device_id=device_id)

    def query_device_history_by_type(self, device_type: str, hours: int = 168) -> list:
        """
        查询某设备类型下所有设备的传感器数据（含 fault_probability）。

        Returns:
            [{device_id, timestamp, temperature, vibration, pressure, fault_probability}, ...]
        """
        flux = self._build_flux_query(hours=hours)
        results = self._execute_query(flux)

        if device_type:
            from config_loader import get_device_profiles
            profiles = get_device_profiles()
            valid_names = set(profiles.keys())
            if device_type not in valid_names:
                logger.warning('未知的设备类型: %s', device_type)
                return []

        return results

    def _build_flux_query(self, device_id: str = None, hours: int = 168) -> str:
        """构建 Flux 查询语句，pivot metric_name 展开各指标列。"""
        start_time = (datetime.utcnow() - timedelta(hours=hours)).isoformat() + 'Z'

        lines = [
            f'from(bucket: "{INFLUXDB_BUCKET}")',
            f'  |> range(start: {start_time})',
            f'  |> filter(fn: (r) => r["_measurement"] == "{MEASUREMENT}")',
            '  |> filter(fn: (r) => r["_field"] == "value")',
        ]
        if device_id:
            lines.append(f'  |> filter(fn: (r) => r["device_id"] == "{device_id}")')
        lines.append('  |> pivot(rowKey: ["_time"], columnKey: ["metric_name"], valueColumn: "_value")')

        return '\n'.join(lines)

    def _execute_query(self, flux: str, device_id: str = None) -> list:
        """执行 Flux 查询并返回结果列表。"""
        try:
            tables = self.query_api.query(flux, org=INFLUXDB_ORG)
            results = []
            for table in tables:
                for record in table.records:
                    row = {
                        'timestamp': record.get_time().isoformat(),
                        'temperature': _to_float(record.values.get('temperature')),
                        'vibration': _to_float(record.values.get('vibration')),
                        'pressure': _to_float(record.values.get('pressure')),
                        'fault_probability': _to_float(record.values.get('fault_probability')),
                    }
                    if device_id is None:
                        row['device_id'] = str(record.values.get('device_id', ''))
                    results.append(row)

            logger.info('查询到 %d 条历史记录', len(results))
            return results
        except Exception as e:
            logger.error('查询 InfluxDB 失败: %s', e)
            return []

    def extract_features_with_proba_labels(self, raw_data: list, device_type: str,
                                           window_size: int = 10) -> tuple:
        """
        将原始数据转换为特征向量 + 基于 fault_probability 的标签。

        标签规则：
        - 窗口内连续 CONSECUTIVE_POINTS 个 fault_probability >= FAULT_THRESHOLD → label=1
        - 窗口内连续 CONSECUTIVE_POINTS 个 fault_probability <= NORMAL_THRESHOLD → label=0
        - 其余窗口丢弃

        Returns:
            X (N, 15), y (N,)
        """
        if len(raw_data) < window_size:
            logger.warning('数据不足 %d 条，无法提取特征', window_size)
            return np.array([]), np.array([])

        sensor_codes = get_sensor_codes()
        features_list = []
        labels = []

        for i in range(len(raw_data) - window_size + 1):
            window = raw_data[i:i + window_size]

            # 检查窗口内是否有缺失值
            has_missing = False
            proba_values = []
            for point in window:
                if (point.get('temperature') is None or
                        point.get('vibration') is None or
                        point.get('pressure') is None):
                    has_missing = True
                    break
                fp = point.get('fault_probability')
                if fp is None:
                    has_missing = True
                    break
                proba_values.append(float(fp))
            if has_missing:
                continue

            # 基于连续 fault_probability 判定标签
            label = self._compute_label_from_proba(proba_values)
            if label is None:
                continue

            # 构建传感器数据格式
            sensor_data = []
            for point in window:
                ts = point['timestamp']
                for code in sensor_codes:
                    sensor_data.append({
                        'timestamp': ts,
                        'sensor_code': code,
                        'value': point[code]
                    })

            features = self.extractor.extract_features(sensor_data, device_type)
            if not features or len(features) != self.extractor.get_expected_feature_count():
                continue

            features_list.append(features)
            labels.append(label)

        if not features_list:
            logger.warning('未能从 InfluxDB 数据中提取有效特征（标签筛选后）')
            return np.array([]), np.array([])

        X = np.array(features_list)
        y = np.array(labels)
        logger.info('提取到 %d 个训练样本（故障: %d, 正常: %d）',
                    len(y), sum(y), len(y) - sum(y))
        return X, y

    @staticmethod
    def _compute_label_from_proba(proba_values: list) -> int | None:
        """基于连续 fault_probability 判定标签。

        连续 CONSECUTIVE_POINTS 个 >= FAULT_THRESHOLD → 1
        连续 CONSECUTIVE_POINTS 个 <= NORMAL_THRESHOLD → 0
        其余 → None（丢弃）
        """
        has_consecutive_fault = False
        has_consecutive_normal = False

        fault_streak = 0
        normal_streak = 0

        for v in proba_values:
            if v >= FAULT_THRESHOLD:
                fault_streak += 1
                normal_streak = 0
                if fault_streak >= CONSECUTIVE_POINTS:
                    has_consecutive_fault = True
                    break
            elif v <= NORMAL_THRESHOLD:
                normal_streak += 1
                fault_streak = 0
                if normal_streak >= CONSECUTIVE_POINTS:
                    has_consecutive_normal = True
                    break
            else:
                fault_streak = 0
                normal_streak = 0

        if has_consecutive_fault:
            return 1
        if has_consecutive_normal:
            return 0
        return None

    def fetch_training_data(self, device_type: str, hours: int = 168,
                            min_samples: int = MIN_WINDOWS_PER_TYPE) -> tuple:
        """
        获取指定设备类型的训练数据。

        Returns:
            X, y, info
            info: {'real_samples': N, 'positive': P, 'negative': N, 'source': 'influxdb' | 'insufficient'}
        """
        logger.info('从 InfluxDB 获取 %s 的训练数据（最近 %d 小时）', device_type, hours)

        raw_data = self.query_device_history_by_type(device_type, hours)

        if len(raw_data) < min_samples:
            logger.warning('InfluxDB 原始数据不足: %d < %d', len(raw_data), min_samples)
            return np.array([]), np.array([]), {
                'real_samples': len(raw_data), 'positive': 0, 'negative': 0,
                'source': 'insufficient'
            }

        X, y = self.extract_features_with_proba_labels(raw_data, device_type)

        if len(X) < min_samples:
            logger.warning('标签筛选后有效样本不足: %d < %d', len(X), min_samples)
            return X, y, {
                'real_samples': len(X),
                'positive': int(sum(y)) if len(y) > 0 else 0,
                'negative': int(len(y) - sum(y)) if len(y) > 0 else 0,
                'source': 'insufficient'
            }

        return X, y, {
            'real_samples': len(X),
            'positive': int(sum(y)),
            'negative': int(len(y) - sum(y)),
            'source': 'influxdb'
        }

    def close(self):
        """关闭 InfluxDB 连接。"""
        self.client.close()


_fetcher = None


def get_data_fetcher() -> InfluxDBDataFetcher:
    """获取数据提取器单例。"""
    global _fetcher
    if _fetcher is None:
        _fetcher = InfluxDBDataFetcher()
    return _fetcher


if __name__ == '__main__':
    fetcher = InfluxDBDataFetcher()
    print('设备类型列表:', get_device_type_names())
    for dtype in get_device_type_names():
        X, y, info = fetcher.fetch_training_data(dtype, hours=24)
        print(f'{dtype}: 样本数={len(X)}, 故障={sum(y) if len(y) > 0 else 0}, '
              f'正常={len(y) - sum(y) if len(y) > 0 else 0}, 来源={info["source"]}')
    fetcher.close()
