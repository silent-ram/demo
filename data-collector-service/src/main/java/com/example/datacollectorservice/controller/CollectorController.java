package com.example.datacollectorservice.controller;

import com.example.datacollectorservice.dto.MetricDTO;
import com.example.datacollectorservice.exception.Result;
import com.example.datacollectorservice.service.InfluxDBService;
import com.example.datacollectorservice.service.SensorSimulator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/collector")
@Tag(name = "数据采集", description = "传感器数据采集和查询接口")
public class CollectorController {

    @Autowired
    private InfluxDBService influxDBService;

    @Autowired
    private SensorSimulator sensorSimulator;

    @GetMapping("/metrics/{deviceId}")
    @Operation(summary = "查询历史指标", description = "查询指定时间范围内的传感器指标")
    public Result<List<MetricDTO>> getMetrics(
            @PathVariable String deviceId,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime) {

        Instant start = startTime != null ? Instant.ofEpochSecond(startTime) : Instant.now().minusSeconds(3600);
        Instant end = endTime != null ? Instant.ofEpochSecond(endTime) : Instant.now();

        List<MetricDTO> metrics = influxDBService.queryMetrics(deviceId, start, end);
        return Result.success(metrics);
    }

    @GetMapping("/latest/{deviceId}")
    @Operation(summary = "获取最新指标", description = "获取指定设备的最新传感器指标")
    public Result<MetricDTO> getLatestMetric(@PathVariable String deviceId) {
        return Result.success(influxDBService.getLatestMetric(deviceId));
    }

    @GetMapping("/metrics")
    @Operation(summary = "获取所有指标", description = "获取所有传感器的指标数据")
    public Result<List<MetricDTO>> getAllMetrics() {
        return Result.success(influxDBService.getAllMetrics());
    }

    @DeleteMapping("/metrics")
    @Operation(summary = "清空指标", description = "清空所有传感器指标数据")
    public String clearMetrics() {
        influxDBService.clearMetrics();
        return "Metrics cleared";
    }

    @PostMapping("/simulate/start")
    @Operation(summary = "启动模拟采集", description = "启动传感器数据模拟采集")
    public Map<String, Object> startSimulation() {
        Map<String, Object> result = new HashMap<>();
        sensorSimulator.start();
        result.put("success", true);
        result.put("message", "Sensor simulation started");
        result.put("running", sensorSimulator.isRunning());
        return result;
    }

    @PostMapping("/simulate/stop")
    @Operation(summary = "停止模拟采集", description = "停止传感器数据模拟采集")
    public Map<String, Object> stopSimulation() {
        Map<String, Object> result = new HashMap<>();
        sensorSimulator.stop();
        result.put("success", true);
        result.put("message", "Sensor simulation stopped");
        result.put("running", sensorSimulator.isRunning());
        return result;
    }

    @GetMapping("/simulate/status")
    @Operation(summary = "获取模拟状态", description = "获取传感器数据模拟采集的运行状态")
    public Map<String, Object> getSimulationStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("running", sensorSimulator.isRunning());
        result.put("message", sensorSimulator.isRunning() ? "Simulation is running" : "Simulation is stopped");
        return result;
    }

    @PostMapping("/simulate/device/{deviceId}/start")
    @Operation(summary = "启动设备模拟", description = "启动指定设备的传感器数据模拟")
    public Map<String, Object> startDeviceSimulation(@PathVariable String deviceId) {
        Map<String, Object> result = new HashMap<>();
        sensorSimulator.startDevice(deviceId);
        result.put("success", true);
        result.put("message", "Device simulation started: " + deviceId);
        result.put("deviceId", deviceId);
        result.put("running", sensorSimulator.isDeviceRunning(deviceId));
        return result;
    }

    @PostMapping("/simulate/device/{deviceId}/stop")
    @Operation(summary = "停止设备模拟", description = "停止指定设备的传感器数据模拟")
    public Map<String, Object> stopDeviceSimulation(@PathVariable String deviceId) {
        Map<String, Object> result = new HashMap<>();
        sensorSimulator.stopDevice(deviceId);
        result.put("success", true);
        result.put("message", "Device simulation stopped: " + deviceId);
        result.put("deviceId", deviceId);
        result.put("running", sensorSimulator.isDeviceRunning(deviceId));
        return result;
    }

    @GetMapping("/simulate/device/{deviceId}/status")
    @Operation(summary = "获取设备模拟状态", description = "获取指定设备的传感器模拟状态")
    public Map<String, Object> getDeviceSimulationStatus(@PathVariable String deviceId) {
        Map<String, Object> result = new HashMap<>();
        result.put("deviceId", deviceId);
        result.put("running", sensorSimulator.isDeviceRunning(deviceId));
        result.put("faultProbability", sensorSimulator.getFaultProbability(deviceId));
        result.put("manualValues", sensorSimulator.getManualValues(deviceId));
        return result;
    }

    @PostMapping("/simulate/device/{deviceId}/set")
    @Operation(summary = "手动设置传感器数据", description = "手动设置指定设备的传感器数值")
    public Map<String, Object> setDeviceManualData(
            @PathVariable String deviceId,
            @RequestBody Map<String, Double> values) {
        Map<String, Object> result = new HashMap<>();

        for (Map.Entry<String, Double> entry : values.entrySet()) {
            sensorSimulator.setManualValue(deviceId, entry.getKey(), entry.getValue());
        }

        result.put("success", true);
        result.put("message", "Manual values set for device: " + deviceId);
        result.put("deviceId", deviceId);
        result.put("values", values);
        return result;
    }

    @DeleteMapping("/simulate/device/{deviceId}/set")
    @Operation(summary = "清除手动设置", description = "清除指定设备的手动传感器设置")
    public Map<String, Object> clearDeviceManualData(@PathVariable String deviceId) {
        Map<String, Object> result = new HashMap<>();
        sensorSimulator.clearManualValues(deviceId);
        result.put("success", true);
        result.put("message", "Manual values cleared for device: " + deviceId);
        result.put("deviceId", deviceId);
        return result;
    }

    @PostMapping("/simulate/device/{deviceId}/mode")
    @Operation(summary = "设置模拟模式", description = "设置设备的模拟模式：NORMAL(正常), MANUAL_INSERT(插入数据), RANDOM_RANGE(随机范围)")
    public Map<String, Object> setDeviceMode(@PathVariable String deviceId, @RequestParam String mode) {
        Map<String, Object> result = new HashMap<>();
        sensorSimulator.setDeviceMode(deviceId, mode);
        result.put("success", true);
        result.put("message", "Device mode set to: " + mode);
        result.put("deviceId", deviceId);
        result.put("mode", mode);
        return result;
    }

    @PostMapping("/simulate/device/{deviceId}/insert")
    @Operation(summary = "插入指定数据", description = "插入指定传感器数据（只用一次）")
    public Map<String, Object> insertDeviceData(
            @PathVariable String deviceId,
            @RequestBody Map<String, Double> values) {
        Map<String, Object> result = new HashMap<>();

        // 先设置模式为插入
        sensorSimulator.setDeviceMode(deviceId, "MANUAL_INSERT");

        for (Map.Entry<String, Double> entry : values.entrySet()) {
            sensorSimulator.insertValue(deviceId, entry.getKey(), entry.getValue());
        }

        result.put("success", true);
        result.put("message", "Data inserted for next cycle: " + deviceId);
        result.put("deviceId", deviceId);
        result.put("values", values);
        return result;
    }

    @PostMapping("/simulate/device/{deviceId}/range")
    @Operation(summary = "设置随机范围", description = "设置设备传感器随机范围")
    public Map<String, Object> setDeviceRandomRange(
            @PathVariable String deviceId,
            @RequestBody Map<String, Map<String, Double>> ranges) {
        Map<String, Object> result = new HashMap<>();

        // 设置模式为随机范围
        sensorSimulator.setDeviceMode(deviceId, "RANDOM_RANGE");

        for (Map.Entry<String, Map<String, Double>> entry : ranges.entrySet()) {
            Map<String, Double> range = entry.getValue();
            if (range.containsKey("min") && range.containsKey("max")) {
                sensorSimulator.setRandomRange(deviceId, entry.getKey(), range.get("min"), range.get("max"));
            }
        }

        result.put("success", true);
        result.put("message", "Random range set for device: " + deviceId);
        result.put("deviceId", deviceId);
        result.put("ranges", ranges);
        return result;
    }

    @GetMapping("/fault-probability-history/{deviceId}")
    @Operation(summary = "查询故障概率历史", description = "查询指定设备最近 N 小时的故障概率历史数据（5分钟聚合）")
    public Result<List<Map<String, Object>>> getFaultProbabilityHistory(
            @PathVariable String deviceId,
            @RequestParam(required = false, defaultValue = "24") int hours) {
        List<Map<String, Object>> history = influxDBService.queryFaultProbabilityHistory(deviceId, hours);
        return Result.success(history);
    }

    @GetMapping("/simulate/device/{deviceId}/mode")
    @Operation(summary = "获取设备模拟模式", description = "获取指定设备的当前模拟模式")
    public Result<Map<String, Object>> getDeviceMode(@PathVariable String deviceId) {
        Map<String, Object> result = new HashMap<>();
        result.put("deviceId", deviceId);
        result.put("mode", sensorSimulator.getDeviceMode(deviceId));
        result.put("running", sensorSimulator.isDeviceRunning(deviceId));
        return Result.success(result);
    }

    // ========== 新增：传感模拟模式控制（STABLE/DEGRADING_SLOW/DEGRADING_FAST/SUDDEN_FAULT/SPIKE_RECOVER）==========

    @PostMapping("/simulate/device/{deviceId}/sim-mode")
    @Operation(summary = "设置传感模拟模式", description = "设置设备的传感模拟模式：STABLE(稳定运行), DEGRADING_SLOW(缓慢劣化), DEGRADING_FAST(快速劣化), SUDDEN_FAULT(突发故障), SPIKE_RECOVER(偶发异常)")
    public Result<Map<String, Object>> setSimMode(@PathVariable String deviceId, @RequestParam String mode) {
        Map<String, Object> result = new HashMap<>();
        try {
            sensorSimulator.setSimMode(deviceId, mode);
            SensorSimulator.SimMode simMode = sensorSimulator.getSimMode(deviceId);
            result.put("deviceId", deviceId);
            result.put("mode", simMode.name());
            result.put("desc", simMode.getDesc());
            result.put("canTriggerAlert", simMode.canTriggerAlert());
            result.put("success", true);
            return Result.success(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "设置失败: " + e.getMessage());
            return Result.success(result);
        }
    }

    @GetMapping("/simulate/device/{deviceId}/sim-mode")
    @Operation(summary = "获取传感模拟模式", description = "获取指定设备的当前传感模拟模式")
    public Result<Map<String, Object>> getSimMode(@PathVariable String deviceId) {
        Map<String, Object> result = new HashMap<>();
        SensorSimulator.SimMode simMode = sensorSimulator.getSimMode(deviceId);
        result.put("deviceId", deviceId);
        result.put("mode", simMode.name());
        result.put("desc", simMode.getDesc());
        result.put("canTriggerAlert", simMode.canTriggerAlert());
        result.put("faultProbability", sensorSimulator.getFaultProbability(deviceId));
        result.put("running", sensorSimulator.isDeviceRunning(deviceId));
        return Result.success(result);
    }

    @PostMapping("/simulate/device/{deviceId}/sim-mode/reset")
    @Operation(summary = "重置为稳定运行", description = "将设备的传感模拟模式重置为 STABLE，并将传感器值平滑恢复到基线")
    public Result<Map<String, Object>> resetSimMode(@PathVariable String deviceId) {
        Map<String, Object> result = new HashMap<>();
        sensorSimulator.resetToStable(deviceId);
        SensorSimulator.SimMode simMode = sensorSimulator.getSimMode(deviceId);
        result.put("deviceId", deviceId);
        result.put("mode", simMode.name());
        result.put("desc", simMode.getDesc());
        result.put("success", true);
        return Result.success(result);
    }
}
