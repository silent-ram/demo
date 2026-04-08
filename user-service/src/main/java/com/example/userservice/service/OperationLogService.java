package com.example.userservice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.userservice.entity.OperationLog;

public interface OperationLogService extends IService<OperationLog> {

    Page<OperationLog> page(int page, int size, String operationType, Long userId);

    void logOperation(Long userId, String username, String operationType, String operationContent, Long deviceId, String ipAddress);
}
