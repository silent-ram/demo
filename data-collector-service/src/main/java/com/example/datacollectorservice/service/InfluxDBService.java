package com.example.datacollectorservice.service;

import com.example.datacollectorservice.dto.DeviceDTO;
import com.example.datacollectorservice.dto.MetricDTO;
import com.example.datacollectorservice.dto.PredictRequest;
import com.example.datacollectorservice.dto.PredictResponse;
import com.example.datacollectorservice.feign.DeviceServiceClient;
import com.example.datacollectorservice.feign.MlServiceClient;
import com.example.datacollectorservice.exception.Result;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApi;
import com.influxdb.client.QueryApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InfluxDBService {

    private static final Logger log = LoggerFactory.getLogger(InfluxDBService.class);

    @Autowired
    private InfluxDBClient influxDBClient;

    @Autowired
    private MlServiceClient mlServiceClient;

    @Autowired
    private DeviceServiceClient deviceServiceClient;

    @Value("${influxdb.bucket}")
    private String bucket;

    @Value("${influxdb.org}")
    private String org;

    @Value("${influxdb.url}")
    private String url;

    private static final String MEASUREMENT = "sensor_data";

    public void writeMetric(MetricDTO metric) {
        Point point = Point.measurement(MEASUREMENT)
                .addTag("device_id", metric.getDeviceId())
                .addTag("metric_name", metric.getMetricName())
                .addField("value", metric.getValue())
                .addField("unit", metric.getUnit() != null ? metric.getUnit() : "")
                .time(metric.getTimestamp(), WritePrecision.MS);

        try (WriteApi writeApi = influxDBClient.getWriteApi()) {
            writeApi.writePoint(bucket, org, point);
        }
        log.info("Metric written to InfluxDB: {} - {} = {}", metric.getDeviceId(), metric.getMetricName(), metric.getValue());
    }

    public void writeMetrics(List<MetricDTO> metrics) {
        writeMetrics(metrics, null);
    }

    public void writeMetrics(List<MetricDTO> metrics, Double faultProbability) {
        log.info("Attempting to write {} metrics to InfluxDB", metrics.size());

        List<Point> points = new ArrayList<>();
        for (MetricDTO metric : metrics) {
            Point point = Point.measurement(MEASUREMENT)
                    .addTag("device_id", metric.getDeviceId())
                    .addTag("metric_name", metric.getMetricName())
                    .addField("value", metric.getValue())
                    .addField("unit", metric.getUnit() != null ? metric.getUnit() : "")
                    .time(metric.getTimestamp(), WritePrecision.MS);
            points.add(point);
        }

        // 新增：写入故障概率
        if (faultProbability != null && !metrics.isEmpty()) {
            String deviceId = metrics.get(0).getDeviceId();
            Instant timestamp = metrics.get(0).getTimestamp();
            Point probaPoint = Point.measurement(MEASUREMENT)
                    .addTag("device_id", deviceId)
                    .addTag("metric_name", "fault_probability")
                    .addField("value", faultProbability)
                    .time(timestamp, WritePrecision.MS);
            points.add(probaPoint);
            log.debug("写入故障概率: device={}, probability={}", deviceId, faultProbability);
        }

        try (WriteApi writeApi = influxDBClient.getWriteApi()) {
            writeApi.writePoints(bucket, org, points);
            log.info("Batch metrics written to InfluxDB: {} records", metrics.size());
        } catch (Exception e) {
            log.error("Error writing to InfluxDB", e);
            throw e;
        }
    }

    public List<MetricDTO> queryMetrics(String deviceId, Instant startTime, Instant endTime) {
        String flux = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: %s, stop: %s) " +
                "|> filter(fn: (r) => r[\"_measurement\"] == \"%s\") " +
                "|> filter(fn: (r) => r[\"device_id\"] == \"%s\") " +
                "|> filter(fn: (r) => r[\"_field\"] == \"value\") " +
                "|> pivot(rowKey: [\"_time\"], columnKey: [\"metric_name\"], valueColumn: \"_value\")",
                bucket,
                startTime.toString(),
                endTime.toString(),
                MEASUREMENT,
                deviceId
        );

        QueryApi queryApi = influxDBClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(flux, org);
        List<MetricDTO> results = new ArrayList<>();

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                MetricDTO metric = new MetricDTO();
                metric.setDeviceId(deviceId);
                metric.setTimestamp(record.getTime());

                Object temp = record.getValueByKey("temperature");
                if (temp instanceof Number) metric.setTemperature(((Number) temp).doubleValue());

                Object vib = record.getValueByKey("vibration");
                if (vib instanceof Number) metric.setVibration(((Number) vib).doubleValue());

                Object press = record.getValueByKey("pressure");
                if (press instanceof Number) metric.setPressure(((Number) press).doubleValue());

                results.add(metric);
            }
        }

        return results;
    }

    public List<MetricDTO> queryMetricsByType(String deviceId, String metricName, Instant startTime, Instant endTime) {
        String flux = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: %s, stop: %s) " +
                "|> filter(fn: (r) => r[\"_measurement\"] == \"%s\") " +
                "|> filter(fn: (r) => r[\"device_id\"] == \"%s\") " +
                "|> filter(fn: (r) => r[\"metric_name\"] == \"%s\") " +
                "|> pivot(rowKey: [\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\")",
                bucket,
                startTime.toString(),
                endTime.toString(),
                MEASUREMENT,
                deviceId,
                metricName
        );

        QueryApi queryApi = influxDBClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(flux, org);
        List<MetricDTO> results = new ArrayList<>();

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                MetricDTO metric = new MetricDTO();
                metric.setDeviceId((String) record.getValueByKey("device_id"));
                metric.setMetricName((String) record.getValueByKey("metric_name"));
                Object valueObj = record.getValueByKey("value");
                if (valueObj instanceof Number) {
                    metric.setValue(((Number) valueObj).doubleValue());
                }
                metric.setUnit((String) record.getValueByKey("unit"));
                metric.setTimestamp(record.getTime());
                results.add(metric);
            }
        }

        return results;
    }

    public MetricDTO getLatestMetric(String deviceId) {
        log.debug("getLatestMetric called for device: {}", deviceId);
        List<MetricDTO> metrics = getLatestMetricsForDevice(deviceId);

        if (metrics == null || metrics.isEmpty()) {
            log.warn("No metrics found for device: {}", deviceId);
            return null;
        }

        log.debug("Found {} metrics for device: {}", metrics.size(), deviceId);

        MetricDTO result = new MetricDTO();
        result.setDeviceId(deviceId);

        for (MetricDTO m : metrics) {
            String name = m.getMetricName();
            Double value = m.getValue();

            if ("temperature".equals(name)) {
                result.setTemperature(value);
            } else if ("vibration".equals(name)) {
                result.setVibration(value);
            } else if ("pressure".equals(name)) {
                result.setPressure(value);
            }
        }

        // 设置时间戳为最新一条记录的时间
        result.setTimestamp(metrics.get(0).getTimestamp());

        // 调用 ML 服务进行预测（带上正确的设备类型）
        if (result.getTemperature() != null && result.getVibration() != null && result.getPressure() != null) {
            try {
                // 从 device-service 获取设备类型
                String deviceType = "工业机器人"; // 默认fallback
                try {
                    Result<DeviceDTO> deviceResult = deviceServiceClient.getDevice(Long.valueOf(deviceId));
                    if (deviceResult != null && deviceResult.getData() != null && deviceResult.getData().getType() != null) {
                        deviceType = deviceResult.getData().getType();
                    }
                } catch (Exception ex) {
                    log.warn("Failed to get device type for {}, using default", deviceId);
                }

                PredictRequest request = new PredictRequest();
                request.setDeviceId(deviceId);
                request.setDeviceType(deviceType);
                request.setTemperature(result.getTemperature());
                request.setVibration(result.getVibration());
                request.setPressure(result.getPressure());

                PredictResponse response = mlServiceClient.predict(request);
                if (response != null && response.getData() != null && response.getData().getFaultProbability() != null) {
                    result.setFaultProbability(response.getData().getFaultProbability());
                    log.info("Prediction for device {} (type={}): {}", deviceId, deviceType, response.getData().getFaultProbability());
                }
            } catch (Exception e) {
                log.error("Error calling ML service for device {}", deviceId, e);
                result.setFaultProbability(0.0);
            }
        }

        return result;
    }

    public List<MetricDTO> getLatestMetricsForDevice(String deviceId) {
        log.debug("=== getLatestMetricsForDevice called for device: {} ===", deviceId);
        // 分别查询每种指标，避免 pivot 的类型冲突问题
        String[] metricNames = {"temperature", "vibration", "pressure"};
        List<MetricDTO> results = new ArrayList<>();

        for (String metricName : metricNames) {
            String flux = String.format(
                    "from(bucket: \"%s\") " +
                    "|> range(start: -1h) " +
                    "|> filter(fn: (r) => r[\"_measurement\"] == \"%s\") " +
                    "|> filter(fn: (r) => r[\"device_id\"] == \"%s\") " +
                    "|> filter(fn: (r) => r[\"metric_name\"] == \"%s\") " +
                    "|> filter(fn: (r) => r[\"_field\"] == \"value\") " +
                    "|> last()",
                    bucket,
                    MEASUREMENT,
                    deviceId,
                    metricName
            );

            try {
                QueryApi queryApi = influxDBClient.getQueryApi();
                List<FluxTable> tables = queryApi.query(flux, org);

                for (FluxTable table : tables) {
                    for (FluxRecord record : table.getRecords()) {
                        MetricDTO metric = new MetricDTO();
                        metric.setDeviceId(deviceId);
                        metric.setMetricName(metricName);
                        Object valueObj = record.getValueByKey("_value");
                        if (valueObj instanceof Number) {
                            metric.setValue(((Number) valueObj).doubleValue());
                        }
                        metric.setUnit((String) record.getValueByKey("unit"));
                        metric.setTimestamp(record.getTime());
                        results.add(metric);
                    }
                }
            } catch (Exception e) {
                log.error("Error querying metric {} for device {}", metricName, deviceId, e);
            }
        }

        log.debug("Returning {} metrics for device: {}", results.size(), deviceId);
        return results;
    }

    public List<MetricDTO> getAllMetrics() {
        String flux = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: -24h) " +
                "|> filter(fn: (r) => r[\"_measurement\"] == \"%s\") " +
                "|> pivot(rowKey: [\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\") " +
                "|> limit(n: 1000)",
                bucket,
                MEASUREMENT
        );

        QueryApi queryApi = influxDBClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(flux, org);
        List<MetricDTO> results = new ArrayList<>();

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                MetricDTO metric = new MetricDTO();
                metric.setDeviceId((String) record.getValueByKey("device_id"));
                metric.setMetricName((String) record.getValueByKey("metric_name"));
                Object valueObj = record.getValueByKey("value");
                if (valueObj instanceof Number) {
                    metric.setValue(((Number) valueObj).doubleValue());
                }
                metric.setUnit((String) record.getValueByKey("unit"));
                metric.setTimestamp(record.getTime());
                results.add(metric);
            }
        }

        return results;
    }

    public void clearMetrics() {
        String flux = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: -30d) " +
                "|> filter(fn: (r) => r[\"_measurement\"] == \"%s\") " +
                "|> delete()",
                bucket,
                MEASUREMENT
        );

        QueryApi queryApi = influxDBClient.getQueryApi();
        queryApi.query(flux, org);
        log.info("All metrics cleared from InfluxDB");
    }

    /**
     * 查询设备故障概率历史
     */
    public List<Map<String, Object>> queryFaultProbabilityHistory(String deviceId, int hours) {
        String startTime = java.time.Instant.now().minusSeconds(hours * 3600).toString();
        String flux = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: %s) " +
                "|> filter(fn: (r) => r[\"_measurement\"] == \"%s\") " +
                "|> filter(fn: (r) => r[\"device_id\"] == \"%s\") " +
                "|> filter(fn: (r) => r[\"metric_name\"] == \"fault_probability\") " +
                "|> filter(fn: (r) => r[\"_field\"] == \"value\") " +
                "|> aggregateWindow(every: 5m, fn: mean) " +
                "|> yield(name: \"mean\")",
                bucket, startTime, MEASUREMENT, deviceId
        );

        QueryApi queryApi = influxDBClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(flux, org);
        List<Map<String, Object>> results = new ArrayList<>();

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Map<String, Object> point = new HashMap<>();
                point.put("timestamp", record.getTime().toString());
                Object value = record.getValueByKey("_value");
                point.put("value", value instanceof Number ? ((Number) value).doubleValue() : 0.0);
                results.add(point);
            }
        }

        log.info("查询到设备 {} 的 {} 条故障概率历史记录", deviceId, results.size());
        return results;
    }

    public void clearDeviceMetrics(String deviceId) {
        String flux = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: -30d) " +
                "|> filter(fn: (r) => r[\"_measurement\"] == \"%s\") " +
                "|> filter(fn: (r) => r[\"device_id\"] == \"%s\") " +
                "|> delete()",
                bucket,
                MEASUREMENT,
                deviceId
        );

        QueryApi queryApi = influxDBClient.getQueryApi();
        queryApi.query(flux, org);
        log.info("Metrics cleared for device: {}", deviceId);
    }
}
