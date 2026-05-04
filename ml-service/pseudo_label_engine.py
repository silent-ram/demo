"""
伪标签自训练引擎 v1.0

自动收集高置信度预测样本作为训练数据：
- 正样本：故障概率 >= 0.5 连续 3 次 → 保存为 label=1
- 负样本：稳定运行时段随机采样 1% → 保存为 label=0

所有样本持久化到 pseudo_label_buffer.json
"""

import json
import os
import numpy as np
from collections import deque, defaultdict
from datetime import datetime
import logging
import random

logger = logging.getLogger(__name__)

PERSISTENCE_FILE = os.path.join(os.path.dirname(__file__), 'pseudo_label_buffer.json')


class PseudoLabelEngine:
    """伪标签自训练引擎。

    核心逻辑：
    1. 每次预测后检查 fault_probability >= 0.5
    2. 连续3次高概率 → 保存为 label=1 的伪标签样本
    3. STABLE 模式下低概率 → 随机采样保存为 label=0 的负样本
    4. 所有样本持久化到 JSON 文件
    """

    THRESHOLD = 0.5
    CONSECUTIVE_COUNT = 3
    NEGATIVE_SAMPLE_RATE = 0.01  # 1% 采样率

    def __init__(self, persistence_file=None):
        self._persistence_file = persistence_file or PERSISTENCE_FILE
        self._suspicious_buffer = defaultdict(lambda: deque(maxlen=self.CONSECUTIVE_COUNT))
        self._data = self._load()

    def _load(self) -> dict:
        """加载持久化数据。"""
        if os.path.exists(self._persistence_file):
            try:
                with open(self._persistence_file, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                logger.info('伪标签数据加载成功: %d 正样本, %d 负样本',
                           len(data.get('pseudo_labels', [])),
                           len(data.get('normal_samples', [])))
                return data
            except (json.JSONDecodeError, IOError) as e:
                logger.error('加载伪标签数据失败: %s', e)
        return {'pseudo_labels': [], 'normal_samples': []}

    def _save(self):
        """持久化数据到文件。"""
        try:
            with open(self._persistence_file, 'w', encoding='utf-8') as f:
                json.dump(self._data, f, ensure_ascii=False, indent=2)
        except IOError as e:
            logger.error('保存伪标签数据失败: %s', e)

    def on_prediction(self, device_type: str, features: list, proba: float):
        """每次预测后调用。

        Args:
            device_type: 设备类型
            features: 15维特征向量
            proba: 故障概率 [0, 1]
        """
        if proba >= self.THRESHOLD:
            self._record_suspicious(device_type, features, proba)
        else:
            self._clear_suspicious(device_type)
            self._sample_negative(device_type, features)

    def _record_suspicious(self, device_type: str, features: list, proba: float):
        """记录可疑样本到缓冲。"""
        buf = self._suspicious_buffer[device_type]
        buf.append({
            'features': list(features),
            'proba': float(proba),
            'timestamp': datetime.now().isoformat()
        })

        if len(buf) >= self.CONSECUTIVE_COUNT:
            # 连续3次高概率 → 确认为伪标签正样本
            avg_features = np.mean([b['features'] for b in buf], axis=0).tolist()
            avg_proba = float(np.mean([b['proba'] for b in buf]))

            self._data['pseudo_labels'].append({
                'device_type': device_type,
                'features': avg_features,
                'label': 1,
                'avg_proba': avg_proba,
                'timestamp': datetime.now().isoformat(),
                'confirmed': False
            })
            self._save()
            buf.clear()
            logger.info('伪标签正样本已保存: %s, avg_proba=%.3f', device_type, avg_proba)

    def _clear_suspicious(self, device_type: str):
        """清除可疑缓冲（概率下降时）。"""
        if device_type in self._suspicious_buffer:
            self._suspicious_buffer[device_type].clear()

    def _sample_negative(self, device_type: str, features: list):
        """随机采样负样本。"""
        if random.random() < self.NEGATIVE_SAMPLE_RATE:
            self._data['normal_samples'].append({
                'device_type': device_type,
                'features': list(features),
                'label': 0,
                'timestamp': datetime.now().isoformat()
            })
            self._save()

    def get_samples(self, device_type: str = None, label: int = None) -> list:
        """获取伪标签样本，支持按设备类型和标签过滤。

        Args:
            device_type: 设备类型过滤，None 表示所有
            label: 0 或 1，None 表示所有

        Returns:
            样本列表
        """
        all_samples = []
        if label is None or label == 1:
            all_samples.extend(self._data.get('pseudo_labels', []))
        if label is None or label == 0:
            all_samples.extend(self._data.get('normal_samples', []))

        if device_type:
            all_samples = [s for s in all_samples if s.get('device_type') == device_type]
        return all_samples

    def get_statistics(self) -> dict:
        """返回各设备类型的伪标签统计。"""
        stats = {}
        all_device_types = set()
        for s in self._data.get('pseudo_labels', []):
            all_device_types.add(s.get('device_type'))
        for s in self._data.get('normal_samples', []):
            all_device_types.add(s.get('device_type'))

        for dtype in all_device_types:
            pseudo = [s for s in self._data.get('pseudo_labels', []) if s.get('device_type') == dtype]
            normal = [s for s in self._data.get('normal_samples', []) if s.get('device_type') == dtype]

            # 最近一条的时间
            last_pseudo = max((s['timestamp'] for s in pseudo), default=None)
            last_normal = max((s['timestamp'] for s in normal), default=None)

            stats[dtype] = {
                'pseudo_positive': len(pseudo),
                'normal_negative': len(normal),
                'total': len(pseudo) + len(normal),
                'last_pseudo_label': last_pseudo,
                'last_normal_sample': last_normal
            }
        return stats

    def get_total_counts(self) -> dict:
        """获取全局统计。"""
        pseudo = self._data.get('pseudo_labels', [])
        normal = self._data.get('normal_samples', [])
        return {
            'total_positive': len(pseudo),
            'total_negative': len(normal),
            'total': len(pseudo) + len(normal)
        }

    def clear_all(self):
        """清空所有伪标签数据（谨慎使用）。"""
        self._data = {'pseudo_labels': [], 'normal_samples': []}
        self._save()
        logger.info('伪标签数据已清空')


# 模块级单例
_pseudo_engine = None


def get_pseudo_engine() -> PseudoLabelEngine:
    """获取伪标签引擎单例。"""
    global _pseudo_engine
    if _pseudo_engine is None:
        _pseudo_engine = PseudoLabelEngine()
    return _pseudo_engine
