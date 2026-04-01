# 工业设备故障预警系统 — 开发执行计划

## 状态说明
- [ ] 待开发
- [x] 已完成

---

## 阶段一：基础设施

### [x] 1. sql/ — 数据库初始化脚本 (2026-03-31)
- 创建 3 个数据库：`fault_warning_user`、`fault_warning_device`、`fault_warning_alert`
- 建表：`t_user`、`t_device`、`t_maintenance`、`t_alert`、`t_config`
- 插入初始数据：admin/operator 账号、默认阈值配置
- 文件：`sql/init.sql`

### [x] 2. ml-service/ — Python Flask ML 服务 (2026-03-31)
- `requirements.txt`：flask, scikit-learn, matplotlib, numpy, pandas, joblib, influxdb-client
- `train.py`：生成模拟数据集（5000条，正常/故障标签）+ 训练 LogisticRegression + 保存 model/
- `predict.py`：加载模型，预测故障概率
- `chart.py`：Matplotlib 生成多维趋势图，返回 base64 PNG
- `app.py`：Flask 路由 /predict、/chart/trend、/model/metrics、/model/retrain、/health
- 验证：`python train.py` 输出模型指标，`python app.py` 启动后 curl /health

---

## 阶段二：Spring Cloud 微服务

### [x] 3. eureka-server/ (2026-03-31)
- pom.xml：spring-boot-starter-parent 3.2.5 + spring-cloud-starter-netflix-eureka-server
- application.yml：端口 8761，关闭自我注册
- 主类：`@EnableEurekaServer`
- 验证：访问 http://localhost:8761

### [x] 4. user-service/ (port 8081) (2026-04-01)
- 依赖：eureka-client, web, mysql, mybatis-plus, jjwt, springdoc-openapi
- entity：User
- mapper：UserMapper
- service：UserService（login 返回 JWT，register，list）
- controller：UserController（/user/login, /user/register, /user/info, /user/list）
- config：JwtUtil（生成/解析 token），SecurityConfig（不拦截login/register）
- dto：LoginRequest, LoginResponse, UserDTO
- 全局异常处理：GlobalExceptionHandler + Result<T>
- 权限控制：/user/list 只允许 ADMIN 访问
- 验证：POST /user/login 返回 token，权限控制正常，Swagger 文档可访问

### [x] 5. gateway-service/ (port 8080) (2026-04-01)
- 依赖：eureka-client, spring-cloud-starter-gateway
- application.yml：路由规则（user/device/collector/alert 服务）
  - device-service 路由已修复为 /api/device/**，StripPrefix=1
- JwtAuthFilter：全局过滤器，验证 JWT，白名单 /user/login、/user/register
- 验证：通过网关调用 /api/user/login，JWT 过滤器正常工作（无 token 返回 401）

### [x] 6. device-service/ (port 8082) (2026-04-01)
- entity：Device, Maintenance
- mapper：DeviceMapper, MaintenanceMapper
- service：DeviceService, MaintenanceService
- controller：DeviceController（CRUD + 状态更新 + 搜索），MaintenanceController
- feign：无（被其他服务调用）
- Swagger：所有接口加 @Operation
- 验证：CRUD 设备，查询维修记录，设备搜索功能正常，Swagger 文档可访问

### [x] 7. data-collector-service/ (port 8083) (2026-04-01)
- 依赖：eureka-client, web, influxdb-client-java, openfeign, scheduler, springdoc-openapi
- config：InfluxDBConfig（连接配置）
- service：SensorSimulator（@Scheduled 每5秒生成数据，正常/故障交替模拟）
- service：InfluxDBService（写入/查询 sensor_data）
- feign：AlertServiceClient（推送最新指标触发预警判断）
- controller：CollectorController（/metrics/{deviceId}?range=1h，/latest/{deviceId}）
- Swagger：所有接口添加 @Operation 注解
- 验证：InfluxDB 每5秒写入数据，查询返回时序数据，Swagger 文档可访问

### [x] 8. alert-service/ (port 8084) (2026-04-01)
- 依赖：eureka-client, web, mysql, mybatis-plus, openfeign, resilience4j, websocket
- entity：Alert, Config
- mapper：AlertMapper, ConfigMapper
- feign：MlServiceClient（POST /predict），DeviceServiceClient（PUT /device/{id}/status）
- service：AlertService（接收指标→调ML→比较阈值→存告警→推WebSocket）
- config：WebSocketConfig（STOMP，端点 /ws/alert，topic /topic/alerts）
- config：Resilience4jConfig（ml-service 断路器降级）
- controller：AlertController（list/detail/resolve/stats/threshold/chart）
- 验证：data-collector 推送数据后，alert 表生成记录，前端 WS 收到消息

---

## 阶段三：前端

### [x] 9. frontend/ — Vue 3 + Element Plus (2026-04-01)

**初始化：**
```
npm create vite@latest frontend -- --template vue
cd frontend
npm install element-plus @element-plus/icons-vue
npm install pinia vue-router axios echarts
npm install -D unplugin-auto-import unplugin-vue-components
```

**目录结构：**
```
src/
├── api/          # axios 请求（user.js, device.js, alert.js, collector.js, ml.js）
├── router/       # 路由配置 + 路由守卫
├── stores/       # Pinia（user.js, alert.js）
├── utils/        # 工具类（request.js）
├── views/        # 页面组件
│   ├── LoginView.vue
│   ├── DashboardView.vue
│   ├── DevicesView.vue
│   ├── DeviceDetailView.vue
│   ├── MonitoringView.vue
│   ├── AlertsView.vue
│   ├── MaintenanceView.vue
│   ├── ModelView.vue        # ADMIN only
│   └── UsersView.vue        # ADMIN only
└── components/   # 公共组件（Layout.vue）
```

**已完成功能：**
- [x] 项目初始化与基础配置
- [x] 路由配置与布局组件
- [x] API 封装与 Axios 配置
- [x] Pinia 状态管理
- [x] 登录页面
- [x] 系统首页仪表盘
- [x] 设备管理页面
- [x] 设备详情页面
- [x] 实时监控页面
- [x] 告警管理页面
- [x] 维修记录页面
- [x] 模型管理页面（ADMIN）
- [x] 用户管理页面（ADMIN）

**关键实现点：**
- axios 拦截器：请求注入 `Authorization: Bearer {token}`，401 跳转登录
- WebSocket：连接 `ws://localhost:8084/ws/alert`，新告警 ElNotification 弹出
- ECharts：DeviceDetail 页实时折线图（温度/振动/压力，30秒轮询刷新）
- Matplotlib 图：alert-service /chart/{deviceId} 返回 base64，`<img :src="'data:image/png;base64,'+img">` 展示
- 路由守卫：无 token → /login；OPERATOR 访问 /model 或 /users → 重定向首页

**页面功能：**
| 页面 | 核心内容 | 状态 |
|------|----------|------|
| Login | 登录表单，JWT 存 localStorage | ✅ |
| Dashboard | 4卡片统计 + 告警分布饼图 + 24h趋势图 + 设备风险排行 | ✅ |
| Devices | 设备表格（筛选/分页）+ 新增/编辑弹窗 | ✅ |
| DeviceDetail | 基本信息 + ECharts实时图 + Matplotlib趋势图 + 维修时间轴 | ✅ |
| Monitoring | 所有设备卡片 + 故障概率进度条 + WS实时刷新 | ✅ |
| Alerts | 告警表格（筛选/分页）+ 处理弹窗 | ✅ |
| Maintenance | 维修记录表格 + 新增弹窗 | ✅ |
| Model | 模型指标卡片 + 阈值配置 + 再训练按钮（ADMIN） | ✅ |
| Users | 用户管理表格 + 新增/删除（ADMIN） | ✅ |

---

## 验证清单

- [x] MySQL 三个库建表成功，初始数据存在
- [x] ml-service 启动，/health 返回 200，/predict 返回概率
- [x] Eureka 控制台显示所有服务已注册
- [x] POST /api/user/login（通过网关）返回 JWT token
- [x] 设备 CRUD 接口正常，维修记录可查询
- [x] InfluxDB 每 5 秒写入数据，/collector/latest/{id} 返回最新数据
- [x] 故障概率超阈值时 t_alert 生成记录
- [x] 前端 WebSocket 收到实时告警推送
- [x] 处理告警后自动创建维修记录
- [x] 模型再训练后 /model/metrics 指标更新
- [x] 前端所有页面正常渲染，权限控制生效
