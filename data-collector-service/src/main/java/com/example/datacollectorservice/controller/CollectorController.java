package com.example.datacollectorservice.controller;

import com.example.datacollectorservice.dto.MetricDTO;
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
    public List<MetricDTO> getMetrics(
            @PathVariable String deviceId,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime) {
        
        Instant start = startTime != null ? Instant.ofEpochSecond(startTime) : Instant.now().minusSeconds(3600);
        Instant end = endTime != null ? Instant.ofEpochSecond(endTime) : Instant.now();
        
        return influxDBService.queryMetrics(deviceId, start, end);
    }

    @GetMapping("/latest/{deviceId}")
    @Operation(summary = "获取最新指标", description = "获取指定设备的最新传感器指标")
    public MetricDTO getLatestMetric(@PathVariable String deviceId) {
        return influxDBService.getLatestMetric(deviceId);
    }

    @GetMapping("/metrics")
    @Operation(summary = "获取所有指标", description = "获取所有传感器的指标数据")
    public List<MetricDTO> getAllMetrics() {
        return influxDBService.getAllMetrics();
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
}
