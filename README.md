# 基于 Logistic 回归的工业设备故障预警系统

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.1.8-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3.x-4fc08d)](https://vuejs.org/)
[![Python](https://img.shields.io/badge/Python-3.11-3776ab)](https://www.python.org/)
[![InfluxDB](https://img.shields.io/badge/InfluxDB-2.7-22ad5c)](https://www.influxdata.com/)

面向工业 4.0 场景，对设备温度、振动、压力等传感器数据进行实时采集，利用 **Logistic 回归**模型预测故障概率，结合 Spring Cloud 微服务架构与 WebSocket 实现实时预警推送，助力企业实现**预测性维护**。

---

## 核心功能

| 功能 | 说明 |
|:-----|:-----|
| **实时数据采集** | 多模式传感器模拟（正常/渐变故障/突发故障/随机游走），数据写入 InfluxDB 时序数据库 |
| **故障概率预测** | 15 维时序特征提取 × 6 类设备独立模型，基于规则兜底防止稳定故障漏报 |
| **模型版本管理** | 支持训练、回滚、新旧模型 F1 对比，新模型不低于旧模型 95% 时自动替换 |
| **多级告警** | 故障概率超阈值自动触发，同设备告警合并，超时未处理自动升级 |
| **WebSocket 推送** | 告警消息实时推送至前端，多标签页去重，关联维修记录一键查看 |
| **数据可视化** | ECharts 实时传感器趋势 + Matplotlib 后端生成故障概率曲线，导出为 Base64 图片 |

---

## 系统架构

```
┌─────────────┐     HTTP / WebSocket     ┌──────────────────────────┐
│  Vue 3 前端  │ ◄──────────────────────► │  Spring Cloud Gateway    │
│   (3000)     │                          │         (8080)           │
└─────────────┘                          └────────────┬─────────────┘
                                                      │
       ┌──────────┬──────────┬────────────┬───────────┴──────────┐
       ▼          ▼          ▼            ▼                      ▼
┌────────────┐ ┌────────┐ ┌─────────────┐ ┌──────────────┐ ┌───────────┐
│User Service│ │Device  │ │Data Collector│ │Alert Service │ │ ML Service│
│  用户鉴权   │ │Service │ │  数据采集     │ │   告警预警    │ │ (Python)  │
│   8081     │ │ 8082   │ │    8083      │ │    8084      │ │   5000    │
└────────────┘ └───┬────┘ └──────┬───────┘ └──────┬───────┘ └───────────┘
                   │             │                 │
                   ▼             ▼                 ▼
           ┌──────────────┐ ┌──────────┐  ┌──────────────┐
           │  MySQL 8.0   │ │ InfluxDB │  │  MySQL 8.0   │
           │  设备/用户     │ │  时序数据 │  │  告警/消息    │
           └──────────────┘ └──────────┘  └──────────────┘
                                              ▲
                              ┌────────────────┘
                     服务注册与发现：Eureka (8761)
```

---

## 技术栈

| 层次 | 技术 |
|:-----|:-----|
| **前端** | Vue 3 + Composition API, Element Plus, ECharts, Pinia, Vite |
| **网关** | Spring Cloud Gateway, JWT 认证过滤, CORS, 安全头注入 |
| **微服务** | Spring Boot 3.1, Spring Cloud 2022, OpenFeign, Resilience4j, MyBatis-Plus |
| **机器学习** | Python 3.11, Flask, Scikit-learn (LogisticRegression), Matplotlib, Joblib |
| **时序数据库** | InfluxDB 2.7 — 传感器数据存储与 Flux 查询 |
| **关系数据库** | MySQL 8.0 — 用户/设备/告警/维修业务数据 |
| **服务治理** | Eureka 服务注册发现, Feign 声明式调用, Resilience4j 熔断降级 |
| **实时通信** | WebSocket — 告警推送 + 传感器状态广播 |

---

## 项目结构

```
demo/
├── eureka-server/              # 服务注册中心 (8761)
├── gateway-service/            # API 网关 + JWT 过滤 (8080)
├── user-service/               # 用户管理 + 鉴权 (8081)
├── device-service/             # 设备台账 + 维修记录 (8082)
├── data-collector-service/     # 传感器模拟采集 + InfluxDB 读写 (8083)
├── alert-service/              # 告警生成/合并/升级 + WebSocket (8084)
├── ml-service/                 # Python Flask ML 服务 (5000)
│   ├── app.py                  # Flask 入口 + API 路由
│   ├── train.py                # 模型训练入口（InfluxDB 真实数据）
│   ├── predict.py              # 15 维特征提取 + 6 类设备预测路由
│   ├── data_fetcher.py         # InfluxDB 数据提取器
│   ├── chart.py                # Matplotlib 图表生成
│   ├── model_version_manager.py# 模型版本管理（训练/回滚/对比）
│   └── config_loader.py        # 统一设备配置加载
├── frontend/                   # Vue 3 前端
│   └── src/
│       ├── views/              # 12 个页面视图
│       ├── api/                # 后端接口封装
│       ├── stores/             # Pinia 状态管理
│       └── router/             # Vue Router 路由
├── config/                     # 跨服务共享配置 (device_profiles.json)
└── sql/                        # 数据库初始化脚本
```

---

## 机器学习模块

### 特征工程

从时序传感器数据中提取 **15 维特征**（5 种特征 × 3 个传感器）：

| 特征 | 温度 | 振动 | 压力 |
|:-----|:----:|:----:|:----:|
| 当前值 | ✓ | ✓ | ✓ |
| 趋势 (Trend) | ✓ | ✓ | ✓ |
| 波动率 (Volatility) | ✓ | ✓ | ✓ |
| 累计超限次数 (Accumulation) | ✓ | ✓ | ✓ |
| 移动均值 (Moving Avg) | ✓ | ✓ | ✓ |

### 多模型架构

为 6 类工业设备分别训练独立的 Logistic Regression 模型：

`工业机器人` · `数控机床` · `输送设备` · `焊接设备` · `压力设备` · `包装设备`

### 训练流程

1. 从 InfluxDB 提取设备历史传感器数据 + `fault_probability` 标签
2. 连续 5 点 fault_probability ≥ 0.7 标记为故障样本，≤ 0.1 为正常样本
3. 滑动窗口提取 15 维特征，最少 500 条有效样本
4. 训练新模型并与当前模型 F1 对比，不低于 95% 时自动替换

---

## 快速启动

### 环境依赖

- **JDK 17+**
- **Maven 3.8+**
- **MySQL 8.0+** — 执行 `sql/init.sql` 初始化数据库
- **InfluxDB 2.7+** — 创建 Bucket `sensor_data`，配置 Org 和 Token
- **Python 3.11+** — `cd ml-service && pip install -r requirements.txt`
- **Node.js 18+** — `cd frontend && npm install`

### 环境变量

```bash
# InfluxDB Token（必须配置）
export INFLUXDB_TOKEN=your_token_here

# ML 服务 API Key（可选，默认 myDefaultMLApiKey）
export ML_API_KEY=your_api_key
```

### 启动顺序

```bash
# 1. 注册中心
cd eureka-server && mvn spring-boot:run

# 2. ML 服务
cd ml-service && python app.py

# 3. 网关
cd gateway-service && mvn spring-boot:run

# 4. 业务服务（顺序不限）
cd user-service && mvn spring-boot:run
cd device-service && mvn spring-boot:run
cd data-collector-service && mvn spring-boot:run
cd alert-service && mvn spring-boot:run

# 5. 前端
cd frontend && npm run dev
```

启动完成后访问 http://localhost:3000

---

## 主要页面

| 页面 | 功能 |
|:-----|:-----|
| **Dashboard** | 实时统计面板，告警分布、设备状态概览 |
| **设备监控** | 设备卡片实时展示，WebSocket 告警弹窗 |
| **设备详情** | 传感器实时数据表格 + ECharts 趋势图 + Matplotlib 故障概率曲线 |
| **告警管理** | 告警列表、分页筛选、确认/解决操作 |
| **设备台账** | 设备 CRUD、状态管理、传感器配置 |
| **维修记录** | 维修工单 CRUD，关联告警记录 |
| **消息中心** | WebSocket 告警通知汇总、已读/未读管理 |
| **操作日志** | 用户操作行为审计追踪 |
| **模型管理** | 6 类设备模型指标卡片、数据集展示、单类型/全量训练、版本历史、阈值配置 |

---

## 许可证

本项目仅供学术交流与技术参考使用。
