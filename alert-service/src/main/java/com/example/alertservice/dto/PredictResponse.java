package com.example.alertservice.dto;

public class PredictResponse {
    private Boolean isFault;
    private Double probability;
    private String message;
    private String chartData;

    public PredictResponse() {
    }

    public Boolean getIsFault() {
        return isFault;
    }

    public void setIsFault(Boolean isFault) {
        this.isFault = isFault;
    }

    public Double getProbability() {
        return probability;
    }

    public void setProbability(Double probability) {
        this.probability = probability;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getChartData() {
        return chartData;
    }

    public void setChartData(String chartData) {
        this.chartData = chartData;
    }
}