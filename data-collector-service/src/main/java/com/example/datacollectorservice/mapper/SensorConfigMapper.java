package com.example.datacollectorservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.datacollectorservice.entity.SensorConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 传感器配置 Mapper
 */
@Mapper
public interface SensorConfigMapper extends BaseMapper<SensorConfig> {

    /**
     * 根据设备类型查询启用的传感器配置
     * @param deviceType 设备类型
     * @return 启用的传感器配置列表
     */
    @Select("SELECT * FROM t_sensor_config WHERE device_type = #{deviceType} AND enabled = true")
    List<SensorConfig> findEnabledByDeviceType(@Param("deviceType") String deviceType);
}
