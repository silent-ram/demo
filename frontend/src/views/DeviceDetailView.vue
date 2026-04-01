<template>
  <div class="device-detail">
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <span>设备信息</span>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="设备编号">{{ device.deviceNo }}</el-descriptions-item>
            <el-descriptions-item label="设备名称">{{ device.name }}</el-descriptions-item>
            <el-descriptions-item label="设备类型">{{ device.type }}</el-descriptions-item>
            <el-descriptions-item label="安装位置">{{ device.location }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusType(device.status)">{{ getStatusText(device.status) }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card shadow="hover">
          <template #header>
            <span>实时传感器数据</span>
          </template>
          <div ref="chartRef" style="height: 300px;"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span>多维趋势图</span>
          </template>
          <div v-if="trendImage" class="trend-image">
            <img :src="'data:image/png;base64,' + trendImage" alt="趋势图" />
          </div>
          <el-empty v-else description="暂无趋势图数据" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span>维修记录</span>
          </template>
          <el-timeline v-if="maintenanceList.length > 0">
            <el-timeline-item
              v-for="item in maintenanceList"
              :key="item.id"
              :timestamp="item.repairedAt"
              placement="top"
            >
              <el-card>
                <h4>{{ item.type }}</h4>
                <p>{{ item.description }}</p>
                <p>处理措施：{{ item.actionTaken }}</p>
              </el-card>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无维修记录" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { getDevice } from '@/api/device'
import { getMaintenanceList } from '@/api/device'
import { getMetrics, getLatestMetric } from '@/api/collector'
import { getChart } from '@/api/alert'

const route = useRoute()
const chartRef = ref(null)
let chart = null
let refreshTimer = null

const device = ref({})
const maintenanceList = ref([])
const trendImage = ref('')
const metricsData = ref([])

async function loadDevice() {
  try {
    const res = await getDevice(route.params.id)
    device.value = res.data || {}
  } catch (error) {
    console.error('加载设备信息失败:', error)
  }
}

async function loadMaintenance() {
  try {
    const res = await getMaintenanceList({ page: 1, size: 10 })
    maintenanceList.value = (res.data.records || []).filter(m => m.deviceId == route.params.id)
  } catch (error) {
    console.error('加载维修记录失败:', error)
  }
}

async function loadTrendChart() {
  try {
    const res = await getChart(route.params.id)
    trendImage.value = res.data || ''
  } catch (error) {
    console.error('加载趋势图失败:', error)
  }
}

async function loadMetrics() {
  try {
    const res = await getMetrics(route.params.id, {})
    metricsData.value = res.data || []
    updateChart()
  } catch (error) {
    console.error('加载指标数据失败:', error)
  }
}

function initChart() {
  chart = echarts.init(chartRef.value)
  const option = {
    tooltip: { trigger: 'axis' },
    legend: { data: ['温度', '振动', '压力'] },
    xAxis: { type: 'category', data: [] },
    yAxis: [
      { type: 'value', name: '温度(°C)' },
      { type: 'value', name: '振动(mm/s)' },
      { type: 'value', name: '压力(MPa)' }
    ],
    series: [
      { name: '温度', type: 'line', smooth: true, data: [] },
      { name: '振动', type: 'line', smooth: true, yAxisIndex: 1, data: [] },
      { name: '压力', type: 'line', smooth: true, yAxisIndex: 2, data: [] }
    ]
  }
  chart.setOption(option)
}

function updateChart() {
  if (!chart || metricsData.value.length === 0) return
  
  const times = metricsData.value.slice(-20).map(m => {
    const date = new Date(m.timestamp)
    return `${date.getHours()}:${date.getMinutes()}:${date.getSeconds()}`
  })
  const temperatures = metricsData.value.slice(-20).map(m => m.temperature)
  const vibrations = metricsData.value.slice(-20).map(m => m.vibration)
  const pressures = metricsData.value.slice(-20).map(m => m.pressure)
  
  chart.setOption({
    xAxis: { data: times },
    series: [
      { data: temperatures },
      { data: vibrations },
      { data: pressures }
    ]
  })
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
  loadDevice()
  loadMaintenance()
  loadTrendChart()
  loadMetrics()
  initChart()
  
  refreshTimer = setInterval(() => {
    loadMetrics()
  }, 30000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
  chart?.dispose()
})
</script>

<style scoped>
.trend-image {
  text-align: center;
}

.trend-image img {
  max-width: 100%;
  height: auto;
}
</style>
