"""
时序特征提取器模块

用于从传感器时序数据中提取特征向量，包含趋势、波动率、累积超限和移动平均等特征。
"""

from typing import Dict, List
import numpy as np


class FeatureExtractor:
    """时序特征提取器"""

    def __init__(self, window_size: int = 10, moving_avg_window: int = 5):
        """
        初始化特征提取器

        Args:
            window_size: 用于计算趋势和波动率的数据点数（默认10）
            moving_avg_window: 移动平均窗口大小（默认5）
        """
        self.window_size = window_size
        self.moving_avg_window = moving_avg_window

    def extract_features(self, sensor_data: List[Dict], thresholds: Dict[str, float]) -> List[float]:
        """
        提取时序特征

        Args:
            sensor_data: 传感器数据列表，每个元素包含 timestamp, sensor_code, value
            thresholds: 传感器阈值字典 {sensor_code: threshold}

        Returns:
            特征向量 = [值1, 趋势1, 波动率1, 累积超限1, 移动平均1, 值2, ...]
        """
        if not sensor_data:
            return []

        # 按传感器代码分组
        sensor_groups = self._group_by_sensor(sensor_data)

        features: List[float] = []
        sensor_codes = sorted(sensor_groups.keys())

        for sensor_code in sensor_codes:
            values = sensor_groups[sensor_code]
            if not values:
                continue

            # 当前值
            current_value = values[-1]

            # 趋势
            trend = self._calculate_trend(values)

            # 波动率
            volatility = self._calculate_volatility(values)

            # 累积超限
            threshold = thresholds.get(sensor_code, 0)
            accumulation = self._calculate_accumulation(values, threshold)

            # 移动平均
            moving_avg = self._calculate_moving_avg(values)

            features.extend([current_value, trend, volatility, accumulation, moving_avg])

        return features

    def _calculate_trend(self, values: List[float]) -> int:
        """
        计算趋势：0=下降, 1=平稳, 2=上升

        Args:
            values: 传感器值列表

        Returns:
            趋势标签 (0=下降, 1=平稳, 2=上升)
        """
        if len(values) < 2:
            return 1  # 数据点不足，视为平稳

        # 取最后 window_size 个点
        window = values[-self.window_size:] if len(values) >= self.window_size else values

        # 简单线性回归计算斜率
        x = np.arange(len(window))
        y = np.array(window)

        if len(x) < 2:
            return 1

        # 计算斜率
        slope = np.polyfit(x, y, 1)[0]

        # 根据斜率判断趋势
        if slope < -0.1:
            return 0  # 下降
        elif slope > 0.1:
            return 2  # 上升
        else:
            return 1  # 平稳

    def _calculate_volatility(self, values: List[float]) -> float:
        """
        计算波动率：标准差 / 均值

        Args:
            values: 传感器值列表

        Returns:
            波动率（标准差/均值）
        """
        if len(values) < 2:
            return 0.0

        # 取最后 window_size 个点
        window = values[-self.window_size:] if len(values) >= self.window_size else values

        arr = np.array(window)
        mean = np.mean(arr)
        std = np.std(arr)

        if mean == 0:
            return 0.0

        return float(std / mean)

    def _calculate_accumulation(self, values: List[float], threshold: float) -> int:
        """
        计算累积超限：超阈值次数

        Args:
            values: 传感器值列表
            threshold: 阈值

        Returns:
            超阈值次数
        """
        if threshold <= 0:
            return 0

        # 取最后 window_size 个点
        window = values[-self.window_size:] if len(values) >= self.window_size else values

        count = sum(1 for v in window if v > threshold)
        return count

    def _calculate_moving_avg(self, values: List[float]) -> float:
        """
        计算移动平均：最近K次平均值

        Args:
            values: 传感器值列表

        Returns:
            移动平均值
        """
        if not values:
            return 0.0

        # 取最后 moving_avg_window 个点
        window = values[-self.moving_avg_window:] if len(values) >= self.moving_avg_window else values

        return float(np.mean(window))

    def _group_by_sensor(self, sensor_data: List[Dict]) -> Dict[str, List[float]]:
        """
        按传感器代码分组

        Args:
            sensor_data: 传感器数据列表

        Returns:
            按传感器代码分组的字典 {sensor_code: [values]}
        """
        groups: Dict[str, List[float]] = {}

        for item in sensor_data:
            sensor_code = item.get('sensor_code')
            value = item.get('value')

            if sensor_code is None or value is None:
                continue

            if sensor_code not in groups:
                groups[sensor_code] = []

            groups[sensor_code].append(float(value))

        return groups


# 模块级实例
feature_extractor = FeatureExtractor()
