# 第2批设计：维修管理增强 + 故障预测改进

## 14. 维修管理增强

### 设计思路
1. **MaintenanceType 枚举** — 5种维修类型
2. **FaultCategory 枚举** — 6种故障分类
3. 扩展 Maintenance 实体，添加 type 和 faultCategory 字段

### MaintenanceType 枚举 (device-service)

```java
public enum MaintenanceType {
    ROUTINE("日常保养", "定期保养维护"),
    REPAIR("故障维修", "故障修复"),
    EMERGENCY("紧急抢修", "紧急故障处理"),
    UPGRADE("改造升级", "设备改造升级"),
    INSPECTION("点检", "日常点检");

    private final String name;
    private final String description;
}
```

### FaultCategory 枚举 (device-service)

```java
public enum FaultCategory {
    EQUIPMENT("设备故障", "设备本身故障"),
    ELECTRICAL("电气故障", "电气系统故障"),
    MECHANICAL("机械故障", "机械部件故障"),
    SENSOR("传感器故障", "传感器异常"),
    SOFTWARE("软件故障", "控制系统故障"),
    OTHER("其他", "其他原因");

    private final String name;
    private final String description;
}
```

### t_maintenance 表扩展

| 字段 | 类型 | 说明 |
|------|------|------|
| fault_category | VARCHAR(20) | 故障分类 |

---

## 15. 故障预测改进

### 15.1 动态传感器适配

**设计思路：**
- 预测输入从固定3个传感器改为动态配置
- 根据设备类型从 t_sensor_config 读取传感器列表
- 动态生成特征向量

**特征向量生成流程：**
```
1. 接收 deviceId
2. 查询设备类型
3. 查询 t_sensor_config 获取配置的传感器列表
4. 从 InfluxDB 获取各传感器最近N个数据点
5. 动态生成特征向量
```

**特征向量格式：**
```
特征向量 = [sensor1_value, sensor2_value, ..., sensorN_value]
```

### 15.2 时序特征扩展

**4种时序特征：**

1. **趋势 (trend)**: 参数变化方向
   - 计算方式：比较最近2次测量的差值
   - 0: 下降 (差值 < -threshold)
   - 1: 平稳 (-threshold <= 差值 <= threshold)
   - 2: 上升 (差值 > threshold)

2. **波动率 (volatility)**: 参数波动程度
   - 计算方式：标准差 / 均值
   - 反映传感器数据的稳定性

3. **累积超限 (accumulation)**: 超阈值累积次数
   - 计算方式：过去N次测量中超过阈值的次数
   - 阈值从 t_sensor_config.alert_threshold 读取

4. **移动平均 (moving_avg)**: 滑动窗口平均值
   - 计算方式：最近K次测量的平均值
   - K可配置，默认5

**完整特征向量：**
```
[sensor1值, sensor2值, ..., sensorN值,
 trend1, trend2, ..., trendN,
 volatility1, volatility2, ..., volatilityN,
 accumulation1, accumulation2, ..., accumulationN,
 moving_avg1, moving_avg2, ..., moving_avgN]
```

### 15.3 动态阈值

**设计：**
- 从 t_sensor_config 读取各传感器的 alert_threshold
- 累积超限使用配置的实际阈值
- 不同设备类型使用不同阈值

---

## 数据库变更

### 修改表
```sql
-- t_maintenance 表
ALTER TABLE t_maintenance ADD COLUMN fault_category VARCHAR(20);
```

---

## 实现顺序

1. 创建 MaintenanceType 枚举（device-service）
2. 创建 FaultCategory 枚举（device-service）
3. 扩展 Maintenance 实体添加 faultCategory 字段
4. 修改前端维修记录页面适配新字段
5. 修改 ml-service 支持动态传感器
6. 实现时序特征提取
7. 实现动态阈值
