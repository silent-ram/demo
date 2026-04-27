import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score, roc_auc_score
import joblib
import os

MODEL_DIR = 'model'
MODEL_PATH = os.path.join(MODEL_DIR, 'fault_model.pkl')
SCALER_PATH = os.path.join(MODEL_DIR, 'scaler.pkl')

def generate_dataset(n_samples=5000):
    np.random.seed(42)
    
    temperature = np.random.normal(65, 15, n_samples)
    vibration = np.random.normal(0.3, 0.2, n_samples)
    pressure = np.random.normal(100, 20, n_samples)
    
    label = np.zeros(n_samples, dtype=int)
    
    for i in range(n_samples):
        fault_prob = 0
        if temperature[i] > 80:
            fault_prob += 0.4
        if vibration[i] > 0.6:
            fault_prob += 0.3
        if pressure[i] > 130:
            fault_prob += 0.3
        
        if fault_prob >= 0.5:
            label[i] = 1
    
    df = pd.DataFrame({
        'temperature': temperature,
        'vibration': vibration,
        'pressure': pressure,
        'label': label
    })
    
    return df

def train_model():
    print('生成模拟数据集...')
    df = generate_dataset(5000)
    
    X = df[['temperature', 'vibration', 'pressure']]
    y = df['label']
    
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)
    
    print('特征标准化...')
    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_test_scaled = scaler.transform(X_test)
    
    print('训练 LogisticRegression 模型...')
    model = LogisticRegression(random_state=42, max_iter=1000)
    model.fit(X_train_scaled, y_train)
    
    y_pred = model.predict(X_test_scaled)
    y_pred_proba = model.predict_proba(X_test_scaled)[:, 1]
    
    accuracy = accuracy_score(y_test, y_pred)
    precision = precision_score(y_test, y_pred)
    recall = recall_score(y_test, y_pred)
    f1 = f1_score(y_test, y_pred)
    roc_auc = roc_auc_score(y_test, y_pred_proba)
    
    print('\n模型指标:')
    print(f'准确率: {accuracy:.4f}')
    print(f'精确率: {precision:.4f}')
    print(f'召回率: {recall:.4f}')
    print(f'F1分数: {f1:.4f}')
    print(f'ROC AUC: {roc_auc:.4f}')
    
    if not os.path.exists(MODEL_DIR):
        os.makedirs(MODEL_DIR)
    
    joblib.dump(model, MODEL_PATH)
    joblib.dump(scaler, SCALER_PATH)
    print(f'\n模型已保存到: {MODEL_PATH}')
    print(f'标准化器已保存到: {SCALER_PATH}')

    metrics = {
        'accuracy': accuracy,
        'precision': precision,
        'recall': recall,
        'f1_score': f1,
        'roc_auc': roc_auc,
        'training_samples': len(df),
        'model_loaded': True
    }

    import json
    metrics_path = os.path.join(MODEL_DIR, 'metrics.json')
    with open(metrics_path, 'w') as f:
        json.dump(metrics, f)
    print(f'指标已保存到: {metrics_path}')

    return metrics

if __name__ == '__main__':
    metrics = train_model()
