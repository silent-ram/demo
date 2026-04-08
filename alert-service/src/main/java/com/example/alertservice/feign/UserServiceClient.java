package com.example.alertservice.feign;

import com.example.alertservice.exception.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@FeignClient(name = "user-service", fallback = UserServiceFallback.class)
public interface UserServiceClient {

    @GetMapping("/user/list")
    Result<List<Map<String, Object>>> getUserList();
}
