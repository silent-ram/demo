import joblib
import logging
import numpy as np
import os
import json

from feature_extractor import FeatureExtractor, DEVICE_PROFILES

logger = logging.getLogger(__name__)

MODEL_ROOT = 'model'
METADATA_FILE = os.path.join(MODEL_ROOT, 'metadata.json')

# 向后兼容：旧模型路径（单一全局模型）
LEGACY_MODEL_PATH = os.path.join(MODEL_ROOT, 'fault_model.pkl')
LEGACY_SCALER_PATH = os.path.join(MODEL_ROOT, 'scaler.pkl')


class DevicePredictor:
    """单个设备类型的预测器组件（模型 + 标准化器 + 特征提取器）"""

    def __init__(self, device_type: str, model, scaler, extractor: FeatureExtractor):
        self.device_type = device_type
        self.model = model
        self.scaler = scaler
        self.extractor = extractor

    def predict(self, features: list) -> float:
        """从已提取的15维特征预测故障概率。"""
        if self.model is None:
            logger.error('设备类型 %s 的模型未加载', self.device_type)
            return 0.0

        features_array = np.array([features], dtype=np.float64)

        if self.scaler is not None:
            try:
                features_array = self.scaler.transform(features_array)
            except ValueError as e:
                logger.error('标准化失败: %s (期望维度: %s, 实际: %s)',
                             e,
                             getattr(self.scaler, 'n_features_in_', 'unknown'),
                             len(features))
                return 0.0

        proba = self.model.predict_proba(features_array)[0][1]
        return float(proba)


class FaultPredictor:
    """故障预测器 v2.0

    支持按设备类型加载独立模型，管理6套设备预测器。
    保留向后兼容接口，旧代码不传入 device_type 时降级到默认模型。
    """

    EXPECTED_FEATURE_COUNT = FeatureExtractor.get_expected_feature_count()

    def __init__(self):
        self._predictors: dict = {}
        self._default_type = '工业机器人'
        self._metadata: dict = {}
        self._extractor = FeatureExtractor()
        self.load_all_models()

    def load_all_models(self):
        """加载所有设备类型的模型。"""
        self._predictors.clear()

        # 1. 尝试加载元数据
        if os.path.exists(METADATA_FILE):
            try:
                with open(METADATA_FILE, 'r', encoding='utf-8') as f:
                    self._metadata = json.load(f)
                logger.info('模型元数据加载成功: version=%s',
                            self._metadata.get('version', 'unknown'))
            except Exception as e:
                logger.error('加载模型元数据失败: %s', e)
                self._metadata = {}
        else:
            logger.info('模型元数据不存在，尝试加载旧版单一模型')

        # 2. 优先加载新版多模型结构
        loaded_new = False
        device_types = self._metadata.get('device_types', list(DEVICE_PROFILES.keys()))

        from model_version_manager import get_version_manager
        version_manager = get_version_manager()

        for dtype in device_types:
            # 优先从版本管理加载当前激活版本
            active_version = version_manager.get_active_version(dtype)
            if active_version:
                model_path = os.path.join(MODEL_ROOT, dtype, active_version, 'fault_model.pkl')
                scaler_path = os.path.join(MODEL_ROOT, dtype, active_version, 'scaler.pkl')
            else:
                model_path = os.path.join(MODEL_ROOT, dtype, 'fault_model.pkl')
                scaler_path = os.path.join(MODEL_ROOT, dtype, 'scaler.pkl')

            if os.path.exists(model_path):
                try:
                    model = joblib.load(model_path)
                    scaler = joblib.load(scaler_path) if os.path.exists(scaler_path) else None
                    predictor = DevicePredictor(dtype, model, scaler, self._extractor)
                    self._predictors[dtype] = predictor
                    loaded_new = True
                    logger.info('模型加载成功: %s', dtype)
                except Exception as e:
                    logger.error('加载模型失败 [%s]: %s', dtype, e)

        # 3. 若无新版模型，尝试加载旧版单一模型作为默认
        if not loaded_new and os.path.exists(LEGACY_MODEL_PATH):
            try:
                model = joblib.load(LEGACY_MODEL_PATH)
                scaler = joblib.load(LEGACY_SCALER_PATH) if os.path.exists(LEGACY_SCALER_PATH) else None
                predictor = DevicePredictor(self._default_type, model, scaler, self._extractor)
                self._predictors[self._default_type] = predictor
                logger.info('旧版单一模型加载成功，作为默认模型')
            except Exception as e:
                logger.error('加载旧版模型失败: %s', e)

        if not self._predictors:
            logger.warning('未加载任何模型，请先运行 train.py')

    def get_predictor(self, device_type: str) -> DevicePredictor:
        """获取指定设备类型的预测器。若不存在则返回默认类型。"""
        predictor = self._predictors.get(device_type)
        if predictor is None:
            predictor = self._predictors.get(self._default_type)
            if predictor is not None:
                logger.warning('设备类型 %s 的模型不存在，降级使用 %s',
                               device_type, self._default_type)
        return predictor

    def predict_from_features(self, device_type: str, features: list) -> float:
        """从已提取的15维特征直接预测。

        Args:
            device_type: 设备类型
            features: 15维特征向量

        Returns:
            故障概率 [0.0, 1.0]；模型不可用时返回 0.0
        """
        if len(features) != self.EXPECTED_FEATURE_COUNT:
            logger.error('特征维度不匹配: 期望 %d, 实际 %d',
                         self.EXPECTED_FEATURE_COUNT, len(features))
            return 0.0

        predictor = self.get_predictor(device_type)
        if predictor is None:
            return 0.0

        return predictor.predict(features)

    def predict_from_raw(self, device_type: str, sensor_data: list) -> dict:
        """从原始传感器记录预测。

        Args:
            device_type: 设备类型
            sensor_data: 传感器原始记录列表 [{timestamp, sensor_code, value}, ...]

        Returns:
            {'fault_probability': float, 'features': list, 'device_type': str}
            或 {'fault_probability': 0.0, 'error': str}
        """
        predictor = self.get_predictor(device_type)
        if predictor is None:
            return {'fault_probability': 0.0, 'error': '模型未加载'}

        features = predictor.extractor.extract_features(sensor_data, device_type)
        if not features:
            return {'fault_probability': 0.0, 'error': '特征提取失败'}

        if len(features) != self.EXPECTED_FEATURE_COUNT:
            return {'fault_probability': 0.0,
                    'error': f'特征维度异常: 期望{self.EXPECTED_FEATURE_COUNT}, 实际{len(features)}'}

        proba = predictor.predict(features)

        # 规则下限：多传感器同时超阈值时，模型概率不应过低
        # 解决"持续故障状态 → trend/volatility≈0 → 模型误判为正常"的问题
        rule_score = self._rule_score_from_features(device_type, features)
        if rule_score > 0 and proba < rule_score * 0.8:
            proba = max(proba, rule_score * 0.8)

        return {
            'fault_probability': proba,
            'features': features,
            'device_type': device_type
        }

    # ------------------------------------------------------------------
    # 向后兼容接口
    # ------------------------------------------------------------------

    def predict(self, temperature, vibration, pressure):
        """旧接口：接收3个原始值，使用默认模型预测。

        警告：此接口不支持15维特征，仅用于向后兼容。
        若新版15维模型已加载，降级为基于阈值的规则计算。
        """
        predictor = self._predictors.get(self._default_type)
        if predictor is None:
            return None

        if predictor.model is None:
            return None

        features = np.array([[temperature, vibration, pressure]], dtype=np.float64)
        if predictor.scaler is not None:
            try:
                features = predictor.scaler.transform(features)
                proba = predictor.model.predict_proba(features)[0][1]
                return float(proba)
            except ValueError:
                # 新版模型期望15维，旧3维输入不匹配
                # 降级为基于设备类型的规则计算
                logger.debug('旧接口降级为规则计算（新版模型维度不匹配）')
                return self._rule_based_fallback(self._default_type, temperature, vibration, pressure)

        proba = predictor.model.predict_proba(features)[0][1]
        return float(proba)

    def _rule_based_fallback(self, device_type: str, temperature: float,
                             vibration: float, pressure: float) -> float:
        """基于阈值的规则降级计算（当模型维度不匹配时使用）。"""
        profile = DEVICE_PROFILES.get(device_type, DEVICE_PROFILES['工业机器人'])
        score = 0.0

        if temperature > profile['temperature']['threshold']:
            score += 0.4
        if vibration > profile['vibration']['threshold']:
            score += 0.3
        if pressure > profile['pressure']['threshold']:
            score += 0.3

        return min(score, 0.95)

    def _rule_score_from_features(self, device_type: str, features: list) -> float:
        """从15维特征中的 current 和 accumulation 计算规则得分。

        特征布局: [temp_current, temp_trend, ..., temp_moving_avg,
                   vib_current, ..., vib_moving_avg,
                   pres_current, ..., pres_moving_avg]
        每个 sensor 5维: current(0), trend(1), volatility(2), accumulation(3), moving_avg(4)
        """
        profile = DEVICE_PROFILES.get(device_type, DEVICE_PROFILES['工业机器人'])
        score = 0.0

        sensors = [
            ('temperature', 0, 0.4),
            ('vibration', 5, 0.3),
            ('pressure', 10, 0.3),
        ]
        for name, offset, weight in sensors:
            current = features[offset]       # current
            accumulation = int(features[offset + 3])  # accumulation
            threshold = profile[name]['threshold']
            if current > threshold or accumulation >= 3:
                score += weight

        return min(score, 0.95)

    def predict_batch(self, data_list):
        """旧接口：批量预测。"""
        predictor = self._predictors.get(self._default_type)
        if predictor is None or predictor.model is None:
            return None

        features = np.array([[d['temperature'], d['vibration'], d['pressure']] for d in data_list],
                            dtype=np.float64)
        if predictor.scaler is not None:
            try:
                features = predictor.scaler.transform(features)
            except ValueError:
                return None

        probas = predictor.model.predict_proba(features)[:, 1]
        return [float(p) for p in probas]

    # ------------------------------------------------------------------
    # 模型替换（重训练后调用）
    # ------------------------------------------------------------------

    def reload(self):
        """重新加载所有模型文件（重训练后调用）。"""
        logger.info('重新加载所有模型...')
        self.load_all_models()


# ------------------------------------------------------------------
# 模块级单例
# ------------------------------------------------------------------

predictor = FaultPredictor()


def predict_fault(temperature, vibration, pressure):
    """旧版全局函数，向后兼容。"""
    return predictor.predict(temperature, vibration, pressure)


def predict_with_features(device_type: str, features: list) -> float:
    """新版接口：从15维特征预测。"""
    return predictor.predict_from_features(device_type, features)


def predict_with_raw(device_type: str, sensor_data: list) -> dict:
    """新版接口：从原始传感器记录预测。"""
    return predictor.predict_from_raw(device_type, sensor_data)
