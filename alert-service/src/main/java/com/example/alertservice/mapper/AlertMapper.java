package com.example.alertservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.alertservice.entity.Alert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AlertMapper extends BaseMapper<Alert> {
}