import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np
import io
import base64

def generate_trend_chart(device_id, data_points=100):
    np.random.seed(device_id)
    
    time_points = np.arange(data_points)
    temperature = np.random.normal(65, 10, data_points)
    vibration = np.random.normal(0.3, 0.15, data_points)
    pressure = np.random.normal(100, 15, data_points)
    
    fig, axes = plt.subplots(3, 1, figsize=(12, 10))
    fig.suptitle(f'设备 {device_id} 传感器数据趋势', fontsize=16)
    
    axes[0].plot(time_points, temperature, 'r-', linewidth=2)
    axes[0].set_ylabel('温度 (°C)', fontsize=12)
    axes[0].set_title('温度趋势', fontsize=14)
    axes[0].grid(True, alpha=0.3)
    axes[0].axhline(y=80, color='orange', linestyle='--', linewidth=2, label='警告阈值')
    axes[0].legend()
    
    axes[1].plot(time_points, vibration, 'g-', linewidth=2)
    axes[1].set_ylabel('振动 (mm/s)', fontsize=12)
    axes[1].set_title('振动趋势', fontsize=14)
    axes[1].grid(True, alpha=0.3)
    axes[1].axhline(y=0.6, color='orange', linestyle='--', linewidth=2, label='警告阈值')
    axes[1].legend()
    
    axes[2].plot(time_points, pressure, 'b-', linewidth=2)
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

def generate_multi_device_chart(device_ids):
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
