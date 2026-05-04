package com.example.alertservice.feign;

import com.example.alertservice.dto.MaintenanceDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "device-service")
public interface DeviceServiceClient {

    @GetMapping("/device/{id}")
    Map<String, Object> getDevice(@PathVariable Long id);

    @PutMapping("/device/{id}/status")
    void updateDeviceStatus(@PathVariable Long id, @RequestParam String status);

    @PostMapping("/maintenance")
    Map<String, Object> createMaintenance(@RequestBody MaintenanceDTO maintenanceDTO);

    @GetMapping("/maintenance/device/{deviceId}")
    Map<String, Object> getMaintenancesByDevice(@PathVariable Long deviceId);
}
