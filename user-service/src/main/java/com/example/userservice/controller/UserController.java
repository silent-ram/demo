package com.example.userservice.controller;

import com.example.userservice.dto.LoginRequest;
import com.example.userservice.dto.LoginResponse;
import com.example.userservice.dto.UserDTO;
import com.example.userservice.exception.Result;
import com.example.userservice.service.UserService;
import com.example.userservice.service.OperationLogService;
import com.example.userservice.config.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
@Tag(name = "用户管理", description = "用户登录、注册、查询等接口")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private OperationLogService operationLogService;

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户名密码登录，返回JWT token")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        log.info("收到登录请求: username={}", request.getUsername());
        LoginResponse response = userService.login(request);
        log.info("登录成功: username={}", request.getUsername());

        // 记录登录日志
        try {
            operationLogService.logOperation(
                response.getUserInfo().getId(),
                response.getUserInfo().getUsername(),
                "LOGIN",
                "用户登录",
                null,
                "127.0.0.1"
            );
        } catch (Exception e) {
            log.error("记录登录日志失败", e);
        }

        return Result.success(response);
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "注册新用户，默认角色为OPERATOR")
    public Result<String> register(@RequestBody LoginRequest request) {
        log.info("收到注册请求: username={}", request.getUsername());
        userService.register(request);
        log.info("注册成功: username={}", request.getUsername());
        return Result.success("注册成功", null);
    }

    @GetMapping("/info")
    @Operation(summary = "获取当前用户信息", description = "根据token获取当前登录用户信息")
    public Result<UserDTO> getUserInfo(
            @RequestHeader(value = "X-User-Name", required = false) String username,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        // 如果有 X-User-Name 头（来自网关），直接使用
        if (username != null && !username.isEmpty()) {
            UserDTO userDTO = userService.getUserInfo(username);
            return Result.success(userDTO);
        }

        // 否则从 Authorization header 解析
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.replace("Bearer ", "");
            String tokenUsername = jwtUtil.getUsernameFromToken(token);
            UserDTO userDTO = userService.getUserInfo(tokenUsername);
            return Result.success(userDTO);
        }

        return Result.error(401, "未授权");
    }

    @GetMapping("/list")
    @Operation(summary = "获取用户列表", description = "获取所有用户列表")
    public Result<List<UserDTO>> listUsers() {
        List<UserDTO> users = userService.listUsers();
        return Result.success(users);
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改用户信息", description = "修改用户信息，仅ADMIN可访问")
    public Result<String> updateUser(@PathVariable Long id, @RequestBody UserDTO userDTO) {
        userService.updateUser(id, userDTO);
        return Result.success("用户信息更新成功", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户", description = "删除用户，仅ADMIN可访问")
    public Result<String> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success("用户删除成功", null);
    }
}