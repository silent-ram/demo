<template>
  <div class="sensor-config">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>传感器配置</span>
          <el-button v-if="userStore.role === 'ADMIN'" type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增配置
          </el-button>
        </div>
      </template>

      <el-table :data="configList" v-loading="loading" stripe>
        <el-table-column prop="deviceType" label="设备类型" width="150" />
        <el-table-column prop="sensorCode" label="传感器代码" width="120" />
        <el-table-column prop="alertThreshold" label="告警阈值" width="100" />
        <el-table-column prop="enabled" label="启用状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
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
        @size-change="loadConfigs"
        @current-change="loadConfigs"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="设备类型" prop="deviceType">
          <el-input v-model="form.deviceType" placeholder="请输入设备类型，如：水泵设备" />
        </el-form-item>
        <el-form-item label="传感器代码" prop="sensorCode">
          <el-select v-model="form.sensorCode" placeholder="请选择传感器" style="width: 100%;">
            <el-option v-for="s in sensorTypes" :key="s.code" :label="`${s.name} (${s.code})`" :value="s.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用状态" prop="enabled">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item label="告警阈值" prop="alertThreshold">
          <el-input-number v-model="form.alertThreshold" :min="0" :max="1000" style="width: 100%;" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSensorConfigList, createSensorConfig, updateSensorConfig, deleteSensorConfig } from '@/api/sensor'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const configList = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增配置')
const submitLoading = ref(false)
const formRef = ref(null)

const pagination = reactive({ page: 1, size: 10, total: 0 })

const form = reactive({
  id: null,
  deviceType: '',
  sensorCode: '',
  enabled: true,
  alertThreshold: 0
})

const rules = {
  deviceType: [{ required: true, message: '请输入设备类型', trigger: 'blur' }],
  sensorCode: [{ required: true, message: '请选择传感器', trigger: 'change' }]
}

const sensorTypes = [
  { code: 'temperature', name: '温度' },
  { code: 'vibration', name: '振动' },
  { code: 'pressure', name: '压力' }
]

async function loadConfigs() {
  loading.value = true
  try {
    const res = await getSensorConfigList({ page: pagination.page, size: pagination.size })
    configList.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (error) {
    console.error('加载配置失败:', error)
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  dialogTitle.value = '新增配置'
  Object.assign(form, { id: null, deviceType: '', sensorCode: '', enabled: true, alertThreshold: 0 })
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogTitle.value = '编辑配置'
  Object.assign(form, row)
  dialogVisible.value = true
}

async function handleDelete(row) {
  await ElMessageBox.confirm('确定要删除该配置吗？', '提示', { type: 'warning' })
  try {
    await deleteSensorConfig(row.id)
    ElMessage.success('删除成功')
    loadConfigs()
  } catch (error) {
    console.error('删除失败:', error)
  }
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    if (form.id) {
      await updateSensorConfig(form.id, form)
      ElMessage.success('更新成功')
    } else {
      await createSensorConfig(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadConfigs()
  } catch (error) {
    console.error('提交失败:', error)
  } finally {
    submitLoading.value = false
  }
}

onMounted(() => { loadConfigs() })
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
