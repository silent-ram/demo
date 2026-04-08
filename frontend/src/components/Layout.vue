<template>
  <el-container class="layout-container">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <el-icon size="24"><Monitor /></el-icon>
        <span>故障预警系统</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon>
          <span>系统首页</span>
        </el-menu-item>
        <el-menu-item index="/monitoring">
          <el-icon><View /></el-icon>
          <span>实时监控</span>
        </el-menu-item>
        <el-menu-item index="/devices">
          <el-icon><Setting /></el-icon>
          <span>设备管理</span>
        </el-menu-item>
        <el-menu-item index="/alerts">
          <el-icon><Bell /></el-icon>
          <span>告警管理</span>
          <el-badge v-if="alertStore.unreadCount > 0" :value="alertStore.unreadCount" class="menu-badge" />
        </el-menu-item>
        <el-menu-item index="/messages">
          <el-icon><Message /></el-icon>
          <span>消息中心</span>
          <el-badge v-if="messageStore.unreadCount > 0" :value="messageStore.unreadCount" class="menu-badge" />
        </el-menu-item>
        <el-menu-item index="/maintenance">
          <el-icon><Tools /></el-icon>
          <span>维修记录</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.role === 'ADMIN'" index="/model">
          <el-icon><TrendCharts /></el-icon>
          <span>模型管理</span>
        </el-menu-item>
        <el-menu-item index="/alert-statistics">
          <el-icon><DataAnalysis /></el-icon>
          <span>告警统计</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.role === 'ADMIN'" index="/sensor-config">
          <el-icon><Setting /></el-icon>
          <span>传感器配置</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.role === 'ADMIN'" index="/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.role === 'ADMIN'" index="/operation-log">
          <el-icon><Document /></el-icon>
          <span>操作日志</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown>
            <span class="user-info">
              <el-avatar :size="32" icon="User" />
              <span class="username">{{ userStore.username }}</span>
              <el-tag size="small" :type="userStore.role === 'ADMIN' ? 'danger' : 'info'">
                {{ userStore.role === 'ADMIN' ? '管理员' : '运维人员' }}
              </el-tag>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAlertStore } from '@/stores/alert'
import { useMessageStore } from '@/stores/message'
import { getMessageList } from '@/api/message'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const alertStore = useAlertStore()
const messageStore = useMessageStore()

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta.title || '')

function handleLogout() {
  userStore.logout()
  router.push('/login')
}

async function loadUnreadCounts() {
  if (!userStore.userInfo?.id) return
  try {
    const res = await getMessageList(userStore.userInfo.id)
    messageStore.setMessages(res.data || [])
  } catch (error) {
    console.error('获取消息列表失败:', error)
  }
}

onMounted(() => {
  loadUnreadCounts()
})
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.aside {
  background-color: #304156;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  border-bottom: 1px solid #3a4a5b;
}

.el-menu {
  border-right: none;
}

.menu-badge {
  margin-left: 10px;
}

.header {
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.username {
  font-size: 14px;
}

.main {
  background-color: #f0f2f5;
  padding: 20px;
}
</style>
