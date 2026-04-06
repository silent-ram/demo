# 第2批实现计划：维修管理增强 + 故障预测改进

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现维修管理增强(维修类型+故障分类枚举)和故障预测改进(动态传感器+时序特征+动态阈值)

**Architecture:** 
- 维修管理：通过枚举类统一管理维修类型和故障分类，扩展实体字段
- 故障预测：ml-service 动态读取传感器配置，提取时序特征，使用动态阈值

**Tech Stack:** Spring Boot + MyBatis-Plus + Python Flask + Vue 3

---

## 文件结构

```
device-service/
├── entity/MaintenanceType.java (新建)
├── entity/FaultCategory.java (新建)
├── entity/Maintenance.java (修改)
├── service/MaintenanceService.java (修改)
└── controller/MaintenanceController.java (修改)

ml-service/
├── predict.py (修改)
├── feature_extractor.py (新建)
└── app.py (修改)

frontend/
└── src/views/MaintenanceView.vue (修改)

sql/
└── init.sql (修改)
```

---

## Task 1: 创建 MaintenanceType 枚举 (device-service)

**Files:**
- Create: `device-service/src/main/java/com/example/deviceservice/entity/MaintenanceType.java`

- [ ] **Step 1: 创建 MaintenanceType 枚举类**

```java
package com.example.deviceservice.entity;

public enum MaintenanceType {
    ROUTINE("日常保养", "定期保养维护"),
    REPAIR("故障维修", "故障修复"),
    EMERGENCY("紧急抢修", "紧急故障处理"),
    UPGRADE("改造升级", "设备改造升级"),
    INSPECTION("点检", "日常点检");

    private final String name;
    private final String description;

    MaintenanceType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public static MaintenanceType fromCode(String code) {
        if (code == null) return null;
        for (MaintenanceType type : values()) {
            if (type.name().equals(code)) {
                return type;
            }
        }
        return null;
    }
}
```

- [ ] **Step 2: 提交代码**

```bash
git add device-service/src/main/java/com/example/deviceservice/entity/MaintenanceType.java
git commit -m "feat: 添加维修类型枚举 MaintenanceType"
```

---

## Task 2: 创建 FaultCategory 枚举 (device-service)

**Files:**
- Create: `device-service/src/main/java/com/example/deviceservice/entity/FaultCategory.java`

- [ ] **Step 1: 创建 FaultCategory 枚举类**

```java
package com.example.deviceservice.entity;

public enum FaultCategory {
    EQUIPMENT("设备故障", "设备本身故障"),
    ELECTRICAL("电气故障", "电气系统故障"),
    MECHANICAL("机械故障", "机械部件故障"),
    SENSOR("传感器故障", "传感器异常"),
    SOFTWARE("软件故障", "控制系统故障"),
    OTHER("其他", "其他原因");

    private final String name;
    private final String description;

    FaultCategory(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public static FaultCategory fromCode(String code) {
        if (code == null) return null;
        for (FaultCategory category : values()) {
            if (category.name().equals(code)) {
                return category;
            }
        }
        return null;
    }
}
```

- [ ] **Step 2: 提交代码**

```bash
git add device-service/src/main/java/com/example/deviceservice/entity/FaultCategory.java
git commit -m "feat: 添加故障分类枚举 FaultCategory"
```

---

## Task 3: 扩展 Maintenance 实体 (device-service)

**Files:**
- Modify: `device-service/src/main/java/com/example/deviceservice/entity/Maintenance.java`

- [ ] **Step 1: 添加 faultCategory 字段**

在 Maintenance.java 中添加:
```java
private String faultCategory;
```

添加对应的 getter/setter:
```java
public String getFaultCategory() {
    return faultCategory;
}

public void setFaultCategory(String faultCategory) {
    this.faultCategory = faultCategory;
}
```

- [ ] **Step 2: 提交代码**

```bash
git add device-service/src/main/java/com/example/deviceservice/entity/Maintenance.java
git commit -m "feat: 扩展 Maintenance 实体添加 faultCategory 字段"
```

---

## Task 4: 前端适配维修记录 (frontend)

**Files:**
- Modify: `frontend/src/views/MaintenanceView.vue`

- [ ] **Step 1: 添加维修类型和故障分类选项**

添加维修类型下拉选项：
```javascript
const maintenanceTypes = [
  { value: 'ROUTINE', label: '日常保养' },
  { value: 'REPAIR', label: '故障维修' },
  { value: 'EMERGENCY', label: '紧急抢修' },
  { value: 'UPGRADE', label: '改造升级' },
  { value: 'INSPECTION', label: '点检' }
]

const faultCategories = [
  { value: 'EQUIPMENT', label: '设备故障' },
  { value: 'ELECTRICAL', label: '电气故障' },
  { value: 'MECHANICAL', label: '机械故障' },
  { value: 'SENSOR', label: '传感器故障' },
  { value: 'SOFTWARE', label: '软件故障' },
  { value: 'OTHER', label: '其他' }
]
```

- [ ] **Step 2: 修改表格列显示**

在表格中添加故障分类列：
```vue
<el-table-column prop="faultCategory" label="故障分类" width="120">
  <template #default="{ row }">
    <el-tag>{{ getFaultCategoryText(row.faultCategory) }}</el-tag>
  </template>
</el-table-column>
```

添加方法：
```javascript
function getFaultCategoryText(category) {
  const map = {
    'EQUIPMENT': '设备故障',
    'ELECTRICAL': '电气故障',
    'MECHANICAL': '机械故障',
    'SENSOR': '传感器故障',
    'SOFTWARE': '软件故障',
    'OTHER': '其他'
  }
  return map[category] || category || '-'
}
```

- [ ] **Step 3: 提交代码**

```bash
git add frontend/src/views/MaintenanceView.vue
git commit -m "feat: 前端适配维修类型和故障分类"
```

---

## Task 5: 创建特征提取器 (ml-service)

**Files:**
- Create: `ml-service/feature_extractor.py`

- [ ] **Step 1: 创建特征提取器模块]

```python
"""
特征提取器：计算时序特征
"""
import numpy as np
from typing import List, Dict, Any

class FeatureExtractor:
    def __init__(self, window_size=10, moving_avg_window=5):
        self.window_size = window_size  # 用于计算趋势和波动率的数据点数
        self.moving_avg_window = moving_avg_window  # 移动平均窗口大小
    
    def extract_features(self, sensor_data: List[Dict], thresholds: Dict[str, float]) -> List[float]:
        """
        提取时序特征
        
        Args:
            sensor_data: 传感器数据列表，每个元素包含 timestamp, sensor_code, value
            thresholds: 传感器阈值字典 {sensor_code: threshold}
        
        Returns:
            特征向量
        """
        if not sensor_data:
            return []
        
        # 按传感器分组
        sensor_groups = self._group_by_sensor(sensor_data)
        
        features = []
        for sensor_code, values in sensor_groups.items():
            threshold = thresholds.get(sensor_code, 0)
            
            # 原始值（最新值）
            latest_value = values[-1]['value'] if values else 0
            features.append(latest_value)
            
            # 趋势
            trend = self._calculate_trend(values)
            features.append(trend)
            
            # 波动率
            volatility = self._calculate_volatility(values)
            features.append(volatility)
            
            # 累积超限
            accumulation = self._calculate_accumulation(values, threshold)
            features.append(accumulation)
            
            # 移动平均
            moving_avg = self._calculate_moving_avg(values)
            features.append(moving_avg)
        
        return features
    
    def _group_by_sensor(self, sensor_data: List[Dict]) -> Dict[str, List[Dict]]:
        """按传感器代码分组"""
        groups = {}
        for item in sensor_data:
            sensor_code = item.get('sensor_code', item.get('metric'))
            if sensor_code not in groups:
                groups[sensor_code] = []
            groups[sensor_code].append(item)
        return groups
    
    def _calculate_trend(self, values: List[Dict]) -> int:
        """计算趋势: 0=下降, 1=平稳, 2=上升"""
        if len(values) < 2:
            return 1
        
        recent = values[-3:] if len(values) >= 3 else values
        if len(recent) < 2:
            return 1
        
        diff = recent[-1]['value'] - recent[0]['value']
        threshold = 0.1  # 趋势判断阈值
        
        if diff < -threshold:
            return 0  # 下降
        elif diff > threshold:
            return 2  # 上升
        else:
            return 1  # 平稳
    
    def _calculate_volatility(self, values: List[Dict]) -> float:
        """计算波动率: 标准差 / 均值"""
        if len(values) < 2:
            return 0.0
        
        vals = [v['value'] for v in values]
        mean = np.mean(vals)
        std = np.std(vals)
        
        if mean == 0:
            return 0.0
        return float(std / mean)
    
    def _calculate_accumulation(self, values: List[Dict], threshold: float) -> int:
        """计算累积超限次数"""
        if threshold == 0:
            return 0
        
        count = 0
        for v in values:
            if v['value'] > threshold:
                count += 1
        return count
    
    def _calculate_moving_avg(self, values: List[Dict]) -> float:
        """计算移动平均值"""
        if not values:
            return 0.0
        
        recent = values[-self.moving_avg_window:] if len(values) >= self.moving_avg_window else values
        vals = [v['value'] for v in recent]
        return float(np.mean(vals))


# 默认特征提取器
extractor = FeatureExtractor()
```

- [ ] **Step 2: 提交代码**

```bash
git add ml-service/feature_extractor.py
git commit -m "feat: 添加时序特征提取器"
```

---

## Task 6: 修改预测服务支持动态传感器 (ml-service)

**Files:**
- Modify: `ml-service/predict.py`
- Modify: `ml-service/app.py`

- [ ] **Step 1: 修改 predict.py 支持动态特征]

修改 FaultPredictor 类：
```python
class FaultPredictor:
    def __init__(self):
        self.model = None
        self.scaler = None
        self.load_model()
        self.feature_extractor = FeatureExtractor()
    
    def predict_with_config(self, device_type: str, sensor_data: List[Dict], thresholds: Dict[str, float]):
        """
        使用动态配置的传感器进行预测
        
        Args:
            device_type: 设备类型
            sensor_data: 传感器数据列表
            thresholds: 各传感器的告警阈值
        
        Returns:
            故障概率
        """
        if self.model is None:
            return None
        
        # 提取时序特征
        features = self.feature_extractor.extract_features(sensor_data, thresholds)
        
        if not features:
            return None
        
        # 转换为模型输入格式
        features_array = np.array([features])
        
        if self.scaler is not None:
            features_array = self.scaler.transform(features_array)
        
        proba = self.model.predict_proba(features_array)[0][1]
        return float(proba)
```

- [ ] **Step 2: 修改 app.py 添加新接口]

添加新接口用于动态传感器预测：
```python
@app.route('/api/predict/dynamic', methods=['POST'])
def predict_dynamic():
    """
    动态传感器预测接口
    
    请求体:
    {
        "device_id": "设备ID",
        "device_type": "设备类型",
        "sensor_data": [
            {"timestamp": "2026-04-06T10:00:00", "sensor_code": "temperature", "value": 85},
            ...
        ],
        "thresholds": {
            "temperature": 80,
            "vibration": 0.6
        }
    }
    """
    data = request.get_json()
    
    try:
        predictor = FaultPredictor()
        probability = predictor.predict_with_config(
            data.get('device_type'),
            data.get('sensor_data', []),
            data.get('thresholds', {})
        )
        
        return jsonify({
            'code': 200,
            'message': 'success',
            'data': {
                'faultProbability': probability
            }
        })
    except Exception as e:
        return jsonify({
            'code': 500,
            'message': str(e),
            'data': None
        }), 500
```

- [ ] **Step 3: 提交代码**

```bash
git add ml-service/predict.py ml-service/app.py
git commit -m "feat: ML服务支持动态传感器预测"
```

---

## Task 7: 修改数据采集服务传递传感器配置 (data-collector-service)

**Files:**
- Modify: `data-collector-service/src/main/java/com/example/datacollectorservice/service/SensorSimulator.java`

- [ ] **Step 1: 修改预测请求包含传感器配置)

在 generateSensorData 方法中，调用 ML 服务时传递传感器配置：
```java
// 构建预测请求
PredictRequest request = new PredictRequest();
request.setDeviceId(deviceId);
// 添加传感器数据
request.setSensorData(batchMetrics);
// 添加阈值配置（从 SensorType 枚举获取）
Map<String, Double> thresholds = new HashMap<>();
for (MetricDTO metric : batchMetrics) {
    SensorType type = SensorType.fromCode(metric.getMetricName());
    if (type != null) {
        thresholds.put(metric.getMetricName(), type.getAlertThreshold());
    }
}
request.setThresholds(thresholds);
```

- [ ] **Step 2: 修改 PredictRequest DTO)

在 PredictRequest 中添加字段：
```java
private List<MetricDTO> sensorData;
private Map<String, Double> thresholds;
```

- [ ] **Step 3: 提交代码**

```bash
git add data-collector-service/src/main/java/com/example/datacollectorservice/dto/PredictRequest.java
git add data-collector-service/src/main/java/com/example/datacollectorservice/service/SensorSimulator.java
git commit -m "feat: 数据采集服务传递传感器配置到ML服务"
```

---

## Task 8: 数据库变更

**Files:**
- Modify: `sql/init.sql`

- [ ] **Step 1: 添加 fault_category 字段)**

```sql
-- 扩展 t_maintenance 表
USE fault_warning_device;

ALTER TABLE t_maintenance ADD COLUMN fault_category VARCHAR(20);
```

- [ ] **Step 2: 提交代码**

```bash
git add sql/init.sql
git commit -m "feat: 添加维修记录故障分类字段"
```

---

## 实现顺序说明

1. Task 1-2: 创建枚举类
2. Task 3: 扩展实体
3. Task 4: 前端适配
4. Task 5: 特征提取器
5. Task 6-7: ML 服务和数据采集服务
6. Task 8: 数据库变更

---

## Plan Review

### Spec Coverage Check
- [x] #14 维修管理增强 - Task 1, 2, 3, 4, 8
- [x] #15 故障预测改进 - Task 5, 6, 7

### Placeholder Scan
- 无 TBD/TODO
- 所有代码已完整提供

### Type Consistency
- MaintenanceType 枚举名称与数据库值一致
- FaultCategory 枚举与前端显示映射正确

Plan complete and saved to `docs/superpowers/plans/2026-04-06-maintenance-fault-prediction-plan.md`.
