# 第1批设计：设备状态枚举 + 传感器可扩展化 + 告警管理增强

## 11. 设备状态枚举化

### 设计思路
- 创建 `DeviceStatus` 枚举类，包含 6 种状态
- 设备实体 `Device.status` 字段类型保持 String（兼容数据库），通过枚举统一管理
- 前端根据状态渲染不同颜色

### 枚举定义 (device-service)

```java
public enum DeviceStatus {
    NORMAL("正常运行", "success"),
    RUNNING("运行中", "primary"),
    STANDBY("待机", "info"),
    MAINTENANCE("维护中", "warning"),
    FAULT("故障", "danger"),
    OFFLINE("离线", "info");

    private final String description;
    private final String color;

    DeviceStatus(String description, String color) {
        this.description = description;
        this.color = color;
    }
}
```

### 状态颜色映射（前端）

| 状态 | 颜色 | 说明 |
|------|------|------|
| NORMAL | success（绿色） | 设备完好，可随时启动 |
| RUNNING | primary（蓝色） | 正在执行生产任务 |
| STANDBY | info（灰色） | 设备通电但未生产 |
| MAINTENANCE | warning（黄色） | 正在进行保养或维修 |
| FAULT | danger（红色） | 发生故障，需处理 |
| OFFLINE | info（灰色） | 设备断电/通讯中断 |

---

## 12. 传感器可扩展化

### 设计思路
1. **SensorType 枚举** — 定义传感器类型基本信息
2. **t_sensor_config 表** — 存储各设备类型的传感器配置
3. 不同设备类型配置不同传感器

### SensorType 枚举 (data-collector-service)

```java
public enum SensorType {
    TEMPERATURE("temperature", "温度", "℃", 0, 100, 80),
    VIBRATION("vibration", "振动", "mm/s", 0, 2, 0.6),
    PRESSURE("pressure", "压力", "bar", 0, 200, 130);

    private final String code;
    private final String name;
    private final String unit;
    private final double minValue;
    private final double maxValue;
    private final double alertThreshold;
}
```

### t_sensor_config 表结构

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| device_type | VARCHAR(50) | 设备类型 |
| sensor_code | VARCHAR(50) | 传感器代码 |
| enabled | BOOLEAN | 是否启用 |
| alert_threshold | DOUBLE | 告警阈值 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 配置示例

```sql
-- 设备类型 A：只需要温度传感器
INSERT INTO t_sensor_config (device_type, sensor_code, enabled, alert_threshold) 
VALUES ('TYPE_A', 'temperature', true, 80);

-- 设备类型 B：需要温度+振动+压力
INSERT INTO t_sensor_config (device_type, sensor_code, enabled, alert_threshold) 
VALUES ('TYPE_B', 'temperature', true, 80);
INSERT INTO t_sensor_config (device_type, sensor_code, enabled, alert_threshold) 
VALUES ('TYPE_B', 'vibration', true, 0.6);
INSERT INTO t_sensor_config (device_type, sensor_code, enabled, alert_threshold) 
VALUES ('TYPE_B', 'pressure', true, 130);
```

---

## 13. 告警管理增强

### 设计思路
1. **AlertLevel 枚举** — 4级告警
2. **自动升级** — 从配置表读取超时时间，定时任务扫描处理
3. **基础统计** — 新增统计接口

### AlertLevel 枚举 (alert-service)

```java
public enum AlertLevel {
    INFO("提示", "blue", 24),      // 24小时
    WARNING("警告", "yellow", 4),   // 4小时
    CRITICAL("严重", "orange", 1),  // 1小时
    EMERGENCY("紧急", "red", 0.25); // 15分钟(0.25小时)

    private final String description;
    private final String color;
    private final double upgradeHours; // 升级超时时间(小时)

    AlertLevel(String description, String color, double upgradeHours) {
        this.description = description;
        this.color = color;
        this.upgradeHours = upgradeHours;
    }
}
```

### t_alert 表扩展

| 字段 | 类型 | 说明 |
|------|------|------|
| previous_level | VARCHAR(20) | 升级前的级别 |
| upgraded_at | DATETIME | 升级时间 |

### t_config 表扩展

| 字段 | 类型 | 说明 |
|------|------|------|
| config_key | VARCHAR(50) | 配置键 |
| config_value | VARCHAR(200) | 配置值 |

### 自动升级配置项

| config_key | 说明 | 默认值 |
|------------|------|--------|
| alert.upgrade.info.hours | INFO级升级时间 | 24 |
| alert.upgrade.warning.hours | WARNING级升级时间 | 4 |
| alert.upgrade.critical.hours | CRITICAL级升级时间 | 1 |

### 自动升级逻辑

```java
// 定时任务：每分钟扫描一次
@Scheduled(cron = "0 * * * * ?")
public void autoUpgradeAlerts() {
    List<Alert> unresolvedAlerts = alertMapper.findUnresolved();
    for (Alert alert : unresolvedAlerts) {
        AlertLevel currentLevel = AlertLevel.fromCode(alert.getAlertLevel());
        double upgradeHours = getUpgradeHours(currentLevel);
        
        // 从配置表读取实际超时时间
        double configuredHours = getConfiguredHours(currentLevel);
        
        Duration elapsed = Duration.between(alert.getCreatedAt(), LocalDateTime.now());
        if (elapsed.toHours() >= configuredHours) {
            // 执行升级
            AlertLevel nextLevel = currentLevel.nextLevel();
            alert.setPreviousLevel(currentLevel.name());
            alert.setAlertLevel(nextLevel.name());
            alert.setUpgradedAt(LocalDateTime.now());
            alertMapper.update(alert);
        }
    }
}
```

### 统计接口

#### 告警频次统计
```
GET /api/alert/statistics/frequency
参数:
  - startDate: 开始日期
  - endDate: 结束日期
  - deviceId: 设备ID(可选)
返回:
{
  "data": {
    "total": 100,
    "byLevel": {"INFO": 50, "WARNING": 30, "CRITICAL": 15, "EMERGENCY": 5},
    "byDevice": [{"deviceId": 1, "count": 20}, ...]
  }
}
```

#### 设备故障率排行
```
GET /api/alert/statistics/failure-rank
参数:
  - startDate: 开始日期
  - endDate: 结束日期
返回:
{
  "data": [
    {"deviceId": 1, "deviceName": "设备A", "alertCount": 20, "faultRate": 0.15},
    ...
  ]
}
```

---

## 数据库变更汇总

### 新建表
```sql
CREATE TABLE t_sensor_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_type VARCHAR(50) NOT NULL,
    sensor_code VARCHAR(50) NOT NULL,
    enabled BOOLEAN DEFAULT true,
    alert_threshold DOUBLE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_device_sensor (device_type, sensor_code)
);
```

### 修改表
```sql
-- t_alert 表
ALTER TABLE t_alert ADD COLUMN previous_level VARCHAR(20);
ALTER TABLE t_alert ADD COLUMN upgraded_at DATETIME;

-- t_config 表
INSERT INTO t_config (config_key, config_value, description) VALUES
('alert.upgrade.info.hours', '24', 'INFO级告警升级时间(小时)'),
('alert.upgrade.warning.hours', '4', 'WARNING级告警升级时间(小时)'),
('alert.upgrade.critical.hours', '1', 'CRITICAL级告警升级时间(小时)');
```

---

## 实现顺序

1. 创建 SensorType 枚举（data-collector-service）
2. 创建 t_sensor_config 表及 Mapper
3. 修改数据采集服务支持动态传感器
4. 创建 DeviceStatus 枚举（device-service）
5. 创建 AlertLevel 枚举（alert-service）
6. 扩展 t_alert 表
7. 添加自动升级定时任务
8. 添加统计接口
9. 修改前端适配新状态和枚举
