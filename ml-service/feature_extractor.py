"""
时序特征提取器模块 v2.0

用于从传感器时序数据中提取统一的15维特征向量。
每种设备类型独立建模，特征提取逻辑保持一致。
"""

from typing import Dict, List
import numpy as np
import logging

logger = logging.getLogger(__name__)

# 设备类型基线与阈值配置
# baseline: 设备正常运行时的典型值
# threshold: 告警阈值（用于 accumulation 计算）
DEVICE_PROFILES = {
    '工业机器人': {
        'temperature': {'baseline': 65.0, 'threshold': 80.0},
        'vibration':   {'baseline': 0.25, 'threshold': 0.6},
        'pressure':    {'baseline': 100.0, 'threshold': 130.0},
    },
    '数控机床': {
        'temperature': {'baseline': 55.0, 'threshold': 80.0},
        'vibration':   {'baseline': 0.20, 'threshold': 0.6},
        'pressure':    {'baseline': 90.0, 'threshold': 130.0},
    },
    '输送设备': {
        'temperature': {'baseline': 50.0, 'threshold': 60.0},
        'vibration':   {'baseline': 0.30, 'threshold': 0.5},
        'pressure':    {'baseline': 80.0, 'threshold': 100.0},
    },
    '焊接设备': {
        'temperature': {'baseline': 85.0, 'threshold': 100.0},
        'vibration':   {'baseline': 0.15, 'threshold': 0.5},
        'pressure':    {'baseline': 110.0, 'threshold': 140.0},
    },
    '压力设备': {
        'temperature': {'baseline': 70.0, 'threshold': 90.0},
        'vibration':   {'baseline': 0.20, 'threshold': 0.5},
        'pressure':    {'baseline': 120.0, 'threshold': 150.0},
    },
    '包装设备': {
        'temperature': {'baseline': 45.0, 'threshold': 50.0},
        'vibration':   {'baseline': 0.10, 'threshold': 0.4},
        'pressure':    {'baseline': 70.0, 'threshold': 90.0},
    },
}

# 固定传感器顺序，保证特征向量维度一致
SENSOR_CODES = ['temperature', 'vibration', 'pressure']


class FeatureExtractor:
    """时序特征提取器 v2.0

    输出固定15维特征向量，顺序为：
    [temp_current, temp_trend, temp_volatility, temp_accumulation, temp_moving_avg,
     vib_current,  vib_trend,  vib_volatility,  vib_accumulation,  vib_moving_avg,
     pres_current, pres_trend, pres_volatility, pres_accumulation, pres_moving_avg]
    """

    def __init__(self, window_size: int = 10, moving_avg_window: int = 5):
        self.window_size = window_size
        self.moving_avg_window = moving_avg_window

    def extract_features(self, sensor_data: List[Dict], device_type: str) -> List[float]:
        """提取15维时序特征向量。

        Args:
            sensor_data: 原始传感器记录列表，每个元素包含 timestamp, sensor_code, value
            device_type: 设备类型，如'工业机器人'、'数控机床'等

        Returns:
            长度为15的特征向量；输入为空或清洗后无效时返回空列表
        """
        if not sensor_data:
            return []

        # 1. 数据防御：排序、去重、类型转换
        sensor_data = self._sanitize(sensor_data)
        if not sensor_data:
            return []

        # 2. 补齐缺失传感器（用基线值填充）
        sensor_data = self._fill_missing(sensor_data, device_type)

        # 3. 按传感器分组
        sensor_groups = self._group_by_sensor(sensor_data)

        # 4. 获取该设备类型的配置
        profile = DEVICE_PROFILES.get(device_type)
        if profile is None:
            logger.warning('未知的设备类型: %s，使用默认基线', device_type)
            profile = DEVICE_PROFILES['工业机器人']

        features = []
        for sensor_code in SENSOR_CODES:
            values = sensor_groups.get(sensor_code, [])
            if not values:
                # 补齐后理论上不会为空，但防御性处理
                values = [profile[sensor_code]['baseline']]

            sensor_profile = profile.get(sensor_code, {'baseline': 1.0, 'threshold': 1.0})
            baseline = sensor_profile['baseline']
            threshold = sensor_profile['threshold']

            current = float(values[-1])
            trend = self._calculate_trend(values, threshold)
            volatility = self._calculate_volatility(values, baseline)
            accumulation = self._calculate_accumulation(values, threshold)
            moving_avg = self._calculate_moving_avg(values)

            features.extend([current, trend, volatility, accumulation, moving_avg])

        return features

    @staticmethod
    def get_expected_feature_count() -> int:
        """返回期望的特征维度数（15）"""
        return len(SENSOR_CODES) * 5

    @staticmethod
    def get_feature_names() -> List[str]:
        """返回特征名称列表，用于可解释性分析"""
        names = []
        for code in SENSOR_CODES:
            for suffix in ['current', 'trend', 'volatility', 'accumulation', 'moving_avg']:
                names.append(f'{code}_{suffix}')
        return names

    # ------------------------------------------------------------------
    # 数据防御层
    # ------------------------------------------------------------------

    def _sanitize(self, sensor_data: List[Dict]) -> List[Dict]:
        """数据清洗：过滤无效记录、排序、去重。"""
        valid = []
        for item in sensor_data:
            try:
                ts = item.get('timestamp', '')
                code = item.get('sensor_code')
                value = float(item.get('value'))
                if code and value is not None and not np.isnan(value):
                    valid.append({
                        'timestamp': str(ts),
                        'sensor_code': str(code).strip().lower(),
                        'value': float(value)
                    })
            except (TypeError, ValueError, OverflowError):
                continue

        if not valid:
            return []

        # 按时间戳排序
        valid.sort(key=lambda x: x['timestamp'])

        # 去重：同一传感器同一时间保留最后一条
        seen = {}
        for item in valid:
            key = (item['timestamp'], item['sensor_code'])
            seen[key] = item
        return list(seen.values())

    def _fill_missing(self, sensor_data: List[Dict], device_type: str) -> List[Dict]:
        """补齐缺失的传感器：若某传感器无记录，用设备基线值填充。"""
        present = set(item['sensor_code'] for item in sensor_data)
        missing = set(SENSOR_CODES) - present

        if not missing:
            return sensor_data

        profile = DEVICE_PROFILES.get(device_type, DEVICE_PROFILES['工业机器人'])
        baseline_time = sensor_data[-1]['timestamp'] if sensor_data else '1970-01-01T00:00:00'

        for code in missing:
            baseline = profile.get(code, {}).get('baseline', 0.0)
            sensor_data.append({
                'timestamp': baseline_time,
                'sensor_code': code,
                'value': float(baseline),
                '_interpolated': True
            })

        return sensor_data

    def _group_by_sensor(self, sensor_data: List[Dict]) -> Dict[str, List[float]]:
        """按传感器代码分组，返回 {sensor_code: [values]}。"""
        groups: Dict[str, List[float]] = {code: [] for code in SENSOR_CODES}
        for item in sensor_data:
            code = item.get('sensor_code')
            value = item.get('value')
            if code in groups and value is not None:
                groups[code].append(float(value))
        return groups

    # ------------------------------------------------------------------
    # 特征计算层
    # ------------------------------------------------------------------

    def _calculate_trend(self, values: List[float], threshold: float) -> float:
        """计算趋势：线性回归斜率除以告警阈值，无量纲归一化。

        返回值范围无严格边界，但典型值：
            -1.0  ~ 表示每窗口长度下降一个阈值单位
             0.0  ~ 平稳
            +1.0  ~ 表示每窗口长度上升一个阈值单位
        """
        if len(values) < 2 or threshold <= 0:
            return 0.0

        window = values[-self.window_size:] if len(values) >= self.window_size else values
        x = np.arange(len(window), dtype=np.float64)
        y = np.array(window, dtype=np.float64)

        if len(x) < 2:
            return 0.0

        slope = np.polyfit(x, y, 1)[0]
        # 归一化：斜率 / (阈值 / 窗口长度)
        # 物理意义：若斜率等于“每窗口上升一个阈值”，则返回 1.0
        normalized = slope * len(window) / threshold
        return float(np.clip(normalized, -10.0, 10.0))

    def _calculate_volatility(self, values: List[float], baseline: float) -> float:
        """计算波动率：标准差 / 设备基线，避免 mean 接近0时的除零问题。

        若基线本身为0或负，退化为标准差。
        """
        if len(values) < 2:
            return 0.0

        window = values[-self.window_size:] if len(values) >= self.window_size else values
        arr = np.array(window, dtype=np.float64)
        std = np.std(arr)

        if baseline and baseline > 0:
            return float(np.clip(std / baseline, 0.0, 10.0))
        else:
            return float(np.clip(std, 0.0, 10.0))

    def _calculate_accumulation(self, values: List[float], threshold: float) -> int:
        """计算累积超限：窗口内超阈值次数。"""
        if threshold <= 0 or not values:
            return 0

        window = values[-self.window_size:] if len(values) >= self.window_size else values
        return int(sum(1 for v in window if v > threshold))

    def _calculate_moving_avg(self, values: List[float]) -> float:
        """计算移动平均：最近 moving_avg_window 个点的均值。"""
        if not values:
            return 0.0

        window = values[-self.moving_avg_window:] if len(values) >= self.moving_avg_window else values
        return float(np.mean(window))


# 模块级实例（向后兼容）
feature_extractor = FeatureExtractor()
