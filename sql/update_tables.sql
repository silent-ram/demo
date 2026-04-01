-- 更新表结构脚本
-- 执行前请确保已备份数据

-- 更新 fault_warning_alert 数据库的 t_alert 表
USE fault_warning_alert;

-- 添加缺失的字段
ALTER TABLE t_alert ADD COLUMN IF NOT EXISTS device_name VARCHAR(128) COMMENT '设备名称';
ALTER TABLE t_alert ADD COLUMN IF NOT EXISTS fault_probability DECIMAL(5,4) COMMENT '故障概率';
ALTER TABLE t_alert ADD COLUMN IF NOT EXISTS resolve_note TEXT COMMENT '处理备注';
ALTER TABLE t_alert ADD COLUMN IF NOT EXISTS resolved_at DATETIME COMMENT '解决时间';
ALTER TABLE t_alert ADD COLUMN IF NOT EXISTS resolved_by BIGINT COMMENT '解决人ID';

-- 修改 severity 字段名为 alert_level（如果存在）
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'fault_warning_alert' AND TABLE_NAME = 't_alert' AND COLUMN_NAME = 'severity');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE t_alert CHANGE COLUMN severity alert_level ENUM(''LOW'',''MEDIUM'',''HIGH'') NOT NULL COMMENT ''告警级别''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 修改 device_id 字段类型（如果需要）
ALTER TABLE t_alert MODIFY COLUMN device_id BIGINT NOT NULL COMMENT '设备ID';

-- 更新 fault_warning_device 数据库
USE fault_warning_device;

-- 添加 device_no 字段
ALTER TABLE t_device ADD COLUMN IF NOT EXISTS device_no VARCHAR(64) UNIQUE COMMENT '设备编号' AFTER id;

-- 更新 t_maintenance 表
ALTER TABLE t_maintenance ADD COLUMN IF NOT EXISTS alert_id BIGINT COMMENT '关联告警ID';
ALTER TABLE t_maintenance ADD COLUMN IF NOT EXISTS action_taken TEXT COMMENT '处理措施';
ALTER TABLE t_maintenance ADD COLUMN IF NOT EXISTS operator_id BIGINT COMMENT '维修人员ID';
ALTER TABLE t_maintenance ADD COLUMN IF NOT EXISTS repaired_at DATETIME COMMENT '维修时间';

-- 添加索引
CREATE INDEX IF NOT EXISTS idx_alert_id ON t_maintenance(alert_id);

SELECT 'Database update completed!' AS status;
