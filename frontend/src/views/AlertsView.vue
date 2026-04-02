<template>
  <div class="alerts">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>告警列表</span>
          <div class="filters">
            <el-select v-model="filters.level" placeholder="告警级别" clearable style="width: 120px; margin-right: 10px;">
              <el-option label="高危" value="HIGH" />
              <el-option label="中危" value="MEDIUM" />
              <el-option label="低危" value="LOW" />
            </el-select>
            <el-select v-model="filters.resolved" placeholder="状态" clearable style="width: 120px;">
              <el-option label="待处理" :value="false" />
              <el-option label="已处理" :value="true" />
            </el-select>
          </div>
        </div>
      </template>
      
      <el-table :data="alertList" v-loading="loading" stripe>
        <el-table-column prop="deviceName" label="设备名称" width="150" />
        <el-table-column prop="alertLevel" label="告警级别" width="100">
          <template #default="{ row }">
            <el-tag :type="getLevelType(row.alertLevel)">{{ getLevelText(row.alertLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="faultProbability" label="故障概率" width="120">
          <template #default="{ row }">
            {{ (row.faultProbability * 100).toFixed(1) }}%
          </template>
        </el-table-column>
        <el-table-column prop="message" label="告警信息" />
        <el-table-column prop="createdAt" label="告警时间" width="180" />
        <el-table-column prop="resolved" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.resolved ? 'success' : 'warning'">
              {{ row.resolved ? '已处理' : '待处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button v-if="!row.resolved" type="primary" link @click="handleResolve(row)">处理</el-button>
            <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 20px; justify-content: flex-end;"
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
        <el-form-item label="处理备注">
          <el-input v-model="resolveForm.note" type="textarea" :rows="3" placeholder="请输入处理备注" />
        </el-form-item>
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
          <el-tag :type="getLevelType(currentAlert.alertLevel)">{{ getLevelText(currentAlert.alertLevel) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="故障概率">{{ (currentAlert.faultProbability * 100).toFixed(1) }}%</el-descriptions-item>
        <el-descriptions-item label="告警信息">{{ currentAlert.message }}</el-descriptions-item>
        <el-descriptions-item label="告警时间">{{ currentAlert.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="currentAlert.resolved ? 'success' : 'warning'">
            {{ currentAlert.resolved ? '已处理' : '待处理' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="currentAlert.resolved" label="处理备注">{{ currentAlert.resolveNote }}</el-descriptions-item>
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
  resolveType: 'COMPLETED'
})

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
  resolveDialogVisible.value = true
}

async function submitResolve() {
  resolveLoading.value = true
  try {
    await resolveAlert(resolveForm.id, resolveForm.note, resolveForm.resolveType)
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
  const map = { 'HIGH': 'danger', 'MEDIUM': 'warning', 'LOW': 'info' }
  return map[level] || 'info'
}

function getLevelText(level) {
  const map = { 'HIGH': '高危', 'MEDIUM': '中危', 'LOW': '低危' }
  return map[level] || level
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

.filters {
  display: flex;
  align-items: center;
}
</style>
