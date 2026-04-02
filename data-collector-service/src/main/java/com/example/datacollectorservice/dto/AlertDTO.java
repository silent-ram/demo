package com.example.datacollectorservice.dto;

import java.util.Map;

public class AlertDTO {
    private String deviceId;
    private String deviceName;
    private Double faultProbability;
    private String alertLevel;
    private String type;
    private String message;
    private Map<String, Object> data;

    public AlertDTO() {
    }

    public AlertDTO(String deviceId, String deviceName, Double faultProbability,
                    String alertLevel, String type, String message) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.faultProbability = faultProbability;
        this.alertLevel = alertLevel;
        this.type = type;
        this.message = message;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public Double getFaultProbability() {
        return faultProbability;
    }

    public void setFaultProbability(Double faultProbability) {
        this.faultProbability = faultProbability;
    }

    public String getAlertLevel() {
        return alertLevel;
    }

    public void setAlertLevel(String alertLevel) {
        this.alertLevel = alertLevel;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}
