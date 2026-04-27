package com.example.alertservice.feign;

import com.example.alertservice.dto.PredictRequest;
import com.example.alertservice.dto.PredictResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "ml-service", url = "${ml.service.url:http://localhost:5000}", fallback = MlServiceFallback.class)
public interface MlServiceClient {

    @PostMapping("/ml/predict")
    PredictResponse predict(@RequestBody PredictRequest request);

    @GetMapping("/ml/chart/trend")
    Map<String, Object> getTrendChart(@RequestParam("deviceId") String deviceId,
                         @RequestParam("points") int dataPoints);
}
