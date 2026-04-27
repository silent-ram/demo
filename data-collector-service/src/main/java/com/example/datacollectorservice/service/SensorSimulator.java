package com.example.datacollectorservice.service;

import com.example.datacollectorservice.dto.AlertDTO;
import com.example.datacollectorservice.dto.MetricDTO;
import com.example.datacollectorservice.dto.PredictRequest;
import com.example.datacollectorservice.dto.PredictResponse;
import com.example.datacollectorservice.enums.SensorType;
import com.example.datacollectorservice.entity.SensorConfig;
import com.example.datacollectorservice.feign.AlertServiceClient;
import com.example.datacollectorservice.feign.DeviceServiceClient;
import com.example.datacollectorservice.feign.MlServiceClient;
import com.example.datacollectorservice.mapper.SensorConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SensorSimulator {

    private static final Logger log = LoggerFactory.getLogger(SensorSimulator.class);

    private final Random random = new Random();

    // 每个设备独立的运行状态
    private final Map<String, AtomicBoolean> deviceStatuses = new ConcurrentHashMap<>();

    // 手动设置的传感器数据（用于持续输出）
    private final Map<String, Map<String, Double>> manualValues = new ConcurrentHashMap<>();

    // 待插入的指定数据（只使用一次）
    private final Map<String, Map<String, Double>> pendingInsertValues = new ConcurrentHashMap<>();

    // 手动选取的随机范围
    private final Map<String, Map<String, double[]>> randomRanges = new ConcurrentHashMap<>();

    // 设备当前故障概率（用于告警判断）
    private final Map<String, Double> deviceFaultProbabilities = new ConcurrentHashMap<>();

    // 设备当前模拟模式: NORMAL(正常输出), MANUAL_INSERT(插入指定数据), RANDOM_RANGE(手动范围随机)
    private final Map<String, String> deviceModes = new ConcurrentHashMap<>();

    @Autowired
    private InfluxDBService influxDBService;

    @Autowired
    private MlServiceClient mlServiceClient;

    @Autowired
    private AlertServiceClient alertServiceClient;

    @Autowired
    private DeviceServiceClient deviceServiceClient;

    @Autowired
    private SensorConfigMapper sensorConfigMapper;

    // 全局模拟开关（兼容旧接口）
    private final AtomicBoolean globalRunning = new AtomicBoolean(true);

    public void start() {
        globalRunning.set(true);
    }

    public void stop() {
        globalRunning.set(false);
    }

    public boolean isRunning() {
        return globalRunning.get();
    }

    public void startDevice(String deviceId) {
        deviceStatuses.computeIfAbsent(deviceId, k -> new AtomicBoolean(true)).set(true);
    }

    public void stopDevice(String deviceId) {
        AtomicBoolean status = deviceStatuses.get(deviceId);
        if (status != null) {
            status.set(false);
        }
    }

    public boolean isDeviceRunning(String deviceId) {
        AtomicBoolean status = deviceStatuses.get(deviceId);
        return status != null && status.get();
    }

    /**
     * 设置设备的手动传感器数值（持续输出）
     */
    public void setManualValue(String deviceId, String metricName, Double value) {
        manualValues.computeIfAbsent(deviceId, k -> new ConcurrentHashMap<>())
                    .put(metricName, value);
    }

    /**
     * 插入指定数据（下一次生成时使用）
     */
    public void insertValue(String deviceId, String metricName, Double value) {
        pendingInsertValues.computeIfAbsent(deviceId, k -> new ConcurrentHashMap<>())
                          .put(metricName, value);
    }

    /**
     * 设置随机范围
     */
    public void setRandomRange(String deviceId, String metricName, double min, double max) {
        randomRanges.computeIfAbsent(deviceId, k -> new ConcurrentHashMap<>())
                    .put(metricName, new double[]{min, max});
    }

    /**
     * 设置设备模式
     */
    public void setDeviceMode(String deviceId, String mode) {
        deviceModes.put(deviceId, mode);
    }

    /**
     * 清除设备的手动设置
     */
    public void clearManualValues(String deviceId) {
        manualValues.remove(deviceId);
        pendingInsertValues.remove(deviceId);
        randomRanges.remove(deviceId);
        deviceModes.remove(deviceId);
    }

    /**
     * 获取设备的手动设置值
     */
    public Map<String, Double> getManualValues(String deviceId) {
        return manualValues.get(deviceId);
    }

    /**
     * 获取设备当前故障概率
     */
    public Double getFaultProbability(String deviceId) {
        return deviceFaultProbabilities.get(deviceId);
    }

    /**
     * 获取设备当前模式
     */
    public String getDeviceMode(String deviceId) {
        return deviceModes.get(deviceId);
    }

    @Scheduled(fixedRate = 5000)
    public void generateSensorData() {
        // 检查全局模拟开关
        if (!globalRunning.get()) {
            return;
        }

        // 从 device-service 获取运行中的设备列表
        List<com.example.datacollectorservice.dto.DeviceDTO> runningDevices;
        try {
            com.example.datacollectorservice.exception.Result<List<com.example.datacollectorservice.dto.DeviceDTO>> result = deviceServiceClient.getRunningDevices();
            runningDevices = (result != null && result.getData() != null) ? result.getData() : null;
        } catch (Exception e) {
            log.error("Failed to get running devices", e);
            return;
        }

        if (runningDevices == null || runningDevices.isEmpty()) {
            return;
        }

        // 默认指标名称（只保留温度、振动、压力）
        String[] metricNames = {"temperature", "vibration", "pressure"};
        String[] units = {"°C", "mm/s", "Pa"};

        for (com.example.datacollectorservice.dto.DeviceDTO device : runningDevices) {
            String deviceId = device.getId().toString();

            // 检查该设备是否启用模拟
            AtomicBoolean deviceStatus = deviceStatuses.get(deviceId);
            if (deviceStatus != null && !deviceStatus.get()) {
                continue;
            }
            // 默认启用新设备的模拟
            if (deviceStatus == null) {
                deviceStatuses.put(deviceId, new AtomicBoolean(true));
            }

            // 获取设备模式
            String mode = deviceModes.get(deviceId);
            if (mode == null) {
                mode = "NORMAL"; // 默认正常输出
                deviceModes.put(deviceId, mode);
            }

            // 获取待插入数据
            Map<String, Double> pendingValues = pendingInsertValues.get(deviceId);
            // 获取手动设置的值
            Map<String, Double> manualDeviceValues = manualValues.get(deviceId);
            // 获取随机范围
            Map<String, double[]> rangeValues = randomRanges.get(deviceId);

            // 获取设备类型对应的传感器配置
            String deviceType = device.getType();
            List<SensorConfig> sensorConfigs = sensorConfigMapper.findEnabledByDeviceType(deviceType);
            if (sensorConfigs == null || sensorConfigs.isEmpty()) {
                // 如果没有配置，使用默认传感器
                sensorConfigs = getDefaultSensorConfigs();
            }

            // 构建传感器代码到索引的映射（用于阈值数组索引）
            Map<String, Integer> sensorIndexMap = new ConcurrentHashMap<>();
            for (int i = 0; i < metricNames.length; i++) {
                sensorIndexMap.put(metricNames[i], i);
            }

            List<MetricDTO> batchMetrics = new ArrayList<>();

            for (SensorConfig config : sensorConfigs) {
                String metricName = config.getSensorCode();
                Double threshold = config.getAlertThreshold();
                Integer index = sensorIndexMap.get(metricName);
                String unit = (index != null && index < units.length) ? units[index] : "";
                Double value = null;

                // 根据模式生成数据
                if ("MANUAL_INSERT".equals(mode) && pendingValues != null && pendingValues.containsKey(metricName)) {
                    // 插入指定数据（只用一次）
                    value = pendingValues.get(metricName);
                } else if ("RANDOM_RANGE".equals(mode) && rangeValues != null && rangeValues.containsKey(metricName)) {
                    // 手动范围随机生成
                    double[] range = rangeValues.get(metricName);
                    value = range[0] + random.nextDouble() * (range[1] - range[0]);
                } else if (manualDeviceValues != null && manualDeviceValues.containsKey(metricName)) {
                    // 手动设置持续输出
                    value = manualDeviceValues.get(metricName);
                } else if ("NORMAL".equals(mode)) {
                    // 正常范围输出，使用配置表阈值或默认阈值
                    double alertThreshold = threshold != null ? threshold : getDefaultThreshold(metricName);
                    double normalMax = alertThreshold * 0.8;  // 正常值上限为阈值的80%
                    double faultMin = alertThreshold;  // 故障值下限为阈值
                    boolean isFault = random.nextDouble() < 0.1;
                    if (isFault) {
                        value = faultMin + random.nextDouble() * 5;
                    } else {
                        value = normalMax * (0.3 + random.nextDouble() * 0.5);  // 正常值在30%-80%之间
                        if (value < 0) value = 0.0;
                    }
                } else {
                    // 默认正常输出
                    double alertThreshold = threshold != null ? threshold : getDefaultThreshold(metricName);
                    double normalMax = alertThreshold * 0.8;
                    double faultMin = alertThreshold;
                    boolean isFault = random.nextDouble() < 0.1;
                    if (isFault) {
                        value = faultMin + random.nextDouble() * 5;
                    } else {
                        value = normalMax * (0.3 + random.nextDouble() * 0.5);
                        if (value < 0) value = 0.0;
                    }
                }

                MetricDTO metric = new MetricDTO(deviceId, metricName, value, unit, Instant.now());
                batchMetrics.add(metric);
            }

            // 使用完插入数据后清除
            if ("MANUAL_INSERT".equals(mode) && pendingValues != null && !pendingValues.isEmpty()) {
                pendingInsertValues.remove(deviceId);
                // 插入完成后恢复正常模式
                deviceModes.put(deviceId, "NORMAL");
            }

            if (!batchMetrics.isEmpty()) {
                try {
                    // 写入 InfluxDB
                    influxDBService.writeMetrics(batchMetrics);

                    // 调用 ML 服务获取故障概率
                    MetricDTO latestMetric = batchMetrics.stream()
                        .filter(m -> "temperature".equals(m.getMetricName()))
                        .findFirst().orElse(null);

                    if (latestMetric != null) {
                        double temperature = latestMetric.getValue();
                        double vibration = batchMetrics.stream()
                            .filter(m -> "vibration".equals(m.getMetricName()))
                            .findFirst().map(MetricDTO::getValue).orElse(0.0);
                        double pressure = batchMetrics.stream()
                            .filter(m -> "pressure".equals(m.getMetricName()))
                            .findFirst().map(MetricDTO::getValue).orElse(0.0);

                        PredictRequest request = new PredictRequest();
                        request.setDeviceId(deviceId);
                        request.setTemperature(temperature);
                        request.setVibration(vibration);
                        request.setPressure(pressure);
                        request.setSensorData(batchMetrics);
                        // 添加阈值配置（从 SensorType 枚举获取）
                        Map<String, Double> thresholds = new HashMap<>();
                        for (MetricDTO metric : batchMetrics) {
                            SensorType type = SensorType.fromCode(metric.getMetricName());
                            if (type != null) {
                                thresholds.put(metric.getMetricName(), type.getAlertThreshold());
                            }
                        }
                        request.setThresholds(thresholds);

                        try {
                            PredictResponse response = mlServiceClient.predict(request);
                            if (response != null && response.getData() != null) {
                                double faultProbability = response.getData().getFaultProbability();
                                deviceFaultProbabilities.put(deviceId, faultProbability);

                                // 如果故障概率超过阈值，触发告警
                                if (faultProbability >= 0.7) {
                                    String alertLevel = faultProbability >= 0.9 ? "HIGH" :
                                                       faultProbability >= 0.8 ? "MEDIUM" : "LOW";
                                    AlertDTO alert = new AlertDTO(
                                        deviceId,
                                        device.getName() != null ? device.getName() : "设备" + deviceId,
                                        faultProbability,
                                        alertLevel,
                                        "故障概率预警",
                                        "当前故障概率: " + String.format("%.2f", faultProbability)
                                    );
                                    alertServiceClient.pushAlert(alert);
                                }
                            }
                        } catch (Exception e) {
                            log.error("ML service call failed for device {}", deviceId, e);
                        }
                    }

                } catch (Exception e) {
                    log.error("Failed to write metrics to InfluxDB for device {}", deviceId, e);
                }
            }
        }
    }

    /**
     * 获取所有设备模拟状态
     */
    public Map<String, Boolean> getAllDeviceStatuses() {
        Map<String, Boolean> statuses = new ConcurrentHashMap<>();
        deviceStatuses.forEach((id, status) -> statuses.put(id, status.get()));
        return statuses;
    }

    /**
     * 获取默认传感器配置列表
     */
    private List<SensorConfig> getDefaultSensorConfigs() {
        List<SensorConfig> configs = new ArrayList<>();
        for (String code : Arrays.asList("temperature", "vibration", "pressure")) {
            SensorConfig config = new SensorConfig();
            config.setSensorCode(code);
            config.setEnabled(true);
            config.setAlertThreshold(getDefaultThreshold(code));
            configs.add(config);
        }
        return configs;
    }

    /**
     * 获取默认告警阈值
     */
    private double getDefaultThreshold(String sensorCode) {
        SensorType type = SensorType.fromCode(sensorCode);
        return type != null ? type.getAlertThreshold() : 0;
    }
}
