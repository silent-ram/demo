import joblib
import numpy as np
import os

MODEL_DIR = 'model'
MODEL_PATH = os.path.join(MODEL_DIR, 'fault_model.pkl')
SCALER_PATH = os.path.join(MODEL_DIR, 'scaler.pkl')

class FaultPredictor:
    def __init__(self):
        self.model = None
        self.scaler = None
        self.load_model()
    
    def load_model(self):
        if os.path.exists(MODEL_PATH) and os.path.exists(SCALER_PATH):
            self.model = joblib.load(MODEL_PATH)
            self.scaler = joblib.load(SCALER_PATH)
            print('模型和标准化器加载成功')
        elif os.path.exists(MODEL_PATH):
            self.model = joblib.load(MODEL_PATH)
            self.scaler = None
            print('模型加载成功，但标准化器不存在')
        else:
            print('模型文件不存在，请先运行 train.py')
            self.model = None
            self.scaler = None
    
    def predict(self, temperature, vibration, pressure):
        if self.model is None:
            return None
        
        features = np.array([[temperature, vibration, pressure]])
        
        if self.scaler is not None:
            features = self.scaler.transform(features)
        
        proba = self.model.predict_proba(features)[0][1]
        return float(proba)
    
    def predict_batch(self, data_list):
        if self.model is None:
            return None

        features = np.array([[d['temperature'], d['vibration'], d['pressure']] for d in data_list])

        if self.scaler is not None:
            features = self.scaler.transform(features)

        probas = self.model.predict_proba(features)[:, 1]
        return [float(p) for p in probas]

    def predict_with_config(self, device_type: str, sensor_data: list, thresholds: dict):
        """
        使用动态配置的传感器进行预测

        Args:
            device_type: 设备类型
            sensor_data: 传感器数据列表
            thresholds: 各传感器的告警阈值

        Returns:
            故障概率
        """
        if self.model is None:
            return None

        # 导入特征提取器
        from feature_extractor import FeatureExtractor
        extractor = FeatureExtractor()

        # 提取时序特征
        features = extractor.extract_features(sensor_data, thresholds)

        if not features:
            return None

        # 转换为模型输入格式
        features_array = np.array([features])

        if self.scaler is not None:
            features_array = self.scaler.transform(features_array)

        proba = self.model.predict_proba(features_array)[0][1]
        return float(proba)

predictor = FaultPredictor()

def predict_fault(temperature, vibration, pressure):
    return predictor.predict(temperature, vibration, pressure)
