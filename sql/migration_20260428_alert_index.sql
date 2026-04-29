-- ============================================================
-- 2026-04-28 告警合并并发安全优化
-- ============================================================

USE fault_warning_alert;

-- 添加复合索引，支撑原子 UPDATE 语句的快速定位
-- WHERE device_id = ? AND type = ? AND resolved = false
ALTER TABLE t_alert ADD INDEX idx_device_type_resolved (device_id, type, resolved);

-- （可选）确保 device_id + type + resolved 组合不重复
-- 如果启用 UNIQUE，则数据库层面彻底杜绝重复告警，但 resolved=true 后重新告警需要该组合可重复
-- 因此不添加 UNIQUE，仅添加普通索引加速查询

SELECT '告警表索引优化完成' AS status;
