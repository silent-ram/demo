<template>
  <div class="dashboard">
    <!-- 欢迎横幅 -->
    <div class="welcome-banner animate-fade-in">
      <div class="banner-content">
        <div class="greeting">
          <span class="wave">👋</span>
          <h2>你好，{{ userStore.username }}</h2>
        </div>
        <p class="date-highlight">
          <el-icon><Calendar /></el-icon>
          {{ today }} · 祝您工作顺利
        </p>
      </div>
      <div class="banner-decoration">
        <div class="deco-ring dr1"></div>
        <div class="deco-ring dr2"></div>
      </div>
    </div>

    <!-- 统计卡片组 -->
    <el-row :gutter="24" class="stat-row">
      <el-col :span="6">
        <div class="stat-card sc1 animate-fade-in delay-100">
          <div class="stat-icon-wrap">
            <el-icon :size="26"><Monitor /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.deviceCount }}</div>
            <div class="stat-label">在线设备</div>
          </div>
          <div class="stat-indicator up">
            <el-icon><Top /></el-icon>
            <span>活跃</span>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card sc2 animate-fade-in delay-200">
          <div class="stat-icon-wrap">
            <el-icon :size="26"><Bell /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.todayAlertCount }}</div>
            <div class="stat-label">今日告警</div>
          </div>
          <div class="stat-indicator warn">
            <el-icon><Warning /></el-icon>
            <span>注意</span>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card sc3 animate-fade-in delay-300">
          <div class="stat-icon-wrap">
            <el-icon :size="26"><CircleCheck /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.normalRate }}%</div>
            <div class="stat-label">设备正常率</div>
          </div>
          <div class="stat-indicator good">
            <el-icon><CircleCheck /></el-icon>
            <span>良好</span>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card sc4 animate-fade-in delay-400">
          <div class="stat-icon-wrap">
            <el-icon :size="26"><Warning /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.pendingCount }}</div>
            <div class="stat-label">待处理告警</div>
          </div>
          <div class="stat-indicator danger">
            <el-icon><Bell /></el-icon>
            <span>待处理</span>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="24" class="chart-row">
      <el-col :span="12">
        <el-card shadow="hover" class="chart-card animate-fade-in delay-500">
          <template #header>
            <div class="card-header-custom">
              <div class="header-left">
                <el-icon class="header-icon"><PieChart /></el-icon>
                <span>告警级别分布</span>
              </div>
              <el-tag size="small" type="info" effect="plain">
                实时
              </el-tag>
            </div>
          </template>
          <div ref="pieChartRef" style="height: 300px;"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover" class="chart-card animate-fade-in delay-600">
          <template #header>
            <div class="card-header-custom">
              <div class="header-left">
                <el-icon class="header-icon"><TrendCharts /></el-icon>
                <span>近24小时告警趋势</span>
              </div>
              <el-tag size="small" type="success" effect="plain">
                稳定
              </el-tag>
            </div>
          </template>
          <div ref="lineChartRef" style="height: 300px;"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getAlertStats, getFrequencyStatistics } from '@/api/alert'
import { getDeviceList } from '@/api/device'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
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

const today = computed(() => {
  const now = new Date()
  return `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日`
})

async function loadStats() {
  try {
    const res = await getAlertStats()
    stats.value.pendingCount = res.data.activeCount || 0
    stats.value.todayAlertCount = res.data.todayCount || 0
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

function toLocalISOString(date) {
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

async function loadPieData() {
  try {
    const now = new Date()
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0)
    const res = await getFrequencyStatistics({
      startDate: toLocalISOString(start),
      endDate: toLocalISOString(now)
    })
    const byLevel = res.data.byLevel || {}
    pieChart.setOption({
      series: [{
        data: [
          { value: byLevel.HIGH || 0, name: '高危', itemStyle: { color: '#d62828' } },
          { value: byLevel.MEDIUM || 0, name: '中危', itemStyle: { color: '#f4a261' } },
          { value: byLevel.LOW || 0, name: '低危', itemStyle: { color: '#0077b6' } }
        ]
      }]
    })
  } catch (error) {
    console.error('加载饼图数据失败:', error)
  }
}

async function loadLineData() {
  try {
    const now = new Date()
    const hours = []
    const data = []
    for (let i = 23; i >= 0; i--) {
      const h = new Date(now.getTime() - i * 3600000)
      hours.push(`${h.getHours()}:00`)
      data.push(0)
    }
    const start = new Date(now.getTime() - 24 * 3600000)
    const res = await getFrequencyStatistics({
      startDate: toLocalISOString(start),
      endDate: toLocalISOString(now)
    })
    const byHour = res.data.byHour || {}
    const currentHour = now.getHours()
    for (let i = 0; i < 24; i++) {
      const hourLabel = (currentHour - 23 + i + 24) % 24
      data[i] = byHour[hourLabel.toString()] || 0
    }
    lineChart.setOption({
      xAxis: { data: hours },
      series: [{ data }]
    })
  } catch (error) {
    console.error('加载趋势图数据失败:', error)
  }
}

async function loadDeviceCount() {
  try {
    const res = await getDeviceList({ page: 1, size: 100 })
    const devices = res.data.records || []
    stats.value.deviceCount = devices.length
    const normalCount = devices.filter(d => d.status === 'NORMAL' || d.status === 'RUNNING').length
    stats.value.normalRate = devices.length > 0
      ? ((normalCount / devices.length) * 100).toFixed(1)
      : 100

  } catch (error) {
    console.error('加载设备数量失败:', error)
  }
}

function initPieChart() {
  if (!pieChartRef.value) return
  pieChart = echarts.init(pieChartRef.value)
  const option = {
    tooltip: {
      trigger: 'item',
      backgroundColor: 'white',
      borderColor: '#e0e0e0',
      borderWidth: 1,
      textStyle: { color: '#2d2a26' },
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: '5%',
      top: 'center',
      textStyle: { color: '#5c5750' },
      itemGap: 16
    },
    series: [{
      type: 'pie',
      radius: ['45%', '70%'],
      center: ['60%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: {
        borderRadius: 8,
        borderColor: '#fff',
        borderWidth: 3
      },
      label: { show: false },
      emphasis: {
        label: {
          show: true,
          fontSize: 14,
          fontWeight: 'bold',
          color: '#2d2a26'
        }
      },
      data: [
        { value: 0, name: '高危', itemStyle: { color: '#d62828' } },
        { value: 0, name: '中危', itemStyle: { color: '#f4a261' } },
        { value: 0, name: '低危', itemStyle: { color: '#0077b6' } }
      ]
    }]
  }
  pieChart.setOption(option)
}

function initLineChart() {
  if (!lineChartRef.value) return
  lineChart = echarts.init(lineChartRef.value)
  const option = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'white',
      borderColor: '#e0e0e0',
      textStyle: { color: '#2d2a26' }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: [],
      axisLine: { lineStyle: { color: '#e0e0e0' } },
      axisLabel: { color: '#9a948c', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#f0f0f0' } },
      axisLabel: { color: '#9a948c' }
    },
    series: [{
      data: [],
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { width: 2, color: '#0077b6' },
      itemStyle: { color: '#0077b6' },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(0,119,182,0.25)' },
            { offset: 1, color: 'rgba(0,119,182,0.02)' }
          ]
        }
      }
    }]
  }
  lineChart.setOption(option)
}

function handleResize() {
  pieChart?.resize()
  lineChart?.resize()
}

onMounted(() => {
  loadStats()
  loadDeviceCount()
  nextTick(() => {
    initPieChart()
    initLineChart()
    loadPieData()
    loadLineData()
  })
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  pieChart?.dispose()
  lineChart?.dispose()
})
</script>

<style scoped>
.dashboard {
  max-width: 1600px;
  margin: 0 auto;
}

/* 欢迎横幅 */
.welcome-banner {
  background: linear-gradient(135deg, #ffffff 0%, #f5f2ed 100%);
  border-radius: 20px;
  padding: 36px 42px;
  margin-bottom: 28px;
  position: relative;
  overflow: hidden;
  color: #2d2a26;
  border: 1px solid rgba(45, 42, 38, 0.08);
  box-shadow: 0 4px 20px rgba(45, 42, 38, 0.06);
}

.banner-content {
  position: relative;
  z-index: 2;
}

.greeting {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 12px;
}

.greeting h2 {
  font-family: 'Playfair Display', 'Noto Serif SC', Georgia, serif;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #2d2a26;
}

.wave {
  animation: wave 1s ease-in-out infinite;
  display: inline-block;
  font-size: 28px;
}

@keyframes wave {
  0%, 100% { transform: rotate(0deg); }
  25% { transform: rotate(20deg); }
  75% { transform: rotate(-20deg); }
}

.date-highlight {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: #9a948c;
}

.banner-decoration {
  position: absolute;
  right: 50px;
  top: 50%;
  transform: translateY(-50%);
}

.deco-ring {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(232, 93, 4, 0.3);
}

.dr1 {
  width: 180px;
  height: 180px;
  right: 0;
  top: -60px;
}

.dr2 {
  width: 120px;
  height: 120px;
  right: 80px;
  bottom: -40px;
  border-color: rgba(0, 119, 182, 0.25);
}

/* 统计卡片 */
.stat-row {
  margin-bottom: 28px;
}

.stat-card {
  background: white;
  border: 1px solid rgba(45, 42, 38, 0.08);
  border-radius: 16px;
  padding: 22px;
  display: flex;
  align-items: center;
  position: relative;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(45, 42, 38, 0.04);
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 25px rgba(45, 42, 38, 0.1);
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
}

.sc1::before { background: #0077b6; }
.sc2::before { background: #f4a261; }
.sc3::before { background: #2d936c; }
.sc4::before { background: #d62828; }

.stat-icon-wrap {
  width: 54px;
  height: 54px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.sc1 .stat-icon-wrap { background: linear-gradient(135deg, #0077b6, #00a8e8); }
.sc2 .stat-icon-wrap { background: linear-gradient(135deg, #f4a261, #e89b3d); }
.sc3 .stat-icon-wrap { background: linear-gradient(135deg, #2d936c, #3da87a); }
.sc4 .stat-icon-wrap { background: linear-gradient(135deg, #d62828, #e63939); }

.stat-content {
  margin-left: 16px;
  flex: 1;
}

.stat-value {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 32px;
  font-weight: 700;
  color: #2d2a26;
  line-height: 1;
}

.stat-label {
  font-size: 13px;
  color: #9a948c;
  margin-top: 4px;
}

.stat-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 16px;
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
}

.up { background: rgba(45, 147, 108, 0.1); color: #2d936c; }
.warn { background: rgba(244, 162, 97, 0.15); color: #e85d04; }
.good { background: rgba(0, 119, 182, 0.1); color: #0077b6; }
.danger { background: rgba(214, 40, 40, 0.1); color: #d62828; }

/* 图表卡片 */
.chart-row {
  margin-bottom: 28px;
}

.card-header-custom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
  font-size: 15px;
  color: #2d2a26;
}

.header-icon {
  color: #e85d04;
  font-size: 20px;
}

:deep(.el-card__header) {
  padding: 18px 22px !important;
  border-bottom: 1px solid rgba(45, 42, 38, 0.08) !important;
}
</style>
