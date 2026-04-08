package com.example.alertservice.feign;

import com.example.alertservice.exception.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user-service")
public interface OperationLogClient {

    @PostMapping("/operation-log/log")
    Result<Void> logOperation(
        @RequestParam Long userId,
        @RequestParam String username,
        @RequestParam String operationType,
        @RequestParam String operationContent,
        @RequestParam(required = false) Long deviceId,
        @RequestParam(required = false) String ipAddress
    );
}
