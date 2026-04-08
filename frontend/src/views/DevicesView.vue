<template>
  <div class="devices">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>设备列表</span>
          <el-button type="success" @click="handleExport">
            <el-icon><Download /></el-icon>
            导出
          </el-button>
          <el-button v-if="userStore.role === 'ADMIN'" type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增设备
          </el-button>
        </div>
      </template>
      
      <el-table :data="deviceList" v-loading="loading" stripe>
        <el-table-column prop="deviceNo" label="设备编号" width="150" />
        <el-table-column prop="name" label="设备名称" />
        <el-table-column prop="type" label="设备类型" width="120" />
        <el-table-column prop="location" label="安装位置" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250">
          <template #default="{ row }">
            <el-button v-if="row.status === 'OFFLINE' || row.status === 'STANDBY'" type="success" link @click="handleStart(row)">启动</el-button>
            <el-button v-else-if="row.status === 'NORMAL' || row.status === 'RUNNING'" type="warning" link @click="handleStop(row)">停机</el-button>
            <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
            <el-button v-if="userStore.role === 'ADMIN'" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="userStore.role === 'ADMIN'" type="danger" link @click="handleDelete(row)">删除</el-button>
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
        @size-change="loadDevices"
        @current-change="loadDevices"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="设备编号" prop="deviceNo">
          <el-input v-model="form.deviceNo" placeholder="请输入设备编号" />
        </el-form-item>
        <el-form-item label="设备名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入设备名称" />
        </el-form-item>
        <el-form-item label="设备类型" prop="type">
          <el-input v-model="form.type" placeholder="请输入设备类型" />
        </el-form-item>
        <el-form-item label="安装位置" prop="location">
          <el-input v-model="form.location" placeholder="请输入安装位置" />
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDeviceList, createDevice, updateDevice, deleteDevice, updateDeviceStatus, updateDeviceSimulation } from '@/api/device'
import { exportDeviceReport } from '@/api/report'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const deviceList = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增设备')
const submitLoading = ref(false)
const formRef = ref(null)

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const form = reactive({
  id: null,
  deviceNo: '',
  name: '',
  type: '',
  location: ''
})

const rules = {
  deviceNo: [{ required: true, message: '请输入设备编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入设备名称', trigger: 'blur' }]
}

async function loadDevices() {
  loading.value = true
  try {
    const res = await getDeviceList({ page: pagination.page, size: pagination.size })
    deviceList.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (error) {
    console.error('加载设备列表失败:', error)
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  dialogTitle.value = '新增设备'
  Object.assign(form, { id: null, deviceNo: '', name: '', type: '', location: '' })
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogTitle.value = '编辑设备'
  Object.assign(form, row)
  dialogVisible.value = true
}

function handleDetail(row) {
  router.push(`/device/${row.id}`)
}

async function handleDelete(row) {
  await ElMessageBox.confirm('确定要删除该设备吗？', '提示', { type: 'warning' })
  try {
    await deleteDevice(row.id)
    ElMessage.success('删除成功')
    loadDevices()
  } catch (error) {
    console.error('删除失败:', error)
  }
}

async function handleStart(row) {
  try {
    await updateDeviceStatus(row.id, 'RUNNING')
    // 设备启动时自动开启模拟
    await updateDeviceSimulation(row.id, true)
    ElMessage.success('设备已启动')
    loadDevices()
  } catch (error) {
    console.error('启动失败:', error)
  }
}

async function handleStop(row) {
  try {
    await updateDeviceStatus(row.id, 'OFFLINE')
    // 设备停机时自动停止模拟
    await updateDeviceSimulation(row.id, false)
    ElMessage.success('设备已停机')
    loadDevices()
  } catch (error) {
    console.error('停机失败:', error)
  }
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    if (form.id) {
      await updateDevice(form.id, form)
      ElMessage.success('更新成功')
    } else {
      await createDevice(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadDevices()
  } catch (error) {
    console.error('提交失败:', error)
  } finally {
    submitLoading.value = false
  }
}

async function handleExport() {
  try {
    const res = await exportDeviceReport()
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `设备台账_${new Date().getTime()}.xlsx`
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败')
  }
}

function getStatusType(status) {
  const map = {
    'NORMAL': 'success',
    'RUNNING': 'primary',
    'STANDBY': 'info',
    'MAINTENANCE': 'warning',
    'FAULT': 'danger',
    'OFFLINE': 'info'
  }
  return map[status] || 'info'
}

function getStatusText(status) {
  const map = {
    'NORMAL': '运行中',
    'RUNNING': '运行中',
    'STANDBY': '待机',
    'MAINTENANCE': '维护中',
    'FAULT': '故障',
    'OFFLINE': '离线'
  }
  return map[status] || status
}

onMounted(() => {
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
