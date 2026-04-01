package com.example.datacollectorservice.feign;

import com.example.datacollectorservice.dto.AlertDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "alert-service", url = "http://localhost:8084")
public interface AlertServiceClient {

    @PostMapping("/alert/push")
    void pushAlert(@RequestBody AlertDTO alert);
}