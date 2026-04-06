package com.example.alertservice.enum;

/**
 * 告警级别枚举
 */
public enum AlertLevel {
    /**
     * 提示 - 24小时升级
     */
    INFO("提示", "info", 24),

    /**
     * 警告 - 4小时升级
     */
    WARNING("警告", "warning", 4),

    /**
     * 严重 - 1小时升级
     */
    CRITICAL("严重", "danger", 1),

    /**
     * 紧急 - 15分钟升级
     */
    EMERGENCY("紧急", "danger", 0.25);

    /**
     * 描述文本
     */
    private final String description;

    /**
     * 前端显示颜色
     */
    private final String color;

    /**
     * 升级超时时间(小时)
     */
    private final double upgradeHours;

    AlertLevel(String description, String color, double upgradeHours) {
        this.description = description;
        this.color = color;
        this.upgradeHours = upgradeHours;
    }

    public String getDescription() {
        return description;
    }

    public String getColor() {
        return color;
    }

    public double getUpgradeHours() {
        return upgradeHours;
    }

    /**
     * 根据代码查找枚举
     * @param code 告警级别代码
     * @return 对应的枚举，如果未找到返回 null
     */
    public static AlertLevel fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (AlertLevel level : values()) {
            if (level.name().equalsIgnoreCase(code)) {
                return level;
            }
        }
        return null;
    }

    /**
     * 获取下一级告警
     * @return 下一级告警，如果已是最高级别则返回 null
     */
    public AlertLevel nextLevel() {
        int ordinal = this.ordinal();
        if (ordinal >= values().length - 1) {
            return null;
        }
        return values()[ordinal + 1];
    }
}
