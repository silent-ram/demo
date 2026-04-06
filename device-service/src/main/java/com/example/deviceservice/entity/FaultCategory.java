package com.example.deviceservice.entity;

/**
 * 故障分类枚举
 */
public enum FaultCategory {
    /**
     * 设备故障 - 设备本身故障
     */
    EQUIPMENT("设备故障", "设备本身故障"),

    /**
     * 电气故障 - 电气系统故障
     */
    ELECTRICAL("电气故障", "电气系统故障"),

    /**
     * 机械故障 - 机械部件故障
     */
    MECHANICAL("机械故障", "机械部件故障"),

    /**
     * 传感器故障 - 传感器异常
     */
    SENSOR("传感器故障", "传感器异常"),

    /**
     * 软件故障 - 控制系统故障
     */
    SOFTWARE("软件故障", "控制系统故障"),

    /**
     * 其他 - 其他原因
     */
    OTHER("其他", "其他原因");

    /**
     * 名称
     */
    private final String name;

    /**
     * 描述
     */
    private final String description;

    FaultCategory(String name, String description) {
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
     * @param code 分类代码
     * @return 对应的枚举，如果未找到返回 null
     */
    public static FaultCategory fromCode(String code) {
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
