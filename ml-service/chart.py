import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.font_manager as fm
import numpy as np
import io
import base64

# 显式加载 SimHei 字体，确保中文渲染正常
_simhei_path = r'C:\Windows\Fonts\simhei.ttf'
try:
    fm.fontManager.addfont(_simhei_path)
    _simhei_prop = fm.FontProperties(fname=_simhei_path)
    _simhei_name = _simhei_prop.get_name()
except Exception:
    _simhei_name = 'SimHei'

def _set_chinese_font():
    plt.rcParams['font.family'] = [_simhei_name, 'SimHei', 'Microsoft YaHei', 'DejaVu Sans']
    plt.rcParams['axes.unicode_minus'] = False

def generate_trend_chart(device_id, data_points=100, data=None):
    """
    生成趋势图
    device_id: 设备ID
    data_points: 数据点数量（当没有真实数据时使用）
    data: 真实数据，格式为 {'temperature': [...], 'vibration': [...], 'pressure': [...], 'timestamps': [...]}
    """
    _set_chinese_font()
    if data is not None and 'temperature' in data and len(data['temperature']) > 0:
        temperature = np.array(data['temperature'])
        vibration = np.array(data['vibration'])
        pressure = np.array(data['pressure'])
        timestamps = data.get('timestamps', np.arange(len(temperature)))
        data_points = len(temperature)
    else:
        np.random.seed(device_id)
        time_points = np.arange(data_points)
        temperature = np.random.normal(65, 10, data_points)
        vibration = np.random.normal(0.3, 0.15, data_points)
        pressure = np.random.normal(100, 15, data_points)
        timestamps = time_points

    fig, axes = plt.subplots(3, 1, figsize=(12, 10))
    fig.suptitle(f'设备 {device_id} 传感器数据趋势', fontsize=16)

    axes[0].plot(timestamps, temperature, 'r-', linewidth=2)
    axes[0].set_ylabel('温度 (°C)', fontsize=12)
    axes[0].set_title('温度趋势', fontsize=14)
    axes[0].grid(True, alpha=0.3)
    axes[0].axhline(y=80, color='orange', linestyle='--', linewidth=2, label='警告阈值')
    axes[0].legend()

    axes[1].plot(timestamps, vibration, 'g-', linewidth=2)
    axes[1].set_ylabel('振动 (mm/s)', fontsize=12)
    axes[1].set_title('振动趋势', fontsize=14)
    axes[1].grid(True, alpha=0.3)
    axes[1].axhline(y=0.6, color='orange', linestyle='--', linewidth=2, label='警告阈值')
    axes[1].legend()

    axes[2].plot(timestamps, pressure, 'b-', linewidth=2)
    axes[2].set_xlabel('时间点', fontsize=12)
    axes[2].set_ylabel('压力 (kPa)', fontsize=12)
    axes[2].set_title('压力趋势', fontsize=14)
    axes[2].grid(True, alpha=0.3)
    axes[2].axhline(y=130, color='orange', linestyle='--', linewidth=2, label='警告阈值')
    axes[2].legend()

    plt.tight_layout()

    buf = io.BytesIO()
    plt.savefig(buf, format='png', dpi=100, bbox_inches='tight')
    buf.seek(0)
    img_base64 = base64.b64encode(buf.read()).decode('utf-8')
    plt.close()

    return img_base64


def generate_fault_probability_chart(device_id, history_data, device_name=''):
    """
    生成故障概率趋势图（Matplotlib）。

    输入: [{timestamp, value}, ...]
    输出: Base64 PNG

    符合毕设要求: "Python Matplotlib 故障特征趋势可视化"
    """
    _set_chinese_font()
    if not history_data or len(history_data) == 0:
        # 无数据时返回空图
        fig, ax = plt.subplots(figsize=(12, 5))
        ax.text(0.5, 0.5, '暂无故障概率历史数据', ha='center', va='center',
                fontsize=14, color='#9a948c')
        ax.set_xlim(0, 1)
        ax.set_ylim(0, 1)
        ax.axis('off')
    else:
        timestamps = []
        values = []
        for d in history_data:
            ts = d.get('timestamp')
            val = d.get('value')
            if ts and val is not None:
                timestamps.append(ts)
                values.append(float(val))

        fig, ax = plt.subplots(figsize=(12, 5))

        # 绘制故障概率曲线
        x = np.arange(len(values))
        ax.plot(x, values, linewidth=2, label='故障概率', color='#0077b6')
        ax.fill_between(x, values, alpha=0.2, color='#0077b6')

        # 阈值参考线
        ax.axhline(y=0.5, color='#f4a261', linestyle='--', linewidth=1.5, label='注意阈值 (0.5)')
        ax.axhline(y=0.7, color='#d62828', linestyle='--', linewidth=1.5, label='告警阈值 (0.7)')

        # 标记超过阈值的数据点
        for i, v in enumerate(values):
            if v >= 0.7:
                ax.scatter([i], [v], color='#d62828', s=50, zorder=5)

        title = f'设备 {device_name or device_id} 故障概率趋势（24小时）'
        ax.set_title(title, fontsize=14, fontweight='bold')
        ax.set_ylabel('故障概率', fontsize=12)
        ax.set_xlabel('时间', fontsize=12)
        ax.set_ylim(0, 1.05)
        ax.grid(True, alpha=0.3)
        ax.legend(loc='upper left')

        # 简化 x 轴标签（只显示部分时间点）
        step = max(1, len(x) // 10)
        ax.set_xticks(x[::step])
        ax.set_xticklabels([str(t)[11:16] for t in timestamps[::step]], rotation=45)

    plt.tight_layout()

    buf = io.BytesIO()
    plt.savefig(buf, format='png', dpi=100, bbox_inches='tight')
    buf.seek(0)
    img_base64 = base64.b64encode(buf.read()).decode('utf-8')
    plt.close()

    return img_base64


def generate_multi_device_chart(device_ids):
    _set_chinese_font()
    fig, axes = plt.subplots(3, 1, figsize=(14, 10))
    fig.suptitle('多设备传感器数据对比', fontsize=16)

    colors = ['r', 'g', 'b', 'c', 'm', 'y']

    for idx, device_id in enumerate(device_ids):
        np.random.seed(device_id)
        data_points = 50
        time_points = np.arange(data_points)

        temperature = np.random.normal(65 + idx * 5, 8, data_points)
        vibration = np.random.normal(0.3 + idx * 0.1, 0.1, data_points)
        pressure = np.random.normal(100 + idx * 10, 12, data_points)

        color = colors[idx % len(colors)]

        axes[0].plot(time_points, temperature, color=color, linewidth=2, label=f'设备{device_id}')
        axes[1].plot(time_points, vibration, color=color, linewidth=2, label=f'设备{device_id}')
        axes[2].plot(time_points, pressure, color=color, linewidth=2, label=f'设备{device_id}')

    axes[0].set_ylabel('温度 (°C)', fontsize=12)
    axes[0].set_title('温度对比', fontsize=14)
    axes[0].grid(True, alpha=0.3)
    axes[0].legend()

    axes[1].set_ylabel('振动 (mm/s)', fontsize=12)
    axes[1].set_title('振动对比', fontsize=14)
    axes[1].grid(True, alpha=0.3)
    axes[1].legend()

    axes[2].set_xlabel('时间点', fontsize=12)
    axes[2].set_ylabel('压力 (kPa)', fontsize=12)
    axes[2].set_title('压力对比', fontsize=14)
    axes[2].grid(True, alpha=0.3)
    axes[2].legend()

    plt.tight_layout()

    buf = io.BytesIO()
    plt.savefig(buf, format='png', dpi=100, bbox_inches='tight')
    buf.seek(0)
    img_base64 = base64.b64encode(buf.read()).decode('utf-8')
    plt.close()

    return img_base64
