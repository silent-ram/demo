package com.example.alertservice.enums;

public enum AlertLevel {
    INFO("INFO"),
    WARNING("WARNING"),
    CRITICAL("CRITICAL");

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
        return INFO;
    }

    /**
     * 获取下一级别
     */
    public AlertLevel nextLevel() {
        switch (this) {
            case INFO:
                return WARNING;
            case WARNING:
                return CRITICAL;
            case CRITICAL:
                return CRITICAL;
            default:
                return WARNING;
        }
    }
}
