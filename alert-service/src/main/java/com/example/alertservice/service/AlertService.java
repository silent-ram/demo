package com.example.alertservice.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.alertservice.dto.AlertDTO;
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
import com.example.alertservice.feign.UserServiceClient;
import com.example.alertservice.handler.AlertWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AlertService extends ServiceImpl<AlertMapper, Alert> {
    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

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
    private UserServiceClient userServiceClient;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MessageService messageService;

    @Autowired
    private AlertWebSocketHandler alertWebSocketHandler;

    // 补偿任务记录：{alertId: {type: "device_status" | "maintenance", ...}}
    // 用于远程调用失败后的异步补偿
    private final Map<Long, Map<String, Object>> compensationTasks = new ConcurrentHashMap<>();

    public Page<Alert> listAlerts(int page, int size, String level, Boolean resolved) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Alert> pageParam =
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        QueryWrapper<Alert> wrapper = new QueryWrapper<>();
        if (level != null && !level.isEmpty()) {
            wrapper.eq("alert_level", level);
        }
        if (resolved != null) {
            wrapper.eq("resolved", resolved);
        }
        wrapper.orderByDesc("created_at");
        return alertMapper.selectPage(pageParam, wrapper);
    }

    @Scheduled(fixedRate = 10000)
    public void checkUnresolvedAlerts() {
        QueryWrapper<Alert> wrapper = new QueryWrapper<>();
        wrapper.eq("resolved", false);
        wrapper.lt("created_at", LocalDateTime.now().minusHours(1));

        List<Alert> unresolvedAlerts = alertMapper.selectList(wrapper);

        for (Alert alert : unresolvedAlerts) {
            try {
                // 直接从字段读取故障概率，避免解析消息文本出错
                Double probability = null;
                if (alert.getFaultProbability() != null) {
                    probability = alert.getFaultProbability().doubleValue();
                }

                if (probability != null && probability >= 0.7) {
                    // 故障概率超过阈值，触发升级
                    upgradeAlertLevel(alert);
                }
            } catch (Exception e) {
                // 忽略解析错误
            }
        }
    }

    /**
     * 升级告警级别
     */
    private void upgradeAlertLevel(Alert alert) {
        String currentLevel = alert.getAlertLevel();
        if (currentLevel == null) {
            return;
        }
        String nextLevel = null;

        switch (currentLevel) {
            case "LOW":
                nextLevel = "MEDIUM";
                break;
            case "MEDIUM":
                nextLevel = "HIGH";
                break;
            case "HIGH":
                // 已经是最高级别，不再升级
                return;
            default:
                return;
        }

        alert.setPreviousLevel(currentLevel);
        alert.setAlertLevel(nextLevel);
        alert.setUpgradedAt(LocalDateTime.now());
        alertMapper.updateById(alert);
    }

    @Transactional
    public void receiveAlert(AlertDTO alertDTO) {
        // 告警合并逻辑：原子更新同设备同类型的未解决告警，避免并发竞态
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
            // 使用数据库原子 UPDATE 替代 selectOne + updateById
            int updated = alertMapper.updateExistingUnresolvedAlert(
                deviceId,
                alertType,
                alertDTO.getFaultProbability(),
                alertDTO.getMessage(),
                LocalDateTime.now()
            );

            if (updated > 0) {
                log.info("Alert merged for device: {}, type: {}", deviceId, alertType);
                return;
            }
        }

        // 无合并告警，创建新告警
        log.info("Creating new alert for device: {}, type: {}, probability: {}", deviceId, alertType, alertDTO.getFaultProbability());
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
        log.info("New alert created with id: {}", alert.getId());

        messagingTemplate.convertAndSend("/topic/alerts", alert);

        // 同时通过原生WebSocket广播（关联维修记录上下文）
        try {
            Map<String, Object> context = buildAlertContext(alert);
            alertWebSocketHandler.broadcastAlertContext(alert, context);
        } catch (Exception e) {
            log.error("WebSocket广播失败", e);
        }

        // 发送消息通知给所有管理员
        try {
            sendMessageToAdmins(alert);
        } catch (Exception e) {
            log.error("发送消息通知失败", e);
        }
    }

    /**
     * 构建告警关联上下文（维修记录等）
     */
    private Map<String, Object> buildAlertContext(Alert alert) {
        Map<String, Object> context = new HashMap<>();
        Long deviceId = alert.getDeviceId();
        if (deviceId == null) {
            return context;
        }

        try {
            Map<String, Object> result = deviceServiceClient.getMaintenancesByDevice(deviceId);
            if (result != null && result.get("data") != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("data");
                if (records != null && !records.isEmpty()) {
                    // 取最近3条维修记录
                    List<Map<String, Object>> recent = records.stream()
                        .limit(3)
                        .map(r -> {
                            Map<String, Object> m = new HashMap<>();
                            m.put("type", r.get("type"));
                            m.put("description", r.get("description"));
                            m.put("actionTaken", r.get("actionTaken"));
                            m.put("repairedAt", r.get("repairedAt"));
                            m.put("status", r.get("status"));
                            return m;
                        })
                        .toList();
                    context.put("maintenanceHistory", recent);
                    context.put("maintenanceCount", records.size());
                }
            }
        } catch (Exception e) {
            log.warn("查询设备 {} 维修记录失败: {}", deviceId, e.getMessage());
        }

        return context;
    }

    /**
     * 发送消息给所有用户
     */
    private void sendMessageToAdmins(Alert alert) {
        try {
            var userResult = userServiceClient.getUserList();
            if (userResult != null && userResult.getData() != null) {
                for (var user : userResult.getData()) {
                    Long userId = user.get("id") != null ? Long.valueOf(user.get("id").toString()) : null;
                    if (userId != null) {
                        String title = "【" + alert.getAlertLevel() + "级告警】" + alert.getDeviceName();
                        String content = alert.getMessage();
                        messageService.sendMessage(userId, "ALERT", title, content, alert.getId());
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取用户列表失败", e);
        }
    }

    public void resolveAlert(Long id) {
        resolveAlert(id, null, null, "COMPLETED", null, null, null);
    }

    public void resolveAlert(Long id, String resolveNote, Long operatorId) {
        resolveAlert(id, resolveNote, operatorId, "COMPLETED", null, null, null);
    }

    /**
     * 处理告警（简化版本）
     */
    public void resolveAlert(Long id, String resolveNote, Long operatorId, String resolveType) {
        resolveAlert(id, resolveNote, operatorId, resolveType, null, null, null);
    }

    /**
     * 处理告警（完整版本，带本地事务 + 远程调用补偿机制）
     *
     * 设计：本地 MySQL 更新在 @Transactional 事务中保证原子性；
     * 远程调用（device-service）失败时记录补偿任务，通过定时任务重试。
     */
    @Transactional
    public void resolveAlert(Long id, String resolveNote, Long operatorId, String resolveType,
                             String maintenanceType, String faultCategory, String description) {
        Alert alert = alertMapper.selectById(id);
        if (alert == null) {
            throw new RuntimeException("Alert not found");
        }

        // 1. 本地事务：更新告警状态
        alert.setResolved(true);
        alert.setResolveNote(resolveNote);
        alert.setResolvedBy(operatorId);
        alert.setResolvedAt(LocalDateTime.now());
        alert.setUpdatedAt(LocalDateTime.now());
        alertMapper.updateById(alert);

        Long deviceId = alert.getDeviceId();
        if (deviceId == null) {
            return;
        }

        // 2. 构建维修记录 DTO（共用）
        MaintenanceDTO maintenance = new MaintenanceDTO();
        maintenance.setDeviceId(deviceId);
        maintenance.setType(maintenanceType != null ? maintenanceType : (alert.getType() != null ? alert.getType() : "REPAIR"));
        maintenance.setFaultCategory(faultCategory);
        maintenance.setAlertId(id);
        maintenance.setActionTaken(resolveNote != null ? resolveNote : "已处理");
        maintenance.setOperatorId(operatorId);
        maintenance.setRepairedAt(LocalDateTime.now());

        String targetStatus;
        if ("COMPLETED".equals(resolveType)) {
            targetStatus = "NORMAL";
            maintenance.setStatus("COMPLETED");
            maintenance.setDescription(description != null ? description : ("告警处理: " + (alert.getMessage() != null ? alert.getMessage() : "无描述")));
        } else if ("STOPPED".equals(resolveType)) {
            targetStatus = "OFFLINE";
            maintenance.setStatus("COMPLETED");
            maintenance.setDescription(description != null ? description : ("告警处理-停机: " + (alert.getMessage() != null ? alert.getMessage() : "无描述")));
        } else if ("PENDING".equals(resolveType)) {
            targetStatus = null; // 不更新设备状态
            maintenance.setStatus("PENDING");
            maintenance.setDescription(description != null ? description : ("告警处理-待维修: " + (alert.getMessage() != null ? alert.getMessage() : "无描述")));
        } else {
            return;
        }

        // 3. 远程调用：更新设备状态（如需要）
        if (targetStatus != null) {
            try {
                deviceServiceClient.updateDeviceStatus(deviceId, targetStatus);
                log.info("Device status updated to {} for alert: {}", targetStatus, id);
            } catch (Exception e) {
                log.error("Failed to update device status for alert: {}, recording compensation task", id, e);
                recordCompensationTask(id, deviceId, "UPDATE_STATUS", targetStatus);
            }
        }

        // 4. 远程调用：创建维修记录
        try {
            deviceServiceClient.createMaintenance(maintenance);
            log.info("Maintenance record created for alert: {}", id);
        } catch (Exception e) {
            log.error("Failed to create maintenance record for alert: {}, recording compensation task", id, e);
            recordCompensationTask(id, deviceId, "CREATE_MAINTENANCE", maintenance);
        }
    }

    /**
     * 记录补偿任务，用于远程调用失败后的异步重试
     */
    private void recordCompensationTask(Long alertId, Long deviceId, String taskType, Object payload) {
        Map<String, Object> task = new HashMap<>();
        task.put("alertId", alertId);
        task.put("deviceId", deviceId);
        task.put("type", taskType);
        task.put("payload", payload);
        task.put("createdAt", LocalDateTime.now());
        task.put("retryCount", 0);

        compensationTasks.put(alertId, task);
        log.warn("Compensation task recorded: alertId={}, type={}", alertId, taskType);
    }

    /**
     * 定时执行补偿任务（每5分钟检查一次）
     */
    @Scheduled(fixedRate = 300000)
    public void processCompensationTasks() {
        if (compensationTasks.isEmpty()) {
            return;
        }

        log.info("Processing {} compensation tasks", compensationTasks.size());

        for (Map.Entry<Long, Map<String, Object>> entry : new HashMap<>(compensationTasks).entrySet()) {
            Long alertId = entry.getKey();
            Map<String, Object> task = entry.getValue();
            int retryCount = (int) task.getOrDefault("retryCount", 0);

            if (retryCount >= 3) {
                log.error("Compensation task exceeded max retries, dropping: alertId={}", alertId);
                compensationTasks.remove(alertId);
                continue;
            }

            String taskType = (String) task.get("type");
            Long deviceId = (Long) task.get("deviceId");

            try {
                if ("UPDATE_STATUS".equals(taskType)) {
                    String status = (String) task.get("payload");
                    deviceServiceClient.updateDeviceStatus(deviceId, status);
                    log.info("Compensation success: updated device status for alert {}", alertId);
                    compensationTasks.remove(alertId);
                } else if ("CREATE_MAINTENANCE".equals(taskType)) {
                    MaintenanceDTO maintenance = (MaintenanceDTO) task.get("payload");
                    deviceServiceClient.createMaintenance(maintenance);
                    log.info("Compensation success: created maintenance for alert {}", alertId);
                    compensationTasks.remove(alertId);
                }
            } catch (Exception e) {
                task.put("retryCount", retryCount + 1);
                task.put("lastError", e.getMessage());
                log.error("Compensation failed for alert {} (retry {}/3)", alertId, retryCount + 1, e);
            }
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

        // 今日告警数
        QueryWrapper<Alert> todayWrapper = new QueryWrapper<>();
        todayWrapper.ge("created_at", java.time.LocalDate.now().atStartOfDay());
        Long todayCount = alertMapper.selectCount(todayWrapper);
        stats.put("todayCount", todayCount);

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
        return getChartBase64(deviceId);
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
            log.error("获取趋势图失败", e);
            return null;
        }
    }

    public AlertStatisticsDTO getFrequencyStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        Long total = alertMapper.countByDateRange(startDate, endDate);

        Map<String, Long> byLevel = new LinkedHashMap<>();
        List<Map<String, Object>> levelCounts = alertMapper.countByLevelAndDateRange(startDate, endDate);
        for (Map<String, Object> item : levelCounts) {
            String level = item.get("alert_level") != null ? item.get("alert_level").toString() : "";
            Long count = item.get("count") != null ? ((Number) item.get("count")).longValue() : 0L;
            byLevel.put(level, count);
        }

        Map<String, Long> byDevice = new LinkedHashMap<>();
        List<Map<String, Object>> deviceCounts = alertMapper.countByDeviceAndDateRange(startDate, endDate);
        for (Map<String, Object> item : deviceCounts) {
            String deviceName = item.get("device_name") != null ? item.get("device_name").toString() : null;
            Long deviceId = item.get("device_id") != null ? ((Number) item.get("device_id")).longValue() : null;
            Long count = item.get("count") != null ? ((Number) item.get("count")).longValue() : 0L;
            String key = deviceName != null ? deviceName : "设备" + deviceId;
            byDevice.put(key, count);
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