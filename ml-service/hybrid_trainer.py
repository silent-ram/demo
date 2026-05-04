"""
混合训练器 v1.0

整合真实伪标签数据和模拟数据，按比例混合后训练模型。

混合比例由真实故障样本量决定：
- 真实故障 < 20:  模拟 90%（真实 10%）
- 真实故障 20-50: 模拟 70%（真实 30%）
- 真实故障 50-100: 模拟 50%（真实 50%）
- 真实故障 > 100:  模拟 30%（真实 70%）
"""

import numpy as np
from sklearn.preprocessing import StandardScaler
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score, roc_auc_score
import joblib
import os
import json
from datetime import datetime
import logging

from train import generate_device_data, train_model_for_device_type, MODEL_ROOT
from feature_extractor import FeatureExtractor
from config_loader import get_device_profiles_legacy
from model_version_manager import get_version_manager
from pseudo_label_engine import PseudoLabelEngine

logger = logging.getLogger(__name__)


class HybridTrainer:
    """混合训练器：整合真实伪标签数据和模拟数据。"""

    def __init__(self, pseudo_engine: PseudoLabelEngine = None):
        self.pseudo_engine = pseudo_engine or PseudoLabelEngine()
        self.version_manager = get_version_manager()

    def _compute_sim_ratio(self, real_positive_count: int) -> float:
        """根据真实故障样本量决定模拟数据比例。

        Returns:
            模拟数据应占的比例（0.0~1.0）
        """
        if real_positive_count < 20:
            return 0.90
        elif real_positive_count < 50:
            return 0.70
        elif real_positive_count < 100:
            return 0.50
        else:
            return 0.30

    def prepare_training_data(self, device_type: str, n_sim_samples: int = 5000):
        """为指定设备类型准备训练数据。

        1. 从伪标签引擎获取真实样本
        2. 计算混合比例
        3. 生成模拟数据补充
        4. 合并并打乱

        Returns:
            X, y, mix_info
        """
        # 真实样本
        real_positive = self.pseudo_engine.get_samples(device_type, label=1)
        real_negative = self.pseudo_engine.get_samples(device_type, label=0)

        real_count = len(real_positive) + len(real_negative)
        sim_ratio = self._compute_sim_ratio(len(real_positive))

        # 计算模拟样本数
        if real_count > 0:
            sim_total = int(real_count * sim_ratio / (1 - sim_ratio))
        else:
            sim_total = n_sim_samples

        # 模拟数据
        X_sim, y_sim, feature_names = generate_device_data(
            device_type, n_samples=sim_total,
            random_seed=hash(device_type + str(datetime.now().timestamp())) % 10000
        )

        # 合并真实数据
        if real_positive or real_negative:
            X_real = np.array([s['features'] for s in real_positive + real_negative])
            y_real = np.array([1] * len(real_positive) + [0] * len(real_negative))
            X = np.vstack([X_real, X_sim])
            y = np.concatenate([y_real, y_sim])
        else:
            X = X_sim
            y = y_sim

        # 打乱数据
        indices = np.random.permutation(len(X))
        X = X[indices]
        y = y[indices]

        mix_info = {
            'real_positive': len(real_positive),
            'real_negative': len(real_negative),
            'sim_total': sim_total,
            'sim_positive': int(sum(y_sim)),
            'sim_negative': int(len(y_sim) - sum(y_sim)),
            'total_samples': len(X),
            'real_ratio': real_count / len(X) if len(X) > 0 else 0,
            'sim_ratio': sim_ratio
        }

        return X, y, feature_names, mix_info

    def train_with_comparison(self, device_type: str, X: np.ndarray, y: np.ndarray,
                              feature_names: list, mix_info: dict) -> dict:
        """训练新模型并与旧模型对比。

        1. 训练新模型
        2. 在测试集上评估新模型
        3. 加载旧模型，在同一测试集上评估
        4. 对比 F1 和 ROC-AUC
        5. 决策: 新模型 F1 > 旧模型 F1 * 0.95 ? 替换 : 保留

        Returns:
            {
                'new_metrics': {...},
                'old_metrics': {...} or None,
                'improvement': {'f1': +0.023, 'roc_auc': +0.015},
                'should_replace': bool,
                'version': str
            }
        """
        # 时间切分：前 70% 训练，后 30% 测试
        n_train = int(len(X) * 0.7)
        X_train, X_test = X[:n_train], X[n_train:]
        y_train, y_test = y[:n_train], y[n_train:]

        # 训练新模型
        scaler = StandardScaler()
        X_train_scaled = scaler.fit_transform(X_train)
        X_test_scaled = scaler.transform(X_test)

        model = LogisticRegression(random_state=42, max_iter=1000, class_weight='balanced')
        model.fit(X_train_scaled, y_train)

        # 评估新模型
        y_pred = model.predict(X_test_scaled)
        y_pred_proba = model.predict_proba(X_test_scaled)[:, 1]

        new_metrics = {
            'accuracy': float(accuracy_score(y_test, y_pred)),
            'precision': float(precision_score(y_test, y_pred, zero_division=0)),
            'recall': float(recall_score(y_test, y_pred, zero_division=0)),
            'f1_score': float(f1_score(y_test, y_pred, zero_division=0)),
            'roc_auc': float(roc_auc_score(y_test, y_pred_proba)) if len(set(y_test)) > 1 else 0.0,
            'training_samples': int(len(X_train)),
            'test_samples': int(len(X_test)),
            'feature_count': int(len(feature_names)),
            'trained_at': datetime.now().isoformat(),
            'mix_info': mix_info,
            'model_loaded': True
        }

        # 特征重要性
        coef_dict = {}
        for name, coef in zip(feature_names, model.coef_[0]):
            coef_dict[name] = float(coef)
        new_metrics['feature_importance'] = coef_dict

        # 尝试加载旧模型进行对比
        old_metrics = None
        improvement = {'f1': 0.0, 'roc_auc': 0.0}

        try:
            active_version = self.version_manager.get_active_version(device_type)
            if active_version:
                old_metrics_path = os.path.join(
                    MODEL_ROOT, device_type, active_version, 'metrics.json'
                )
                if os.path.exists(old_metrics_path):
                    with open(old_metrics_path, 'r', encoding='utf-8') as f:
                        old_metrics = json.load(f)

                    old_f1 = old_metrics.get('f1_score', 0)
                    old_roc = old_metrics.get('roc_auc', 0)
                    improvement['f1'] = new_metrics['f1_score'] - old_f1
                    improvement['roc_auc'] = new_metrics['roc_auc'] - old_roc
        except Exception as e:
            logger.warning('加载旧模型指标失败: %s', e)

        # 决策：是否替换旧模型
        should_replace = True
        if old_metrics:
            old_f1 = old_metrics.get('f1_score', 0)
            # 允许 5% 退化（真实数据噪声更大）
            if new_metrics['f1_score'] < old_f1 * 0.95:
                should_replace = False
                logger.warning('新模型 F1 (%.4f) 低于旧模型 (%.4f)，保留旧模型',
                              new_metrics['f1_score'], old_f1)

        # 保存新版本
        version = self.version_manager.save_new_version(
            device_type, model, scaler, new_metrics
        )

        if should_replace:
            self.version_manager.activate_version(device_type, version)
            logger.info('新模型已激活: %s v%s', device_type, version)

        return {
            'new_metrics': new_metrics,
            'old_metrics': old_metrics,
            'improvement': improvement,
            'should_replace': should_replace,
            'version': version
        }

    def train_hybrid_for_device(self, device_type: str, n_sim_samples: int = 5000) -> dict:
        """为单个设备类型执行混合训练。

        Returns:
            训练结果字典
        """
        logger.info('开始混合训练: %s', device_type)

        # 初始化版本结构（兼容旧版）
        self.version_manager.init_version_structure(device_type)

        X, y, feature_names, mix_info = self.prepare_training_data(device_type, n_sim_samples)

        if len(X) == 0:
            raise ValueError(f'{device_type} 无有效训练数据')

        result = self.train_with_comparison(device_type, X, y, feature_names, mix_info)
        result['device_type'] = device_type
        result['mix_info'] = mix_info

        logger.info('混合训练完成: %s -> v%s (F1=%.4f, 替换=%s)',
                   device_type, result['version'],
                   result['new_metrics']['f1_score'],
                   result['should_replace'])
        return result

    def train_all_hybrid(self, n_sim_samples: int = 5000) -> dict:
        """为所有设备类型执行混合训练。

        Returns:
            全局元数据
        """
        print('=' * 60)
        print('混合训练 v2.1 - 伪标签 + 模拟数据')
        print('=' * 60)

        all_results = {}
        all_feature_names = None

        for device_type in get_device_profiles_legacy().keys():
            print(f'\n--- 混合训练: {device_type} ---')
            try:
                result = self.train_hybrid_for_device(device_type, n_sim_samples)
                all_results[device_type] = result
                all_feature_names = list(result['new_metrics'].get('feature_importance', {}).keys())
            except Exception as e:
                print(f'[{device_type}] 混合训练失败: {e}')
                all_results[device_type] = {'error': str(e)}

        # 保存全局元数据
        metadata = {
            'version': '2.1.0',
            'created_at': datetime.now().isoformat(),
            'feature_extractor_version': '2.0',
            'feature_count': FeatureExtractor.get_expected_feature_count(),
            'device_types': list(get_device_profiles_legacy().keys()),
            'device_results': all_results
        }

        metadata_path = os.path.join(MODEL_ROOT, 'metadata.json')
        with open(metadata_path, 'w', encoding='utf-8') as f:
            json.dump(metadata, f, ensure_ascii=False, indent=2)

        print(f'\n全局元数据已保存: {metadata_path}')
        print('=' * 60)
        print('混合训练完成')
        print('=' * 60)

        return metadata
