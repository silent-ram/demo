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
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <span>传感器模拟控制</span>
              <el-switch v-model="simulationEnabled" @change="toggleSimulation" />
            </div>
          </template>
          <div v-if="simulationEnabled">
            <!-- 模式选择 -->
            <el-radio-group v-model="simMode" @change="onModeChange" style="margin-bottom: 15px;">
              <el-radio-button value="NORMAL">正常输出</el-radio-button>
              <el-radio-button value="INSERT">插入数据</el-radio-button>
              <el-radio-button value="RANDOM">随机范围</el-radio-button>
            </el-radio-group>

            <!-- 正常输出模式：显示当前值 -->
            <div v-if="simMode === 'NORMAL'">
              <el-alert type="info" :closable="false" show-icon>
                正常模式：在正常指标范围内自动随机生成数据
              </el-alert>
              <el-form label-width="80px" style="margin-top: 15px;">
                <el-form-item label="当前值">
                  <span>温度: {{ currentValues.temperature?.toFixed(1) || '--' }}°C</span>
                  <span style="margin-left: 20px;">振动: {{ currentValues.vibration?.toFixed(2) || '--' }}mm/s</span>
                  <span style="margin-left: 20px;">压力: {{ currentValues.pressure?.toFixed(0) || '--' }}Pa</span>
                </el-form-item>
              </el-form>
            </div>

            <!-- 插入数据模式 -->
            <div v-if="simMode === 'INSERT'">
              <el-alert type="warning" :closable="false" show-icon>
                插入数据：设置的值只在下一个周期使用一次
              </el-alert>
              <el-form label-width="80px" style="margin-top: 15px;">
                <el-form-item label="温度">
                  <el-slider v-model="insertTemperature" :min="30" :max="100" show-input />
                </el-form-item>
                <el-form-item label="振动">
                  <el-slider v-model="insertVibration" :min="0" :max="5" :step="0.1" show-input />
                </el-form-item>
                <el-form-item label="压力">
                  <el-slider v-model="insertPressure" :min="500" :max="1000" show-input />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="applyInsertData">插入数据</el-button>
                </el-form-item>
              </el-form>
            </div>

            <!-- 随机范围模式 -->
            <div v-if="simMode === 'RANDOM'">
              <el-alert type="success" :closable="false" show-icon>
                随机范围：在设定范围内随机生成数据
              </el-alert>
              <el-form label-width="80px" style="margin-top: 15px;">
                <el-form-item label="温度范围">
                  <el-input-number v-model="rangeTemperature[0]" :min="30" :max="100" />
                  <span> ~ </span>
                  <el-input-number v-model="rangeTemperature[1]" :min="30" :max="100" />
                </el-form-item>
                <el-form-item label="振动范围">
                  <el-input-number v-model="rangeVibration[0]" :min="0" :max="5" :step="0.1" />
                  <span> ~ </span>
                  <el-input-number v-model="rangeVibration[1]" :min="0" :max="5" :step="0.1" />
                </el-form-item>
                <el-form-item label="压力范围">
                  <el-input-number v-model="rangePressure[0]" :min="500" :max="1000" />
                  <span> ~ </span>
                  <el-input-number v-model="rangePressure[1]" :min="500" :max="1000" />
                </el-form-item>
                <el-form-item>
                  <el-button type="success" @click="applyRandomRange">应用范围</el-button>
                  <el-button @click="clearManualData">清除</el-button>
                </el-form-item>
              </el-form>
            </div>
          </div>
          <el-empty v-else description="模拟已关闭，请开启后进行控制" />
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

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="24">
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
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { getDevice, updateDeviceSimulation } from '@/api/device'
import { getMaintenanceList } from '@/api/device'
import { getMetrics, getLatestMetric, setDeviceMode, insertDeviceData, setDeviceRandomRange, getDeviceMode, clearDeviceManualData } from '@/api/collector'
import { getChart } from '@/api/alert'
import { ElMessage } from 'element-plus'

const route = useRoute()
const chartRef = ref(null)
let chart = null
let refreshTimer = null

const device = ref({})
const maintenanceList = ref([])
const trendImage = ref('')
const metricsData = ref([])

// 模拟控制相关
const simulationEnabled = ref(false)
const simMode = ref('NORMAL')  // NORMAL, INSERT, RANDOM

// 插入数据模式的变量
const insertTemperature = ref(60)
const insertVibration = ref(0.2)
const insertPressure = ref(700)

// 随机范围模式的变量
const rangeTemperature = ref([40, 70])
const rangeVibration = ref([0.1, 0.5])
const rangePressure = ref([500, 800])

// 当前显示值
const currentValues = ref({})

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

async function loadSimulationStatus() {
  try {
    // 从设备详情中获取 simulationEnabled 状态
    if (device.value.simulationEnabled !== undefined) {
      simulationEnabled.value = device.value.simulationEnabled
    } else {
      // 兼容旧数据，默认开启
      simulationEnabled.value = true
    }

    // 获取设备当前模式
    try {
      const modeRes = await getDeviceMode(route.params.id)
      if (modeRes.data && modeRes.data.mode) {
        simMode.value = modeRes.data.mode
      }
    } catch (e) {
      // 兼容旧数据
      simMode.value = 'NORMAL'
    }
  } catch (error) {
    console.error('加载模拟状态失败:', error)
  }
}

async function toggleSimulation(value) {
  try {
    // 调用后端接口更新设备模拟开关状态
    await updateDeviceSimulation(route.params.id, value)
    // 同步设置模式
    if (value) {
      await setDeviceMode(route.params.id, 'NORMAL')
      simMode.value = 'NORMAL'
    }
    ElMessage.success(value ? '模拟已启动' : '模拟已停止')
  } catch (error) {
    console.error('切换模拟状态失败:', error)
    simulationEnabled.value = !value
  }
}

async function onModeChange(mode) {
  try {
    await setDeviceMode(route.params.id, mode)
    ElMessage.success('模式已切换为: ' + (mode === 'NORMAL' ? '正常输出' : mode === 'INSERT' ? '插入数据' : '随机范围'))
  } catch (error) {
    console.error('切换模式失败:', error)
  }
}

async function applyInsertData() {
  try {
    const values = {
      temperature: insertTemperature.value,
      vibration: insertVibration.value,
      pressure: insertPressure.value
    }
    await insertDeviceData(route.params.id, values)
    ElMessage.success('数据已插入，将在下一个周期生效')
  } catch (error) {
    console.error('插入数据失败:', error)
    ElMessage.error('插入失败')
  }
}

async function applyRandomRange() {
  try {
    const ranges = {
      temperature: { min: rangeTemperature.value[0], max: rangeTemperature.value[1] },
      vibration: { min: rangeVibration.value[0], max: rangeVibration.value[1] },
      pressure: { min: rangePressure.value[0], max: rangePressure.value[1] }
    }
    await setDeviceRandomRange(route.params.id, ranges)
    ElMessage.success('随机范围已设置')
  } catch (error) {
    console.error('设置随机范围失败:', error)
    ElMessage.error('设置失败')
  }
}

async function clearManualData() {
  try {
    await clearDeviceManualData(route.params.id)
    // 恢复正常模式
    await setDeviceMode(route.params.id, 'NORMAL')
    simMode.value = 'NORMAL'
    ElMessage.success('已清除并恢复正常模式')
  } catch (error) {
    console.error('清除失败:', error)
  }
}

async function loadCurrentValues() {
  try {
    const res = await getLatestMetric(route.params.id)
    if (res.data) {
      currentValues.value = {
        temperature: res.data.temperature,
        vibration: res.data.vibration,
        pressure: res.data.pressure
      }
    }
  } catch (error) {
    console.error('加载当前值失败:', error)
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
  loadSimulationStatus()
  loadCurrentValues()
  initChart()

  // 每 5 秒刷新实时数据
  refreshTimer = setInterval(() => {
    loadMetrics()
    loadCurrentValues()
  }, 5000)
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
