package com.example.alertservice.feign;

import com.example.alertservice.dto.AlertDTO;
import com.example.alertservice.dto.MetricDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "data-collector-service")
public interface CollectorServiceClient {

    @PostMapping("/collector/alert")
    void receiveAlert(@RequestBody AlertDTO alert);

    @GetMapping("/collector/latest/{deviceId}")
    MetricDTO getLatestMetric(@PathVariable("deviceId") String deviceId);
}