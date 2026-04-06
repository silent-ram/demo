package com.example.datacollectorservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.datacollectorservice.entity.SensorConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SensorConfigMapper extends BaseMapper<SensorConfig> {

    @Select("SELECT * FROM t_sensor_config WHERE device_type = #{deviceType} AND enabled = true")
    List<SensorConfig> findEnabledByDeviceType(String deviceType);
}
