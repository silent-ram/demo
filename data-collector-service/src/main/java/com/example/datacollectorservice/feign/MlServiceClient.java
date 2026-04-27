package com.example.datacollectorservice.feign;

import com.example.datacollectorservice.dto.PredictRequest;
import com.example.datacollectorservice.dto.PredictResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ml-service", url = "${ml.service.url:http://localhost:5000}")
public interface MlServiceClient {

    @PostMapping("/ml/predict")
    PredictResponse predict(@RequestBody PredictRequest request);
}
