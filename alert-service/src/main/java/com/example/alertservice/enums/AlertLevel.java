package com.example.alertservice.enums;

public enum AlertLevel {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH");

    private final String value;

    AlertLevel(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AlertLevel fromValue(String value) {
        for (AlertLevel level : values()) {
            if (level.value.equalsIgnoreCase(value)) {
                return level;
            }
        }
        return LOW;
    }

    /**
     * 获取下一级别
     */
    public AlertLevel nextLevel() {
        switch (this) {
            case LOW:
                return MEDIUM;
            case MEDIUM:
                return HIGH;
            case HIGH:
                return HIGH;
            default:
                return MEDIUM;
        }
    }
}
