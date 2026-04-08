package com.example.userservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.userservice.entity.OperationLog;
import com.example.userservice.exception.Result;
import com.example.userservice.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/operation-log")
@Tag(name = "操作日志", description = "用户操作日志查询")
public class OperationLogController {

    @Autowired
    private OperationLogService operationLogService;

    @GetMapping
    @Operation(summary = "分页查询操作日志")
    public Result<Page<OperationLog>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) Long userId) {
        return Result.success(operationLogService.page(page, size, operationType, userId));
    }

    @PostMapping("/log")
    @Operation(summary = "记录操作日志", description = "供其他服务调用记录操作日志")
    public Result<Void> log(
            @RequestParam Long userId,
            @RequestParam String username,
            @RequestParam String operationType,
            @RequestParam String operationContent,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String ipAddress) {
        operationLogService.logOperation(userId, username, operationType, operationContent, deviceId, ipAddress);
        return Result.success(null);
    }
}
