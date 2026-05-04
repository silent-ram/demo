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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SensorSimulator {

    private static final Logger log = LoggerFactory.getLogger(SensorSimulator.class);

    /**
     * 传感器模拟模式
     * STABLE: 稳定运行，不自发异常，故障率<5%
     * DEGRADING_SLOW: 缓慢劣化，渐进式故障
     * DEGRADING_FAST: 快速劣化，较快的渐进式故障
     * SUDDEN_FAULT: 突发故障，瞬间跳变到阈值以上
     * SPIKE_RECOVER: 偶发异常，短暂跳变后恢复
     */
    public enum SimMode {
        STABLE("稳定运行", false),
        DEGRADING_SLOW("缓慢劣化", true),
        DEGRADING_FAST("快速劣化", true),
        SUDDEN_FAULT("突发故障", true),
        SPIKE_RECOVER("偶发异常", false);

        private final String desc;
        private final boolean canTriggerAlert;

        SimMode(String desc, boolean canTriggerAlert) {
            this.desc = desc;
            this.canTriggerAlert = canTriggerAlert;
        }

        public String getDesc() { return desc; }
        public boolean canTriggerAlert() { return canTriggerAlert; }
    }

    /**
     * 传感器配置（从 device_profiles.json 加载）
     */
    public static class SensorProfile {
        final double baseline;
        final double threshold;
        final String unit;
        // 各模式参数
        final double stableNoiseRatio;
        final double stableDriftRatio;
        final double stableBoundRatio;
        final double degradingStepMin;
        final double degradingStepMax;
        final double degradingTargetRatio;
        final double spikeMagnitudeMin;
        final double spikeMagnitudeMax;

        SensorProfile(double baseline, double threshold, String unit,
                      double stableNoiseRatio, double stableDriftRatio, double stableBoundRatio,
                      double degradingStepMin, double degradingStepMax, double degradingTargetRatio,
                      double spikeMagnitudeMin, double spikeMagnitudeMax) {
            this.baseline = baseline;
            this.threshold = threshold;
            this.unit = unit;
            this.stableNoiseRatio = stableNoiseRatio;
            this.stableDriftRatio = stableDriftRatio;
            this.stableBoundRatio = stableBoundRatio;
            this.degradingStepMin = degradingStepMin;
            this.degradingStepMax = degradingStepMax;
            this.degradingTargetRatio = degradingTargetRatio;
            this.spikeMagnitudeMin = spikeMagnitudeMin;
            this.spikeMagnitudeMax = spikeMagnitudeMax;
        }
    }

    // 从配置文件加载的设备参数
    private Map<String, Map<String, SensorProfile>> deviceProfiles;

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

    // 设备当前模拟模式（向后兼容：NORMAL->STABLE, MANUAL_INSERT->保留, RANDOM_RANGE->保留）
    private final Map<String, String> deviceModes = new ConcurrentHashMap<>();

    // 新增：传感模拟模式（STABLE/DEGRADING_SLOW/DEGRADING_FAST/SUDDEN_FAULT/SPIKE_RECOVER）
    private final Map<String, SimMode> deviceSimModes = new ConcurrentHashMap<>();

    // 设备随机游走当前值（保持连续性）
    private final Map<String, Map<String, Double>> deviceCurrentValues = new ConcurrentHashMap<>();

    // 设备状态计数器（用于 DEGRADING_FAST/SPIKE_RECOVER 的持续时间控制）
    private final Map<String, Integer> deviceStateCounters = new ConcurrentHashMap<>();

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

    @PostConstruct
    public void init() {
        loadDeviceProfiles();
    }

    /**
     * 从 config/device_profiles.json 加载设备配置
     */
    private void loadDeviceProfiles() {
        deviceProfiles = new HashMap<>();
        try {
            // 配置文件在项目根目录的 config/ 下，从 JAR 所在目录向上退一级
            String configPath = Paths.get(System.getProperty("user.dir"), "..", "config", "device_profiles.json").toString();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(new File(configPath));
            JsonNode deviceTypes = root.get("device_types");

            if (deviceTypes == null) {
                log.warn("device_profiles.json 中没有 device_types 节点，使用默认配置");
                loadDefaultProfiles();
                return;
            }

            deviceTypes.fields().forEachRemaining(entry -> {
                String deviceType = entry.getKey();
                JsonNode sensors = entry.getValue().get("sensors");
                Map<String, SensorProfile> sensorMap = new HashMap<>();

                sensors.fields().forEachRemaining(sensorEntry -> {
                    String sensorCode = sensorEntry.getKey();
                    JsonNode cfg = sensorEntry.getValue();
                    JsonNode stable = cfg.get("stable");
                    JsonNode degrading = cfg.get("degrading");
                    JsonNode spike = cfg.get("spike");

                    SensorProfile profile = new SensorProfile(
                        cfg.get("baseline").asDouble(),
                        cfg.get("threshold").asDouble(),
                        cfg.get("unit").asText(),
                        stable.get("noise_ratio").asDouble(),
                        stable.get("drift_ratio").asDouble(),
                        stable.get("bound_ratio").asDouble(),
                        degrading.get("step_min").asDouble(),
                        degrading.get("step_max").asDouble(),
                        degrading.get("target_ratio").asDouble(),
                        spike.get("magnitude_min").asDouble(),
                        spike.get("magnitude_max").asDouble()
                    );
                    sensorMap.put(sensorCode, profile);
                });
                deviceProfiles.put(deviceType, sensorMap);
            });

            log.info("成功加载 {} 种设备类型的配置", deviceProfiles.size());
        } catch (IOException e) {
            log.error("加载 device_profiles.json 失败，使用默认配置: {}", e.getMessage());
            loadDefaultProfiles();
        }
    }

    private void loadDefaultProfiles() {
        // 回退到硬编码默认值（与 config/device_profiles.json 一致）
        Map<String, SensorProfile> robot = new HashMap<>();
        robot.put("temperature", new SensorProfile(65.0, 80.0, "°C", 0.008, 0.002, 1.05, 0.5, 2.0, 1.3, 5.0, 10.0));
        robot.put("vibration",   new SensorProfile(0.25, 0.6, "mm/s", 0.008, 0.002, 1.05, 0.05, 0.15, 1.3, 0.1, 0.2));
        robot.put("pressure",    new SensorProfile(100.0, 130.0, "Pa", 0.008, 0.002, 1.05, 2.0, 5.0, 1.3, 10.0, 20.0));
        deviceProfiles.put("工业机器人", robot);

        Map<String, SensorProfile> cnc = new HashMap<>();
        cnc.put("temperature", new SensorProfile(55.0, 80.0, "°C", 0.008, 0.002, 1.05, 0.5, 2.5, 1.3, 5.0, 12.0));
        cnc.put("vibration",   new SensorProfile(0.20, 0.6, "mm/s", 0.008, 0.002, 1.05, 0.05, 0.15, 1.3, 0.1, 0.25));
        cnc.put("pressure",    new SensorProfile(90.0, 130.0, "Pa", 0.008, 0.002, 1.05, 2.0, 6.0, 1.3, 10.0, 25.0));
        deviceProfiles.put("数控机床", cnc);

        Map<String, SensorProfile> conveyor = new HashMap<>();
        conveyor.put("temperature", new SensorProfile(50.0, 60.0, "°C", 0.008, 0.002, 1.05, 0.3, 1.0, 1.3, 3.0, 8.0));
        conveyor.put("vibration",   new SensorProfile(0.30, 0.5, "mm/s", 0.008, 0.002, 1.05, 0.03, 0.1, 1.3, 0.08, 0.15));
        conveyor.put("pressure",    new SensorProfile(80.0, 100.0, "Pa", 0.008, 0.002, 1.05, 1.0, 3.0, 1.3, 5.0, 15.0));
        deviceProfiles.put("输送设备", conveyor);

        Map<String, SensorProfile> welder = new HashMap<>();
        welder.put("temperature", new SensorProfile(85.0, 100.0, "°C", 0.008, 0.002, 1.05, 0.5, 2.0, 1.3, 5.0, 10.0));
        welder.put("vibration",   new SensorProfile(0.15, 0.5, "mm/s", 0.008, 0.002, 1.05, 0.03, 0.12, 1.3, 0.05, 0.15));
        welder.put("pressure",    new SensorProfile(110.0, 140.0, "Pa", 0.008, 0.002, 1.05, 2.0, 6.0, 1.3, 10.0, 25.0));
        deviceProfiles.put("焊接设备", welder);

        Map<String, SensorProfile> press = new HashMap<>();
        press.put("temperature", new SensorProfile(70.0, 90.0, "°C", 0.008, 0.002, 1.05, 0.5, 2.0, 1.3, 5.0, 10.0));
        press.put("vibration",   new SensorProfile(0.20, 0.5, "mm/s", 0.008, 0.002, 1.05, 0.03, 0.1, 1.3, 0.08, 0.18));
        press.put("pressure",    new SensorProfile(120.0, 150.0, "Pa", 0.008, 0.002, 1.05, 2.5, 7.0, 1.3, 15.0, 30.0));
        deviceProfiles.put("压力设备", press);

        Map<String, SensorProfile> packer = new HashMap<>();
        packer.put("temperature", new SensorProfile(45.0, 50.0, "°C", 0.008, 0.002, 1.05, 0.2, 0.8, 1.3, 2.0, 5.0));
        packer.put("vibration",   new SensorProfile(0.10, 0.4, "mm/s", 0.008, 0.002, 1.05, 0.02, 0.08, 1.3, 0.05, 0.12));
        packer.put("pressure",    new SensorProfile(70.0, 90.0, "Pa", 0.008, 0.002, 1.05, 1.5, 4.0, 1.3, 8.0, 18.0));
        deviceProfiles.put("包装设备", packer);
    }

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
     * 设置传感模拟模式（新增）
     */
    public void setSimMode(String deviceId, String mode) {
        try {
            SimMode simMode = SimMode.valueOf(mode);
            deviceSimModes.put(deviceId, simMode);
            deviceStateCounters.put(deviceId, 0);
            log.info("设备 {} 模拟模式设置为: {} ({})", deviceId, simMode.name(), simMode.getDesc());
        } catch (IllegalArgumentException e) {
            log.warn("未知的模拟模式: {}, 设备: {}", mode, deviceId);
        }
    }

    /**
     * 获取传感模拟模式（新增）
     */
    public SimMode getSimMode(String deviceId) {
        return deviceSimModes.getOrDefault(deviceId, SimMode.STABLE);
    }

    /**
     * 重置为稳定运行模式
     */
    public void resetToStable(String deviceId) {
        deviceSimModes.put(deviceId, SimMode.STABLE);
        deviceStateCounters.put(deviceId, 0);
        // 将当前值平滑拉回到基线附近
        Map<String, Double> currentValues = deviceCurrentValues.get(deviceId);
        if (currentValues != null) {
            String deviceType = getDeviceTypeById(deviceId);
            if (deviceType != null) {
                Map<String, SensorProfile> profiles = deviceProfiles.get(deviceType);
                if (profiles != null) {
                    for (Map.Entry<String, SensorProfile> entry : profiles.entrySet()) {
                        String metric = entry.getKey();
                        SensorProfile profile = entry.getValue();
                        Double current = currentValues.get(metric);
                        if (current != null) {
                            currentValues.put(metric, profile.baseline);
                        }
                    }
                }
            }
        }
        log.info("设备 {} 已重置为稳定运行模式", deviceId);
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
     * 设置设备模式（向后兼容）
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
     * 获取设备当前模式（向后兼容）
     */
    public String getDeviceMode(String deviceId) {
        return deviceModes.get(deviceId);
    }

    @Scheduled(fixedRate = 5000)
    public void generateSensorData() {
        if (!globalRunning.get()) {
            return;
        }

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

        String[] metricNames = {"temperature", "vibration", "pressure"};
        String[] units = {"°C", "mm/s", "Pa"};

        for (com.example.datacollectorservice.dto.DeviceDTO device : runningDevices) {
            String deviceId = device.getId().toString();

            AtomicBoolean deviceStatus = deviceStatuses.get(deviceId);
            if (deviceStatus != null && !deviceStatus.get()) {
                continue;
            }
            if (deviceStatus == null) {
                deviceStatuses.put(deviceId, new AtomicBoolean(true));
            }

            String mode = deviceModes.get(deviceId);
            if (mode == null) {
                mode = "NORMAL";
                deviceModes.put(deviceId, mode);
            }

            Map<String, Double> pendingValues = pendingInsertValues.get(deviceId);
            Map<String, Double> manualDeviceValues = manualValues.get(deviceId);
            Map<String, double[]> rangeValues = randomRanges.get(deviceId);

            String deviceType = device.getType();
            List<SensorConfig> sensorConfigs = sensorConfigMapper.findEnabledByDeviceType(deviceType);
            if (sensorConfigs == null || sensorConfigs.isEmpty()) {
                sensorConfigs = getDefaultSensorConfigs();
            }

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

                if ("MANUAL_INSERT".equals(mode) && pendingValues != null && pendingValues.containsKey(metricName)) {
                    value = pendingValues.get(metricName);
                } else if ("RANDOM_RANGE".equals(mode) && rangeValues != null && rangeValues.containsKey(metricName)) {
                    double[] range = rangeValues.get(metricName);
                    value = range[0] + random.nextDouble() * (range[1] - range[0]);
                } else if (manualDeviceValues != null && manualDeviceValues.containsKey(metricName)) {
                    value = manualDeviceValues.get(metricName);
                } else {
                    value = generateSimulatedValue(deviceId, deviceType, metricName,
                            threshold != null ? threshold : getDefaultThreshold(metricName));
                }

                MetricDTO metric = new MetricDTO(deviceId, metricName, value, unit, Instant.now());
                batchMetrics.add(metric);
            }

            if ("MANUAL_INSERT".equals(mode) && pendingValues != null && !pendingValues.isEmpty()) {
                pendingInsertValues.remove(deviceId);
                deviceModes.put(deviceId, "NORMAL");
            }

            Double faultProbability = null;
            if (!batchMetrics.isEmpty()) {
                try {
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
                        request.setDeviceType(deviceType);
                        request.setTemperature(temperature);
                        request.setVibration(vibration);
                        request.setPressure(pressure);
                        request.setSensorData(batchMetrics);

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
                                faultProbability = response.getData().getFaultProbability();
                                deviceFaultProbabilities.put(deviceId, faultProbability);

                                SimMode simMode = getSimMode(deviceId);
                                // 只有 canTriggerAlert 的模式才推送告警
                                if (faultProbability >= 0.7 && simMode.canTriggerAlert()) {
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

                    // 写入传感器数据（包含故障概率，如果已获取）
                    influxDBService.writeMetrics(batchMetrics, faultProbability);

                } catch (Exception e) {
                    log.error("Failed to write metrics to InfluxDB for device {}", deviceId, e);
                }
            }
        }
    }

    /**
     * 基于多模式生成传感器值
     */
    private double generateSimulatedValue(String deviceId, String deviceType, String metricName, double threshold) {
        SensorProfile profile = getProfile(deviceType, metricName, threshold);
        double baseline = profile.baseline;

        Map<String, Double> currentValues = deviceCurrentValues.computeIfAbsent(deviceId, k -> new ConcurrentHashMap<>());
        double current = currentValues.getOrDefault(metricName, baseline);

        SimMode simMode = getSimMode(deviceId);
        int counter = deviceStateCounters.getOrDefault(deviceId, 0);

        switch (simMode) {
            case STABLE:
                // 稳定运行：纯随机游走，不自发异常
                double noiseStd = baseline * profile.stableNoiseRatio;
                double drift = random.nextGaussian() * baseline * profile.stableDriftRatio;
                current += drift + random.nextGaussian() * noiseStd;
                // 边界保护：baseline * (1 ± bound_ratio)
                double lowerBound = baseline * 0.95;
                double upperBound = baseline * profile.stableBoundRatio;
                current = Math.max(lowerBound, Math.min(current, upperBound));
                break;

            case DEGRADING_SLOW:
                // 缓慢劣化：渐进式向阈值推进
                double slowStep = profile.degradingStepMin + random.nextDouble() *
                                 (profile.degradingStepMax - profile.degradingStepMin) * 0.3; // 0.3倍减速
                double slowTarget = baseline + (threshold - baseline) * profile.degradingTargetRatio;
                if (current < slowTarget) {
                    current = Math.min(current + slowStep, slowTarget);
                }
                break;

            case DEGRADING_FAST:
                // 快速劣化：较快的渐进式故障
                double fastStep = profile.degradingStepMin + random.nextDouble() *
                                 (profile.degradingStepMax - profile.degradingStepMin);
                double fastTarget = baseline + (threshold - baseline) * profile.degradingTargetRatio;
                if (current < fastTarget) {
                    current = Math.min(current + fastStep, fastTarget);
                }
                break;

            case SUDDEN_FAULT:
                // 突发故障：瞬间跳变到阈值以上
                double jump = (threshold - baseline) * (1.0 + random.nextDouble() * 0.3);
                current = baseline + jump;
                break;

            case SPIKE_RECOVER:
                // 偶发异常：短暂跳变后自动恢复
                if (counter <= 0) {
                    // 开始跳变
                    counter = random.nextInt(3) + 1; // 1-3个tick
                    double spike = profile.spikeMagnitudeMin + random.nextDouble() *
                                  (profile.spikeMagnitudeMax - profile.spikeMagnitudeMin);
                    current = baseline + spike;
                } else {
                    counter--;
                    if (counter <= 0) {
                        // 恢复完成，自动切回 STABLE
                        deviceSimModes.put(deviceId, SimMode.STABLE);
                        current = baseline; // 瞬间恢复（模拟偶发干扰）
                    }
                }
                break;
        }

        deviceStateCounters.put(deviceId, counter);
        currentValues.put(metricName, current);
        return current;
    }

    private SensorProfile getProfile(String deviceType, String metricName, double fallbackThreshold) {
        Map<String, SensorProfile> typeProfiles = deviceProfiles.get(deviceType);
        if (typeProfiles != null && typeProfiles.containsKey(metricName)) {
            return typeProfiles.get(metricName);
        }
        // 回退：基于阈值推算基线，使用默认参数
        double fallbackBaseline = fallbackThreshold * 0.8;
        return new SensorProfile(fallbackBaseline, fallbackThreshold, "",
                0.008, 0.002, 1.05, 0.5, 2.0, 1.3, 5.0, 10.0);
    }

    private String getDeviceTypeById(String deviceId) {
        // 通过 device-service 查询设备类型（简化实现）
        try {
            com.example.datacollectorservice.exception.Result<com.example.datacollectorservice.dto.DeviceDTO> result =
                deviceServiceClient.getDevice(Long.valueOf(deviceId));
            if (result != null && result.getData() != null) {
                return result.getData().getType();
            }
        } catch (Exception e) {
            log.warn("获取设备类型失败: {}", deviceId);
        }
        return null;
    }

    public Map<String, Boolean> getAllDeviceStatuses() {
        Map<String, Boolean> statuses = new ConcurrentHashMap<>();
        deviceStatuses.forEach((id, status) -> statuses.put(id, status.get()));
        return statuses;
    }

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

    private double getDefaultThreshold(String sensorCode) {
        SensorType type = SensorType.fromCode(sensorCode);
        return type != null ? type.getAlertThreshold() : 0;
    }
}
