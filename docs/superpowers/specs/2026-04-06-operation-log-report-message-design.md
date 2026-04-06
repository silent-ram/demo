# 第3批设计：操作日志 + 报表导出 + 消息通知改进

## 16. 操作日志

### 设计思路
1. **创建操作日志表** `t_operation_log`
2. **操作类型枚举** - 5种类型
3. **日志记录服务** - 统一记录接口

### OperationType 枚举 (user-service)

```java
public enum OperationType {
    LOGIN("登录", "用户登录"),
    LOGOUT("登出", "用户登出"),
    DEVICE("设备操作", "设备启停/参数修改"),
    ALERT("告警处理", "告警处理/确认"),
    MAINTENANCE("维修记录", "创建/修改维修记录"),
    CONFIG("系统配置", "修改阈值/参数配置");
    
    private final String name;
    private final String description;
}
```

### t_operation_log 表结构

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 操作人ID |
| username | VARCHAR(50) | 操作人姓名 |
| operation_type | VARCHAR(20) | 操作类型 |
| operation_content | VARCHAR(500) | 操作内容 |
| device_id | BIGINT | 关联设备(可选) |
| ip_address | VARCHAR(50) | IP地址 |
| created_at | DATETIME | 操作时间 |

---

## 17. 报表导出

### 设计思路
1. **使用 Apache POI** 生成 Excel 文件
2. **后端生成** - 提供下载接口
3. **4种报表** - 设备台账、告警统计、维修记录、设备运行报表

### 导出接口

```
GET /api/report/export/{reportType}?startDate=&endDate=&deviceId=
```

**reportType 选项：**
- DEVICE: 设备台账
- ALERT: 告警统计
- MAINTENANCE: 维修记录
- OPERATION: 设备运行报表

### Excel 格式要求
- 表头带样式（居中、加粗）
- 数据自动换行
- 数值类型正确格式化
- 中文表头
- 自动列宽调整

---

## 18. 消息通知改进

### 设计思路
1. **消息实体** - 站内消息
2. **消息服务** - 发送/标记已读
3. **消息中心前端** - 展示所有消息
4. **通知配置** - 哪些级别需要推送

### t_message 表结构

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 接收用户 |
| type | VARCHAR(20) | 消息类型 |
| title | VARCHAR(100) | 标题 |
| content | VARCHAR(500) | 内容 |
| related_id | BIGINT | 关联ID(如告警ID) |
| is_read | BOOLEAN | 是否已读 |
| created_at | DATETIME | 创建时间 |

### 消息类型

| 类型代码 | 说明 |
|----------|------|
| ALERT_NOTIFY | 告警通知 |
| ALERT_UPGRADE | 告警升级 |
| DEVICE_STATUS | 设备状态变更 |
| SYSTEM | 系统通知 |

### 通知配置表 t_notification_config

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户ID |
| alert_level | VARCHAR(20) | 告警级别 |
| enable_push | BOOLEAN | 是否推送 |
| enable_message | BOOLEAN | 是否发送站内消息 |

---

## 数据库变更

### 新建表
```sql
-- 操作日志表
CREATE TABLE t_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    username VARCHAR(50),
    operation_type VARCHAR(20) NOT NULL,
    operation_content VARCHAR(500),
    device_id BIGINT,
    ip_address VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_type (operation_type),
    INDEX idx_created (created_at)
);

-- 消息表
CREATE TABLE t_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(100) NOT NULL,
    content VARCHAR(500),
    related_id BIGINT,
    is_read BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_is_read (is_read)
);

-- 通知配置表
CREATE TABLE t_notification_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    alert_level VARCHAR(20) NOT NULL,
    enable_push BOOLEAN DEFAULT TRUE,
    enable_message BOOLEAN DEFAULT TRUE,
    UNIQUE KEY uk_user_level (user_id, alert_level)
);
```

---

## 实现顺序

1. 创建 OperationType 枚举（user-service）
2. 创建操作日志表和 Mapper
3. 创建操作日志记录服务
4. 创建消息表和 Mapper
5. 创建消息服务
6. 实现报表导出服务
7. 前端消息中心页面
8. 前端报表下载功能
