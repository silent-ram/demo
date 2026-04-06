-- ============================================================
-- 工业设备故障预警系统 - 示例数据初始化
-- ============================================================

USE fault_warning_device;

-- 删除维修记录
DELETE FROM t_maintenance;
-- 删除设备
DELETE FROM t_device;

-- 插入示例设备
INSERT INTO t_device (device_no, name, type, status, location, simulation_enabled) VALUES
('DEV001', '工业机器人A1', '工业机器人', 'NORMAL', '车间1-A区', TRUE),
('DEV002', '数控机床B2', '数控机床', 'NORMAL', '车间1-B区', TRUE),
('DEV003', '输送带C3', '输送设备', 'NORMAL', '车间2-A区', TRUE),
('DEV004', '焊接机D4', '焊接设备', 'OFFLINE', '车间2-B区', FALSE),
('DEV005', '压力机E5', '压力设备', 'NORMAL', '车间3-A区', TRUE),
('DEV006', '包装机F6', '包装设备', 'NORMAL', '车间3-B区', TRUE);

USE fault_warning_alert;

-- 先删除有外键关联的记录
DELETE FROM t_alert;
-- 插入示例告警 (device_id 使用子查询获取)
INSERT INTO t_alert (device_id, device_name, fault_probability, alert_level, type, message, resolved, resolve_note, resolved_at, resolved_by)
SELECT
    d.id, d.name, 0.85, 'HIGH', '故障概率预警', '当前故障概率: 0.8500', TRUE, '已完成维修', '2026-03-15 11:00:00', 1
FROM fault_warning_device.t_device d WHERE d.device_no = 'DEV001'
UNION ALL
SELECT
    d.id, d.name, 0.72, 'MEDIUM', '故障概率预警', '当前故障概率: 0.7200', TRUE, '已检查无需维修', '2026-03-20 15:00:00', 1
FROM fault_warning_device.t_device d WHERE d.device_no = 'DEV002'
UNION ALL
SELECT
    d.id, d.name, 0.65, 'LOW', '故障概率预警', '当前故障概率: 0.6500', FALSE, NULL, NULL, NULL
FROM fault_warning_device.t_device d WHERE d.device_no = 'DEV003'
UNION ALL
SELECT
    d.id, d.name, 0.91, 'HIGH', '故障概率预警', '当前故障概率: 0.9100', FALSE, NULL, NULL, NULL
FROM fault_warning_device.t_device d WHERE d.device_no = 'DEV006';

-- 删除配置
DELETE FROM t_config;
-- 插入初始配置
INSERT INTO t_config (config_key, value) VALUES
('fault_threshold', '0.7'),
('check_interval', '10000'),
('alert_retention_days', '30');

SELECT '示例数据初始化完成!' AS status;

-- 操作日志表
USE fault_warning_user;

CREATE TABLE IF NOT EXISTS t_operation_log (
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
USE fault_warning_alert;

CREATE TABLE IF NOT EXISTS t_message (
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
CREATE TABLE IF NOT EXISTS t_notification_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    alert_level VARCHAR(20) NOT NULL,
    enable_push BOOLEAN DEFAULT TRUE,
    enable_message BOOLEAN DEFAULT TRUE,
    UNIQUE KEY uk_user_level (user_id, alert_level)
);
