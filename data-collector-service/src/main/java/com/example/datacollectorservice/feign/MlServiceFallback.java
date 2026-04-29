package com.example.datacollectorservice.feign;

import com.example.datacollectorservice.dto.PredictRequest;
import com.example.datacollectorservice.dto.PredictResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ML 服务熔断降级处理类
 *
 * 当 ml-service 不可用时，降级为基于阈值的规则计算故障概率。
 * 规则与 FeatureExtractor 中各设备类型的阈值配置保持一致。
 */
@Component
public class MlServiceFallback implements MlServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MlServiceFallback.class);

    @Override
    public PredictResponse predict(PredictRequest request) {
        log.warn("ML Service unavailable, using threshold-based fallback for device: {}", request.getDeviceId());

        double temperature = request.getTemperature() != null ? request.getTemperature() : 0;
        double vibration = request.getVibration() != null ? request.getVibration() : 0;
        double pressure = request.getPressure() != null ? request.getPressure() : 0;
        String deviceType = request.getDeviceType() != null ? request.getDeviceType() : "工业机器人";

        // 基于设备类型的阈值规则（与 DEVICE_PROFILES 保持一致）
        double faultProbability = calculateFallbackProbability(
                deviceType, temperature, vibration, pressure);

        PredictResponse response = new PredictResponse();
        response.setSuccess(true);
        response.setMessage("ML Service fallback - threshold based prediction");

        PredictResponse.PredictionData data = new PredictResponse.PredictionData();
        data.setFaultProbability(faultProbability);
        data.setIsFault(faultProbability >= 0.7);
        response.setData(data);

        return response;
    }

    /**
     * 基于设备类型的阈值规则计算故障概率（降级策略）
     */
    private double calculateFallbackProbability(String deviceType,
                                                 double temperature,
                                                 double vibration,
                                                 double pressure) {
        double score = 0.0;

        // 各设备类型的阈值配置（与 Python 端 DEVICE_PROFILES 保持一致）
        switch (deviceType) {
            case "工业机器人":
                if (temperature > 80) score += 0.4;
                if (vibration > 0.6) score += 0.3;
                if (pressure > 130) score += 0.3;
                break;
            case "数控机床":
                if (temperature > 80) score += 0.4;
                if (vibration > 0.6) score += 0.3;
                if (pressure > 130) score += 0.3;
                break;
            case "输送设备":
                if (temperature > 60) score += 0.4;
                if (vibration > 0.5) score += 0.3;
                if (pressure > 100) score += 0.3;
                break;
            case "焊接设备":
                if (temperature > 100) score += 0.4;
                if (vibration > 0.5) score += 0.3;
                if (pressure > 140) score += 0.3;
                break;
            case "压力设备":
                if (temperature > 90) score += 0.4;
                if (vibration > 0.5) score += 0.3;
                if (pressure > 150) score += 0.3;
                break;
            case "包装设备":
                if (temperature > 50) score += 0.4;
                if (vibration > 0.4) score += 0.3;
                if (pressure > 90) score += 0.3;
                break;
            default:
                // 未知设备类型使用保守默认
                if (temperature > 80) score += 0.4;
                if (vibration > 0.6) score += 0.3;
                if (pressure > 130) score += 0.3;
                break;
        }

        return Math.min(score, 0.95);
    }
}
