<template>
  <div class="message-center">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="title">消息中心</span>
          <el-button type="primary" @click="handleMarkAllRead">全部标为已读</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="全部消息" name="all">
          <el-table :data="allMessages" v-loading="loading" stripe>
            <el-table-column prop="title" label="标题" width="200">
              <template #default="{ row }">
                <div class="message-title" :class="{ unread: !row.isRead }">
                  <el-icon v-if="!row.isRead" class="unread-dot"><CircleFilled /></el-icon>
                  <span>{{ row.title }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="type" label="类型" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ getTypeText(row.type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="content" label="内容" />
            <el-table-column prop="createdAt" label="时间" width="180">
              <template #default="{ row }">
                <span class="time-value">{{ row.createdAt }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button v-if="!row.isRead" type="primary" link @click="handleMarkAsRead(row)">标为已读</el-button>
                <span v-else class="read-text">已读</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="未读消息" name="unread">
          <el-table :data="unreadMessages" v-loading="loading" stripe>
            <el-table-column prop="title" label="标题" width="200">
              <template #default="{ row }">
                <div class="message-title unread">
                  <el-icon class="unread-dot"><CircleFilled /></el-icon>
                  <span>{{ row.title }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="type" label="类型" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ getTypeText(row.type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="content" label="内容" />
            <el-table-column prop="createdAt" label="时间" width="180">
              <template #default="{ row }">
                <span class="time-value">{{ row.createdAt }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button type="primary" link @click="handleMarkAsRead(row)">标为已读</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getMessageList, getUnreadMessages, markAsRead, markAllAsRead } from '@/api/message'
import { useUserStore } from '@/stores/user'
import { useMessageStore } from '@/stores/message'

const userStore = useUserStore()
const messageStore = useMessageStore()
const loading = ref(false)
const activeTab = ref('all')
const allMessages = ref([])
const unreadMessages = ref([])

function getTypeText(type) {
  const typeMap = { 'ALERT_NOTIFICATION': '告警通知', 'ALERT_ESCALATION': '告警升级', 'DEVICE_STATUS': '设备状态', 'SYSTEM_NOTIFICATION': '系统通知' }
  return typeMap[type] || type
}

async function loadAllMessages() {
  loading.value = true
  try {
    const res = await getMessageList(userStore.userInfo?.id)
    allMessages.value = res.data || []
    messageStore.setMessages(allMessages.value)
  } catch (error) { console.error('加载消息列表失败:', error) }
  finally { loading.value = false }
}

async function loadUnreadMessages() {
  loading.value = true
  try {
    const res = await getUnreadMessages(userStore.userInfo?.id)
    unreadMessages.value = res.data || []
  } catch (error) { console.error('加载未读消息失败:', error) }
  finally { loading.value = false }
}

async function handleMarkAsRead(row) {
  try {
    await markAsRead(row.id)
    messageStore.markAsRead(row.id)
    ElMessage.success('标记成功')
    loadAllMessages()
    loadUnreadMessages()
  } catch (error) { console.error('标记失败:', error) }
}

async function handleMarkAllRead() {
  try {
    await markAllAsRead(userStore.userInfo?.id)
    messageStore.markAllAsRead()
    ElMessage.success('全部已读')
    loadAllMessages()
    loadUnreadMessages()
  } catch (error) { console.error('标记全部失败:', error) }
}

watch(activeTab, (tabName) => {
  if (tabName === 'all') loadAllMessages()
  else loadUnreadMessages()
})

onMounted(() => loadAllMessages())
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.title { font-family: 'Playfair Display', Georgia, serif; font-size: 18px; font-weight: 700; color: #2d2a26; }
.message-title { display: flex; align-items: center; gap: 8px; color: #5c5750; }
.message-title.unread { color: #2d2a26; font-weight: 600; }
.unread-dot { color: #e85d04; font-size: 10px; }
.time-value { font-family: 'IBM Plex Mono', monospace; font-size: 12px; color: #9a948c; }
.read-text { color: #9a948c; font-size: 12px; }
</style>
