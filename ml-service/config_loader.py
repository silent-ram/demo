"""
统一配置加载器
读取项目根目录 config/device_profiles.json，为 feature_extractor 和 train 提供统一的设备参数。
"""

import json
import os
import logging

logger = logging.getLogger(__name__)

# 项目根目录下的配置文件路径
_CONFIG_PATH = os.path.join(
    os.path.dirname(__file__), '..', 'config', 'device_profiles.json'
)

_device_profiles = None
_feature_names = None


def _load_config():
    """加载并缓存配置文件。"""
    global _device_profiles
    if _device_profiles is None:
        try:
            with open(_CONFIG_PATH, 'r', encoding='utf-8') as f:
                config = json.load(f)
            _device_profiles = config.get('device_types', {})
            logger.info('配置文件加载成功: %s', _CONFIG_PATH)
        except FileNotFoundError:
            logger.error('配置文件不存在: %s', _CONFIG_PATH)
            _device_profiles = {}
        except json.JSONDecodeError as e:
            logger.error('配置文件解析失败: %s', e)
            _device_profiles = {}
    return _device_profiles


def get_device_profiles():
    """获取所有设备类型的配置。"""
    return _load_config()


def get_device_type_names():
    """获取所有设备类型名称列表。"""
    return list(_load_config().keys())


def get_sensor_config(device_type, sensor_code):
    """
    获取指定设备类型和传感器的配置。

    Returns:
        dict: 包含 baseline, threshold, unit, stable, degrading, spike 等键
        若不存在则返回空 dict
    """
    profiles = _load_config()
    return profiles.get(device_type, {}).get('sensors', {}).get(sensor_code, {})


def get_sensor_baseline(device_type, sensor_code):
    """获取传感器基线值。"""
    return get_sensor_config(device_type, sensor_code).get('baseline')


def get_sensor_threshold(device_type, sensor_code):
    """获取传感器告警阈值。"""
    return get_sensor_config(device_type, sensor_code).get('threshold')


def get_stable_params(device_type, sensor_code):
    """获取稳定运行模式参数。"""
    return get_sensor_config(device_type, sensor_code).get('stable', {})


def get_degrading_params(device_type, sensor_code):
    """获取劣化模式参数。"""
    return get_sensor_config(device_type, sensor_code).get('degrading', {})


def get_spike_params(device_type, sensor_code):
    """获取尖峰模式参数。"""
    return get_sensor_config(device_type, sensor_code).get('spike', {})


def reload_profiles():
    """强制重新加载配置文件（支持热更新）。"""
    global _device_profiles
    _device_profiles = None
    logger.info('配置文件已重新加载')


def get_feature_names():
    """
    返回15维特征名称列表（与 feature_extractor 一致）。
    """
    global _feature_names
    if _feature_names is None:
        sensor_codes = ['temperature', 'vibration', 'pressure']
        feature_suffixes = ['current', 'trend', 'volatility', 'accumulation', 'moving_avg']
        _feature_names = []
        for sensor in sensor_codes:
            for suffix in feature_suffixes:
                _feature_names.append(f'{sensor}_{suffix}')
    return _feature_names


# 向后兼容：提供 DEVICE_PROFILES 字典格式
# 供 feature_extractor.py 和 train.py 直接替换原有硬编码

def get_device_profiles_legacy():
    """
    返回与旧版 DEVICE_PROFILES 兼容的字典格式：
    {
        '工业机器人': {
            'temperature': {'baseline': 65.0, 'threshold': 80.0},
            ...
        }
    }
    """
    profiles = _load_config()
    legacy = {}
    for dtype, cfg in profiles.items():
        legacy[dtype] = {}
        for sensor, sensor_cfg in cfg.get('sensors', {}).items():
            legacy[dtype][sensor] = {
                'baseline': sensor_cfg.get('baseline'),
                'threshold': sensor_cfg.get('threshold')
            }
    return legacy


def get_sensor_codes():
    """获取传感器代码列表。"""
    return ['temperature', 'vibration', 'pressure']


if __name__ == '__main__':
    # 简单测试
    print('设备类型:', get_device_type_names())
    print('工业机器人温度基线:', get_sensor_baseline('工业机器人', 'temperature'))
    print('工业机器人温度稳定参数:', get_stable_params('工业机器人', 'temperature'))
    print('特征名称:', get_feature_names())
