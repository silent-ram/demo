# 基于 Logistic 回归的工业设备故障预警系统

河南科技大学 2026届毕业设计 | 信息工程学院 | 陈阳

指导教师：王红艺

---

## 项目简介

本系统面向工业制造场景，通过采集设备运行时的温度、振动、压力等时序传感器数据，利用 Logistic 回归算法实时预测设备故障概率，在故障发生前触发预警，辅助运维人员将「事后维修」转变为「预测性维护」，有效降低企业非计划停机损失。

## 系统架构

```
┌────────────────────────────────────────────┐
│           Vue 3 前端 (port 3000)            │
└────────────────┬───────────────────────────┘
                 │ HTTP / WebSocket
┌────────────────▼───────────────────────────┐
│   gateway-service  Spring Cloud Gateway     │
│   (port 8080)  JWT鉴权 + 路由转发            │
└──┬──────────┬──────────┬───────────────────┘
   │          │          │         │
8081       8082       8083      8084
user-    device-    data-     alert-
service  service  collector  service
   │          │       │          │
 MySQL     MySQL  InfluxDB    MySQL
                          Feign→ml-service(5000)

┌────────────────────────────────────────────┐
│   eureka-server (port 8761) 服务注册中心     │
└────────────────────────────────────────────┘
┌────────────────────────────────────────────┐
│   ml-service  Python Flask (port 5000)      │
│   scikit-learn Logistic回归 + Matplotlib    │
└────────────────────────────────────────────┘
```

## 技术栈

| 层次 | 技术 |
|------|------|
| 前端 | Vue 3 + Element Plus + ECharts + Vite |
| 网关 | Spring Cloud Gateway + JWT |
| 后端微服务 | Spring Boot 3.2 + Spring Cloud 2023 + MyBatis-Plus |
| ML 服务 | Python 3.11 + Flask + scikit-learn + Matplotlib |
| 时序数据库 | InfluxDB 2.7 |
| 关系型数据库 | MySQL 8.0 |
| 服务注册 | Spring Cloud Eureka |
| 服务调用 | OpenFeign |
| 熔断降级 | Resilience4j |
| 实时推送 | WebSocket (STOMP) |
| API 文档 | springdoc-openapi (Swagger UI) |

## 项目结构

```
demo/
├── docs/                          # 项目文档
│   ├── 需求规格说明书.md
│   └── 系统设计说明书.md
├── sql/                           # 数据库初始化脚本
├── eureka-server/                 # 服务注册中心
├── gateway-service/               # API 网关
├── user-service/                  # 用户认证服务
├── device-service/                # 设备管理服务
├── data-collector-service/        # 数据采集服务
├── alert-service/                 # 预警服务
├── ml-service/                    # Python ML 服务
└── frontend/                      # Vue 3 前端
```

## 快速启动

### 环境要求

- JDK 17+
- Maven 3.8+
- Python 3.11+
- MySQL 8.0
- InfluxDB 2.7
- Node.js 18+

### 启动顺序

```bash
# 1. 初始化数据库
mysql -u root -p < sql/init.sql

# 2. 启动 InfluxDB（需提前安装并配置 bucket: device_metrics）

# 3. 训练 ML 模型
cd ml-service
pip install -r requirements.txt
python train.py
python app.py

# 4. 启动注册中心
cd eureka-server && mvn spring-boot:run

# 5. 启动各业务服务（可并行）
cd user-service && mvn spring-boot:run
cd device-service && mvn spring-boot:run
cd data-collector-service && mvn spring-boot:run
cd alert-service && mvn spring-boot:run

# 6. 启动网关
cd gateway-service && mvn spring-boot:run

# 7. 启动前端
cd frontend
npm install
npm run dev
```

### 访问地址

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:3000 |
| API 网关 | http://localhost:8080 |
| Eureka 控制台 | http://localhost:8761 |
| Swagger UI (user) | http://localhost:8081/swagger-ui.html |
| Swagger UI (device) | http://localhost:8082/swagger-ui.html |
| Swagger UI (collector) | http://localhost:8083/swagger-ui.html |
| Swagger UI (alert) | http://localhost:8084/swagger-ui.html |
| ML 服务 | http://localhost:5000 |

### 默认账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | admin123 |
| 运维人员 | operator | operator123 |

⚠️ **安全提示**：首次登录后请立即修改默认密码！

---

## 🔐 安全配置

本项目已实施全面的安全加固措施，详见：

- **[安全配置指南](docs/security-configuration.md)** - 环境变量配置、密钥生成方法、安全最佳实践
- **[部署安全检查清单](docs/deployment-security-checklist.md)** - 部署前安全检查项

### 环境变量配置

1. 复制环境变量模板：
   ```bash
   cp .env.example .env
   ```

2. 编辑 `.env` 文件，填写实际配置值

3. 配置环境变量（Windows PowerShell）：
   ```powershell
   $env:DB_USERNAME="your_db_username"
   $env:DB_PASSWORD="your_secure_password"
   $env:JWT_SECRET="your_jwt_secret"
   # ... 其他环境变量
   ```

### 安全特性

- ✅ 敏感信息使用环境变量管理
- ✅ JWT强随机密钥认证
- ✅ Spring Security注解式权限控制
- ✅ CORS跨域访问限制
- ✅ ML服务API密钥认证
- ✅ 速率限制防止暴力破解
- ✅ 安全HTTP响应头
- ✅ 统一错误处理避免信息泄露
- ✅ 安全日志记录
- ✅ 输入验证防止注入攻击

### 密钥生成

```bash
# JWT密钥（至少256位）
openssl rand -base64 32

# 或使用Python
python -c "import secrets; print(secrets.token_urlsafe(32))"
```

---

## 许可证
