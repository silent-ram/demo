package com.example.alertservice.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.alertservice.dto.AlertStatisticsDTO;
import com.example.alertservice.dto.FailureRankDTO;
import com.example.alertservice.dto.MaintenanceDTO;
import com.example.alertservice.dto.MetricDTO;
import com.example.alertservice.dto.PredictRequest;
import com.example.alertservice.dto.PredictResponse;
import com.example.alertservice.entity.Alert;
import com.example.alertservice.entity.Config;
import com.example.alertservice.mapper.AlertMapper;
import com.example.alertservice.mapper.ConfigMapper;
import com.example.alertservice.feign.CollectorServiceClient;
import com.example.alertservice.feign.DeviceServiceClient;
import com.example.alertservice.feign.MlServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AlertService extends ServiceImpl<AlertMapper, Alert> {

    @Autowired
    private AlertMapper alertMapper;

    @Autowired
    private ConfigMapper configMapper;

    @Autowired
    private MlServiceClient mlServiceClient;

    @Autowired
    private DeviceServiceClient deviceServiceClient;

    @Autowired
    private CollectorServiceClient collectorServiceClient;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public Page<Alert> page(com.baomidou.mybatisplus.extension.plugins.pagination.Page<Alert> page) {
        return alertMapper.selectPage(page, null);
    }

    @Scheduled(fixedRate = 10000)
    public void checkUnresolvedAlerts() {
        QueryWrapper<Alert> wrapper = new QueryWrapper<>();
        wrapper.eq("resolved", false);
        wrapper.lt("created_at", LocalDateTime.now().minusHours(1));
        
        List<Alert> unresolvedAlerts = alertMapper.selectList(wrapper);
        
        for (Alert alert : unresolvedAlerts) {
            PredictRequest request = new PredictRequest();
            request.setDeviceId(alert.getDeviceId().toString());
            request.setMetricName(alert.getType());
            request.setValue(Double.parseDouble(alert.getMessage()));
            
            PredictResponse response = mlServiceClient.predict(request);
            
            if (response.getIsFault()) {
                alert.setResolved(false);
                alert.setUpdatedAt(LocalDateTime.now());
                alertMapper.updateById(alert);
            }
        }
    }

    public void receiveAlert(AlertDTO alertDTO) {
        // 告警合并逻辑：检查是否有相同设备的未解决同类型告警
        Long deviceId = null;
        try {
            if (alertDTO.getDeviceId() != null) {
                deviceId = Long.parseLong(alertDTO.getDeviceId().toString());
            }
        } catch (NumberFormatException e) {
            // 忽略
        }

        String alertType = alertDTO.getType();

        if (deviceId != null && alertType != null) {
            // 查询30分钟内的同设备同类型未解决告警
            QueryWrapper<Alert> wrapper = new QueryWrapper<>();
            wrapper.eq("device_id", deviceId);
            wrapper.eq("type", alertType);
            wrapper.eq("resolved", false);
            wrapper.ge("created_at", LocalDateTime.now().minusMinutes(30));

            Alert existingAlert = alertMapper.selectOne(wrapper);
            if (existingAlert != null) {
                // 更新告警的故障概率和消息（合并）
                existingAlert.setFaultProbability(alertDTO.getFaultProbability());
                existingAlert.setMessage(alertDTO.getMessage());
                existingAlert.setUpdatedAt(LocalDateTime.now());
                alertMapper.updateById(existingAlert);
                // 不发送WebSocket，只更新现有告警
                System.out.println("Alert merged for device: " + deviceId + ", type: " + alertType);
                return;
            }
        }

        // 无合并告警，创建新告警
        Alert alert = new Alert();
        alert.setDeviceId(deviceId);
        alert.setDeviceName(alertDTO.getDeviceName());
        alert.setFaultProbability(alertDTO.getFaultProbability());
        alert.setAlertLevel(alertDTO.getAlertLevel() != null ? alertDTO.getAlertLevel() : "MEDIUM");
        alert.setType(alertType);
        alert.setMessage(alertDTO.getMessage());
        alert.setResolved(false);
        alert.setCreatedAt(LocalDateTime.now());
        alert.setUpdatedAt(LocalDateTime.now());

        alertMapper.insert(alert);

        messagingTemplate.convertAndSend("/topic/alerts", alert);
    }

    public void resolveAlert(Long id) {
        resolveAlert(id, null, null, "COMPLETED");
    }

    public void resolveAlert(Long id, String resolveNote, Long operatorId) {
        resolveAlert(id, resolveNote, operatorId, "COMPLETED");
    }

    /**
     * 处理告警
     * @param id 告警ID
     * @param resolveNote 处理备注
     * @param operatorId 操作人ID
     * @param resolveType 处理类型: COMPLETED(已维修), PENDING(待维修), STOPPED(停机)
     */
    public void resolveAlert(Long id, String resolveNote, Long operatorId, String resolveType) {
        Alert alert = alertMapper.selectById(id);
        if (alert == null) {
            throw new RuntimeException("Alert not found");
        }

        alert.setResolved(true);
        alert.setResolveNote(resolveNote);
        alert.setResolvedBy(operatorId);
        alert.setResolvedAt(LocalDateTime.now());
        alert.setUpdatedAt(LocalDateTime.now());
        alertMapper.updateById(alert);

        Long deviceId = alert.getDeviceId();

        if (deviceId != null) {
            if ("COMPLETED".equals(resolveType)) {
                // 已维修：更新设备状态为正常，并创建维修记录
                deviceServiceClient.updateDeviceStatus(deviceId, "NORMAL");

                MaintenanceDTO maintenance = new MaintenanceDTO();
                maintenance.setDeviceId(deviceId);
                maintenance.setType(alert.getType() != null ? alert.getType() : "FAULT");
                maintenance.setDescription("告警处理: " + (alert.getMessage() != null ? alert.getMessage() : "无描述"));
                maintenance.setAlertId(id);
                maintenance.setActionTaken(resolveNote != null ? resolveNote : "已维修");
                maintenance.setOperatorId(operatorId);
                maintenance.setStatus("COMPLETED");
                maintenance.setRepairedAt(LocalDateTime.now());

                try {
                    deviceServiceClient.createMaintenance(maintenance);
                    System.out.println("Maintenance record created for alert: " + id);
                } catch (Exception e) {
                    System.err.println("Failed to create maintenance record: " + e.getMessage());
                }
            } else if ("STOPPED".equals(resolveType)) {
                // 停机：更新设备状态为离线
                deviceServiceClient.updateDeviceStatus(deviceId, "OFFLINE");
            }
            // PENDING(待维修)：只标记告警为已解决，不改变设备状态
        }
    }

    public List<Alert> getActiveAlerts() {
        QueryWrapper<Alert> wrapper = new QueryWrapper<>();
        wrapper.eq("resolved", false);
        wrapper.orderByDesc("created_at");
        return alertMapper.selectList(wrapper);
    }

    public List<Alert> getResolvedAlerts() {
        QueryWrapper<Alert> wrapper = new QueryWrapper<>();
        wrapper.eq("resolved", true);
        wrapper.orderByDesc("created_at");
        return alertMapper.selectList(wrapper);
    }

    public Alert getAlertById(Long id) {
        return alertMapper.selectById(id);
    }

    public Map<String, Object> getConfig() {
        Map<String, Object> configMap = new java.util.HashMap<>();
        List<Config> configs = configMapper.selectList(null);
        for (Config config : configs) {
            configMap.put(config.getConfigKey(), config.getValue());
        }
        return configMap;
    }

    public void updateConfig(String key, String value) {
        QueryWrapper<Config> wrapper = new QueryWrapper<>();
        wrapper.eq("config_key", key);
        Config config = configMapper.selectOne(wrapper);
        if (config == null) {
            config = new Config();
            config.setConfigKey(key);
            config.setValue(value);
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            configMapper.insert(config);
        } else {
            config.setValue(value);
            config.setUpdatedAt(LocalDateTime.now());
            configMapper.updateById(config);
        }
    }

    public void putConfig(String key, String value) {
        QueryWrapper<Config> wrapper = new QueryWrapper<>();
        wrapper.eq("config_key", key);
        Config config = configMapper.selectOne(wrapper);
        if (config == null) {
            config = new Config();
            config.setConfigKey(key);
            config.setValue(value);
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            configMapper.insert(config);
        } else {
            config.setValue(value);
            config.setUpdatedAt(LocalDateTime.now());
            configMapper.updateById(config);
        }
    }

    public Map<String, Object> getAlertStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        
        QueryWrapper<Alert> activeWrapper = new QueryWrapper<>();
        activeWrapper.eq("resolved", false);
        Long activeCount = alertMapper.selectCount(activeWrapper);
        stats.put("activeCount", activeCount);
        
        QueryWrapper<Alert> resolvedWrapper = new QueryWrapper<>();
        resolvedWrapper.eq("resolved", true);
        Long resolvedCount = alertMapper.selectCount(resolvedWrapper);
        stats.put("resolvedCount", resolvedCount);
        
        QueryWrapper<Alert> highWrapper = new QueryWrapper<>();
        highWrapper.eq("alert_level", "HIGH");
        Long highCount = alertMapper.selectCount(highWrapper);
        stats.put("highCount", highCount);
        
        QueryWrapper<Alert> mediumWrapper = new QueryWrapper<>();
        mediumWrapper.eq("alert_level", "MEDIUM");
        Long mediumCount = alertMapper.selectCount(mediumWrapper);
        stats.put("mediumCount", mediumCount);
        
        QueryWrapper<Alert> lowWrapper = new QueryWrapper<>();
        lowWrapper.eq("alert_level", "LOW");
        Long lowCount = alertMapper.selectCount(lowWrapper);
        stats.put("lowCount", lowCount);
        
        return stats;
    }

    public String getThreshold() {
        QueryWrapper<Config> wrapper = new QueryWrapper<>();
        wrapper.eq("config_key", "fault_threshold");
        Config config = configMapper.selectOne(wrapper);
        return config != null ? config.getValue() : "0.7";
    }

    public void updateThreshold(String value) {
        updateConfig("fault_threshold", value);
    }

    public String getChart(Long deviceId) {
        // 先获取最新传感器数据
        MetricDTO metric = collectorServiceClient.getLatestMetric(deviceId.toString());
        if (metric == null) {
            return null;
        }

        PredictRequest request = new PredictRequest();
        request.setDeviceId(deviceId.toString());
        request.setTemperature(metric.getTemperature());
        request.setVibration(metric.getVibration());
        request.setPressure(metric.getPressure());

        PredictResponse response = mlServiceClient.predict(request);
        return response.getChartData();
    }

    public String getChartBase64(Long deviceId) {
        try {
            // 直接调用 ML 服务的趋势图接口
            Map<String, Object> chartResponse = mlServiceClient.getTrendChart(deviceId.toString(), 20);
            if (chartResponse != null && chartResponse.containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) chartResponse.get("data");
                if (data != null && data.containsKey("image")) {
                    return data.get("image").toString();
                }
            }
            return null;
        } catch (Exception e) {
            System.err.println("获取趋势图失败: " + e.getMessage());
            return null;
        }
    }

    public AlertStatisticsDTO getFrequencyStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        Long total = alertMapper.countByDateRange(startDate, endDate);

        Map<String, Long> byLevel = new LinkedHashMap<>();
        List<AlertMapper.AlertLevelCount> levelCounts = alertMapper.countByLevelAndDateRange(startDate, endDate);
        for (AlertMapper.AlertLevelCount item : levelCounts) {
            byLevel.put(item.getAlertLevel(), item.getCount());
        }

        Map<String, Long> byDevice = new LinkedHashMap<>();
        List<AlertMapper.AlertDeviceCount> deviceCounts = alertMapper.countByDeviceAndDateRange(startDate, endDate);
        for (AlertMapper.AlertDeviceCount item : deviceCounts) {
            String key = item.getDeviceName() != null ? item.getDeviceName() : "设备" + item.getDeviceId();
            byDevice.put(key, item.getCount());
        }

        return new AlertStatisticsDTO(total, byLevel, byDevice);
    }

    public List<FailureRankDTO> getFailureRank(LocalDateTime startDate, LocalDateTime endDate, Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 10;
        }
        return alertMapper.findFailureRank(startDate, endDate, limit);
    }
}