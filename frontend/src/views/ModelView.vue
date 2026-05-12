<template>
  <div class="model-management">
    <!-- 全局控制与统计 -->
    <el-row :gutter="20" class="animate-fade-in">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header-flex">
              <span class="card-title">模型训练控制</span>
            </div>
          </template>
          <div class="control-section">
            <el-button type="primary" :loading="retrainLoading" @click="handleRetrain">
              <el-icon><Refresh /></el-icon>
              开始训练
            </el-button>
            <el-button @click="loadMetrics" :loading="metricsLoading">
              刷新指标
            </el-button>
          </div>
          <p class="tip">
            从 InfluxDB 读取真实传感器数据 + fault_probability 标签训练模型。标签规则：连续5点 fault_probability >= 0.7 为故障，连续5点 <= 0.1 为正常。新模型 F1 不低于旧模型 95% 时自动替换。
          </p>
        </el-card>
      </el-col>
    </el-row>

    <!-- 6个设备类型模型卡片 -->
    <el-row :gutter="20" style="margin-top: 20px;" class="animate-fade-in delay-100">
      <el-col :span="8" v-for="model in modelList" :key="model.deviceType">
        <el-card shadow="hover" :class="{'model-card': true, 'improved': model.improved}">
          <template #header>
            <div class="card-header-flex">
              <span class="card-title">{{ model.deviceType }}</span>
              <div class="card-actions">
                <el-button size="small" type="primary" :loading="singleTrainLoading[model.deviceType]" @click="handleRetrainSingle(model.deviceType)">训练</el-button>
                <el-tag :type="model.replaced ? 'success' : 'info'" size="small">
                  {{ model.replaced ? '已更新' : '当前版本' }}
                </el-tag>
              </div>
            </div>
          </template>

          <div class="version-info">
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="当前版本">{{ model.version || '--' }}</el-descriptions-item>
              <el-descriptions-item label="InfluxDB 数据量">
                {{ model.influxSamples || 0 }}
                <span v-if="model.influxPositive !== undefined" class="sample-detail">
                  (故障{{ model.influxPositive }}/正常{{ model.influxNegative }})
                </span>
              </el-descriptions-item>
            </el-descriptions>
          </div>

          <el-divider />

          <el-row :gutter="10">
            <el-col :span="12">
              <div class="metric-item">
                <div class="metric-value" :class="getMetricClass(model.metrics?.f1_score)">
                  {{ model.metrics?.f1_score?.toFixed(4) || '--' }}
                </div>
                <div class="metric-label">F1 Score</div>
                <div v-if="model.improvement?.f1 !== undefined" class="improvement-tag"
                     :class="model.improvement.f1 > 0 ? 'up' : 'down'">
                  {{ model.improvement.f1 > 0 ? '+' : '' }}
                  {{ (model.improvement.f1 * 100).toFixed(2) }}%
                </div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="metric-item">
                <div class="metric-value" :class="getMetricClass(model.metrics?.roc_auc)">
                  {{ model.metrics?.roc_auc?.toFixed(4) || '--' }}
                </div>
                <div class="metric-label">ROC AUC</div>
                <div v-if="model.improvement?.roc_auc !== undefined" class="improvement-tag"
                     :class="model.improvement.roc_auc > 0 ? 'up' : 'down'">
                  {{ model.improvement.roc_auc > 0 ? '+' : '' }}
                  {{ (model.improvement.roc_auc * 100).toFixed(2) }}%
                </div>
              </div>
            </el-col>
          </el-row>

          <el-row :gutter="10" style="margin-top: 10px;">
            <el-col :span="8">
              <div class="metric-small">
                <div class="metric-value-small">{{ model.metrics?.accuracy?.toFixed(3) || '--' }}</div>
                <div class="metric-label-small">准确率</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="metric-small">
                <div class="metric-value-small">{{ model.metrics?.precision?.toFixed(3) || '--' }}</div>
                <div class="metric-label-small">精确率</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="metric-small">
                <div class="metric-value-small">{{ model.metrics?.recall?.toFixed(3) || '--' }}</div>
                <div class="metric-label-small">召回率</div>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>

    <!-- 训练数据集管理区 -->
    <el-row :gutter="20" style="margin-top: 20px;" class="animate-fade-in delay-200">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header-flex">
              <span class="card-title">训练数据集</span>
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-select v-model="datasetFilter" placeholder="设备类型" size="small" style="width: 140px;" clearable @change="loadDatasetData">
                  <el-option v-for="dt in deviceTypes" :key="dt" :label="dt" :value="dt" />
                </el-select>
                <el-button size="small" @click="loadDatasetData" :loading="datasetLoading">刷新</el-button>
              </div>
            </div>
          </template>
          <div v-if="datasetSummary.total > 0" class="dataset-summary">
            <span>共 <strong>{{ datasetSummary.total }}</strong> 条</span>
            <el-tag type="danger" size="small" style="margin-left: 8px;">故障 {{ datasetSummary.fault }}</el-tag>
            <el-tag type="success" size="small" style="margin-left: 4px;">正常 {{ datasetSummary.normal }}</el-tag>
          </div>
          <el-table :data="datasetRecords" stripe size="small" style="margin-top: 10px;" max-height="500" v-loading="datasetLoading">
            <el-table-column prop="device_type" label="设备类型" width="100" />
            <el-table-column prop="window_start" label="窗口起始" width="170">
              <template #default="{ row }">{{ formatTimestamp(row.window_start) }}</template>
            </el-table-column>
            <el-table-column prop="window_end" label="窗口结束" width="170">
              <template #default="{ row }">{{ formatTimestamp(row.window_end) }}</template>
            </el-table-column>
            <el-table-column v-for="fn in datasetFeatureNames" :key="fn" :prop="fn" :label="fn" width="120" align="right">
              <template #default="{ row }">{{ row[fn] !== undefined ? row[fn].toFixed(4) : '--' }}</template>
            </el-table-column>
            <el-table-column prop="label" label="标签" width="80" align="center" fixed="right">
              <template #default="{ row }">
                <el-tag v-if="row.label === '故障'" type="danger" size="small">故障</el-tag>
                <el-tag v-else type="success" size="small">正常</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div style="display: flex; justify-content: center; margin-top: 12px;">
            <el-pagination
              v-model:current-page="datasetPage"
              :page-size="datasetSize"
              :total="datasetSummary.total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @current-change="loadDatasetData"
              @size-change="handleDatasetSizeChange"
            />
          </div>
          <el-empty v-if="!datasetLoading && datasetRecords.length === 0" description="暂无训练数据" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 版本历史时间线 -->
    <el-row :gutter="20" style="margin-top: 20px;" class="animate-fade-in delay-200">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header-flex">
              <span class="card-title">版本历史</span>
              <el-button size="small" @click="loadVersions" :loading="versionsLoading">刷新</el-button>
            </div>
          </template>
          <el-timeline v-if="versionHistory.length > 0">
            <el-timeline-item
              v-for="item in versionHistory"
              :key="`${item.deviceType}-${item.version}`"
              :type="item.active ? 'success' : 'primary'"
              :timestamp="item.trained_at || '未知时间'"
            >
              <div class="timeline-content">
                <strong>{{ item.deviceType }}</strong> {{ item.version }}
                <el-tag v-if="item.active" type="success" size="small" style="margin-left: 8px;">当前激活</el-tag>
                <div class="timeline-metrics">
                  F1: {{ item.f1_score?.toFixed(4) || '--' }}
                  | AUC: {{ item.roc_auc?.toFixed(4) || '--' }}
                  | 样本: {{ item.training_samples || '--' }}
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无版本历史" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 阈值配置 -->
    <el-row :gutter="20" style="margin-top: 20px;" class="animate-fade-in delay-300">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header><span class="card-title">阈值配置</span></template>
          <el-form :model="thresholdForm" label-width="120px">
            <el-form-item label="故障概率阈值">
              <el-slider v-model="thresholdForm.value" :min="0" :max="1" :step="0.01" show-input style="width: 400px;" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="updateThresholdValue">保存阈值</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getModelMetrics, retrainModel, getModelVersions, getDataset } from '@/api/ml'
import { getThreshold, updateThreshold } from '@/api/alert'

const retrainLoading = ref(false)
const singleTrainLoading = reactive({})
const metricsLoading = ref(false)
const versionsLoading = ref(false)
const allMetrics = ref({})
const allVersions = ref({})

const thresholdForm = reactive({ value: 0.7 })
const deviceTypes = ['工业机器人', '数控机床', '输送设备', '焊接设备', '压力设备', '包装设备']
const datasetFilter = ref('')
const datasetLoading = ref(false)
const datasetRecords = ref([])
const datasetPage = ref(1)
const datasetSize = ref(20)
const datasetSummary = reactive({ total: 0, fault: 0, normal: 0 })
const datasetFeatureNames = ref([])

const modelList = computed(() => {
  const deviceTypes = ['工业机器人', '数控机床', '输送设备', '焊接设备', '压力设备', '包装设备']
  const metrics = allMetrics.value.device_metrics || {}
  const results = allMetrics.value.device_results || {}

  return deviceTypes.map(dtype => {
    const data = results[dtype] || metrics[dtype] || {}
    const versions = allVersions.value[dtype] || []
    const activeVersion = versions.find(v => v.active)

    return {
      deviceType: dtype,
      version: activeVersion ? activeVersion.version : (data.version || 'v1'),
      replaced: data.should_replace || false,
      improved: (data.improvement?.f1 || 0) > 0,
      metrics: data.new_metrics || data || {},
      improvement: data.improvement || {},
      influxSamples: data.data_info?.real_samples || data.training_samples || 0,
      influxPositive: data.data_info?.positive ?? data.new_metrics?.positive_samples,
      influxNegative: data.data_info?.negative ?? data.new_metrics?.negative_samples,
    }
  })
})

const versionHistory = computed(() => {
  const history = []
  for (const [dtype, versions] of Object.entries(allVersions.value)) {
    versions.forEach(v => {
      history.push({
        ...v,
        deviceType: dtype
      })
    })
  }
  return history.sort((a, b) => {
    const ta = a.trained_at ? new Date(a.trained_at) : new Date(0)
    const tb = b.trained_at ? new Date(b.trained_at) : new Date(0)
    return tb - ta
  })
})

async function loadMetrics() {
  metricsLoading.value = true
  try {
    const res = await getModelMetrics()
    allMetrics.value = res.data || {}
  } catch (error) { console.error('加载模型指标失败:', error) }
  finally { metricsLoading.value = false }
}

async function loadVersions() {
  versionsLoading.value = true
  try {
    const res = await getModelVersions()
    allVersions.value = res.data || {}
  } catch (error) { console.error('加载版本历史失败:', error) }
  finally { versionsLoading.value = false }
}

async function handleRetrain() {
  retrainLoading.value = true
  try {
    const res = await retrainModel()
    allMetrics.value = res.data || {}
    ElMessage.success('模型训练成功')
    await loadVersions()
  } catch (error) {
    console.error('训练失败:', error)
    ElMessage.error('模型训练失败')
  } finally {
    retrainLoading.value = false
  }
}

async function handleRetrainSingle(deviceType) {
  singleTrainLoading[deviceType] = true
  try {
    const res = await retrainModel(deviceType)
    const result = res.data || {}
    // Merge single result into allMetrics
    allMetrics.value = {
      ...allMetrics.value,
      device_results: { ...(allMetrics.value.device_results || {}), ...result }
    }
    ElMessage.success(`${deviceType} 模型训练成功`)
    await loadVersions()
  } catch (error) {
    console.error('训练失败:', error)
    ElMessage.error(`${deviceType} 模型训练失败`)
  } finally {
    singleTrainLoading[deviceType] = false
  }
}

async function loadThreshold() {
  try {
    const res = await getThreshold()
    thresholdForm.value = parseFloat(res.data) || 0.7
  } catch (error) { console.error('加载阈值失败:', error) }
}

async function updateThresholdValue() {
  try {
    await updateThreshold(thresholdForm.value.toString())
    ElMessage.success('阈值更新成功')
  } catch (error) { console.error('更新阈值失败:', error) }
}

async function loadDatasetData() {
  datasetLoading.value = true
  try {
    const params = { page: datasetPage.value, size: datasetSize.value }
    if (datasetFilter.value) params.device_type = datasetFilter.value
    const res = await getDataset(params)
    const data = res.data || {}
    datasetRecords.value = data.records || []
    datasetSummary.total = data.summary?.total || 0
    datasetSummary.fault = data.summary?.fault || 0
    datasetSummary.normal = data.summary?.normal || 0
    datasetFeatureNames.value = data.feature_names || []
  } catch (error) {
    console.error('加载数据集失败:', error)
  } finally {
    datasetLoading.value = false
  }
}

function handleDatasetSizeChange(size) {
  datasetSize.value = size
  datasetPage.value = 1
  loadDatasetData()
}

function formatTimestamp(ts) {
  if (!ts) return '--'
  if (ts.includes('T')) {
    return ts.replace('T', ' ').substring(0, 19)
  }
  return ts
}

function getMetricClass(value) {
  if (value === undefined || value === null) return ''
  if (value >= 0.9) return 'excellent'
  if (value >= 0.7) return 'good'
  if (value >= 0.5) return 'warning'
  return 'danger'
}

onMounted(() => {
  loadMetrics()
  loadVersions()
  loadThreshold()
  loadDatasetData()
})
</script>

<style scoped>
.model-management { padding: 20px; }
.card-title { font-family: 'Playfair Display', Georgia, serif; font-size: 16px; font-weight: 600; color: #2d2a26; }
.card-header-flex { display: flex; justify-content: space-between; align-items: center; }
.card-actions { display: flex; align-items: center; gap: 8px; }
.control-section { display: flex; align-items: center; gap: 15px; margin-bottom: 15px; }
.tip { font-size: 12px; color: #9a948c; margin-top: 10px; }

.model-card { transition: all 0.3s; }
.model-card.improved { border-color: #2d936c; box-shadow: 0 4px 16px rgba(45, 147, 108, 0.15); }

.version-info { font-size: 13px; }
.sample-detail { color: #9a948c; margin-left: 4px; }

.metric-item { text-align: center; padding: 10px 0; }
.metric-value { font-family: 'IBM Plex Mono', monospace; font-size: 28px; font-weight: 700; margin-bottom: 6px; }
.metric-value.excellent { color: #2d936c; }
.metric-value.good { color: #0077b6; }
.metric-value.warning { color: #f4a261; }
.metric-value.danger { color: #d62828; }
.metric-label { font-size: 12px; color: #9a948c; }
.improvement-tag { font-size: 11px; margin-top: 4px; font-family: 'IBM Plex Mono', monospace; }
.improvement-tag.up { color: #2d936c; }
.improvement-tag.down { color: #d62828; }

.metric-small { text-align: center; padding: 6px 0; }
.metric-value-small { font-family: 'IBM Plex Mono', monospace; font-size: 16px; font-weight: 600; color: #5c5750; }
.metric-label-small { font-size: 11px; color: #9a948c; }

.timeline-content { font-size: 14px; }
.timeline-metrics { font-size: 12px; color: #9a948c; margin-top: 4px; font-family: 'IBM Plex Mono', monospace; }

.animate-fade-in { animation: fadeIn 0.5s ease-in; }
.delay-100 { animation-delay: 0.1s; }
.delay-200 { animation-delay: 0.2s; }
.delay-300 { animation-delay: 0.3s; }

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
.dataset-summary { font-size: 13px; color: #5c5750; display: flex; align-items: center; }
</style>
