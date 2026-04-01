package com.example.datacollectorservice.service;

import com.example.datacollectorservice.dto.AlertDTO;
import com.example.datacollectorservice.dto.MetricDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SensorSimulator {

    private final Random random = new Random();
    private final List<MetricDTO> metricsBuffer = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    
    @Autowired
    private InfluxDBService influxDBService;

    public void start() {
        if (running.compareAndSet(false, true)) {
            System.out.println("Sensor simulator started");
        } else {
            System.out.println("Sensor simulator is already running");
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            System.out.println("Sensor simulator stopped");
        } else {
            System.out.println("Sensor simulator is not running");
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    @Scheduled(fixedRate = 5000)
    public void generateSensorData() {
        if (!running.get()) {
            return;
        }

        // 使用数字 ID，与数据库设备 ID 对应
        Integer[] deviceIds = {1, 2, 3, 4, 5, 6};
        String[] metricNames = {"temperature", "vibration", "pressure", "current"};
        String[] units = {"°C", "mm/s", "Pa", "A"};
        // 压力值最大不超过 1000，以匹配 ml-service 的范围校验
        Double[][] thresholds = {{80.0, 10.0, 800.0, 5.0}, {90.0, 15.0, 950.0, 7.0}};

        List<MetricDTO> batchMetrics = new ArrayList<>();

        for (Integer deviceId : deviceIds) {
            for (int i = 0; i < metricNames.length; i++) {
                String metricName = metricNames[i];
                String unit = units[i];

                boolean isFault = random.nextDouble() < 0.1;
                Double value;
                Double threshold;
                String level;

                if (isFault) {
                    value = thresholds[1][i] + random.nextDouble() * 5;
                    threshold = thresholds[1][i];
                    level = "HIGH";
                } else {
                    value = thresholds[0][i] - random.nextDouble() * 10;
                    threshold = thresholds[0][i];
                    level = "NORMAL";
                }

                // 将 deviceId 转为字符串以匹配 InfluxDB tag
                MetricDTO metric = new MetricDTO(deviceId.toString(), metricName, value, unit, Instant.now());
                batchMetrics.add(metric);
                metricsBuffer.add(metric);

                if ("HIGH".equals(level)) {
                    AlertDTO alert = new AlertDTO(deviceId.toString(), metricName, value, threshold.toString(), level, null);
                    System.out.println("Alert generated: " + alert);
                }
            }
        }
        
        if (!batchMetrics.isEmpty()) {
            try {
                influxDBService.writeMetrics(batchMetrics);
                System.out.println("Written " + batchMetrics.size() + " metrics to InfluxDB");
            } catch (Exception e) {
                System.err.println("Failed to write metrics to InfluxDB: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        if (metricsBuffer.size() > 1000) {
            metricsBuffer.clear();
        }
    }

    public List<MetricDTO> getMetricsBuffer() {
        return new ArrayList<>(metricsBuffer);
    }

    public void clearMetricsBuffer() {
        metricsBuffer.clear();
    }
}
