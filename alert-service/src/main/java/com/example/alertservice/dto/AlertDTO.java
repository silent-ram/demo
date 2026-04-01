package com.example.alertservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AlertDTO {
    private Long id;
    private Long deviceId;
    private String deviceName;
    private BigDecimal faultProbability;
    private String alertLevel;
    private String type;
    private String message;
    private Boolean resolved;
    private LocalDateTime resolvedAt;
    private Long resolvedBy;
    private String resolveNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AlertDTO() {
    }

    public AlertDTO(Long deviceId, String type, String message, String alertLevel) {
        this.deviceId = deviceId;
        this.type = type;
        this.message = message;
        this.alertLevel = alertLevel;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public BigDecimal getFaultProbability() {
        return faultProbability;
    }

    public void setFaultProbability(BigDecimal faultProbability) {
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

    public Boolean getResolved() {
        return resolved;
    }

    public void setResolved(Boolean resolved) {
        this.resolved = resolved;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Long getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(Long resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public String getResolveNote() {
        return resolveNote;
    }

    public void setResolveNote(String resolveNote) {
        this.resolveNote = resolveNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
