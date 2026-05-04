"""
模型版本管理器 v1.0

管理每个设备类型的模型版本：
- 保存新版本到 model/{device_type}/v{N}/
- 通过 model/{device_type}/current 文件记录激活版本
- 支持版本列表查询和回滚

Windows 下用文本文件替代符号链接。
"""

import json
import os
import shutil
import glob
import logging

logger = logging.getLogger(__name__)

MODEL_ROOT = 'model'


class ModelVersionManager:
    """模型版本管理器。

    目录结构：
    model/
    ├── 工业机器人/
    │   ├── current              # 文本文件，内容为 "v3"
    │   ├── v1/
    │   │   ├── fault_model.pkl
    │   │   ├── scaler.pkl
    │   │   └── metrics.json
    │   ├── v2/
    │   └── v3/
    └── metadata.json
    """

    def __init__(self, model_root=None):
        self.model_root = model_root or MODEL_ROOT

    def _get_device_dir(self, device_type: str) -> str:
        """获取设备类型的模型目录。"""
        return os.path.join(self.model_root, device_type)

    def _get_version_dir(self, device_type: str, version: str) -> str:
        """获取指定版本的目录。"""
        return os.path.join(self._get_device_dir(device_type), version)

    def _get_current_file(self, device_type: str) -> str:
        """获取 current 文件路径。"""
        return os.path.join(self._get_device_dir(device_type), 'current')

    def _get_next_version(self, device_type: str) -> str:
        """获取下一个版本号。"""
        versions = self._list_version_dirs(device_type)
        if not versions:
            return 'v1'
        max_num = max(int(v.replace('v', '')) for v in versions)
        return f'v{max_num + 1}'

    def _list_version_dirs(self, device_type: str) -> list:
        """列出某设备类型的所有版本目录。"""
        device_dir = self._get_device_dir(device_type)
        if not os.path.exists(device_dir):
            return []
        versions = []
        for item in os.listdir(device_dir):
            item_path = os.path.join(device_dir, item)
            if os.path.isdir(item_path) and item.startswith('v'):
                try:
                    int(item.replace('v', ''))
                    versions.append(item)
                except ValueError:
                    continue
        return sorted(versions, key=lambda x: int(x.replace('v', '')))

    def save_new_version(self, device_type: str, model, scaler, metrics: dict) -> str:
        """保存新版本模型。

        Returns:
            新版本号
        """
        import joblib

        version = self._get_next_version(device_type)
        version_dir = self._get_version_dir(device_type, version)
        os.makedirs(version_dir, exist_ok=True)

        joblib.dump(model, os.path.join(version_dir, 'fault_model.pkl'))
        joblib.dump(scaler, os.path.join(version_dir, 'scaler.pkl'))

        with open(os.path.join(version_dir, 'metrics.json'), 'w', encoding='utf-8') as f:
            json.dump(metrics, f, ensure_ascii=False, indent=2)

        logger.info('模型已保存: %s/%s', device_type, version)
        return version

    def activate_version(self, device_type: str, version: str):
        """激活指定版本。"""
        version_dir = self._get_version_dir(device_type, version)
        if not os.path.exists(version_dir):
            raise ValueError(f'版本不存在: {device_type}/{version}')

        current_file = self._get_current_file(device_type)
        with open(current_file, 'w', encoding='utf-8') as f:
            f.write(version)

        logger.info('激活版本: %s -> %s', device_type, version)

    def get_active_version(self, device_type: str) -> str:
        """获取当前激活版本。"""
        current_file = self._get_current_file(device_type)
        if os.path.exists(current_file):
            with open(current_file, 'r', encoding='utf-8') as f:
                return f.read().strip()
        # 兼容旧版：检查根目录是否有直接文件
        legacy_model = os.path.join(self._get_device_dir(device_type), 'fault_model.pkl')
        if os.path.exists(legacy_model):
            return None  # 旧版无版本管理
        return None

    def get_active_version_dir(self, device_type: str) -> str:
        """获取当前激活版本的目录路径。"""
        active = self.get_active_version(device_type)
        if active:
            return self._get_version_dir(device_type, active)
        # 兼容旧版
        return self._get_device_dir(device_type)

    def list_versions(self, device_type: str) -> list:
        """列出某设备类型的所有历史版本。

        Returns:
            [{version, trained_at, metrics}, ...]
        """
        versions = self._list_version_dirs(device_type)
        result = []
        active = self.get_active_version(device_type)

        for v in versions:
            metrics_path = os.path.join(self._get_version_dir(device_type, v), 'metrics.json')
            info = {'version': v, 'active': v == active}
            if os.path.exists(metrics_path):
                try:
                    with open(metrics_path, 'r', encoding='utf-8') as f:
                        metrics = json.load(f)
                    info['trained_at'] = metrics.get('trained_at', '')
                    info['f1_score'] = metrics.get('f1_score', 0)
                    info['roc_auc'] = metrics.get('roc_auc', 0)
                    info['accuracy'] = metrics.get('accuracy', 0)
                    info['training_samples'] = metrics.get('training_samples', 0)
                except (json.JSONDecodeError, IOError):
                    pass
            result.append(info)
        return result

    def get_version_metrics(self, device_type: str, version: str) -> dict:
        """获取指定版本的指标。"""
        metrics_path = os.path.join(self._get_version_dir(device_type, version), 'metrics.json')
        if os.path.exists(metrics_path):
            with open(metrics_path, 'r', encoding='utf-8') as f:
                return json.load(f)
        return {}

    def rollback(self, device_type: str, version: str):
        """回滚到指定版本。"""
        self.activate_version(device_type, version)
        logger.info('回滚完成: %s -> %s', device_type, version)

    def cleanup_old_versions(self, device_type: str, keep: int = 5):
        """清理旧版本，默认保留最近5个。"""
        versions = self._list_version_dirs(device_type)
        if len(versions) <= keep:
            return

        active = self.get_active_version(device_type)
        to_delete = versions[:-keep]

        for v in to_delete:
            if v == active:
                continue  # 不删除当前激活版本
            version_dir = self._get_version_dir(device_type, v)
            shutil.rmtree(version_dir)
            logger.info('清理旧版本: %s/%s', device_type, v)

    def init_version_structure(self, device_type: str):
        """将旧版模型迁移到版本管理结构。"""
        device_dir = self._get_device_dir(device_type)
        legacy_model = os.path.join(device_dir, 'fault_model.pkl')

        if os.path.exists(legacy_model) and not os.path.exists(self._get_current_file(device_type)):
            # 迁移到 v1
            import joblib
            v1_dir = self._get_version_dir(device_type, 'v1')
            os.makedirs(v1_dir, exist_ok=True)

            shutil.copy2(legacy_model, os.path.join(v1_dir, 'fault_model.pkl'))
            legacy_scaler = os.path.join(device_dir, 'scaler.pkl')
            if os.path.exists(legacy_scaler):
                shutil.copy2(legacy_scaler, os.path.join(v1_dir, 'scaler.pkl'))
            legacy_metrics = os.path.join(device_dir, 'metrics.json')
            if os.path.exists(legacy_metrics):
                shutil.copy2(legacy_metrics, os.path.join(v1_dir, 'metrics.json'))

            self.activate_version(device_type, 'v1')
            logger.info('旧版模型已迁移到版本管理: %s -> v1', device_type)


# 模块级单例
_version_manager = None


def get_version_manager() -> ModelVersionManager:
    """获取版本管理器单例。"""
    global _version_manager
    if _version_manager is None:
        _version_manager = ModelVersionManager()
    return _version_manager
