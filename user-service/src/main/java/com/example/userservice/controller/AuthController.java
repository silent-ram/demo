package com.example.userservice.controller;

import com.example.userservice.dto.LoginRequest;
import com.example.userservice.dto.LoginResponse;
import com.example.userservice.dto.UserDTO;
import com.example.userservice.exception.Result;
import com.example.userservice.service.UserService;
import com.example.userservice.config.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        log.info("AuthController 收到登录请求: username={}", request.getUsername());
        try {
            LoginResponse response = userService.login(request);
            log.info("AuthController 登录成功: username={}", request.getUsername());
            return Result.success(response);
        } catch (Exception e) {
            log.error("AuthController 登录失败: {}", e.getMessage(), e);
            throw e;
        }
    }
}