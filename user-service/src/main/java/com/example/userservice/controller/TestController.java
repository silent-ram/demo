package com.example.userservice.controller;

import com.example.userservice.exception.Result;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("Hello from user-service!");
    }

    @PostMapping("/echo")
    public Result<String> echo(@RequestBody String body) {
        return Result.success("Received: " + body);
    }
}