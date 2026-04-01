package com.example.deviceservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.deviceservice.entity.Device;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceMapper extends BaseMapper<Device> {
}