package com.example.alertservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.alertservice.dto.FailureRankDTO;
import com.example.alertservice.entity.Alert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AlertMapper extends BaseMapper<Alert> {

    @Select("SELECT * FROM t_alert WHERE resolved = false ORDER BY created_at DESC")
    List<Alert> findUnresolved();

    @Select("SELECT * FROM t_alert WHERE resolved = false AND created_at < #{timeLimit} ORDER BY created_at DESC")
    List<Alert> findUnresolvedBefore(LocalDateTime timeLimit);

    @Select("SELECT COUNT(*) FROM t_alert WHERE created_at BETWEEN #{startDate} AND #{endDate}")
    Long countByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Select("SELECT alert_level, COUNT(*) as count FROM t_alert WHERE created_at BETWEEN #{startDate} AND #{endDate} GROUP BY alert_level")
    List<AlertLevelCount> countByLevelAndDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Select("SELECT device_id, device_name, COUNT(*) as count FROM t_alert WHERE created_at BETWEEN #{startDate} AND #{endDate} GROUP BY device_id, device_name")
    List<AlertDeviceCount> countByDeviceAndDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Select("SELECT device_id as deviceId, device_name as deviceName, COUNT(*) as alertCount, " +
            "ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM t_alert WHERE created_at BETWEEN #{startDate} AND #{endDate}), 2) as faultRate " +
            "FROM t_alert WHERE created_at BETWEEN #{startDate} AND #{endDate} " +
            "GROUP BY device_id, device_name ORDER BY alertCount DESC LIMIT #{limit}")
    List<FailureRankDTO> findFailureRank(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate, @Param("limit") Integer limit);

    interface AlertLevelCount {
        String getAlertLevel();
        Long getCount();
    }

    interface AlertDeviceCount {
        Long getDeviceId();
        String getDeviceName();
        Long getCount();
    }
}
