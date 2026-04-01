<template>
  <div class="maintenance">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>维修记录</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增记录
          </el-button>
        </div>
      </template>
      
      <el-table :data="maintenanceList" v-loading="loading" stripe>
        <el-table-column prop="type" label="维修类型" width="120" />
        <el-table-column prop="description" label="故障描述" />
        <el-table-column prop="actionTaken" label="处理措施" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="repairedAt" label="维修时间" width="180" />
      </el-table>
      
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 20px; justify-content: flex-end;"
        @size-change="loadMaintenance"
        @current-change="loadMaintenance"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增维修记录" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="设备" prop="deviceId">
          <el-select v-model="form.deviceId" placeholder="请选择设备" style="width: 100%;">
            <el-option v-for="device in deviceList" :key="device.id" :label="device.name" :value="device.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="维修类型" prop="type">
          <el-input v-model="form.type" placeholder="请输入维修类型" />
        </el-form-item>
        <el-form-item label="故障描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入故障描述" />
        </el-form-item>
        <el-form-item label="处理措施" prop="actionTaken">
          <el-input v-model="form.actionTaken" type="textarea" :rows="3" placeholder="请输入处理措施" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMaintenanceList, createMaintenance } from '@/api/device'
import { getDeviceList } from '@/api/device'

const loading = ref(false)
const maintenanceList = ref([])
const deviceList = ref([])
const dialogVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const form = reactive({
  deviceId: null,
  type: '',
  description: '',
  actionTaken: '',
  status: 'COMPLETED'
})

const rules = {
  deviceId: [{ required: true, message: '请选择设备', trigger: 'change' }],
  type: [{ required: true, message: '请输入维修类型', trigger: 'blur' }],
  description: [{ required: true, message: '请输入故障描述', trigger: 'blur' }]
}

async function loadMaintenance() {
  loading.value = true
  try {
    const res = await getMaintenanceList({ page: pagination.page, size: pagination.size })
    maintenanceList.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (error) {
    console.error('加载维修记录失败:', error)
  } finally {
    loading.value = false
  }
}

async function loadDevices() {
  try {
    const res = await getDeviceList({ page: 1, size: 100 })
    deviceList.value = res.data.records || []
  } catch (error) {
    console.error('加载设备列表失败:', error)
  }
}

function handleAdd() {
  Object.assign(form, { deviceId: null, type: '', description: '', actionTaken: '', status: 'COMPLETED' })
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    await createMaintenance(form)
    ElMessage.success('创建成功')
    dialogVisible.value = false
    loadMaintenance()
  } catch (error) {
    console.error('提交失败:', error)
  } finally {
    submitLoading.value = false
  }
}

function getStatusType(status) {
  const map = { 'PENDING': 'info', 'IN_PROGRESS': 'warning', 'COMPLETED': 'success' }
  return map[status] || 'info'
}

function getStatusText(status) {
  const map = { 'PENDING': '待处理', 'IN_PROGRESS': '处理中', 'COMPLETED': '已完成' }
  return map[status] || status
}

onMounted(() => {
  loadMaintenance()
  loadDevices()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
