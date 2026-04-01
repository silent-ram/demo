<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: #409EFF;">
            <el-icon size="28"><Monitor /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.deviceCount }}</div>
            <div class="stat-label">在线设备</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: #E6A23C;">
            <el-icon size="28"><Bell /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.todayAlertCount }}</div>
            <div class="stat-label">今日告警</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: #67C23A;">
            <el-icon size="28"><CircleCheck /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.normalRate }}%</div>
            <div class="stat-label">设备正常率</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: #F56C6C;">
            <el-icon size="28"><Warning /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.pendingCount }}</div>
            <div class="stat-label">待处理告警</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span>告警级别分布</span>
          </template>
          <div ref="pieChartRef" style="height: 300px;"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span>近24小时告警趋势</span>
          </template>
          <div ref="lineChartRef" style="height: 300px;"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <span>设备故障概率排行</span>
          </template>
          <el-table :data="deviceRankList" stripe>
            <el-table-column prop="deviceName" label="设备名称" />
            <el-table-column prop="deviceNo" label="设备编号" />
            <el-table-column label="故障概率">
              <template #default="{ row }">
                <el-progress
                  :percentage="row.faultProbability * 100"
                  :color="getProgressColor(row.faultProbability)"
                  :format="() => (row.faultProbability * 100).toFixed(1) + '%'"
                />
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { getAlertStats } from '@/api/alert'
import { getDeviceList } from '@/api/device'
import { getLatestMetric } from '@/api/collector'

const pieChartRef = ref(null)
const lineChartRef = ref(null)
let pieChart = null
let lineChart = null

const stats = ref({
  deviceCount: 0,
  todayAlertCount: 0,
  normalRate: 100,
  pendingCount: 0
})

const deviceRankList = ref([])

async function loadStats() {
  try {
    const res = await getAlertStats()
    stats.value.pendingCount = res.data.activeCount || 0
    stats.value.todayAlertCount = (res.data.activeCount || 0) + (res.data.resolvedCount || 0)
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

async function loadDeviceRank() {
  try {
    const res = await getDeviceList({ page: 1, size: 10 })
    const devices = res.data.records || []
    
    const rankList = await Promise.all(
      devices.map(async (device) => {
        try {
          const metricRes = await getLatestMetric(device.id)
          return {
            ...device,
            faultProbability: metricRes.data?.faultProbability || 0
          }
        } catch {
          return {
            ...device,
            faultProbability: 0
          }
        }
      })
    )
    
    deviceRankList.value = rankList
      .sort((a, b) => b.faultProbability - a.faultProbability)
      .slice(0, 5)
    
    stats.value.deviceCount = devices.length
    const normalCount = devices.filter(d => d.status === 'NORMAL').length
    stats.value.normalRate = devices.length > 0 
      ? ((normalCount / devices.length) * 100).toFixed(1) 
      : 100
  } catch (error) {
    console.error('加载设备排行失败:', error)
  }
}

function initPieChart() {
  pieChart = echarts.init(pieChartRef.value)
  const option = {
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', left: 'left' },
    series: [{
      type: 'pie',
      radius: '50%',
      data: [
        { value: 5, name: '高危', itemStyle: { color: '#F56C6C' } },
        { value: 10, name: '中危', itemStyle: { color: '#E6A23C' } },
        { value: 8, name: '低危', itemStyle: { color: '#409EFF' } }
      ]
    }]
  }
  pieChart.setOption(option)
}

function initLineChart() {
  lineChart = echarts.init(lineChartRef.value)
  const hours = Array.from({ length: 24 }, (_, i) => `${i}:00`)
  const option = {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: hours },
    yAxis: { type: 'value' },
    series: [{
      data: Array.from({ length: 24 }, () => Math.floor(Math.random() * 10)),
      type: 'line',
      smooth: true,
      areaStyle: { opacity: 0.3 }
    }]
  }
  lineChart.setOption(option)
}

function getProgressColor(probability) {
  if (probability >= 0.9) return '#F56C6C'
  if (probability >= 0.7) return '#E6A23C'
  return '#67C23A'
}

function getStatusType(status) {
  const map = {
    'NORMAL': 'success',
    'WARNING': 'warning',
    'FAULT': 'danger',
    'OFFLINE': 'info'
  }
  return map[status] || 'info'
}

function handleResize() {
  pieChart?.resize()
  lineChart?.resize()
}

onMounted(() => {
  loadStats()
  loadDeviceRank()
  initPieChart()
  initLineChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  pieChart?.dispose()
  lineChart?.dispose()
})
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
}

.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  width: 100%;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.stat-info {
  margin-left: 20px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 5px;
}
</style>
