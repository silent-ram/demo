<template>
  <div class="monitoring">
    <el-row :gutter="20">
      <el-col v-for="device in deviceList" :key="device.id" :span="6">
        <el-card shadow="hover" class="device-card" :class="{ 'warning-card': device.faultProbability >= 0.7 }">
          <div class="device-header">
            <el-icon size="24"><Monitor /></el-icon>
            <span class="device-name">{{ device.name }}</span>
          </div>
          <div class="device-info">
            <p>编号：{{ device.deviceNo }}</p>
            <p>类型：{{ device.type }}</p>
          </div>
          <div class="fault-probability">
            <span>故障概率</span>
            <el-progress
              :percentage="device.faultProbability * 100"
              :color="getProgressColor(device.faultProbability)"
              :format="() => (device.faultProbability * 100).toFixed(1) + '%'"
            />
          </div>
          <div class="metrics">
            <div class="metric-item">
              <el-icon><Sunset /></el-icon>
              <span>{{ device.temperature?.toFixed(1) || '--' }}°C</span>
            </div>
            <div class="metric-item">
              <el-icon><Stopwatch /></el-icon>
              <span>{{ device.vibration?.toFixed(2) || '--' }} mm/s</span>
            </div>
            <div class="metric-item">
              <el-icon><Odometer /></el-icon>
              <span>{{ device.pressure?.toFixed(2) || '--' }} MPa</span>
            </div>
          </div>
          <div class="device-status">
            <el-tag :type="getStatusType(device.status)" size="small">
              {{ getStatusText(device.status) }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElNotification } from 'element-plus'
import { getDeviceList } from '@/api/device'
import { getLatestMetric } from '@/api/collector'
import { useAlertStore } from '@/stores/alert'

const alertStore = useAlertStore()
const deviceList = ref([])
let ws = null
let refreshTimer = null

async function loadDevices() {
  try {
    const res = await getDeviceList({ page: 1, size: 100 })
    const devices = res.data.records || []

    for (const device of devices) {
      try {
        const metricRes = await getLatestMetric(device.id)
        const metricData = metricRes.data || metricRes
        device.faultProbability = metricData.faultProbability || metricData.faultProbability || 0
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
  ws = new WebSocket('ws://localhost:8084/ws/alert')
  
  ws.onopen = () => {
    console.log('WebSocket 连接成功')
    alertStore.setWsConnected(true)
  }
  
  ws.onmessage = (event) => {
    console.log('收到告警消息:', event.data)
    ElNotification({
      title: '新告警',
      message: event.data,
      type: 'warning',
      duration: 5000
    })
    loadDevices()
  }
  
  ws.onerror = (error) => {
    console.error('WebSocket 错误:', error)
    alertStore.setWsConnected(false)
  }
  
  ws.onclose = () => {
    console.log('WebSocket 连接关闭')
    alertStore.setWsConnected(false)
    setTimeout(connectWebSocket, 5000)
  }
}

function getProgressColor(probability) {
  if (probability >= 0.9) return '#F56C6C'
  if (probability >= 0.7) return '#E6A23C'
  return '#67C23A'
}

function getStatusType(status) {
  const map = { 'NORMAL': 'success', 'WARNING': 'warning', 'FAULT': 'danger', 'OFFLINE': 'info' }
  return map[status] || 'info'
}

function getStatusText(status) {
  const map = { 'NORMAL': '正常', 'WARNING': '预警', 'FAULT': '故障', 'OFFLINE': '离线' }
  return map[status] || status
}

onMounted(() => {
  loadDevices()
  connectWebSocket()
  refreshTimer = setInterval(loadDevices, 30000)
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
.device-card {
  margin-bottom: 20px;
  transition: all 0.3s;
}

.device-card.warning-card {
  border-color: #E6A23C;
  box-shadow: 0 0 10px rgba(230, 162, 60, 0.3);
}

.device-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 15px;
}

.device-name {
  font-size: 16px;
  font-weight: bold;
}

.device-info {
  font-size: 12px;
  color: #909399;
  margin-bottom: 15px;
}

.device-info p {
  margin: 5px 0;
}

.fault-probability {
  margin-bottom: 15px;
}

.fault-probability span {
  font-size: 12px;
  color: #606266;
  margin-bottom: 5px;
  display: block;
}

.metrics {
  display: flex;
  justify-content: space-around;
  padding: 10px 0;
  border-top: 1px solid #EBEEF5;
  border-bottom: 1px solid #EBEEF5;
  margin-bottom: 15px;
}

.metric-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  font-size: 12px;
}

.device-status {
  text-align: center;
}
</style>
