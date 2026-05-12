package com.example.alertservice.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.alertservice.entity.Alert;
import com.example.alertservice.entity.Config;
import com.example.alertservice.enums.AlertLevel;
import com.example.alertservice.mapper.AlertMapper;
import com.example.alertservice.mapper.ConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警自动升级定时任务
 * 每分钟检查未解决的告警，根据配置的超时时间自动升级告警级别
 */
@Component
public class AlertUpgradeScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertUpgradeScheduler.class);

    @Autowired
    private AlertMapper alertMapper;

    @Autowired
    private ConfigMapper configMapper;

    /**
     * 配置键前缀
     */
    private static final String CONFIG_KEY_PREFIX = "alert.upgrade.";

    /**
     * 定时任务：每分钟执行一次，检查并升级告警
     */
    @Scheduled(cron = "0 * * * * ?")
    public void upgradeAlerts() {
        log.info("开始执行告警自动升级任务");

        // 获取所有未解决的告警
        List<Alert> unresolvedAlerts = alertMapper.findUnresolved();

        if (unresolvedAlerts == null || unresolvedAlerts.isEmpty()) {
            log.info("没有未解决的告警");
            return;
        }

        log.info("发现 {} 条未解决告警", unresolvedAlerts.size());

        for (Alert alert : unresolvedAlerts) {
            try {
                processAlertUpgrade(alert);
            } catch (Exception e) {
                log.error("处理告警升级失败，告警ID: {}, 错误: {}", alert.getId(), e.getMessage());
            }
        }

        log.info("告警自动升级任务执行完成");
    }

    /**
     * 处理单个告警的升级
     */
    private void processAlertUpgrade(Alert alert) {
        String currentLevel = alert.getAlertLevel();
        if (currentLevel == null) {
            currentLevel = "LOW";
        }

        // 获取当前级别的告警是否已经达到最高级别
        AlertLevel level = AlertLevel.fromValue(currentLevel);
        AlertLevel nextLevel = level.nextLevel();

        // 如果已经是最高级别，不再升级
        if (nextLevel == level && !level.equals(AlertLevel.LOW)) {
            log.debug("告警ID {} 已达到最高级别 {}", alert.getId(), currentLevel);
            return;
        }

        // 获取升级超时时间（小时）
        int timeoutHours = getUpgradeTimeoutHours(currentLevel);
        if (timeoutHours <= 0) {
            log.debug("告警ID {} 的级别 {} 未配置升级超时时间", alert.getId(), currentLevel);
            return;
        }

        // 检查告警创建时间是否超过超时时间
        LocalDateTime createdAt = alert.getCreatedAt();
        if (createdAt == null) {
            log.warn("告警ID {} 创建时间为空，跳过", alert.getId());
            return;
        }

        LocalDateTime timeLimit = LocalDateTime.now().minusHours(timeoutHours);

        // 如果告警创建时间在超时时间之前，则执行升级
        if (createdAt.isBefore(timeLimit)) {
            // 设置 previousLevel 为当前级别
            alert.setPreviousLevel(currentLevel);

            // 设置 alertLevel 为下一级
            alert.setAlertLevel(nextLevel.getValue());

            // 设置 upgradedAt 为当前时间
            alert.setUpgradedAt(LocalDateTime.now());

            alert.setUpdatedAt(LocalDateTime.now());

            alertMapper.updateById(alert);

            log.info("告警ID {} 已从 {} 升级到 {}", alert.getId(), currentLevel, nextLevel.getValue());
        }
    }

    /**
     * 从配置表获取各级别的升级超时时间（小时）
     */
    private int getUpgradeTimeoutHours(String level) {
        String configKey = CONFIG_KEY_PREFIX + level.toLowerCase() + ".hours";

        QueryWrapper<Config> wrapper = new QueryWrapper<>();
        wrapper.eq("config_key", configKey);
        Config config = configMapper.selectOne(wrapper);

        if (config == null || config.getValue() == null) {
            log.debug("未找到配置键: {}，使用默认值", configKey);
            // 返回默认值
            return getDefaultTimeoutHours(level);
        }

        try {
            return Integer.parseInt(config.getValue());
        } catch (NumberFormatException e) {
            log.warn("配置键 {} 的值不是有效的数字: {}", configKey, config.getValue());
            return getDefaultTimeoutHours(level);
        }
    }

    /**
     * 获取默认的升级超时时间（小时）
     */
    private int getDefaultTimeoutHours(String level) {
        switch (level.toUpperCase()) {
            case "LOW":
                return 24;  // LOW 级别默认 24 小时
            case "MEDIUM":
                return 12;  // MEDIUM 级别默认 12 小时
            case "HIGH":
                return 6;   // HIGH 级别默认 6 小时
            default:
                return 24;
        }
    }
}
