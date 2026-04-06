package com.example.alertservice.dto;

import java.math.BigDecimal;

public class FailureRankDTO {
    private Long deviceId;
    private String deviceName;
    private Long alertCount;
    private BigDecimal faultRate;

    public FailureRankDTO() {
    }

    public FailureRankDTO(Long deviceId, String deviceName, Long alertCount, BigDecimal faultRate) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.alertCount = alertCount;
        this.faultRate = faultRate;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public Long getAlertCount() {
        return alertCount;
    }

    public void setAlertCount(Long alertCount) {
        this.alertCount = alertCount;
    }

    public BigDecimal getFaultRate() {
        return faultRate;
    }

    public void setFaultRate(BigDecimal faultRate) {
        this.faultRate = faultRate;
    }
}
