package com.example.alertservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.alertservice.entity.Alert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AlertMapper extends BaseMapper<Alert> {

    @Select("SELECT * FROM t_alert WHERE resolved = false ORDER BY created_at DESC")
    List<Alert> findUnresolved();

    @Select("SELECT * FROM t_alert WHERE resolved = false AND created_at < #{timeLimit} ORDER BY created_at DESC")
    List<Alert> findUnresolvedBefore(LocalDateTime timeLimit);
}
