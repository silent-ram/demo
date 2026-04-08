package com.example.datacollectorservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.datacollectorservice.entity.SensorConfig;
import com.example.datacollectorservice.mapper.SensorConfigMapper;
import com.example.datacollectorservice.service.SensorConfigService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class SensorConfigServiceImpl extends ServiceImpl<SensorConfigMapper, SensorConfig> implements SensorConfigService {

    @Override
    public Page<SensorConfig> page(int page, int size, String deviceType) {
        Page<SensorConfig> pageParam = new Page<>(page, size);
        QueryWrapper<SensorConfig> wrapper = new QueryWrapper<>();
        if (deviceType != null && !deviceType.isEmpty()) {
            wrapper.eq("device_type", deviceType);
        }
        wrapper.orderByDesc("created_at");
        return this.page(pageParam, wrapper);
    }

    @Override
    public void create(SensorConfig config) {
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        if (config.getEnabled() == null) {
            config.setEnabled(true);
        }
        this.save(config);
    }

    @Override
    public void update(Long id, SensorConfig config) {
        config.setId(id);
        config.setUpdatedAt(LocalDateTime.now());
        this.updateById(config);
    }

    @Override
    public void delete(Long id) {
        this.removeById(id);
    }
}
