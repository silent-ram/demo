package com.example.userservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.userservice.entity.OperationLog;
import com.example.userservice.mapper.OperationLogMapper;
import com.example.userservice.service.OperationLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {

    @Override
    public void logOperation(Long userId, String username, String operationType, String operationContent, Long deviceId, String ipAddress) {
        OperationLog operationLog = new OperationLog();
        operationLog.setUserId(userId);
        operationLog.setUsername(username);
        operationLog.setOperationType(operationType);
        operationLog.setOperationContent(operationContent);
        operationLog.setDeviceId(deviceId);
        operationLog.setIpAddress(ipAddress);
        operationLog.setCreatedAt(LocalDateTime.now());
        this.save(operationLog);
    }

    @Override
    public Page<OperationLog> page(int page, int size, String operationType, Long userId) {
        Page<OperationLog> pageParam = new Page<>(page, size);
        QueryWrapper<OperationLog> wrapper = new QueryWrapper<>();
        if (operationType != null && !operationType.isEmpty()) {
            wrapper.eq("operation_type", operationType);
        }
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        wrapper.orderByDesc("created_at");
        return this.page(pageParam, wrapper);
    }
}
