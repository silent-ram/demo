package com.example.deviceservice.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.deviceservice.dto.MaintenanceDTO;
import com.example.deviceservice.entity.Maintenance;
import com.example.deviceservice.mapper.MaintenanceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MaintenanceService extends ServiceImpl<MaintenanceMapper, Maintenance> {
    
    @Autowired
    private MaintenanceMapper maintenanceMapper;

    public Page<MaintenanceDTO> listMaintenances(int page, int size) {
        Page<Maintenance> maintenancePage = new Page<>(page, size);
        Page<Maintenance> result = maintenanceMapper.selectPage(maintenancePage, null);
        
        List<MaintenanceDTO> dtoList = result.getRecords().stream()
                .map(maintenance -> new MaintenanceDTO(
                        maintenance.getId(),
                        maintenance.getDeviceId(),
                        maintenance.getType(),
                        maintenance.getDescription(),
                        maintenance.getStatus(),
                        maintenance.getCreatedAt(),
                        maintenance.getUpdatedAt()
                ))
                .collect(Collectors.toList());
        
        Page<MaintenanceDTO> dtoPage = new Page<>(page, size);
        dtoPage.setRecords(dtoList);
        dtoPage.setTotal(result.getTotal());
        
        return dtoPage;
    }

    public MaintenanceDTO getMaintenanceById(Long id) {
        Maintenance maintenance = maintenanceMapper.selectById(id);
        if (maintenance == null) {
            throw new RuntimeException("维修记录不存在");
        }
        return new MaintenanceDTO(
                maintenance.getId(),
                maintenance.getDeviceId(),
                maintenance.getType(),
                maintenance.getDescription(),
                maintenance.getStatus(),
                maintenance.getCreatedAt(),
                maintenance.getUpdatedAt()
        );
    }

    public void createMaintenance(MaintenanceDTO dto) {
        Maintenance maintenance = new Maintenance();
        maintenance.setDeviceId(dto.getDeviceId());
        maintenance.setAlertId(dto.getAlertId());
        maintenance.setType(dto.getType());
        maintenance.setDescription(dto.getDescription());
        maintenance.setActionTaken(dto.getActionTaken());
        maintenance.setOperatorId(dto.getOperatorId());
        maintenance.setRepairedAt(dto.getRepairedAt());
        maintenance.setStatus(dto.getStatus() != null ? dto.getStatus() : "PENDING");
        maintenance.setCreatedAt(LocalDateTime.now());
        maintenance.setUpdatedAt(LocalDateTime.now());
        maintenanceMapper.insert(maintenance);
    }

    public void updateMaintenance(Long id, MaintenanceDTO dto) {
        Maintenance maintenance = maintenanceMapper.selectById(id);
        if (maintenance == null) {
            throw new RuntimeException("维修记录不存在");
        }
        
        if (dto.getType() != null) {
            maintenance.setType(dto.getType());
        }
        if (dto.getDescription() != null) {
            maintenance.setDescription(dto.getDescription());
        }
        if (dto.getStatus() != null) {
            maintenance.setStatus(dto.getStatus());
        }
        maintenance.setUpdatedAt(LocalDateTime.now());
        
        maintenanceMapper.updateById(maintenance);
    }

    public void deleteMaintenance(Long id) {
        maintenanceMapper.deleteById(id);
    }

    public List<MaintenanceDTO> getMaintenancesByDeviceId(Long deviceId) {
        QueryWrapper<Maintenance> wrapper = new QueryWrapper<>();
        wrapper.eq("device_id", deviceId);
        wrapper.orderByDesc("created_at");
        
        return maintenanceMapper.selectList(wrapper).stream()
                .map(maintenance -> new MaintenanceDTO(
                        maintenance.getId(),
                        maintenance.getDeviceId(),
                        maintenance.getType(),
                        maintenance.getDescription(),
                        maintenance.getStatus(),
                        maintenance.getCreatedAt(),
                        maintenance.getUpdatedAt()
                ))
                .collect(Collectors.toList());
    }
}