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
            <el-radio-group v-model="trainMode" style="margin-right: 20px;">
              <el-radio-button label="simulated">纯模拟训练</el-radio-button>
              <el-radio-button label="hybrid">混合训练（推荐）</el-radio-button>
            </el-radio-group>
            <el-button type="primary" :loading="retrainLoading" @click="handleRetrain">
              <el-icon><Refresh /></el-icon>
              开始重训练
            </el-button>
            <el-button @click="loadLabelingStatus" :loading="labelingLoading">
              刷新标注统计
            </el-button>
          </div>
          <div v-if="labelingStats.totals" class="labeling-stats">
            <el-descriptions :column="3" border size="small">
              <el-descriptions-item label="伪标签正样本">{{ labelingStats.totals.total_positive }}</el-descriptions-item>
              <el-descriptions-item label="负样本">{{ labelingStats.totals.total_negative }}</el-descriptions-item>
              <el-descriptions-item label="总样本">{{ labelingStats.totals.total }}</el-descriptions-item>
            </el-descriptions>
          </div>
          <p class="tip">
            混合训练会自动将系统运行中收集的伪标签样本与模拟数据按比例混合后训练新模型。
            只有当新模型 F1 分数优于旧模型 95% 时才会自动替换。
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
              <el-tag :type="model.replaced ? 'success' : 'info'" size="small">
                {{ model.replaced ? '已更新' : '当前版本' }}
              </el-tag>
            </div>
          </template>

          <div class="version-info">
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="当前版本">{{ model.version || '--' }}</el-descriptions-item>
              <el-descriptions-item label="真实样本">
                {{ model.realSamples ? model.realSamples.total : 0 }}
                <span v-if="model.realSamples" class="sample-detail">
                  (正{{model.realSamples.positive}}/负{{model.realSamples.negative}})
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="混合比例">
                <span v-if="model.realRatio !== undefined">
                  真实 {{ (model.realRatio * 100).toFixed(0) }}%
                </span>
                <span v-else>纯模拟</span>
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
                <div v-if="model.improvement?.f1_score !== undefined" class="improvement-tag"
                     :class="model.improvement.f1_score > 0 ? 'up' : 'down'">
                  {{ model.improvement.f1_score > 0 ? '+' : '' }}
                  {{ (model.improvement.f1_score * 100).toFixed(2) }}%
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
import { getModelMetrics, retrainModel, getModelVersions, getLabelingStatus } from '@/api/ml'
import { getThreshold, updateThreshold } from '@/api/alert'

const trainMode = ref('hybrid')
const retrainLoading = ref(false)
const labelingLoading = ref(false)
const versionsLoading = ref(false)
const labelingStats = ref({ totals: null, statistics: {} })
const allMetrics = ref({})
const allVersions = ref({})

const thresholdForm = reactive({ value: 0.7 })

// 模型列表（6个设备类型）
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
      improved: (data.improvement?.f1_score || 0) > 0,
      metrics: data.new_metrics || data || {},
      improvement: data.improvement || {},
      realSamples: data.mix_info ? {
        total: data.mix_info.real_positive + data.mix_info.real_negative,
        positive: data.mix_info.real_positive,
        negative: data.mix_info.real_negative
      } : null,
      realRatio: data.mix_info ? data.mix_info.real_ratio : 0
    }
  })
})

// 版本历史
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
  // 按训练时间倒序
  return history.sort((a, b) => {
    const ta = a.trained_at ? new Date(a.trained_at) : new Date(0)
    const tb = b.trained_at ? new Date(b.trained_at) : new Date(0)
    return tb - ta
  })
})

async function loadMetrics() {
  try {
    const res = await getModelMetrics()
    allMetrics.value = res.data || {}
  } catch (error) { console.error('加载模型指标失败:', error) }
}

async function loadVersions() {
  versionsLoading.value = true
  try {
    const res = await getModelVersions()
    allVersions.value = res.data || {}
  } catch (error) { console.error('加载版本历史失败:', error) }
  finally { versionsLoading.value = false }
}

async function loadLabelingStatus() {
  labelingLoading.value = true
  try {
    const res = await getLabelingStatus()
    if (res.data) {
      labelingStats.value = {
        totals: res.data.totals,
        statistics: res.data.statistics
      }
    }
  } catch (error) { console.error('加载标注统计失败:', error) }
  finally { labelingLoading.value = false }
}

async function handleRetrain() {
  retrainLoading.value = true
  try {
    const res = await retrainModel(trainMode.value)
    allMetrics.value = res.data || {}
    ElMessage.success(`模型重新训练成功 (mode=${trainMode.value})`)
    // 刷新版本列表
    await loadVersions()
  } catch (error) {
    console.error('重新训练失败:', error)
    ElMessage.error('重新训练失败')
  } finally {
    retrainLoading.value = false
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
  loadLabelingStatus()
  loadThreshold()
})
</script>

<style scoped>
.model-management { padding: 20px; }
.card-title { font-family: 'Playfair Display', Georgia, serif; font-size: 16px; font-weight: 600; color: #2d2a26; }
.card-header-flex { display: flex; justify-content: space-between; align-items: center; }
.control-section { display: flex; align-items: center; gap: 15px; margin-bottom: 15px; }
.labeling-stats { margin-bottom: 15px; }
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
</style>
