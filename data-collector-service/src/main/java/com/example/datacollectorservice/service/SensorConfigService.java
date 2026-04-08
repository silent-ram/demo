package com.example.datacollectorservice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.datacollectorservice.entity.SensorConfig;

public interface SensorConfigService extends IService<SensorConfig> {

    Page<SensorConfig> page(int page, int size, String deviceType);

    void create(SensorConfig config);

    void update(Long id, SensorConfig config);

    void delete(Long id);
}
