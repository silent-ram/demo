package com.example.alertservice.feign;

import com.example.alertservice.dto.PredictRequest;
import com.example.alertservice.dto.PredictResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MlServiceFallback implements MlServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MlServiceFallback.class);

    @Override
    public PredictResponse predict(PredictRequest request) {
        log.warn("ML Service unavailable, returning fallback response");
        PredictResponse response = new PredictResponse();
        response.setIsFault(false);
        response.setProbability(0.0);
        response.setMessage("ML Service unavailable - fallback response");
        return response;
    }

    @Override
    public Map<String, Object> getTrendChart(String deviceId, int dataPoints) {
        log.warn("ML Service unavailable, returning empty chart");
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "ML Service unavailable");
        return result;
    }
}
