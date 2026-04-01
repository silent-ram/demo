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

predictor = FaultPredictor()

def predict_fault(temperature, vibration, pressure):
    return predictor.predict(temperature, vibration, pressure)
