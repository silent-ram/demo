package com.example.alertservice.feign;

import com.example.alertservice.dto.PredictRequest;
import com.example.alertservice.dto.PredictResponse;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MlServiceFallback implements MlServiceClient {

    @Override
    public PredictResponse predict(PredictRequest request) {
        System.err.println("ML Service unavailable, returning fallback response");
        PredictResponse response = new PredictResponse();
        response.setIsFault(false);
        response.setProbability(0.0);
        response.setMessage("ML Service unavailable - fallback response");
        return response;
    }

    @Override
    public Map<String, Object> getTrendChart(String deviceId, int dataPoints) {
        System.err.println("ML Service unavailable, returning empty chart");
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "ML Service unavailable");
        return result;
    }
}
