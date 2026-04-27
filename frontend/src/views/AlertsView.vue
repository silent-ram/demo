<template>
  <div class="alerts">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="title">告警列表</span>
          <div class="filters">
            <el-select v-model="filters.level" placeholder="告警级别" clearable style="width: 120px; margin-right: 10px;">
              <el-option label="高" value="HIGH" />
              <el-option label="中" value="MEDIUM" />
              <el-option label="低" value="LOW" />
            </el-select>
            <el-select v-model="filters.resolved" placeholder="状态" clearable style="width: 120px;">
              <el-option label="待处理" :value="false" />
              <el-option label="已处理" :value="true" />
            </el-select>
          </div>
        </div>
      </template>

      <el-table :data="alertList" v-loading="loading" stripe>
        <el-table-column prop="deviceName" label="设备名称" width="150">
          <template #default="{ row }">
            <div class="device-cell">
              <el-icon class="device-icon"><Monitor /></el-icon>
              <span>{{ row.deviceName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="alertLevel" label="告警级别" width="100">
          <template #default="{ row }">
            <el-tag :type="getLevelType(row.alertLevel)" size="small">
              {{ getLevelText(row.alertLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="faultProbability" label="故障概率" width="120">
          <template #default="{ row }">
            <span class="probability-value" :class="getProbabilityClass(row.faultProbability)">
              {{ (row.faultProbability * 100).toFixed(1) }}%
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="告警信息" />
        <el-table-column prop="createdAt" label="告警时间" width="180">
          <template #default="{ row }">
            <span class="time-value">{{ row.createdAt }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="resolved" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.resolved ? 'success' : 'warning'" size="small">
              {{ row.resolved ? '已处理' : '待处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button v-if="!row.resolved" type="primary" link @click="handleResolve(row)">处理</el-button>
              <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadAlerts"
        @current-change="loadAlerts"
      />
    </el-card>

    <el-dialog v-model="resolveDialogVisible" title="处理告警" width="500px">
      <el-form :model="resolveForm" label-width="80px">
        <el-form-item label="处理方式">
          <el-radio-group v-model="resolveForm.resolveType">
            <el-radio-button value="COMPLETED">去维修</el-radio-button>
            <el-radio-button value="PENDING">待维修</el-radio-button>
            <el-radio-button value="STOPPED">停机</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <template v-if="resolveForm.resolveType === 'COMPLETED'">
          <el-form-item label="维修类型" required>
            <el-select v-model="resolveForm.maintenanceType" placeholder="请选择维修类型" style="width: 100%;">
              <el-option v-for="mt in maintenanceTypes" :key="mt.value" :label="mt.label" :value="mt.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="故障分类">
            <el-select v-model="resolveForm.faultCategory" placeholder="请选择故障分类" style="width: 100%;">
              <el-option v-for="fc in faultCategories" :key="fc.value" :label="fc.label" :value="fc.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="故障描述">
            <el-input v-model="resolveForm.description" type="textarea" :rows="2" placeholder="请输入故障描述" />
          </el-form-item>
          <el-form-item label="处理措施">
            <el-input v-model="resolveForm.actionTaken" type="textarea" :rows="2" placeholder="请输入处理措施" />
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="处理备注">
            <el-input v-model="resolveForm.note" type="textarea" :rows="3" placeholder="请输入处理备注" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="resolveDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="resolveLoading" @click="submitResolve">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailDialogVisible" title="告警详情" width="500px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="设备名称">{{ currentAlert.deviceName }}</el-descriptions-item>
        <el-descriptions-item label="告警级别">
          <el-tag :type="getLevelType(currentAlert.alertLevel)" size="small">{{ getLevelText(currentAlert.alertLevel) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="故障概率">
          <span class="probability-value" :class="getProbabilityClass(currentAlert.faultProbability)">
            {{ (currentAlert.faultProbability * 100).toFixed(1) }}%
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="告警信息">{{ currentAlert.message }}</el-descriptions-item>
        <el-descriptions-item label="告警时间">{{ currentAlert.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="currentAlert.resolved ? 'success' : 'warning'" size="small">
            {{ currentAlert.resolved ? '已处理' : '待处理' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="currentAlert.resolveNote" label="处理备注">{{ currentAlert.resolveNote }}</el-descriptions-item>
        <el-descriptions-item v-if="currentAlert.previousLevel" label="升级前级别">
          <el-tag :type="getLevelType(currentAlert.previousLevel)" size="small">{{ getLevelText(currentAlert.previousLevel) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="currentAlert.upgradedAt" label="升级时间">{{ currentAlert.upgradedAt }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getAlertList, resolveAlert } from '@/api/alert'
import { useAlertStore } from '@/stores/alert'

const alertStore = useAlertStore()
const loading = ref(false)
const alertList = ref([])
const resolveDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const resolveLoading = ref(false)
const currentAlert = ref({})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const filters = reactive({
  level: '',
  resolved: null
})

const resolveForm = reactive({
  id: null,
  note: '',
  resolveType: 'COMPLETED',
  maintenanceType: '',
  faultCategory: '',
  description: '',
  actionTaken: ''
})

const maintenanceTypes = [
  { value: 'ROUTINE', label: '日常保养' },
  { value: 'REPAIR', label: '故障维修' },
  { value: 'EMERGENCY', label: '紧急抢修' },
  { value: 'UPGRADE', label: '改造升级' },
  { value: 'INSPECTION', label: '点检' }
]

const faultCategories = [
  { value: 'EQUIPMENT', label: '设备故障' },
  { value: 'ELECTRICAL', label: '电气故障' },
  { value: 'MECHANICAL', label: '机械故障' },
  { value: 'SENSOR', label: '传感器故障' },
  { value: 'SOFTWARE', label: '软件故障' },
  { value: 'OTHER', label: '其他' }
]

async function loadAlerts() {
  loading.value = true
  try {
    const res = await getAlertList({ page: pagination.page, size: pagination.size })
    let list = res.data.records || []

    if (filters.level) {
      list = list.filter(a => a.alertLevel === filters.level)
    }
    if (filters.resolved !== null && filters.resolved !== '') {
      list = list.filter(a => a.resolved === filters.resolved)
    }

    alertList.value = list
    pagination.total = res.data.total || 0
    alertStore.setAlerts(list)
  } catch (error) {
    console.error('加载告警列表失败:', error)
  } finally {
    loading.value = false
  }
}

function handleResolve(row) {
  currentAlert.value = row
  resolveForm.id = row.id
  resolveForm.note = ''
  resolveForm.resolveType = 'COMPLETED'
  resolveForm.maintenanceType = ''
  resolveForm.faultCategory = ''
  resolveForm.description = row.message || ''
  resolveForm.actionTaken = ''
  resolveDialogVisible.value = true
}

async function submitResolve() {
  resolveLoading.value = true
  try {
    if (resolveForm.resolveType === 'COMPLETED') {
      await resolveAlert(
        resolveForm.id,
        resolveForm.actionTaken || resolveForm.note,
        resolveForm.resolveType,
        resolveForm.maintenanceType,
        resolveForm.faultCategory,
        resolveForm.description
      )
    } else {
      await resolveAlert(resolveForm.id, resolveForm.note, resolveForm.resolveType)
    }
    ElMessage.success('处理成功')
    resolveDialogVisible.value = false
    loadAlerts()
  } catch (error) {
    console.error('处理失败:', error)
  } finally {
    resolveLoading.value = false
  }
}

function handleDetail(row) {
  currentAlert.value = row
  detailDialogVisible.value = true
}

function getLevelType(level) {
  const map = {
    'EMERGENCY': 'danger',
    'CRITICAL': 'danger',
    'HIGH': 'danger',
    'WARNING': 'warning',
    'MEDIUM': 'warning',
    'INFO': 'info',
    'LOW': 'info'
  }
  return map[level] || 'info'
}

function getLevelText(level) {
  const map = {
    'EMERGENCY': '紧急',
    'CRITICAL': '严重',
    'HIGH': '高危',
    'WARNING': '警告',
    'MEDIUM': '中危',
    'INFO': '提示',
    'LOW': '低危'
  }
  return map[level] || level
}

function getProbabilityClass(prob) {
  if (prob >= 0.7) return 'danger'
  if (prob >= 0.4) return 'warning'
  return 'normal'
}

watch(filters, () => {
  pagination.page = 1
  loadAlerts()
}, { deep: true })

onMounted(() => {
  loadAlerts()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title {
  font-family: 'Playfair Display', 'Noto Serif SC', Georgia, serif;
  font-size: 18px;
  font-weight: 700;
  color: #2d2a26;
}

.filters {
  display: flex;
  align-items: center;
}

.device-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.device-icon {
  color: #0077b6;
}

.probability-value {
  font-family: 'IBM Plex Mono', monospace;
  font-weight: 600;
}

.probability-value.normal { color: #2d936c; }
.probability-value.warning { color: #e85d04; }
.probability-value.danger { color: #d62828; }

.time-value {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 12px;
  color: #9a948c;
}

.action-buttons {
  display: flex;
  gap: 4px;
}

:deep(.el-pagination) {
  margin-top: 20px;
}
</style>
