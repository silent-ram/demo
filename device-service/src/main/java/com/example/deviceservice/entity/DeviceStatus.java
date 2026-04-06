package com.example.deviceservice.entity;

/**
 * 设备状态枚举
 */
public enum DeviceStatus {
    /**
     * 正常运行 - 设备完好，可随时启动
     */
    NORMAL("正常运行", "success"),

    /**
     * 运行中 - 正在执行生产任务
     */
    RUNNING("运行中", "primary"),

    /**
     * 待机 - 设备通电但未生产
     */
    STANDBY("待机", "info"),

    /**
     * 维护中 - 正在进行保养或维修
     */
    MAINTENANCE("维护中", "warning"),

    /**
     * 故障 - 发生故障，需处理
     */
    FAULT("故障", "danger"),

    /**
     * 离线 - 设备断电/通讯中断
     */
    OFFLINE("离线", "info");

    /**
     * 状态描述
     */
    private final String description;

    /**
     * 前端显示颜色
     */
    private final String color;

    DeviceStatus(String description, String color) {
        this.description = description;
        this.color = color;
    }

    public String getDescription() {
        return description;
    }

    public String getColor() {
        return color;
    }

    /**
     * 根据代码查找枚举
     * @param code 状态代码
     * @return 对应的枚举，如果未找到返回 null
     */
    public static DeviceStatus fromCode(String code) {
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