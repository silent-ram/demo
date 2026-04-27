# 工业设备故障预警系统

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3.x-4fc08d)](https://vuejs.org/)
[![Python](https://img.shields.io/badge/Python-3.11-3776ab)](https://www.python.org/)
[![InfluxDB](https://img.shields.io/badge/InfluxDB-2.7-22ad5c)](https://www.influxdata.com/)

基于 **Logistic 回归** 的工业设备故障预警与预测性维护系统，采用 Spring Cloud 微服务架构 + Vue 3 前端 + Python Flask ML 服务。

---

## 项目简介

面向工业 4.0 场景的智能化故障预测与维护系统。通过对工业设备（电机、泵组、数控机床等）的温度、振动、压力等传感器数据进行实时采集，利用 Logistic 回归机器学习算法实现故障概率的毫秒级预测，并结合 WebSocket 实现预警消息的主动推送。系统解决工业现场「事后维修成本高」和「计划维护过度」的痛点，助力企业实现**预测性维护 (Predictive Maintenance)**。

---

## 核心功能

- **实时监测**：集成 ECharts 实现传感器数据的秒级动态展示
- **智能预测**：基于 Python Flask 的 ML 服务，利用逻辑回归模型对设备故障概率进行实时预测
- **多级预警**：当故障概率超过设定阈值时，系统自动触发告警并通过 WebSocket 实时推送
- **维修闭环**：完整的设备台账管理及维修记录跟踪，实现从预警到响应的闭环管理
- **趋势分析**：后端生成基于 Matplotlib 的多维数据趋势图，辅助预测性维护决策

---

## 系统架构

```
┌─────────────┐      HTTP/WS       ┌─────────────────────────┐
│  Vue 3 前端  │ ◄───────────────►  │ Spring Cloud Gateway    │
└─────────────┘                    │         (8080)          │
                                   └───────────┬─────────────┘
                                               │
        ┌─────────────┬─────────────┬──────────┴──────────┬─────────────┐
        ▼             ▼             ▼                     ▼             ▼
┌──────────────┐ ┌──────────┐ ┌───────────────┐ ┌──────────────┐ ┌─────────────┐
│ User Service │ │ Device   │ │ Data Collector│ │ Alert Service│ │ ML Service  │
│   (鉴权)     │ │ Service  │ │   (采集)       │ │   (预警)     │ │ (Python)    │
│   8081       │ │  8082    │ │    8083       │ │    8084      │ │  5000       │
└──────────────┘ └──────────┘ └───────┬───────┘ └──────┬───────┘ └─────────────┘
                                      │                │
                                      ▼                ▼
                              ┌──────────────┐ ┌──────────────┐
                              │  InfluxDB    │ │    MySQL     │
                              │  (时序数据)   │ │  (业务数据)   │
                              └──────────────┘ └──────────────┘
```

---

## 技术栈

| 模块 | 技术实现 |
| :--- | :--- |
| **前端** | Vue 3, Element Plus, ECharts, Vite, Pinia |
| **网关** | Spring Cloud Gateway, JWT 认证过滤 |
| **后端微服务** | Spring Boot 3.2, Spring Cloud 2023, OpenFeign, Resilience4j |
| **机器学习** | Python 3.11, Flask, Scikit-learn, Matplotlib, Joblib |
| **数据库** | MySQL 8.0 (业务数据), InfluxDB 2.7 (时序数据) |
| **基础设施** | Eureka (服务注册), WebSocket (实时推送) |

---

## 快速启动

### 环境准备

- **MySQL 8.0+**：执行 `sql/init.sql` 初始化数据库和示例数据
- **InfluxDB 2.7+**：创建存储桶 (Bucket) `sensor_data`，组织名 (Org) 按需配置
- **Python 3.11+**：进入 `ml-service` 执行 `pip install -r requirements.txt`
- **Node.js 18+**：进入 `frontend` 执行 `npm install`

### 启动顺序

1. **eureka-server** (8761) — 注册中心
2. **ml-service** (5000) — Python Flask 机器学习服务
3. **gateway-service** (8080) — Spring Cloud 网关
4. **user-service** (8081) / **device-service** (8082) / **data-collector-service** (8083) / **alert-service** (8084) — 业务微服务
5. **frontend** — `npm run dev` 启动前端开发服务器 (3000)

### InfluxDB Token 配置

本地启动前，请在系统环境变量中设置真实的 InfluxDB Token：

```bash
# Windows
set INFLUXDB_TOKEN=your_real_token_here

# Linux / macOS
export INFLUXDB_TOKEN=your_real_token_here
```

各服务的 `application.yml` 中默认值为占位符，需通过环境变量注入真实 Token。

---

## 接口文档

各微服务启动后，访问对应端口的 Swagger UI 查看 API 文档：

```
http://localhost:8081/swagger-ui.html  # User Service
http://localhost:8082/swagger-ui.html  # Device Service
http://localhost:8083/swagger-ui.html  # Data Collector Service
http://localhost:8084/swagger-ui.html  # Alert Service
```

---

## 主要模块说明

### 前端 (frontend)

- **Dashboard**：实时统计面板，展示告警分布与趋势
- **Monitoring**：设备实时监控卡片，WebSocket 推送告警弹窗
- **Alerts**：告警列表与处理（已解决 / 待处理）
- **Devices**：设备台账 CRUD、维修记录关联
- **Model Management**：模型训练、重训练、指标查看

### 后端微服务

| 服务 | 职责 |
|------|------|
| **gateway-service** | 统一入口、JWT 认证、路由转发、CORS 跨域 |
| **user-service** | 用户注册/登录、JWT 签发、角色管理 (ADMIN/OPERATOR) |
| **device-service** | 设备台账、维修记录、设备状态管理 |
| **data-collector-service** | 传感器模拟采集、InfluxDB 读写、ML 预测调用 |
| **alert-service** | 告警生成/合并/升级、WebSocket 推送、消息通知 |
| **ml-service** | Logistic 回归模型训练/预测、趋势图生成 |

---

## 项目亮点

- **微服务架构**：Eureka 服务发现 + Gateway 统一网关，支持水平扩展
- **熔断降级**：Resilience4j 保护 ML 服务调用，避免级联故障
- **告警合并**：同设备同类型未解决告警自动合并，避免重复通知
- **告警升级**：超过 1 小时未解决且故障概率高的告警自动提升级别
- **跨标签页去重**：WebSocket 告警弹窗通过 `localStorage` 实现浏览器多标签页共享去重
- **模型指标持久化**：训练完成后真实指标写入 `model/metrics.json`，避免硬编码假数据

---

## 许可证

本项目仅供学术交流与技术参考使用。
