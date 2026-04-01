package com.example.datacollectorservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PredictResponse {
    private Boolean success;
    private String message;
    private PredictionData data;

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public PredictionData getData() {
        return data;
    }

    public void setData(PredictionData data) {
        this.data = data;
    }

    public static class PredictionData {
        @JsonProperty("fault_probability")
        private Double faultProbability;

        @JsonProperty("is_fault")
        private Boolean isFault;

        public Double getFaultProbability() {
            return faultProbability;
        }

        public void setFaultProbability(Double faultProbability) {
            this.faultProbability = faultProbability;
        }

        public Boolean getIsFault() {
            return isFault;
        }

        public void setIsFault(Boolean isFault) {
            this.isFault = isFault;
        }
    }
}
