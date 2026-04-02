package com.example.datacollectorservice.feign;

import com.example.datacollectorservice.dto.DeviceDTO;
import com.example.datacollectorservice.exception.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "device-service", url = "http://localhost:8082")
public interface DeviceServiceClient {

    @GetMapping("/device/running")
    Result<List<DeviceDTO>> getRunningDevices();
}
