package com.example.datacollectorservice.dto;

import java.util.List;
import java.util.Map;

public class PredictRequest {
    private String deviceId;
    private String metricName;
    private Double value;
    private Double temperature;
    private Double vibration;
    private Double pressure;
    private List<MetricDTO> sensorData;
    private Map<String, Double> thresholds;

    public PredictRequest() {
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

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getVibration() {
        return vibration;
    }

    public void setVibration(Double vibration) {
        this.vibration = vibration;
    }

    public Double getPressure() {
        return pressure;
    }

    public void setPressure(Double pressure) {
        this.pressure = pressure;
    }

    public List<MetricDTO> getSensorData() {
        return sensorData;
    }

    public void setSensorData(List<MetricDTO> sensorData) {
        this.sensorData = sensorData;
    }

    public Map<String, Double> getThresholds() {
        return thresholds;
    }

    public void setThresholds(Map<String, Double> thresholds) {
        this.thresholds = thresholds;
    }
}
