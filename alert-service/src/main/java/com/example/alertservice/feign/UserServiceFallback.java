package com.example.alertservice.feign;

import com.example.alertservice.exception.Result;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class UserServiceFallback implements UserServiceClient {

    @Override
    public Result<List<Map<String, Object>>> getUserList() {
        return Result.success(Collections.emptyList());
    }
}
