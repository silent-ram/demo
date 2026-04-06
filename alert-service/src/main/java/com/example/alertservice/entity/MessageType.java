package com.example.alertservice.entity;

/**
 * 消息类型枚举
 */
public enum MessageType {
    /**
     * 告警通知 - 新告警产生
     */
    ALERT_NOTIFY("告警通知", "新告警产生"),

    /**
     * 告警升级 - 告警超时自动升级
     */
    ALERT_UPGRADE("告警升级", "告警超时自动升级"),

    /**
     * 设备状态 - 设备状态变更
     */
    DEVICE_STATUS("设备状态", "设备状态变更"),

    /**
     * 系统通知 - 系统公告
     */
    SYSTEM("系统通知", "系统公告");

    /**
     * 名称
     */
    private final String name;

    /**
     * 描述
     */
    private final String description;

    MessageType(String name, String description) {
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
     * @param code 消息类型代码
     * @return 对应的枚举，如果未找到返回 null
     */
    public static MessageType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (MessageType type : values()) {
            if (type.name().equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
