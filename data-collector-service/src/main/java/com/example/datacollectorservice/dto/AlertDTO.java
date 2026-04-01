package com.example.datacollectorservice.dto;

import java.util.Map;

public class AlertDTO {
    private String deviceId;
    private String metricName;
    private Double value;
    private String threshold;
    private String level;
    private Map<String, Object> data;

    public AlertDTO() {
    }

    public AlertDTO(String deviceId, String metricName, Double value, String threshold, String level, Map<String, Object> data) {
        this.deviceId = deviceId;
        this.metricName = metricName;
        this.value = value;
        this.threshold = threshold;
        this.level = level;
        this.data = data;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public String getThreshold() {
        return threshold;
    }

    public void setThreshold(String threshold) {
        this.threshold = threshold;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}