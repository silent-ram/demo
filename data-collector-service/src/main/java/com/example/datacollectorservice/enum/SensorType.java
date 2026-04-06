package com.example.datacollectorservice.enum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 传感器类型枚举
 */
@Getter
@AllArgsConstructor
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

    private final String code;
    private final String name;
    private final String unit;
    private final double minValue;
    private final double maxValue;
    private final double alertThreshold;

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
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }
}
