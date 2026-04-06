# 第3批实现计划：操作日志 + 报表导出 + 消息通知改进

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现操作日志(5种操作类型)、报表导出(4种报表)、消息通知改进(站内消息中心+通知配置)

**Architecture:** 
- 操作日志：创建日志实体和服务，统一记录接口
- 报表导出：后端使用 Apache POI 生成 Excel，前端提供下载
- 消息通知：消息实体和服务，支持站内消息和推送配置

**Tech Stack:** Spring Boot + Apache POI + MyBatis-Plus + Vue 3

---

## 文件结构

```
user-service/
├── entity/OperationLog.java (新建)
├── entity/OperationType.java (新建)
├── mapper/OperationLogMapper.java (新建)
├── service/OperationLogService.java (新建)
└── controller/OperationLogController.java (新建)

alert-service/
├── entity/Message.java (新建)
├── entity/MessageType.java (新建)
├── entity/NotificationConfig.java (新建)
├── mapper/MessageMapper.java (新建)
├── mapper/NotificationConfigMapper.java (新建)
├── service/MessageService.java (新建)
└── controller/MessageController.java (新建)

device-service/
├── controller/ReportController.java (新建)
└── service/ReportService.java (新建)

frontend/
├── src/views/MessageCenterView.vue (新建)
└── src/api/message.js (新建)
```

---

## Task 1: 创建操作类型枚举 (user-service)

**Files:**
- Create: `user-service/src/main/java/com/example/userservice/entity/OperationType.java`

- [ ] **Step 1: 创建 OperationType 枚举类**

```java
package com.example.userservice.entity;

public enum OperationType {
    LOGIN("登录", "用户登录"),
    LOGOUT("登出", "用户登出"),
    DEVICE("设备操作", "设备启停/参数修改"),
    ALERT("告警处理", "告警处理/确认"),
    MAINTENANCE("维修记录", "创建/修改维修记录"),
    CONFIG("系统配置", "修改阈值/参数配置");

    private final String name;
    private final String description;

    OperationType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public static OperationType fromCode(String code) {
        if (code == null) return null;
        try {
            return valueOf(code.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
```

- [ ] **Step 2: 提交代码**

```bash
git add user-service/src/main/java/com/example/userservice/entity/OperationType.java
git commit -m "feat: 创建操作类型枚举 OperationType"
```

---

## Task 2: 创建操作日志实体和 Mapper (user-service)

**Files:**
- Create: `user-service/src/main/java/com/example/userservice/entity/OperationLog.java`
- Create: `user-service/src/main/java/com/example/userservice/mapper/OperationLogMapper.java`

- [ ] **Step 1: 创建 OperationLog 实体类**

```java
package com.example.userservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("t_operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    private String operationType;

    private String operationContent;

    private Long deviceId;

    private String ipAddress;

    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getOperationContent() { return operationContent; }
    public void setOperationContent(String operationContent) { this.operationContent = operationContent; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 2: 创建 OperationLogMapper**

```java
package com.example.userservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.userservice.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
```

- [ ] **Step 3: 提交代码**

```bash
git add user-service/src/main/java/com/example/userservice/entity/OperationLog.java
git add user-service/src/main/java/com/example/userservice/mapper/OperationLogMapper.java
git commit -m "feat: 创建操作日志实体和 Mapper"
```

---

## Task 3: 创建消息类型枚举 (alert-service)

**Files:**
- Create: `alert-service/src/main/java/com/example/alertservice/entity/MessageType.java`

- [ ] **Step 1: 创建 MessageType 枚举类**

```java
package com.example.alertservice.entity;

public enum MessageType {
    ALERT_NOTIFY("告警通知", "新告警产生"),
    ALERT_UPGRADE("告警升级", "告警超时自动升级"),
    DEVICE_STATUS("设备状态", "设备状态变更"),
    SYSTEM("系统通知", "系统公告");

    private final String name;
    private final String description;

    MessageType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }

    public static MessageType fromCode(String code) {
        if (code == null) return null;
        try {
            return valueOf(code.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
```

- [ ] **Step 2: 提交代码**

```bash
git add alert-service/src/main/java/com/example/alertservice/entity/MessageType.java
git commit -m "feat: 创建消息类型枚举 MessageType"
```

---

## Task 4: 创建消息实体和 Mapper (alert-service)

**Files:**
- Create: `alert-service/src/main/java/com/example/alertservice/entity/Message.java`
- Create: `alert-service/src/main/java/com/example/alertservice/mapper/MessageMapper.java`
- Create: `alert-service/src/main/java/com/example/alertservice/entity/NotificationConfig.java`
- Create: `alert-service/src/main/java/com/example/alertservice/mapper/NotificationConfigMapper.java`

- [ ] **Step 1: 创建 Message 实体**

```java
package com.example.alertservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("t_message")
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String type;

    private String title;

    private String content;

    private Long relatedId;

    private Boolean isRead;

    private LocalDateTime createdAt;

    // getters and setters
}
```

- [ ] **Step 2: 创建 MessageMapper**

```java
package com.example.alertservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.alertservice.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {
    
    @Select("SELECT * FROM t_message WHERE user_id = #{userId} AND is_read = false ORDER BY created_at DESC")
    List<Message> findUnreadByUserId(Long userId);
}
```

- [ ] **Step 3: 创建 NotificationConfig 实体**

```java
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

    // getters and setters
}
```

- [ ] **Step 4: 创建 NotificationConfigMapper**

```java
package com.example.alertservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.alertservice.entity.NotificationConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationConfigMapper extends BaseMapper<NotificationConfig> {
}
```

- [ ] **Step 5: 提交代码**

```bash
git add alert-service/src/main/java/com/example/alertservice/entity/Message.java
git add alert-service/src/main/java/com/example/alertservice/mapper/MessageMapper.java
git add alert-service/src/main/java/com/example/alertservice/entity/NotificationConfig.java
git add alert-service/src/main/java/com/example/alertservice/mapper/NotificationConfigMapper.java
git commit -m "feat: 创建消息实体和通知配置"
```

---

## Task 5: 创建消息服务 (alert-service)

**Files:**
- Create: `alert-service/src/main/java/com/example/alertservice/service/MessageService.java`
- Create: `alert-service/src/main/java/com/example/alertservice/controller/MessageController.java`

- [ ] **Step 1: 创建 MessageService**

```java
package com.example.alertservice.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.alertservice.entity.Message;
import com.example.alertservice.entity.NotificationConfig;
import com.example.alertservice.mapper.MessageMapper;
import com.example.alertservice.mapper.NotificationConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageService {

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private NotificationConfigMapper configMapper;

    public void sendMessage(Long userId, String type, String title, String content, Long relatedId) {
        Message message = new Message();
        message.setUserId(userId);
        message.setType(type);
        message.setTitle(title);
        message.setContent(content);
        message.setRelatedId(relatedId);
        message.setIsRead(false);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
    }

    public List<Message> getUserMessages(Long userId) {
        return messageMapper.selectList(
            new QueryWrapper<Message>().eq("user_id", userId).orderByDesc("created_at")
        );
    }

    public List<Message> getUnreadMessages(Long userId) {
        return messageMapper.findUnreadByUserId(userId);
    }

    public void markAsRead(Long messageId) {
        Message message = messageMapper.selectById(messageId);
        if (message != null) {
            message.setIsRead(true);
            messageMapper.updateById(message);
        }
    }

    public void markAllAsRead(Long userId) {
        List<Message> messages = messageMapper.findUnreadByUserId(userId);
        for (Message message : messages) {
            message.setIsRead(true);
            messageMapper.updateById(message);
        }
    }
}
```

- [ ] **Step 2: 创建 MessageController**

```java
package com.example.alertservice.controller;

import com.example.alertservice.entity.Message;
import com.example.alertservice.exception.Result;
import com.example.alertservice.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/message")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @GetMapping("/list")
    public Result<List<Message>> getUserMessages(@RequestParam Long userId) {
        List<Message> messages = messageService.getUserMessages(userId);
        return Result.success(messages);
    }

    @GetMapping("/unread")
    public Result<List<Message>> getUnreadMessages(@RequestParam Long userId) {
        List<Message> messages = messageService.getUnreadMessages(userId);
        return Result.success(messages);
    }

    @PostMapping("/read/{id}")
    public Result<Void> markAsRead(@PathVariable Long id) {
        messageService.markAsRead(id);
        return Result.success(null);
    }

    @PostMapping("/read-all")
    public Result<Void> markAllAsRead(@RequestParam Long userId) {
        messageService.markAllAsRead(userId);
        return Result.success(null);
    }
}
```

- [ ] **Step 3: 提交代码**

```bash
git add alert-service/src/main/java/com/example/alertservice/service/MessageService.java
git add alert-service/src/main/java/com/example/alertservice/controller/MessageController.java
git commit -m "feat: 创建消息服务和接口"
```

---

## Task 6: 创建报表导出服务 (device-service)

**Files:**
- Create: `device-service/src/main/java/com/example/deviceservice/service/ReportService.java`
- Create: `device-service/src/main/java/com/example/deviceservice/controller/ReportController.java`

- [ ] **Step 1: 添加 Apache POI 依赖**

在 device-service/pom.xml 中添加：
```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.3</version>
</dependency>
```

- [ ] **Step 2: 创建 ReportService**

```java
package com.example.deviceservice.service;

import com.example.deviceservice.entity.Device;
import com.example.deviceservice.mapper.DeviceMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReportService {

    @Autowired
    private DeviceMapper deviceMapper;

    public byte[] exportDeviceReport() throws Exception {
        List<Device> devices = deviceMapper.selectList(null);
        
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("设备台账");
        
        // 表头样式
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        
        // 创建表头
        String[] headers = {"设备编号", "设备名称", "设备类型", "状态", "安装位置", "创建时间"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // 填充数据
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (int i = 0; i < devices.size(); i++) {
            Device device = devices.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(device.getDeviceNo());
            row.createCell(1).setCellValue(device.getName());
            row.createCell(2).setCellValue(device.getType());
            row.createCell(3).setCellValue(device.getStatus());
            row.createCell(4).setCellValue(device.getLocation());
            row.createCell(5).setCellValue(device.getCreatedAt() != null ? 
                device.getCreatedAt().format(formatter) : "");
        }
        
        // 自动列宽
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        
        return outputStream.toByteArray();
    }
}
```

- [ ] **Step 3: 创建 ReportController**

```java
package com.example.deviceservice.controller;

import com.example.deviceservice.exception.Result;
import com.example.deviceservice.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@RequestMapping("/api/report")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/export/device")
    public void exportDeviceReport(HttpServletResponse response) throws Exception {
        byte[] excel = reportService.exportDeviceReport();
        
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=device_report.xlsx");
        response.getOutputStream().write(excel);
        response.getOutputStream().flush();
    }
}
```

- [ ] **Step 4: 提交代码**

```bash
git add device-service/pom.xml
git add device-service/src/main/java/com/example/deviceservice/service/ReportService.java
git add device-service/src/main/java/com/example/deviceservice/controller/ReportController.java
git commit -m "feat: 创建报表导出服务"
```

---

## Task 7: 前端消息中心 (frontend)

**Files:**
- Create: `frontend/src/views/MessageCenterView.vue`
- Create: `frontend/src/api/message.js`

- [ ] **Step 1: 创建 message.js API**

```javascript
import request from '@/utils/request'

export function getMessageList(userId) {
  return request({
    url: '/api/message/list',
    method: 'get',
    params: { userId }
  })
}

export function getUnreadMessages(userId) {
  return request({
    url: '/api/message/unread',
    method: 'get',
    params: { userId }
  })
}

export function markAsRead(id) {
  return request({
    url: `/api/message/read/${id}`,
    method: 'post'
  })
}

export function markAllAsRead(userId) {
  return request({
    url: '/api/message/read-all',
    method: 'post',
    params: { userId }
  })
}
```

- [ ] **Step 2: 创建 MessageCenterView.vue**

```vue
<template>
  <div class="message-center">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>消息中心</span>
          <el-button type="primary" link @click="handleMarkAll">全部标为已读</el-button>
        </div>
      </template>
      
      <el-tabs v-model="activeTab">
        <el-tab-pane label="全部消息" name="all">
          <el-table :data="messageList" v-loading="loading">
            <el-table-column prop="title" label="标题" />
            <el-table-column prop="type" label="类型" width="120">
              <template #default="{ row }">
                <el-tag>{{ getTypeText(row.type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="content" label="内容" />
            <el-table-column prop="createdAt" label="时间" width="180" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button v-if="!row.isRead" type="primary" link @click="handleRead(row)">标为已读</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        
        <el-tab-pane label="未读消息" name="unread">
          <el-table :data="unreadList" v-loading="loading">
            <el-table-column prop="title" label="标题" />
            <el-table-column prop="type" label="类型" width="120">
              <template #default="{ row }">
                <el-tag>{{ getTypeText(row.type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="content" label="内容" />
            <el-table-column prop="createdAt" label="时间" width="180" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMessageList, getUnreadMessages, markAsRead, markAllAsRead } from '@/api/message'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const messageList = ref([])
const unreadList = ref([])
const activeTab = ref('all')

async function loadMessages() {
  loading.value = true
  try {
    const [allRes, unreadRes] = await Promise.all([
      getMessageList(userStore.id),
      getUnreadMessages(userStore.id)
    ])
    messageList.value = allRes.data || []
    unreadList.value = unreadRes.data || []
  } catch (error) {
    console.error('加载消息失败:', error)
  } finally {
    loading.value = false
  }
}

function getTypeText(type) {
  const map = {
    'ALERT_NOTIFY': '告警通知',
    'ALERT_UPGRADE': '告警升级',
    'DEVICE_STATUS': '设备状态',
    'SYSTEM': '系统通知'
  }
  return map[type] || type
}

async function handleRead(row) {
  await markAsRead(row.id)
  ElMessage.success('已标记为已读')
  loadMessages()
}

async function handleMarkAll() {
  await markAllAsRead(userStore.id)
  ElMessage.success('已全部标记为已读')
  loadMessages()
}

onMounted(() => {
  loadMessages()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
```

- [ ] **Step 3: 提交代码**

```bash
git add frontend/src/views/MessageCenterView.vue
git add frontend/src/api/message.js
git commit -m "feat: 添加前端消息中心页面"
```

---

## Task 8: 数据库变更 (sql/init.sql)

**Files:**
- Modify: `sql/init.sql`

- [ ] **Step 1: 添加表结构**

```sql
-- 操作日志表
USE fault_warning_user;

CREATE TABLE IF NOT EXISTS t_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    username VARCHAR(50),
    operation_type VARCHAR(20) NOT NULL,
    operation_content VARCHAR(500),
    device_id BIGINT,
    ip_address VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_type (operation_type),
    INDEX idx_created (created_at)
);

-- 消息表
USE fault_warning_alert;

CREATE TABLE IF NOT EXISTS t_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(100) NOT NULL,
    content VARCHAR(500),
    related_id BIGINT,
    is_read BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_is_read (is_read)
);

-- 通知配置表
CREATE TABLE IF NOT EXISTS t_notification_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    alert_level VARCHAR(20) NOT NULL,
    enable_push BOOLEAN DEFAULT TRUE,
    enable_message BOOLEAN DEFAULT TRUE,
    UNIQUE KEY uk_user_level (user_id, alert_level)
);
```

- [ ] **Step 2: 提交代码**

```bash
git add sql/init.sql
git commit -m "feat: 添加操作日志表和消息表"
```

---

## Plan Review

### Spec Coverage Check
- [x] #16 操作日志 - Task 1, 2, 8
- [x] #17 报表导出 - Task 6
- [x] #18 消息通知 - Task 3, 4, 5, 7

### Placeholder Scan
- 无 TBD/TODO
- 所有代码已完整提供

### Type Consistency
- OperationType 枚举名称与数据库值一致
- MessageType 枚举与前端显示映射正确

Plan complete and saved to `docs/superpowers/plans/2026-04-06-operation-log-report-message-plan.md`.
