package com.example.alertservice.feign;

import com.example.alertservice.dto.PredictRequest;
import com.example.alertservice.dto.PredictResponse;
import org.springframework.stereotype.Component;

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
}
