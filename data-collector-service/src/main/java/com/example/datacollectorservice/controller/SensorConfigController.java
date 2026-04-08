package com.example.datacollectorservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.datacollectorservice.entity.SensorConfig;
import com.example.datacollectorservice.exception.Result;
import com.example.datacollectorservice.service.SensorConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sensor-config")
@Tag(name = "传感器配置", description = "传感器类型配置CRUD")
public class SensorConfigController {

    @Autowired
    private SensorConfigService sensorConfigService;

    @GetMapping
    @Operation(summary = "分页查询传感器配置")
    public Result<Page<SensorConfig>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String deviceType) {
        return Result.success(sensorConfigService.page(page, size, deviceType));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取传感器配置")
    public Result<SensorConfig> get(@PathVariable Long id) {
        SensorConfig config = sensorConfigService.getById(id);
        if (config == null) {
            return Result.error("配置不存在");
        }
        return Result.success(config);
    }

    @PostMapping
    @Operation(summary = "创建传感器配置")
    public Result<String> create(@RequestBody SensorConfig config) {
        sensorConfigService.create(config);
        return Result.success("创建成功", null);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新传感器配置")
    public Result<String> update(@PathVariable Long id, @RequestBody SensorConfig config) {
        sensorConfigService.update(id, config);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除传感器配置")
    public Result<String> delete(@PathVariable Long id) {
        sensorConfigService.delete(id);
        return Result.success("删除成功", null);
    }
}
