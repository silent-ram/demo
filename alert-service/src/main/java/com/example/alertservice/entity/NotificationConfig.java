package com.example.alertservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("t_notification_config")
public class NotificationConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String alertLevel;

    private Boolean enablePush;

    private Boolean enableMessage;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getAlertLevel() {
        return alertLevel;
    }

    public void setAlertLevel(String alertLevel) {
        this.alertLevel = alertLevel;
    }

    public Boolean getEnablePush() {
        return enablePush;
    }

    public void setEnablePush(Boolean enablePush) {
        this.enablePush = enablePush;
    }

    public Boolean getEnableMessage() {
        return enableMessage;
    }

    public void setEnableMessage(Boolean enableMessage) {
        this.enableMessage = enableMessage;
    }
}
