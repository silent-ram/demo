<template>
  <div class="alert-statistics">
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span class="header-title">告警频次统计</span>
              <el-date-picker v-model="frequencyDateRange" type="datetimerange" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" @change="loadFrequencyStats" style="width: 400px;" />
            </div>
          </template>
          <div ref="frequencyChartRef" style="height: 300px;"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span class="header-title">设备故障率排行</span>
              <el-date-picker v-model="rankDateRange" type="datetimerange" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" @change="loadFailureRank" style="width: 400px;" />
            </div>
          </template>
          <div ref="rankChartRef" style="height: 300px;"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <span class="header-title">统计数据</span>
          </template>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="总告警数"><span class="stat-value">{{ stats.total || 0 }}</span></el-descriptions-item>
            <el-descriptions-item label="高风险告警"><span class="stat-value danger">{{ stats.highCount || 0 }}</span></el-descriptions-item>
            <el-descriptions-item label="中风险告警"><span class="stat-value warning">{{ stats.mediumCount || 0 }}</span></el-descriptions-item>
            <el-descriptions-item label="低风险告警"><span class="stat-value info">{{ stats.lowCount || 0 }}</span></el-descriptions-item>
            <el-descriptions-item label="已解决"><span class="stat-value success">{{ stats.resolvedCount || 0 }}</span></el-descriptions-item>
            <el-descriptions-item label="待处理"><span class="stat-value pending">{{ stats.activeCount || 0 }}</span></el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { getFrequencyStatistics, getFailureRank, getAlertStats } from '@/api/alert'

const frequencyChartRef = ref(null)
const rankChartRef = ref(null)
const frequencyDateRange = ref([])
const rankDateRange = ref([])
const stats = reactive({ total: 0, highCount: 0, mediumCount: 0, lowCount: 0, resolvedCount: 0, activeCount: 0 })
let frequencyChart = null
let rankChart = null

function initCharts() {
  if (frequencyChartRef.value) frequencyChart = echarts.init(frequencyChartRef.value)
  if (rankChartRef.value) rankChart = echarts.init(rankChartRef.value)
}

async function loadFrequencyStats() {
  if (!frequencyDateRange.value || frequencyDateRange.value.length !== 2) return
  const [startDate, endDate] = frequencyDateRange.value
  try {
    const res = await getFrequencyStatistics({ startDate: startDate.toISOString(), endDate: endDate.toISOString() })
    renderFrequencyChart(res.data)
  } catch (error) { console.error('加载频次统计失败:', error) }
}

async function loadFailureRank() {
  if (!rankDateRange.value || rankDateRange.value.length !== 2) return
  const [startDate, endDate] = rankDateRange.value
  try {
    const res = await getFailureRank({ startDate: startDate.toISOString(), endDate: endDate.toISOString(), limit: 10 })
    renderRankChart(res.data)
  } catch (error) { console.error('加载故障排行失败:', error) }
}

async function loadStats() {
  try { const res = await getAlertStats(); Object.assign(stats, res.data || {}) }
  catch (error) { console.error('加载统计概览失败:', error) }
}

function renderFrequencyChart(data) {
  if (!frequencyChart) return
  const labels = data.byDevice ? Object.keys(data.byDevice) : []
  const values = data.byDevice ? Object.values(data.byDevice) : []
  frequencyChart.setOption({
    tooltip: { trigger: 'axis', backgroundColor: 'white', borderColor: '#e0e0e0', textStyle: { color: '#2d2a26' } },
    legend: { data: ['告警数量'], textStyle: { color: '#5c5750' } },
    xAxis: { type: 'category', data: labels, axisLine: { lineStyle: { color: '#e0e0e0' } }, axisLabel: { color: '#9a948c' } },
    yAxis: { type: 'value', axisLine: { show: false }, splitLine: { lineStyle: { color: '#f0f0f0' } }, axisLabel: { color: '#9a948c' } },
    series: [{ name: '告警数量', data: values, type: 'bar', itemStyle: { color: '#0077b6' }, borderRadius: [6, 6, 0, 0] }]
  })
}

function renderRankChart(data) {
  if (!rankChart) return
  const deviceNames = data.map(d => d.deviceName)
  const faultCounts = data.map(d => d.alertCount)
  const faultRates = data.map(d => d.faultRate)
  rankChart.setOption({
    tooltip: { trigger: 'axis', backgroundColor: 'white', borderColor: '#e0e0e0', textStyle: { color: '#2d2a26' } },
    legend: { data: ['告警次数', '故障率'], textStyle: { color: '#5c5750' } },
    xAxis: { type: 'category', data: deviceNames, axisLine: { lineStyle: { color: '#e0e0e0' } }, axisLabel: { color: '#9a948c' } },
    yAxis: [
      { type: 'value', name: '告警次数', axisLine: { show: false }, splitLine: { lineStyle: { color: '#f0f0f0' } }, axisLabel: { color: '#9a948c' } },
      { type: 'value', name: '故障率%', max: 100, axisLine: { show: false }, splitLine: { show: false }, axisLabel: { color: '#9a948c' } }
    ],
    series: [
      { name: '告警次数', data: faultCounts, type: 'bar', itemStyle: { color: '#2d936c' }, borderRadius: [6, 6, 0, 0] },
      { name: '故障率', data: faultRates, type: 'line', yAxisIndex: 1, lineStyle: { color: '#d62828', width: 3 }, itemStyle: { color: '#d62828' }, smooth: true }
    ]
  })
}

onMounted(() => {
  initCharts()
  const now = new Date()
  const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)
  frequencyDateRange.value = [weekAgo, now]
  rankDateRange.value = [weekAgo, now]
  loadStats()
  loadFrequencyStats()
  loadFailureRank()
  window.addEventListener('resize', handleResize)
})

function handleResize() {
  frequencyChart?.resize()
  rankChart?.resize()
}

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  frequencyChart?.dispose()
  rankChart?.dispose()
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-title { font-family: 'Playfair Display', Georgia, serif; font-size: 16px; font-weight: 600; color: #2d2a26; }
.stat-value { font-family: 'IBM Plex Mono', monospace; font-weight: 600; color: #2d2a26; }
.stat-value.danger { color: #d62828; }
.stat-value.warning { color: #e85d04; }
.stat-value.info { color: #0077b6; }
.stat-value.success { color: #2d936c; }
.stat-value.pending { color: #f4a261; }
</style>
