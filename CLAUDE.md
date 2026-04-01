# 项目编码规范与开发指导

## 语言

使用中文沟通。

***

\##数据库

-influxdb: Token ： CexQOQRSk8eHkEaZGLWUEH9qcRtCeaxeeOGKunkU5NPPvSiZe8MuYvdRWL1CDonBcRJvZnVUWxDxuyh7OZKYqQ==

-组织 ：cy

\-- 数据库类型：MySQL

- 用户名：root
- 密码：1234

## Java 开发规范（Spring Boot / Spring Cloud）

### 命名规范

- 类名：UpperCamelCase，如 `DeviceController`、`AlertService`
- 方法名/变量名：lowerCamelCase，如 `getFaultProbability`
- 常量：全大写 + 下划线，如 `DEFAULT_THRESHOLD`
- 包名：全小写，如 `com.example.alertservice.service`
- 数据库表名：`t_` 前缀 + 小写下划线，如 `t_device`
- InfluxDB Measurement：小写下划线，如 `sensor_data`

### 项目结构（每个微服务统一）

```
src/main/java/com/example/{servicename}/
├── config/          # 配置类（Security、Swagger、WebSocket等）
├── controller/      # REST 控制器
├── service/         # 业务逻辑接口
│   └── impl/        # 业务逻辑实现
├── mapper/          # MyBatis-Plus Mapper 接口
├── entity/          # 数据库实体（对应表结构）
├── dto/             # 数据传输对象（请求/响应）
├── feign/           # OpenFeign 客户端接口
├── exception/       # 全局异常处理
└── utils/           # 工具类
```

### REST API 规范

- 统一响应体：`Result<T>` 包装 `{code, message, data}`
- 成功：code=200；业务错误：code=4xx；系统错误：code=5xx
- 使用 `@RestControllerAdvice` 全局异常处理
- 所有接口添加 `@Operation` 注解（springdoc-openapi）
- 路径使用复数名词：`/devices`、`/alerts`

### 数据库规范

- 所有实体继承或包含 `id`（BIGINT AUTO\_INCREMENT）、`created_at`、`updated_at`
- 使用 MyBatis-Plus `@TableName`、`@TableId`、`@TableField` 注解
- 禁止在 Java 代码中拼接 SQL，使用 MyBatis-Plus QueryWrapper 或 XML
- 时间字段使用 `LocalDateTime`，配置 Jackson 序列化为 `yyyy-MM-dd HH:mm:ss`

### 安全规范

- 密码必须 BCrypt 加密，禁止明文存储
- JWT 密钥从配置文件读取，不硬编码在代码中
- Feign 内部调用添加内部鉴权 Header，与外部 JWT 区分
- 敏感配置（数据库密码、JWT secret）放在 `application.yml`，不提交真实值

### 依赖版本（统一使用以下版本，不随意升级）

```xml
<!-- Spring Boot -->
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.2.5</version>
</parent>
<!-- Spring Cloud -->
<spring-cloud.version>2023.0.1</spring-cloud.version>
<!-- MyBatis-Plus -->
<mybatis-plus.version>3.5.7</mybatis-plus.version>
<!-- InfluxDB Client -->
<influxdb-client.version>7.1.0</influxdb-client.version>
<!-- springdoc-openapi -->
<springdoc.version>2.5.0</springdoc.version>
<!-- jjwt -->
<jjwt.version>0.12.5</jjwt.version>
```

### application.yml 规范

- 每个服务配置文件包含：服务名、端口、Eureka 注册地址、数据库连接、日志级别
- 使用 `spring.application.name` 作为 Feign 调用的服务名
- InfluxDB 配置统一放在 `data-collector-service` 和 `alert-service`

***

## Python 开发规范（ml-service）

- 文件结构：`app.py`（Flask入口）、`train.py`（训练脚本）、`predict.py`（预测逻辑）、`chart.py`（图表生成）
- 模型文件保存至 `model/` 目录
- 所有接口统一返回 JSON，含 `success`、`data`、`message` 字段
- 使用 `requirements.txt` 管理依赖

***

## 前端开发规范（Vue 3）

- 组件文件名：UpperCamelCase，如 `DeviceList.vue`
- Pinia store 文件名：`useXxxStore.js`
- API 请求统一封装在 `src/api/` 目录，按模块分文件
- Axios 统一拦截器：请求注入 JWT Header，响应统一处理 401（重定向登录）
- 路由守卫：未登录跳转 `/login`，OPERATOR 禁止访问 ADMIN 路由

***

## 开发顺序（执行 plan.md）

严格按以下顺序开发，每完成一个模块进行基本验证再进入下一个：

1. `sql/` — MySQL 建库建表脚本
2. `ml-service/` — Python 模型训练 + Flask 服务
3. `eureka-server/` — 注册中心
4. `user-service/` — 用户认证 + JWT
5. `gateway-service/` — 网关 + JWT 过滤
6. `device-service/` — 设备管理 + 维修记录
7. `data-collector-service/` — 传感器模拟 + InfluxDB
8. `alert-service/` — 预警 + WebSocket + Resilience4j
9. `frontend/` — Vue 3 所有页面

