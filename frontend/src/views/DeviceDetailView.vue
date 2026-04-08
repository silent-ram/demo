<template>
  <div class="device-detail">
    <div class="page-header">
      <el-button @click="goBack" icon="ArrowLeft">返回</el-button>
    </div>
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
            <el-descriptions-item label="故障概率">
              <el-progress :percentage="(device.faultProbability * 100 || 0)" :color="getFaultProbColor(device.faultProbability)" style="width: 150px;" />
              <span style="margin-left: 10px;">{{ ((device.faultProbability || 0) * 100).toFixed(1) }}%</span>
            </el-descriptions-item>
            <el-descriptions-item label="操作">
              <el-button v-if="device.status === 'OFFLINE' || device.status === 'STANDBY'" type="success" size="small" @click="handleStart">启动</el-button>
              <el-button v-else-if="device.status === 'NORMAL' || device.status === 'RUNNING'" type="warning" size="small" @click="handleStop">停机</el-button>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card shadow="hover">
          <template #header>
            <span>实时传感器数据</span>
          </template>
          <div ref="chartRef" style="height: 450px;"></div>
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

            <!-- 正常输出模式 -->
            <div v-if="simMode === 'NORMAL'">
              <el-alert type="info" :closable="false" show-icon>
                正常模式：在正常指标范围内自动随机生成数据
              </el-alert>
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
          <div style="max-height: 300px; overflow-y: auto;">
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
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <span>多维趋势图</span>
            <el-button type="primary" size="small" @click="loadTrendChart" :loading="trendLoading">刷新</el-button>
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
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'

const route = useRoute()
const router = useRouter()
import { getDevice, updateDeviceSimulation, updateDeviceStatus } from '@/api/device'
import { getMaintenanceList } from '@/api/device'
import { getMetrics, getLatestMetric, setDeviceMode, insertDeviceData, setDeviceRandomRange, getDeviceMode, clearDeviceManualData } from '@/api/collector'
import { getChart } from '@/api/alert'
import { ElMessage } from 'element-plus'

const chartRef = ref(null)
let chart = null
let refreshTimer = null

const device = ref({})
const maintenanceList = ref([])
const trendImage = ref('')
const trendLoading = ref(false)
const metricsData = ref([])

// 模拟控制相关
const simulationEnabled = ref(false)  // 默认关闭
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
    // 同步更新模拟开关状态
    simulationEnabled.value = device.value.simulationEnabled || false
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
    trendLoading.value = true
    const res = await getChart(route.params.id)
    trendImage.value = res.data || ''
  } catch (error) {
    console.error('加载趋势图失败:', error)
  } finally {
    trendLoading.value = false
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
    // 直接从API获取设备信息，确保拿到最新状态
    const deviceRes = await getDevice(route.params.id)
    if (deviceRes.data) {
      simulationEnabled.value = deviceRes.data.simulationEnabled || false
    } else {
      simulationEnabled.value = false
    }

    // 获取设备当前模式
    try {
      const modeRes = await getDeviceMode(route.params.id)
      if (modeRes.data && modeRes.data.mode) {
        simMode.value = modeRes.data.mode
      }
    } catch (e) {
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
      // 更新设备的故障概率
      if (res.data.faultProbability !== undefined && res.data.faultProbability !== null) {
        device.value.faultProbability = res.data.faultProbability
      }
    }
  } catch (error) {
    console.error('加载当前值失败:', error)
  }
}

function initChart() {
  chart = echarts.init(chartRef.value)
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'line',
        lineStyle: { type: 'dashed', color: '#666', width: 1 }
      },
      formatter(params) {
        let result = params[0].name + '<br/>'
        params.forEach(p => {
          const colors = ['#5470C6', '#91CC75', '#EE6666']
          const units = ['°C', 'mm/s', 'bar']
          result += `<span style="display:inline-block;margin-right:4px;border-radius:10px;width:10px;height:10px;background-color:${colors[p.seriesIndex]};"></span>${p.seriesName}: ${p.value}${units[p.seriesIndex]}<br/>`
        })
        return result
      }
    },
    legend: { data: ['温度(°C)', '振动(mm/s)', '压力(bar)'], top: 0 },
    grid: [
      { left: 70, right: 20, top: 50, height: 110 },
      { left: 70, right: 20, top: 175, height: 110 },
      { left: 70, right: 20, top: 300, height: 110 }
    ],
    xAxis: [
      { type: 'category', data: [], gridIndex: 0, boundaryGap: false, axisLine: { show: true } },
      { type: 'category', data: [], gridIndex: 1, boundaryGap: false, axisLine: { show: true } },
      { type: 'category', data: [], gridIndex: 2, boundaryGap: false, axisLine: { show: true } }
    ],
    yAxis: [
      { type: 'value', name: '温度(°C)', nameLocation: 'middle', nameGap: 40, nameTextStyle: { fontSize: 11 }, gridIndex: 0 },
      { type: 'value', name: '振动(mm/s)', nameLocation: 'middle', nameGap: 40, nameTextStyle: { fontSize: 11 }, gridIndex: 1 },
      { type: 'value', name: '压力(bar)', nameLocation: 'middle', nameGap: 40, nameTextStyle: { fontSize: 11 }, gridIndex: 2 }
    ],
    series: [
      { name: '温度(°C)', type: 'line', smooth: true, xAxisIndex: 0, yAxisIndex: 0, data: [], showSymbol: false },
      { name: '振动(mm/s)', type: 'line', smooth: true, xAxisIndex: 1, yAxisIndex: 1, data: [], showSymbol: false },
      { name: '压力(bar)', type: 'line', smooth: true, xAxisIndex: 2, yAxisIndex: 2, data: [], showSymbol: false }
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
    xAxis: [
      { data: times },
      { data: times },
      { data: times }
    ],
    series: [
      { data: temperatures },
      { data: vibrations },
      { data: pressures }
    ]
  })
}

function getStatusType(status) {
  const map = { 'NORMAL': 'success', 'RUNNING': 'primary', 'STANDBY': 'info', 'MAINTENANCE': 'warning', 'FAULT': 'danger', 'OFFLINE': 'info' }
  return map[status] || 'info'
}

function getStatusText(status) {
  const map = { 'NORMAL': '运行中', 'RUNNING': '运行中', 'STANDBY': '待机', 'MAINTENANCE': '维护中', 'FAULT': '故障', 'OFFLINE': '离线' }
  return map[status] || status
}

function getFaultProbColor(prob) {
  if (prob >= 0.7) return '#F56C6C'
  if (prob >= 0.5) return '#E6A23C'
  return '#67C23A'
}

async function handleStart() {
  try {
    await updateDeviceStatus(device.value.id, 'RUNNING')
    await updateDeviceSimulation(device.value.id, true)
    ElMessage.success('设备已启动')
    await loadDevice()
  } catch (error) {
    console.error('启动失败:', error)
    ElMessage.error('启动失败: ' + error.message)
  }
}

async function handleStop() {
  try {
    await updateDeviceStatus(device.value.id, 'OFFLINE')
    await updateDeviceSimulation(device.value.id, false)
    ElMessage.success('设备已停机')
    await loadDevice()
  } catch (error) {
    console.error('停机失败:', error)
    ElMessage.error('停机失败: ' + error.message)
  }
}

function goBack() {
  router.back()
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
.page-header {
  margin-bottom: 20px;
}

.trend-image {
  text-align: center;
}

.trend-image img {
  max-width: 100%;
  height: auto;
}
</style>
