<template>
  <div class="model">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-value">{{ metrics.accuracy?.toFixed(4) || '--' }}</div>
          <div class="metric-label">准确率 (Accuracy)</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-value">{{ metrics.precision?.toFixed(4) || '--' }}</div>
          <div class="metric-label">精确率 (Precision)</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-value">{{ metrics.recall?.toFixed(4) || '--' }}</div>
          <div class="metric-label">召回率 (Recall)</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-value">{{ metrics.f1?.toFixed(4) || '--' }}</div>
          <div class="metric-label">F1分数</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span>ROC-AUC</span>
          </template>
          <div class="roc-value">{{ metrics.roc_auc?.toFixed(4) || '--' }}</div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span>模型操作</span>
          </template>
          <div class="model-actions">
            <el-button type="primary" :loading="retrainLoading" @click="handleRetrain">
              <el-icon><Refresh /></el-icon>
              重新训练模型
            </el-button>
            <p class="tip">点击按钮将使用最新数据重新训练 Logistic 回归模型</p>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>阈值配置</span>
            </div>
          </template>
          <el-form :model="thresholdForm" label-width="100px">
            <el-form-item label="故障概率阈值">
              <el-slider v-model="thresholdForm.value" :min="0" :max="1" :step="0.01" show-input />
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getModelMetrics, retrainModel } from '@/api/ml'
import { getThreshold, updateThreshold } from '@/api/alert'

const metrics = ref({})
const retrainLoading = ref(false)

const thresholdForm = reactive({
  value: 0.7
})

async function loadMetrics() {
  try {
    const res = await getModelMetrics()
    metrics.value = res.data || {}
  } catch (error) {
    console.error('加载模型指标失败:', error)
  }
}

async function loadThreshold() {
  try {
    const res = await getThreshold()
    thresholdForm.value = parseFloat(res.data) || 0.7
  } catch (error) {
    console.error('加载阈值失败:', error)
  }
}

async function handleRetrain() {
  retrainLoading.value = true
  try {
    const res = await retrainModel()
    metrics.value = res.data || {}
    ElMessage.success('模型重新训练成功')
  } catch (error) {
    console.error('重新训练失败:', error)
  } finally {
    retrainLoading.value = false
  }
}

async function updateThresholdValue() {
  try {
    await updateThreshold(thresholdForm.value.toString())
    ElMessage.success('阈值更新成功')
  } catch (error) {
    console.error('更新阈值失败:', error)
  }
}

onMounted(() => {
  loadMetrics()
  loadThreshold()
})
</script>

<style scoped>
.metric-card {
  text-align: center;
  padding: 20px 0;
}

.metric-value {
  font-size: 32px;
  font-weight: bold;
  color: #409EFF;
}

.metric-label {
  font-size: 14px;
  color: #909399;
  margin-top: 10px;
}

.roc-value {
  font-size: 48px;
  font-weight: bold;
  color: #67C23A;
  text-align: center;
  padding: 30px 0;
}

.model-actions {
  text-align: center;
  padding: 20px 0;
}

.tip {
  font-size: 12px;
  color: #909399;
  margin-top: 15px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
