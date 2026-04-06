package com.example.deviceservice.entity;

/**
 * 维修类型枚举
 */
public enum MaintenanceType {
    /**
     * 日常保养 - 定期保养维护
     */
    ROUTINE("日常保养", "定期保养维护"),

    /**
     * 故障维修 - 故障修复
     */
    REPAIR("故障维修", "故障修复"),

    /**
     * 紧急抢修 - 紧急故障处理
     */
    EMERGENCY("紧急抢修", "紧急故障处理"),

    /**
     * 改造升级 - 设备改造升级
     */
    UPGRADE("改造升级", "设备改造升级"),

    /**
     * 点检 - 日常点检
     */
    INSPECTION("点检", "日常点检");

    /**
     * 名称
     */
    private final String name;

    /**
     * 描述
     */
    private final String description;

    MaintenanceType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据代码查找枚举
     * @param code 类型代码
     * @return 对应的枚举，如果未找到返回 null
     */
    public static MaintenanceType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        try {
            return valueOf(code.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}