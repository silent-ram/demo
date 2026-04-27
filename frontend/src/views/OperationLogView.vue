<template>
  <div class="operation-log">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="title">操作日志</span>
          <div class="filters">
            <el-select v-model="filters.operationType" placeholder="操作类型" clearable style="width: 150px;" @change="loadLogs">
              <el-option v-for="t in operationTypes" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </div>
        </div>
      </template>

      <el-table :data="logList" v-loading="loading" stripe>
        <el-table-column prop="username" label="用户名" width="120">
          <template #default="{ row }">
            <div class="user-cell">
              <el-icon class="user-icon"><User /></el-icon>
              <span>{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="operationType" label="操作类型" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ getTypeText(row.operationType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operationContent" label="操作内容" />
        <el-table-column prop="ipAddress" label="IP地址" width="150">
          <template #default="{ row }">
            <span class="ip-value">{{ row.ipAddress }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="操作时间" width="180">
          <template #default="{ row }">
            <span class="time-value">{{ row.createdAt }}</span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadLogs"
        @current-change="loadLogs"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getOperationLogList } from '@/api/operationLog'

const loading = ref(false)
const logList = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const filters = reactive({ operationType: '' })

const operationTypes = [
  { value: 'LOGIN', label: '登录' }, { value: 'LOGOUT', label: '登出' },
  { value: 'DEVICE', label: '设备操作' }, { value: 'ALERT', label: '告警处理' },
  { value: 'MAINTENANCE', label: '维修记录' }, { value: 'CONFIG', label: '系统配置' }
]

async function loadLogs() {
  loading.value = true
  try {
    const params = { page: pagination.page, size: pagination.size }
    if (filters.operationType) params.operationType = filters.operationType
    const res = await getOperationLogList(params)
    logList.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (error) { console.error('加载日志失败:', error) }
  finally { loading.value = false }
}

function getTypeText(type) {
  const map = { 'LOGIN': '登录', 'LOGOUT': '登出', 'DEVICE': '设备操作', 'ALERT': '告警处理', 'MAINTENANCE': '维修记录', 'CONFIG': '系统配置' }
  return map[type] || type
}

onMounted(() => loadLogs())
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.title { font-family: 'Playfair Display', Georgia, serif; font-size: 18px; font-weight: 700; color: #2d2a26; }
.filters { display: flex; gap: 10px; }
.user-cell { display: flex; align-items: center; gap: 8px; }
.user-icon { color: #0077b6; }
.ip-value { font-family: 'IBM Plex Mono', monospace; font-size: 12px; color: #5c5750; }
.time-value { font-family: 'IBM Plex Mono', monospace; font-size: 12px; color: #9a948c; }
:deep(.el-pagination) { margin-top: 20px; }
</style>
