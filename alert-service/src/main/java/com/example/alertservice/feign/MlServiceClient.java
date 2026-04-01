package com.example.alertservice.feign;

import com.example.alertservice.dto.PredictRequest;
import com.example.alertservice.dto.PredictResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ml-service", url = "http://localhost:5000", fallback = MlServiceFallback.class)
public interface MlServiceClient {

    @PostMapping("/predict")
    PredictResponse predict(@RequestBody PredictRequest request);
}
