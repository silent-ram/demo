package com.example.deviceservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.deviceservice.dto.DeviceDTO;
import com.example.deviceservice.entity.Device;
import com.example.deviceservice.exception.Result;
import com.example.deviceservice.feign.OperationLogClient;
import com.example.deviceservice.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/device")
@Tag(name = "设备管理", description = "设备CRUD操作接口")
public class DeviceController {

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private OperationLogClient operationLogClient;

    @GetMapping
    @Operation(summary = "分页查询设备列表", description = "支持分页查询所有设备")
    public Result<Page<DeviceDTO>> listDevices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<DeviceDTO> result = deviceService.listDevices(page, size);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取设备详情", description = "根据ID获取单个设备的详细信息")
    public Result<DeviceDTO> getDevice(@PathVariable Long id) {
        DeviceDTO device = deviceService.getDeviceById(id);
        return Result.success(device);
    }

    @PostMapping
    @Operation(summary = "新增设备", description = "创建新的设备记录")
    public Result<String> createDevice(@RequestBody DeviceDTO dto,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "0") Long userId,
            @RequestHeader(value = "X-User-Name", required = false, defaultValue = "unknown") String username) {
        deviceService.createDevice(dto);
        logOperation(userId, username, "DEVICE", "创建设备: " + dto.getName(), dto.getId());
        return Result.success("设备创建成功", null);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新设备", description = "更新设备的基本信息")
    public Result<String> updateDevice(@PathVariable Long id, @RequestBody DeviceDTO dto,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "0") Long userId,
            @RequestHeader(value = "X-User-Name", required = false, defaultValue = "unknown") String username) {
        deviceService.updateDevice(id, dto);
        logOperation(userId, username, "DEVICE", "更新设备: " + dto.getName(), id);
        return Result.success("设备更新成功", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除设备", description = "根据ID删除设备记录")
    public Result<String> deleteDevice(@PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "0") Long userId,
            @RequestHeader(value = "X-User-Name", required = false, defaultValue = "unknown") String username) {
        DeviceDTO device = deviceService.getDeviceById(id);
        deviceService.deleteDevice(id);
        logOperation(userId, username, "DEVICE", "删除设备: " + (device != null ? device.getName() : "ID:" + id), id);
        return Result.success("设备删除成功", null);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "更新设备状态", description = "更新设备的运行状态（NORMAL/FAULT）")
    public Result<String> updateDeviceStatus(@PathVariable Long id, @RequestParam String status,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "0") Long userId,
            @RequestHeader(value = "X-User-Name", required = false, defaultValue = "unknown") String username) {
        deviceService.updateDeviceStatus(id, status);
        DeviceDTO device = deviceService.getDeviceById(id);
        logOperation(userId, username, "DEVICE", "更新设备状态: " + (device != null ? device.getName() : "ID:" + id) + " -> " + status, id);
        return Result.success("设备状态更新成功", null);
    }

    @GetMapping("/search")
    @Operation(summary = "搜索设备", description = "根据关键词搜索设备")
    public Result<java.util.List<DeviceDTO>> searchDevices(@RequestParam String keyword) {
        java.util.List<DeviceDTO> devices = deviceService.searchDevices(keyword);
        return Result.success(devices);
    }

    @GetMapping("/running")
    @Operation(summary = "获取运行中设备", description = "获取所有状态为NORMAL的设备列表")
    public Result<java.util.List<DeviceDTO>> getRunningDevices() {
        java.util.List<DeviceDTO> devices = deviceService.getRunningDevices();
        return Result.success(devices);
    }

    @PutMapping("/{id}/simulation")
    @Operation(summary = "更新设备模拟开关", description = "更新设备的模拟开关状态")
    public Result<String> updateSimulationEnabled(@PathVariable Long id, @RequestParam Boolean enabled,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "0") Long userId,
            @RequestHeader(value = "X-User-Name", required = false, defaultValue = "unknown") String username) {
        deviceService.updateSimulationEnabled(id, enabled);
        DeviceDTO device = deviceService.getDeviceById(id);
        logOperation(userId, username, "DEVICE", "更新模拟开关: " + (device != null ? device.getName() : "ID:" + id) + " -> " + (enabled ? "开启" : "关闭"), id);
        return Result.success("模拟开关更新成功", null);
    }

    private void logOperation(Long userId, String username, String operationType, String content, Long deviceId) {
        try {
            operationLogClient.logOperation(userId, username, operationType, content, deviceId, "127.0.0.1");
        } catch (Exception e) {
            // 忽略日志记录失败，不影响主业务
        }
    }
}