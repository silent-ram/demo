package com.example.deviceservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.deviceservice.dto.MaintenanceDTO;
import com.example.deviceservice.exception.Result;
import com.example.deviceservice.service.MaintenanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/maintenance")
@Tag(name = "维修管理", description = "设备维修记录管理接口")
public class MaintenanceController {
    
    @Autowired
    private MaintenanceService maintenanceService;

    @GetMapping
    @Operation(summary = "分页查询维修记录", description = "支持分页查询所有维修记录")
    public Result<Page<MaintenanceDTO>> listMaintenances(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<MaintenanceDTO> result = maintenanceService.listMaintenances(page, size);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取维修记录详情", description = "根据ID获取单个维修记录的详细信息")
    public Result<MaintenanceDTO> getMaintenance(@PathVariable Long id) {
        MaintenanceDTO maintenance = maintenanceService.getMaintenanceById(id);
        return Result.success(maintenance);
    }

    @PostMapping
    @Operation(summary = "新增维修记录", description = "创建新的维修记录")
    public Result<String> createMaintenance(@RequestBody MaintenanceDTO dto) {
        maintenanceService.createMaintenance(dto);
        return Result.success("维修记录创建成功", null);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新维修记录", description = "更新维修记录的信息")
    public Result<String> updateMaintenance(@PathVariable Long id, @RequestBody MaintenanceDTO dto) {
        maintenanceService.updateMaintenance(id, dto);
        return Result.success("维修记录更新成功", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除维修记录", description = "根据ID删除维修记录")
    public Result<String> deleteMaintenance(@PathVariable Long id) {
        maintenanceService.deleteMaintenance(id);
        return Result.success("维修记录删除成功", null);
    }
}