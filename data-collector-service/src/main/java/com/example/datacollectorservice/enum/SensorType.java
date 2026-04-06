package com.example.datacollectorservice.enum;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SensorType {
    TEMPERATURE("temperature", "温度", "℃", 0, 100, 80),
    VIBRATION("vibration", "振动", "mm/s", 0, 2, 0.6),
    PRESSURE("pressure", "压力", "bar", 0, 200, 130),
    CURRENT("current", "电流", "A", 0, 10, 7);

    private final String code;
    private final String name;
    private final String unit;
    private final double minValue;
    private final double maxValue;
    private final double alertThreshold;

    public static SensorType fromCode(String code) {
        for (SensorType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }
}
