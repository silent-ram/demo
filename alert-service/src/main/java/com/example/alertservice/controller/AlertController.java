package com.example.alertservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.alertservice.dto.AlertDTO;
import com.example.alertservice.entity.Alert;
import com.example.alertservice.service.AlertService;
import com.example.alertservice.exception.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/alert")
@Tag(name = "告警管理", description = "告警CRUD操作接口")
public class AlertController {

    @Autowired
    private AlertService alertService;

    @GetMapping
    @Operation(summary = "分页查询告警", description = "支持分页查询所有告警")
    public Result<Page<Alert>> listAlerts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Alert> result = alertService.page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size));
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取告警详情", description = "根据ID获取单个告警的详细信息")
    public Result<Alert> getAlert(@PathVariable Long id) {
        Alert alert = alertService.getAlertById(id);
        return Result.success(alert);
    }

    @PostMapping
    @Operation(summary = "创建告警", description = "创建新的告警记录")
    public Result<String> createAlert(@RequestBody AlertDTO dto) {
        alertService.receiveAlert(dto);
        return Result.success("告警创建成功", null);
    }

    @PutMapping("/{id}/resolve")
    @Operation(summary = "解决告警", description = "标记告警为已解决")
    public Result<String> resolveAlert(@PathVariable Long id) {
        alertService.resolveAlert(id);
        return Result.success("告警已解决", null);
    }

    @GetMapping("/active")
    @Operation(summary = "获取活跃告警", description = "查询所有未解决的告警")
    public Result<List<Alert>> getActiveAlerts() {
        List<Alert> alerts = alertService.getActiveAlerts();
        return Result.success(alerts);
    }

    @GetMapping("/resolved")
    @Operation(summary = "获取已解决告警", description = "查询所有已解决的告警")
    public Result<List<Alert>> getResolvedAlerts() {
        List<Alert> alerts = alertService.getResolvedAlerts();
        return Result.success(alerts);
    }

    @GetMapping("/config")
    @Operation(summary = "获取配置", description = "获取系统配置")
    public Result<Map<String, Object>> getConfig() {
        Map<String, Object> config = alertService.getConfig();
        return Result.success(config);
    }

    @PutMapping("/config")
    @Operation(summary = "更新配置", description = "更新系统配置")
    public Result<String> updateConfig(@RequestBody Map<String, String> config) {
        for (Map.Entry<String, String> entry : config.entrySet()) {
            alertService.putConfig(entry.getKey(), entry.getValue());
        }
        return Result.success("配置更新成功", null);
    }

    @PutMapping("/config/{key}")
    @Operation(summary = "更新单个配置", description = "更新单个配置项")
    public Result<String> putConfig(@PathVariable String key, @RequestBody String value) {
        alertService.putConfig(key, value);
        return Result.success("配置更新成功", null);
    }

    @GetMapping("/stats")
    @Operation(summary = "告警统计", description = "获取告警统计信息")
    public Result<Map<String, Object>> getAlertStats() {
        Map<String, Object> stats = alertService.getAlertStats();
        return Result.success(stats);
    }

    @GetMapping("/threshold")
    @Operation(summary = "获取当前阈值", description = "获取当前故障概率阈值")
    public Result<String> getThreshold() {
        String threshold = alertService.getThreshold();
        return Result.success(threshold);
    }

    @PutMapping("/threshold")
    @Operation(summary = "更新阈值", description = "更新故障概率阈值")
    public Result<String> updateThreshold(@RequestBody String value) {
        alertService.updateThreshold(value);
        return Result.success("阈值更新成功", null);
    }

    @GetMapping("/chart/{deviceId}")
    @Operation(summary = "获取趋势图", description = "获取指定设备的趋势图")
    public Result<String> getChart(@PathVariable Long deviceId) {
        String chartData = alertService.getChartBase64(deviceId);
        return Result.success(chartData);
    }
}