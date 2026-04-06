package com.example.userservice.entity;

/**
 * 操作类型枚举
 */
public enum OperationType {
    /**
     * 登录 - 用户登录
     */
    LOGIN("登录", "用户登录"),

    /**
     * 登出 - 用户登出
     */
    LOGOUT("登出", "用户登出"),

    /**
     * 设备操作 - 设备启停/参数修改
     */
    DEVICE("设备操作", "设备启停/参数修改"),

    /**
     * 告警处理 - 告警处理/确认
     */
    ALERT("告警处理", "告警处理/确认"),

    /**
     * 维修记录 - 创建/修改维修记录
     */
    MAINTENANCE("维修记录", "创建/修改维修记录"),

    /**
     * 系统配置 - 修改阈值/参数配置
     */
    CONFIG("系统配置", "修改阈值/参数配置");

    /**
     * 操作名称
     */
    private final String name;

    /**
     * 操作描述
     */
    private final String description;

    OperationType(String name, String description) {
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
     * @param code 操作类型代码
     * @return 对应的枚举，如果未找到返回 null
     */
    public static OperationType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (OperationType type : values()) {
            if (type.name().equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}