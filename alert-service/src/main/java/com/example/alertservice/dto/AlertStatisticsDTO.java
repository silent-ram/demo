package com.example.alertservice.dto;

import java.math.BigDecimal;
import java.util.Map;

public class AlertStatisticsDTO {
    private Long total;
    private Map<String, Long> byLevel;
    private Map<String, Long> byDevice;

    public AlertStatisticsDTO() {
    }

    public AlertStatisticsDTO(Long total, Map<String, Long> byLevel, Map<String, Long> byDevice) {
        this.total = total;
        this.byLevel = byLevel;
        this.byDevice = byDevice;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Map<String, Long> getByLevel() {
        return byLevel;
    }

    public void setByLevel(Map<String, Long> byLevel) {
        this.byLevel = byLevel;
    }

    public Map<String, Long> getByDevice() {
        return byDevice;
    }

    public void setByDevice(Map<String, Long> byDevice) {
        this.byDevice = byDevice;
    }
}
