"""
InfluxDB 数据提取器 v1.0

从 InfluxDB 查询设备传感器历史数据，用于模型训练。
支持按设备ID或设备类型查询，自动标注正常/故障标签。
"""

import os
import json
import numpy as np
from datetime import datetime, timedelta
from influxdb_client import InfluxDBClient
from influxdb_client.client.query_api import QueryApi
import logging

from config_loader import get_sensor_baseline, get_sensor_threshold, get_sensor_codes, get_device_type_names
from feature_extractor import FeatureExtractor

logger = logging.getLogger(__name__)

INFLUXDB_URL = os.environ.get('INFLUXDB_URL', 'http://localhost:8086')
INFLUXDB_TOKEN = os.environ.get('INFLUXDB_TOKEN', '')
INFLUXDB_ORG = os.environ.get('INFLUXDB_ORG', 'cy')
INFLUXDB_BUCKET = os.environ.get('INFLUXDB_BUCKET', 'sensor_data')
MEASUREMENT = 'sensor_data'


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
        查询某设备最近 N 小时的传感器数据。

        Returns:
            [{timestamp, temperature, vibration, pressure}, ...]
        """
        start_time = (datetime.utcnow() - timedelta(hours=hours)).isoformat() + 'Z'
        flux = f'''
        from(bucket: "{INFLUXDB_BUCKET}")
        |> range(start: {start_time})
        |> filter(fn: (r) => r["_measurement"] == "{MEASUREMENT}")
        |> filter(fn: (r) => r["device_id"] == "{device_id}")
        |> filter(fn: (r) => r["_field"] == "value")
        |> pivot(rowKey: ["_time"], columnKey: ["metric_name"], valueColumn: "_value")
        '''

        try:
            tables = self.query_api.query(flux, org=INFLUXDB_ORG)
            results = []
            for table in tables:
                for record in table.records:
                    results.append({
                        'timestamp': record.get_time().isoformat(),
                        'temperature': self._get_number(record, 'temperature'),
                        'vibration': self._get_number(record, 'vibration'),
                        'pressure': self._get_number(record, 'pressure')
                    })
            logger.info('查询到设备 %s 的 %d 条历史记录', device_id, len(results))
            return results
        except Exception as e:
            logger.error('查询 InfluxDB 失败: %s', e)
            return []

    def query_device_history_by_type(self, device_type: str, hours: int = 168) -> list:
        """
        查询某设备类型下所有设备的传感器数据。

        Returns:
            [{device_id, timestamp, temperature, vibration, pressure}, ...]
        """
        start_time = (datetime.utcnow() - timedelta(hours=hours)).isoformat() + 'Z'
        flux = f'''
        from(bucket: "{INFLUXDB_BUCKET}")
        |> range(start: {start_time})
        |> filter(fn: (r) => r["_measurement"] == "{MEASUREMENT}")
        |> filter(fn: (r) => r["_field"] == "value")
        |> pivot(rowKey: ["_time"], columnKey: ["metric_name"], valueColumn: "_value")
        '''

        try:
            tables = self.query_api.query(flux, org=INFLUXDB_ORG)
            results = []
            for table in tables:
                for record in table.records:
                    results.append({
                        'device_id': str(record.values.get('device_id', '')),
                        'timestamp': record.get_time().isoformat(),
                        'temperature': self._get_number(record, 'temperature'),
                        'vibration': self._get_number(record, 'vibration'),
                        'pressure': self._get_number(record, 'pressure')
                    })
            logger.info('查询到设备类型 %s 的 %d 条历史记录', device_type, len(results))
            return results
        except Exception as e:
            logger.error('查询 InfluxDB 失败: %s', e)
            return []

    def _get_number(self, record, key):
        """安全获取数值。"""
        val = record.values.get(key)
        if val is None:
            return None
        if isinstance(val, (int, float, np.number)):
            return float(val)
        try:
            return float(val)
        except (ValueError, TypeError):
            return None

    def extract_features_for_training(self, raw_data: list, device_type: str, window_size: int = 10) -> tuple:
        """
        将原始传感器数据转换为15维特征向量和标签。

        标签策略：
        - 如果窗口内有任何传感器的值超过阈值 → label=1（故障）
        - 否则 → label=0（正常）

        Returns:
            X (N, 15), y (N,)
        """
        if len(raw_data) < window_size:
            logger.warning('数据不足 %d 条，无法提取特征', window_size)
            return np.array([]), np.array([])

        sensor_codes = get_sensor_codes()
        thresholds = {
            'temperature': get_sensor_threshold(device_type, 'temperature'),
            'vibration': get_sensor_threshold(device_type, 'vibration'),
            'pressure': get_sensor_threshold(device_type, 'pressure')
        }

        features_list = []
        labels = []

        for i in range(len(raw_data) - window_size + 1):
            window = raw_data[i:i + window_size]

            # 检查窗口内是否有缺失值
            has_missing = False
            for point in window:
                if point['temperature'] is None or point['vibration'] is None or point['pressure'] is None:
                    has_missing = True
                    break
            if has_missing:
                continue

            # 构建传感器数据格式（与 FeatureExtractor 兼容）
            sensor_data = []
            for j, point in enumerate(window):
                ts = point['timestamp']
                for code in sensor_codes:
                    sensor_data.append({
                        'timestamp': ts,
                        'sensor_code': code,
                        'value': point[code]
                    })

            # 提取15维特征
            features = self.extractor.extract_features(sensor_data, device_type)
            if not features or len(features) != 15:
                continue

            # 标签：窗口内是否有任何值超过阈值
            is_fault = False
            for point in window:
                if (point['temperature'] and thresholds['temperature'] and
                        point['temperature'] > thresholds['temperature']):
                    is_fault = True
                    break
                if (point['vibration'] and thresholds['vibration'] and
                        point['vibration'] > thresholds['vibration']):
                    is_fault = True
                    break
                if (point['pressure'] and thresholds['pressure'] and
                        point['pressure'] > thresholds['pressure']):
                    is_fault = True
                    break

            features_list.append(features)
            labels.append(1 if is_fault else 0)

        if not features_list:
            logger.warning('未能从 InfluxDB 数据中提取有效特征')
            return np.array([]), np.array([])

        X = np.array(features_list)
        y = np.array(labels)
        logger.info('提取到 %d 个训练样本（正样本: %d, 负样本: %d）',
                   len(y), sum(y), len(y) - sum(y))
        return X, y

    def fetch_training_data(self, device_type: str, hours: int = 168, min_samples: int = 100) -> tuple:
        """
        获取指定设备类型的训练数据。

        优先从 InfluxDB 读取真实数据，不足时返回空数组由调用方补充模拟数据。

        Returns:
            X, y, source_info
            source_info: {'real_samples': N, 'source': 'influxdb' | 'insufficient'}
        """
        logger.info('从 InfluxDB 获取 %s 的训练数据（最近 %d 小时）', device_type, hours)

        # 查询该类型下所有设备的数据
        raw_data = self.query_device_history_by_type(device_type, hours)

        if len(raw_data) < min_samples:
            logger.warning('InfluxDB 数据不足: %d < %d，需要补充模拟数据', len(raw_data), min_samples)
            return np.array([]), np.array([]), {'real_samples': len(raw_data), 'source': 'insufficient'}

        X, y = self.extract_features_for_training(raw_data, device_type)

        if len(X) < min_samples // 2:
            logger.warning('特征提取后样本不足: %d，需要补充模拟数据', len(X))
            return X, y, {'real_samples': len(X), 'source': 'insufficient'}

        return X, y, {'real_samples': len(X), 'source': 'influxdb'}

    def close(self):
        """关闭 InfluxDB 连接。"""
        self.client.close()


# 模块级单例
_fetcher = None


def get_data_fetcher() -> InfluxDBDataFetcher:
    """获取数据提取器单例。"""
    global _fetcher
    if _fetcher is None:
        _fetcher = InfluxDBDataFetcher()
    return _fetcher


if __name__ == '__main__':
    fetcher = InfluxDBDataFetcher()
    # 简单测试
    print('设备类型列表:', get_device_type_names())
    for dtype in get_device_type_names():
        X, y, info = fetcher.fetch_training_data(dtype, hours=24)
        print(f'{dtype}: 样本数={len(X)}, 正样本={sum(y) if len(y) > 0 else 0}, 来源={info["source"]}')
    fetcher.close()
