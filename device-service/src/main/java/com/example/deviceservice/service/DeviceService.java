package com.example.deviceservice.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.deviceservice.dto.DeviceDTO;
import com.example.deviceservice.entity.Device;
import com.example.deviceservice.mapper.DeviceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DeviceService extends ServiceImpl<DeviceMapper, Device> {
    
    @Autowired
    private DeviceMapper deviceMapper;

    public Page<DeviceDTO> listDevices(int page, int size) {
        Page<Device> devicePage = new Page<>(page, size);
        Page<Device> result = deviceMapper.selectPage(devicePage, null);
        
        List<DeviceDTO> dtoList = result.getRecords().stream()
                .map(device -> new DeviceDTO(
                        device.getId(),
                        device.getDeviceNo(),
                        device.getName(),
                        device.getType(),
                        device.getStatus(),
                        device.getLocation(),
                        device.getCreatedAt(),
                        device.getUpdatedAt()
                ))
                .collect(Collectors.toList());
        
        Page<DeviceDTO> dtoPage = new Page<>(page, size);
        dtoPage.setRecords(dtoList);
        dtoPage.setTotal(result.getTotal());
        
        return dtoPage;
    }

    public DeviceDTO getDeviceById(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw new RuntimeException("设备不存在");
        }
        return new DeviceDTO(
                device.getId(),
                device.getDeviceNo(),
                device.getName(),
                device.getType(),
                device.getStatus(),
                device.getLocation(),
                device.getCreatedAt(),
                device.getUpdatedAt()
        );
    }

    public void createDevice(DeviceDTO dto) {
        Device device = new Device();
        device.setDeviceNo(dto.getDeviceNo());
        device.setName(dto.getName());
        device.setType(dto.getType());
        device.setStatus(dto.getStatus() != null ? dto.getStatus() : "NORMAL");
        device.setLocation(dto.getLocation());
        device.setCreatedAt(LocalDateTime.now());
        device.setUpdatedAt(LocalDateTime.now());
        deviceMapper.insert(device);
    }

    public void updateDevice(Long id, DeviceDTO dto) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw new RuntimeException("设备不存在");
        }
        
        if (dto.getName() != null) {
            device.setName(dto.getName());
        }
        if (dto.getType() != null) {
            device.setType(dto.getType());
        }
        if (dto.getStatus() != null) {
            device.setStatus(dto.getStatus());
        }
        if (dto.getLocation() != null) {
            device.setLocation(dto.getLocation());
        }
        device.setUpdatedAt(LocalDateTime.now());
        
        deviceMapper.updateById(device);
    }

    public void updateDeviceStatus(Long id, String status) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw new RuntimeException("设备不存在");
        }
        device.setStatus(status);
        device.setUpdatedAt(LocalDateTime.now());
        deviceMapper.updateById(device);
    }

    public void deleteDevice(Long id) {
        deviceMapper.deleteById(id);
    }

    public List<DeviceDTO> searchDevices(String keyword) {
        QueryWrapper<Device> wrapper = new QueryWrapper<>();
        wrapper.like("name", keyword)
                .or()
                .like("type", keyword)
                .or()
                .like("location", keyword);
        
        return deviceMapper.selectList(wrapper).stream()
                .map(device -> new DeviceDTO(
                        device.getId(),
                        device.getDeviceNo(),
                        device.getName(),
                        device.getType(),
                        device.getStatus(),
                        device.getLocation(),
                        device.getCreatedAt(),
                        device.getUpdatedAt()
                ))
                .collect(Collectors.toList());
    }
}