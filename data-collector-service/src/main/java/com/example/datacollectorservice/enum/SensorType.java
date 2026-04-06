package com.example.datacollectorservice.enum;

/**
 * 传感器类型枚举
 */
public enum SensorType {
    /**
     * 温度传感器
     */
    TEMPERATURE("temperature", "温度", "℃", 0, 100, 80),

    /**
     * 振动传感器
     */
    VIBRATION("vibration", "振动", "mm/s", 0, 2, 0.6),

    /**
     * 压力传感器
     */
    PRESSURE("pressure", "压力", "bar", 0, 200, 130),

    /**
     * 电流传感器
     */
    CURRENT("current", "电流", "A", 0, 10, 7);

    /**
     * 传感器代码
     */
    private final String code;

    /**
     * 传感器名称
     */
    private final String name;

    /**
     * 单位
     */
    private final String unit;

    /**
     * 最小值
     */
    private final double minValue;

    /**
     * 最大值
     */
    private final double maxValue;

    /**
     * 告警阈值
     */
    private final double alertThreshold;

    SensorType(String code, String name, String unit, double minValue, double maxValue, double alertThreshold) {
        this.code = code;
        this.name = name;
        this.unit = unit;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.alertThreshold = alertThreshold;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getUnit() {
        return unit;
    }

    public double getMinValue() {
        return minValue;
    }

    public double getMaxValue() {
        return maxValue;
    }

    public double getAlertThreshold() {
        return alertThreshold;
    }

    /**
     * 根据代码查找枚举
     * @param code 传感器代码
     * @return 对应的枚举，如果未找到返回 null
     */
    public static SensorType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (SensorType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
