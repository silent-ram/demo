<template>
  <div class="monitoring">
    <el-row :gutter="20">
      <el-col v-for="device in deviceList" :key="device.id" :span="6">
        <div
          class="device-card"
          :class="{ 'warning-card': device.faultProbability >= 0.7, 'danger-card': device.faultProbability >= 0.9 }"
          @click="goToDetail(device.id)"
        >
          <div class="device-header">
            <div class="device-icon-wrap">
              <el-icon size="22"><Monitor /></el-icon>
            </div>
            <span class="device-name">{{ device.name }}</span>
          </div>

          <div class="device-info">
            <p class="info-item">
              <span class="info-label">编号</span>
              <span class="info-value mono-text">{{ device.deviceNo }}</span>
            </p>
            <p class="info-item">
              <span class="info-label">类型</span>
              <span class="info-value">{{ device.type || '--' }}</span>
            </p>
          </div>

          <div class="fault-section">
            <div class="fault-header">
              <span class="fault-label">故障概率</span>
              <span class="fault-value" :class="getFaultClass(device.faultProbability)">
                {{ (device.faultProbability * 100).toFixed(1) }}%
              </span>
            </div>
            <div class="fault-bar">
              <div
                class="fault-bar-fill"
                :style="{ width: (device.faultProbability * 100) + '%' }"
              ></div>
            </div>
          </div>

          <div class="metrics">
            <div class="metric-item">
              <el-icon class="metric-icon temp"><Sunset /></el-icon>
              <span class="metric-value">{{ device.temperature?.toFixed(1) || '--' }}</span>
              <span class="metric-unit">°C</span>
            </div>
            <div class="metric-item">
              <el-icon class="metric-icon vib"><Operation /></el-icon>
              <span class="metric-value">{{ device.vibration?.toFixed(2) || '--' }}</span>
              <span class="metric-unit">mm/s</span>
            </div>
            <div class="metric-item">
              <el-icon class="metric-icon pres"><Odometer /></el-icon>
              <span class="metric-value">{{ device.pressure?.toFixed(2) || '--' }}</span>
              <span class="metric-unit">MPa</span>
            </div>
          </div>

          <div class="device-footer">
            <el-tag :type="getStatusType(device.status)" size="small">
              {{ getStatusText(device.status) }}
            </el-tag>
            <span class="view-detail">查看详情 →</span>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElNotification } from 'element-plus'
import { getDeviceList, getMaintenancesByDevice } from '@/api/device'
import { getLatestMetric } from '@/api/collector'
import { useAlertStore } from '@/stores/alert'

const router = useRouter()
const alertStore = useAlertStore()
const deviceList = ref([])
let ws = null
let refreshTimer = null
const notifiedAlertIds = new Set()

async function loadDevices() {
  try {
    const res = await getDeviceList({ page: 1, size: 100 })
    const devices = res.data.records || []

    for (const device of devices) {
      try {
        const metricRes = await getLatestMetric(device.id)
        const metricData = metricRes.data || metricRes
        device.faultProbability = metricData.faultProbability || 0
        device.temperature = metricData.temperature
        device.vibration = metricData.vibration
        device.pressure = metricData.pressure
      } catch {
        device.faultProbability = 0
      }
    }

    deviceList.value = devices.sort((a, b) => b.faultProbability - a.faultProbability)
  } catch (error) {
    console.error('加载设备列表失败:', error)
  }
}

function connectWebSocket() {
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
    return
  }
  const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsPort = window.location.hostname === 'localhost' ? '8080' : window.location.port
  const wsHost = window.location.hostname + ':' + wsPort
  ws = new WebSocket(`${wsProtocol}//${wsHost}/ws/alert`)

  ws.onopen = () => {
    alertStore.setWsConnected(true)
  }

  ws.onmessage = async (event) => {
    try {
      const alert = typeof event.data === 'string' ? JSON.parse(event.data) : event.data
      const alertId = alert.id || alert.deviceId + '_' + alert.createdAt

      // 跨标签页去重：使用 localStorage 保证所有标签页共享同一状态
      const storageKey = 'alert_notified_' + alertId
      if (localStorage.getItem(storageKey)) {
        return
      }
      localStorage.setItem(storageKey, Date.now().toString())
      setTimeout(() => localStorage.removeItem(storageKey), 5 * 60 * 1000)

      const prob = (alert.faultProbability * 100 || 0).toFixed(1)

      let maintenanceInfo = ''
      if (alert.deviceId) {
        try {
          const mRes = await getMaintenancesByDevice(alert.deviceId)
          const records = mRes.data || []
          if (records.length > 0) {
            const latest = records[0]
            maintenanceInfo = '\n历史维修: ' + (latest.type || '未知') + ' - ' + (latest.description || '无描述')
          } else {
            maintenanceInfo = '\n历史维修: 暂无记录'
          }
        } catch (e) {
          maintenanceInfo = '\n历史维修: 查询失败'
        }
      }

      ElNotification({
        title: '【' + alert.alertLevel + '级告警】' + alert.deviceName,
        message: '故障概率: ' + prob + '%\n告警类型: ' + (alert.type || '未知') + '\n消息: ' + (alert.message || '无') + maintenanceInfo,
        type: 'warning',
        duration: 10000,
        showClose: true
      })
      loadDevices()
    } catch (e) {
      console.error('解析告警消息失败:', e)
    }
  }

  ws.onerror = (error) => {
    console.error('WebSocket 错误:', error)
    alertStore.setWsConnected(false)
  }

  ws.onclose = () => {
    alertStore.setWsConnected(false)
    setTimeout(connectWebSocket, 5000)
  }
}

function getFaultClass(probability) {
  if (probability >= 0.9) return 'danger'
  if (probability >= 0.7) return 'warning'
  return 'normal'
}

function getStatusType(status) {
  const map = {
    'NORMAL': 'success',
    'RUNNING': 'success',
    'STANDBY': 'info',
    'MAINTENANCE': 'warning',
    'FAULT': 'danger',
    'OFFLINE': 'info'
  }
  return map[status] || 'info'
}

function getStatusText(status) {
  const map = {
    'NORMAL': '正常运行',
    'RUNNING': '运行中',
    'STANDBY': '待机',
    'MAINTENANCE': '维护中',
    'FAULT': '故障',
    'OFFLINE': '离线'
  }
  return map[status] || status
}

function goToDetail(deviceId) {
  router.push(`/device/${deviceId}`)
}

onMounted(() => {
  loadDevices()
  connectWebSocket()
  refreshTimer = setInterval(loadDevices, 5000)
})

onUnmounted(() => {
  if (ws) {
    ws.close()
  }
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})
</script>

<style scoped>
.monitoring {
  padding: 4px;
}

.device-card {
  background: white;
  border: 1px solid rgba(45, 42, 38, 0.08);
  border-radius: 16px;
  padding: 20px;
  margin-bottom: 20px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(45, 42, 38, 0.04);
  transition: all 0.3s ease;
}

.device-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 35px rgba(45, 42, 38, 0.12);
}

.device-card.warning-card {
  border-color: rgba(244, 162, 97, 0.4);
}

.device-card.danger-card {
  border-color: rgba(214, 40, 40, 0.4);
}

.device-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.device-icon-wrap {
  width: 42px;
  height: 42px;
  background: linear-gradient(135deg, rgba(0, 119, 182, 0.1), rgba(0, 168, 232, 0.08));
  border: 1px solid rgba(0, 119, 182, 0.15);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #0077b6;
}

.device-name {
  font-size: 16px;
  font-weight: 600;
  color: #2d2a26;
}

.device-info {
  margin-bottom: 16px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  font-size: 13px;
}

.info-label {
  color: #9a948c;
}

.info-value {
  color: #5c5750;
}

.mono-text {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 12px;
}

.fault-section {
  margin-bottom: 16px;
}

.fault-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.fault-label {
  font-size: 12px;
  color: #9a948c;
}

.fault-value {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 16px;
  font-weight: 700;
}

.fault-value.normal { color: #2d936c; }
.fault-value.warning { color: #e85d04; }
.fault-value.danger { color: #d62828; }

.fault-bar {
  height: 6px;
  background: #f0f0f0;
  border-radius: 3px;
  overflow: hidden;
}

.fault-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.5s ease;
  background: linear-gradient(90deg, #2d936c, #3da87a);
}

.warning-card .fault-bar-fill {
  background: linear-gradient(90deg, #f4a261, #e89b3d);
}

.danger-card .fault-bar-fill {
  background: linear-gradient(90deg, #d62828, #e63939);
}

.metrics {
  display: flex;
  justify-content: space-around;
  padding: 14px 0;
  border-top: 1px solid rgba(45, 42, 38, 0.06);
  border-bottom: 1px solid rgba(45, 42, 38, 0.06);
  margin-bottom: 14px;
}

.metric-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.metric-icon {
  font-size: 16px;
  margin-bottom: 2px;
}

.metric-icon.temp { color: #f4a261; }
.metric-icon.vib { color: #0077b6; }
.metric-icon.pres { color: #e85d04; }

.metric-value {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 14px;
  font-weight: 600;
  color: #2d2a26;
}

.metric-unit {
  font-size: 10px;
  color: #9a948c;
}

.device-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.view-detail {
  font-size: 12px;
  color: #0077b6;
  transition: all 0.25s ease;
}

.device-card:hover .view-detail {
  color: #e85d04;
  transform: translateX(3px);
}
</style>
