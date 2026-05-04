<template>
  <div class="device-detail">
    <div class="page-header animate-fade-in">
      <el-button @click="goBack" class="back-button">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
    </div>

    <el-row :gutter="20" class="animate-fade-in delay-100">
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header><span class="card-title">设备信息</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="设备编号"><span class="mono-text">{{ device.deviceNo }}</span></el-descriptions-item>
            <el-descriptions-item label="设备名称">{{ device.name }}</el-descriptions-item>
            <el-descriptions-item label="设备类型">{{ device.type }}</el-descriptions-item>
            <el-descriptions-item label="安装位置">{{ device.location }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusType(device.status)" size="small">{{ getStatusText(device.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="故障概率">
              <div class="fault-display">
                <el-progress :percentage="(device.faultProbability * 100 || 0)" :color="getFaultProbColor(device.faultProbability)" style="width: 150px;" />
                <span class="fault-value" :class="getFaultClass(device.faultProbability)">{{ ((device.faultProbability || 0) * 100).toFixed(1) }}%</span>
              </div>
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
          <template #header><span class="card-title">实时传感器数据</span></template>
          <div ref="chartRef" style="height: 450px;"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;" class="animate-fade-in delay-200">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header-flex">
              <span class="card-title">传感器模拟控制</span>
              <el-switch v-model="simulationEnabled" @change="toggleSimulation" />
            </div>
          </template>
          <div v-if="simulationEnabled">
            <!-- 原有模式控制（数据生成方式） -->
            <div class="control-section">
              <div class="section-label">数据生成方式</div>
              <el-radio-group v-model="simMode" @change="onModeChange" style="margin-bottom: 15px;">
                <el-radio-button value="NORMAL">正常输出</el-radio-button>
                <el-radio-button value="INSERT">插入数据</el-radio-button>
                <el-radio-button value="RANDOM">随机范围</el-radio-button>
              </el-radio-group>
            </div>

            <!-- 新增：传感模拟模式（运行场景） -->
            <div class="control-section" v-if="simMode === 'NORMAL'">
              <div class="section-label">运行场景模拟</div>
              <el-radio-group v-model="sensorSimMode" @change="onSensorSimModeChange" style="margin-bottom: 10px;" size="small">
                <el-radio-button value="STABLE">稳定运行</el-radio-button>
                <el-radio-button value="DEGRADING_SLOW">缓慢劣化</el-radio-button>
                <el-radio-button value="DEGRADING_FAST">快速劣化</el-radio-button>
                <el-radio-button value="SUDDEN_FAULT">突发故障</el-radio-button>
                <el-radio-button value="SPIKE_RECOVER">偶发异常</el-radio-button>
              </el-radio-group>
              <div style="margin-top: 10px;">
                <el-button type="primary" size="small" @click="applyResetStable">恢复稳定</el-button>
              </div>
              <el-alert
                v-if="sensorSimMode && modeDescriptions[sensorSimMode]"
                :type="modeDescriptions[sensorSimMode].type"
                :title="modeDescriptions[sensorSimMode].title"
                :description="modeDescriptions[sensorSimMode].desc"
                :closable="false"
                show-icon
                style="margin-top: 10px;"
              />
            </div>

            <div v-if="simMode === 'INSERT'">
              <el-alert type="warning" :closable="false" show-icon>插入数据：设置的值只在下一个周期使用一次</el-alert>
              <el-form label-width="80px" style="margin-top: 15px;">
                <el-form-item label="温度"><el-slider v-model="insertTemperature" :min="30" :max="100" show-input /></el-form-item>
                <el-form-item label="振动"><el-slider v-model="insertVibration" :min="0" :max="5" :step="0.1" show-input /></el-form-item>
                <el-form-item label="压力"><el-slider v-model="insertPressure" :min="500" :max="1000" show-input /></el-form-item>
                <el-form-item><el-button type="primary" @click="applyInsertData">插入数据</el-button></el-form-item>
              </el-form>
            </div>

            <div v-if="simMode === 'RANDOM'">
              <el-alert type="success" :closable="false" show-icon>随机范围：在设定范围内随机生成数据</el-alert>
              <el-form label-width="80px" style="margin-top: 15px;">
                <el-form-item label="温度范围"><el-input-number v-model="rangeTemperature[0]" :min="30" :max="100" /><span class="range-sep">~</span><el-input-number v-model="rangeTemperature[1]" :min="30" :max="100" /></el-form-item>
                <el-form-item label="振动范围"><el-input-number v-model="rangeVibration[0]" :min="0" :max="5" :step="0.1" /><span class="range-sep">~</span><el-input-number v-model="rangeVibration[1]" :min="0" :max="5" :step="0.1" /></el-form-item>
                <el-form-item label="压力范围"><el-input-number v-model="rangePressure[0]" :min="500" :max="1000" /><span class="range-sep">~</span><el-input-number v-model="rangePressure[1]" :min="500" :max="1000" /></el-form-item>
                <el-form-item><el-button type="success" @click="applyRandomRange">应用范围</el-button><el-button @click="clearManualData">清除</el-button></el-form-item>
              </el-form>
            </div>
          </div>
          <el-empty v-else description="模拟已关闭，请开启后进行控制" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><span class="card-title">维修记录</span></template>
          <div style="max-height: 300px; overflow-y: auto;">
            <el-timeline v-if="maintenanceList.length > 0">
              <el-timeline-item v-for="item in maintenanceList" :key="item.id" :timestamp="item.repairedAt" placement="top">
                <el-card shadow="hover" class="timeline-card">
                  <h4>{{ getMaintenanceTypeText(item.type) }}</h4>
                  <p>{{ item.description }}</p>
                  <p class="action-text">处理措施：{{ item.actionTaken }}</p>
                </el-card>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无维修记录" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;" class="animate-fade-in delay-300">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header-flex">
              <span class="card-title">传感器多维趋势图</span>
              <el-button type="primary" size="small" @click="loadTrendChart" :loading="trendLoading">刷新</el-button>
            </div>
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
            <div class="card-header-flex">
              <span class="card-title">故障概率趋势图（Matplotlib）</span>
              <el-button type="primary" size="small" @click="loadFaultProbChart" :loading="faultProbLoading">刷新</el-button>
            </div>
          </template>
          <div v-if="faultProbImage" class="trend-image">
            <img :src="'data:image/png;base64,' + faultProbImage" alt="故障概率趋势" />
          </div>
          <el-empty v-else description="暂无故障概率历史数据" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { getDevice, updateDeviceSimulation, updateDeviceStatus, getMaintenanceList } from '@/api/device'
import { getMetrics, getLatestMetric, setDeviceMode, insertDeviceData, setDeviceRandomRange, getDeviceMode, clearDeviceManualData, setSimMode, getSimMode, resetSimMode, getFaultProbabilityHistory } from '@/api/collector'
import { getChart } from '@/api/alert'
import { getFaultProbabilityChart } from '@/api/ml'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const chartRef = ref(null)
let chart = null
let refreshTimer = null

const device = ref({})
const maintenanceList = ref([])
const trendImage = ref('')
const trendLoading = ref(false)
const faultProbImage = ref('')
const faultProbLoading = ref(false)
const metricsData = ref([])

const simulationEnabled = ref(false)
const simMode = ref('NORMAL')
const sensorSimMode = ref('STABLE')
const simModeDetail = ref({ desc: '稳定运行', canTriggerAlert: false })
const insertTemperature = ref(60)
const insertVibration = ref(0.2)
const insertPressure = ref(700)
const rangeTemperature = ref([40, 70])
const rangeVibration = ref([0.1, 0.5])
const rangePressure = ref([500, 800])

const modeDescriptions = {
  STABLE: { title: '稳定运行', desc: '传感器在基线附近小幅随机游走，不会自发产生异常，故障概率<5%', type: 'info' },
  DEGRADING_SLOW: { title: '缓慢劣化', desc: '模拟轴承磨损等渐进故障，传感器值缓慢向阈值推进', type: 'warning' },
  DEGRADING_FAST: { title: '快速劣化', desc: '模拟润滑失效等较快速渐进故障，传感器值较快向阈值推进', type: 'warning' },
  SUDDEN_FAULT: { title: '突发故障', desc: '模拟电路短路或机械断裂，传感器值瞬间跳变到阈值以上', type: 'danger' },
  SPIKE_RECOVER: { title: '偶发异常', desc: '模拟电磁干扰或瞬时过载，短暂跳变后自动恢复，不应触发告警', type: 'info' }
}

async function loadDevice() {
  try { const res = await getDevice(route.params.id); device.value = res.data || {}; simulationEnabled.value = device.value.simulationEnabled || false }
  catch (error) { console.error('加载设备信息失败:', error) }
}

async function loadMaintenance() {
  try { const res = await getMaintenanceList({ page: 1, size: 10 }); maintenanceList.value = (res.data.records || []).filter(m => m.deviceId == route.params.id) }
  catch (error) { console.error('加载维修记录失败:', error) }
}

async function loadTrendChart() {
  try { trendLoading.value = true; const res = await getChart(route.params.id); trendImage.value = res.data || '' }
  catch (error) { console.error('加载趋势图失败:', error) }
  finally { trendLoading.value = false }
}

async function loadFaultProbChart() {
  try {
    faultProbLoading.value = true
    const res = await getFaultProbabilityChart(route.params.id, device.value.name, 24)
    if (res.data && res.data.image) {
      faultProbImage.value = res.data.image
    }
  } catch (error) { console.error('加载故障概率趋势图失败:', error) }
  finally { faultProbLoading.value = false }
}

async function loadMetrics() {
  try { const res = await getMetrics(route.params.id, {}); metricsData.value = res.data || []; updateChart() }
  catch (error) { console.error('加载指标数据失败:', error) }
}

async function loadSimulationStatus() {
  try {
    const deviceRes = await getDevice(route.params.id)
    if (deviceRes.data) simulationEnabled.value = deviceRes.data.simulationEnabled || false
    else simulationEnabled.value = false
    try {
      const modeRes = await getDeviceMode(route.params.id)
      if (modeRes.data?.mode) simMode.value = modeRes.data.mode
    } catch (e) { simMode.value = 'NORMAL' }
    try {
      const simModeRes = await getSimMode(route.params.id)
      if (simModeRes.data?.mode) {
        sensorSimMode.value = simModeRes.data.mode
        simModeDetail.value = {
          desc: simModeRes.data.desc || modeDescriptions[sensorSimMode.value]?.title || '',
          canTriggerAlert: simModeRes.data.canTriggerAlert || false
        }
      }
    } catch (e) {
      sensorSimMode.value = 'STABLE'
      simModeDetail.value = { desc: '稳定运行', canTriggerAlert: false }
    }
  } catch (error) { console.error('加载模拟状态失败:', error) }
}

async function toggleSimulation(value) {
  try {
    await updateDeviceSimulation(route.params.id, value)
    if (value) {
      await setDeviceMode(route.params.id, 'NORMAL')
      simMode.value = 'NORMAL'
      await setSimMode(route.params.id, 'STABLE')
      sensorSimMode.value = 'STABLE'
    }
    ElMessage.success(value ? '模拟已启动' : '模拟已停止')
  } catch (error) { console.error('切换模拟状态失败:', error); simulationEnabled.value = !value }
}

async function onModeChange(mode) {
  try { await setDeviceMode(route.params.id, mode); ElMessage.success('模式已切换为: ' + (mode === 'NORMAL' ? '正常输出' : mode === 'INSERT' ? '插入数据' : '随机范围')) }
  catch (error) { console.error('切换模式失败:', error) }
}

async function onSensorSimModeChange(mode) {
  try {
    await setSimMode(route.params.id, mode)
    sensorSimMode.value = mode
    const info = modeDescriptions[mode]
    simModeDetail.value = { desc: info.title, canTriggerAlert: info.type === 'warning' || info.type === 'danger' }
    ElMessage.success('传感模拟模式已切换为: ' + info.title)
  } catch (error) { console.error('切换传感模拟模式失败:', error) }
}

async function applyResetStable() {
  try {
    await resetSimMode(route.params.id)
    sensorSimMode.value = 'STABLE'
    simModeDetail.value = { desc: '稳定运行', canTriggerAlert: false }
    ElMessage.success('已重置为稳定运行模式')
  } catch (error) { console.error('重置失败:', error) }
}

async function applyInsertData() {
  try { await insertDeviceData(route.params.id, { temperature: insertTemperature.value, vibration: insertVibration.value, pressure: insertPressure.value }); ElMessage.success('数据已插入') }
  catch (error) { console.error('插入数据失败:', error); ElMessage.error('插入失败') }
}

async function applyRandomRange() {
  try { await setDeviceRandomRange(route.params.id, { temperature: { min: rangeTemperature.value[0], max: rangeTemperature.value[1] }, vibration: { min: rangeVibration.value[0], max: rangeVibration.value[1] }, pressure: { min: rangePressure.value[0], max: rangePressure.value[1] } }); ElMessage.success('随机范围已设置') }
  catch (error) { console.error('设置随机范围失败:', error); ElMessage.error('设置失败') }
}

async function clearManualData() {
  try {
    await clearDeviceManualData(route.params.id)
    await setDeviceMode(route.params.id, 'NORMAL')
    simMode.value = 'NORMAL'
    await resetSimMode(route.params.id)
    sensorSimMode.value = 'STABLE'
    ElMessage.success('已清除并恢复稳定模式')
  } catch (error) { console.error('清除失败:', error) }
}

async function loadCurrentValues() {
  try {
    const res = await getLatestMetric(route.params.id)
    if (res.data) {
      if (res.data.faultProbability !== undefined && res.data.faultProbability !== null) device.value.faultProbability = res.data.faultProbability
    }
  } catch (error) { console.error('加载当前值失败:', error) }
}

function initChart() {
  chart = echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'line', lineStyle: { type: 'dashed', color: 'rgba(45, 42, 38, 0.3)', width: 1 } }, backgroundColor: 'white', borderColor: '#e0e0e0', textStyle: { color: '#2d2a26' } },
    legend: { data: ['温度(°C)', '振动(mm/s)', '压力(bar)'], top: 0, textStyle: { color: '#5c5750' } },
    grid: [{ left: 70, right: 20, top: 50, height: 110 }, { left: 70, right: 20, top: 175, height: 110 }, { left: 70, right: 20, top: 300, height: 110 }],
    xAxis: [
      { type: 'category', data: [], gridIndex: 0, boundaryGap: false, axisLine: { show: true, lineStyle: { color: '#e0e0e0' } } },
      { type: 'category', data: [], gridIndex: 1, boundaryGap: false, axisLine: { show: true, lineStyle: { color: '#e0e0e0' } } },
      { type: 'category', data: [], gridIndex: 2, boundaryGap: false, axisLine: { show: true, lineStyle: { color: '#e0e0e0' } } }
    ],
    yAxis: [
      { type: 'value', name: '温度(°C)', nameLocation: 'middle', nameGap: 40, nameTextStyle: { fontSize: 11, color: '#9a948c' }, gridIndex: 0, axisLine: { show: false }, splitLine: { lineStyle: { color: '#f0f0f0' } }, axisLabel: { color: '#9a948c' } },
      { type: 'value', name: '振动(mm/s)', nameLocation: 'middle', nameGap: 40, nameTextStyle: { fontSize: 11, color: '#9a948c' }, gridIndex: 1, axisLine: { show: false }, splitLine: { lineStyle: { color: '#f0f0f0' } }, axisLabel: { color: '#9a948c' } },
      { type: 'value', name: '压力(bar)', nameLocation: 'middle', nameGap: 40, nameTextStyle: { fontSize: 11, color: '#9a948c' }, gridIndex: 2, axisLine: { show: false }, splitLine: { lineStyle: { color: '#f0f0f0' } }, axisLabel: { color: '#9a948c' } }
    ],
    series: [
      { name: '温度(°C)', type: 'line', smooth: true, xAxisIndex: 0, yAxisIndex: 0, data: [], showSymbol: false, lineStyle: { color: '#f4a261', width: 2 }, itemStyle: { color: '#f4a261' } },
      { name: '振动(mm/s)', type: 'line', smooth: true, xAxisIndex: 1, yAxisIndex: 1, data: [], showSymbol: false, lineStyle: { color: '#0077b6', width: 2 }, itemStyle: { color: '#0077b6' } },
      { name: '压力(bar)', type: 'line', smooth: true, xAxisIndex: 2, yAxisIndex: 2, data: [], showSymbol: false, lineStyle: { color: '#e85d04', width: 2 }, itemStyle: { color: '#e85d04' } }
    ]
  })
}

function updateChart() {
  if (!chart || metricsData.value.length === 0) return
  const times = metricsData.value.slice(-20).map(m => { const date = new Date(m.timestamp); return `${date.getHours()}:${date.getMinutes()}:${date.getSeconds()}` })
  const temperatures = metricsData.value.slice(-20).map(m => m.temperature)
  const vibrations = metricsData.value.slice(-20).map(m => m.vibration)
  const pressures = metricsData.value.slice(-20).map(m => m.pressure)
  chart.setOption({ xAxis: [{ data: times }, { data: times }, { data: times }], series: [{ data: temperatures }, { data: vibrations }, { data: pressures }] })
}

function getStatusType(status) { const map = { 'NORMAL': 'success', 'RUNNING': 'success', 'STANDBY': 'info', 'MAINTENANCE': 'warning', 'FAULT': 'danger', 'OFFLINE': 'info' }; return map[status] || 'info' }
function getStatusText(status) { const map = { 'NORMAL': '运行中', 'RUNNING': '运行中', 'STANDBY': '待机', 'MAINTENANCE': '维护中', 'FAULT': '故障', 'OFFLINE': '离线' }; return map[status] || status }
function getFaultProbColor(prob) { if (prob >= 0.7) return '#d62828'; if (prob >= 0.5) return '#f4a261'; return '#2d936c' }
function getFaultClass(prob) { if (prob >= 0.7) return 'danger'; if (prob >= 0.5) return 'warning'; return 'normal' }
function getMaintenanceTypeText(type) { const map = { 'ROUTINE': '日常保养', 'REPAIR': '故障维修', 'EMERGENCY': '紧急抢修', 'UPGRADE': '改造升级', 'INSPECTION': '点检' }; return map[type] || type || '-' }

async function handleStart() {
  try { await updateDeviceStatus(device.value.id, 'RUNNING'); await updateDeviceSimulation(device.value.id, true); ElMessage.success('设备已启动'); await loadDevice() }
  catch (error) { console.error('启动失败:', error); ElMessage.error('启动失败') }
}

async function handleStop() {
  try { await updateDeviceStatus(device.value.id, 'OFFLINE'); await updateDeviceSimulation(device.value.id, false); ElMessage.success('设备已停机'); await loadDevice() }
  catch (error) { console.error('停机失败:', error); ElMessage.error('停机失败') }
}

function goBack() { router.back() }

onMounted(() => {
  loadDevice(); loadMaintenance(); loadTrendChart(); loadFaultProbChart(); loadMetrics(); loadSimulationStatus(); loadCurrentValues(); initChart()
  refreshTimer = setInterval(() => { loadMetrics(); loadCurrentValues() }, 5000)
})

onUnmounted(() => { if (refreshTimer) clearInterval(refreshTimer); chart?.dispose() })
</script>

<style scoped>
.page-header { margin-bottom: 24px; }
.back-button { background: white !important; border-color: rgba(45, 42, 38, 0.12) !important; color: #5c5750 !important; }
.back-button:hover { background: #f5f2ed !important; }
.card-title { font-family: 'Playfair Display', Georgia, serif; font-size: 16px; font-weight: 600; color: #2d2a26; }
.card-header-flex { display: flex; justify-content: space-between; align-items: center; }
.mono-text { font-family: 'IBM Plex Mono', monospace; font-size: 13px; color: #5c5750; }
.fault-display { display: flex; align-items: center; gap: 12px; }
.fault-value { font-family: 'IBM Plex Mono', monospace; font-weight: 600; }
.fault-value.normal { color: #2d936c; }
.fault-value.warning { color: #e85d04; }
.fault-value.danger { color: #d62828; }
.range-sep { margin: 0 10px; color: #9a948c; }
.timeline-card { background: #faf8f5 !important; border-color: rgba(45, 42, 38, 0.08) !important; }
.timeline-card h4 { color: #2d2a26; margin-bottom: 8px; }
.timeline-card p { color: #5c5750; margin-bottom: 4px; font-size: 13px; }
.action-text { color: #0077b6 !important; }
.trend-image { text-align: center; }
.trend-image img { max-width: 100%; height: auto; border-radius: 8px; }
</style>
