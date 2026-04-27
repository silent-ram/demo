<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside width="260px" class="aside">
      <!-- Logo区域 -->
      <div class="logo-section">
        <div class="logo-icon">
          <el-icon :size="24"><Monitor /></el-icon>
        </div>
        <div class="logo-text">
          <span class="logo-title">故障预警</span>
          <span class="logo-subtitle">Fault Warning</span>
        </div>
      </div>

      <!-- 菜单 -->
      <el-menu
        :default-active="activeMenu"
        router
        class="sidebar-menu"
        :collapse="false"
      >
        <div
          v-for="(item, index) in filteredMenuItems"
          :key="item.path"
          class="menu-wrapper"
          :class="`animate-fade-left delay-${(index + 1) * 100}`"
        >
          <el-menu-item
            :index="item.path"
            class="menu-item"
          >
            <el-icon class="menu-icon">
              <component :is="item.icon" />
            </el-icon>
            <span class="menu-label">{{ item.label }}</span>
            <el-badge
              v-if="item.badge && getBadgeCount(item.badge) > 0"
              :value="getBadgeCount(item.badge)"
              class="menu-badge"
              :max="99"
            />
          </el-menu-item>
        </div>
      </el-menu>

      <!-- 侧边栏底部 -->
      <div class="sidebar-footer animate-fade-in delay-800">
        <div class="status-indicator">
          <span class="status-dot" :class="wsConnected ? 'success' : 'warning'"></span>
          <span class="status-text">{{ wsConnected ? '系统运行正常' : '连接中...' }}</span>
        </div>
      </div>
    </el-aside>

    <!-- 主内容区 -->
    <el-container class="main-container">
      <!-- 头部 -->
      <el-header class="header">
        <div class="header-left">
          <el-breadcrumb separator-class="el-icon-arrow-right" class="breadcrumb">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">
              <el-icon><House /></el-icon>
              首页
            </el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <!-- 当前时间 -->
          <div class="time-display">
            <el-icon><Clock /></el-icon>
            <span>{{ currentTime }}</span>
          </div>

          <!-- 用户信息 -->
          <el-dropdown @command="handleCommand" trigger="hover">
            <div class="user-info">
              <el-avatar :size="38" class="user-avatar">
                {{ userStore.username?.charAt(0).toUpperCase() }}
              </el-avatar>
              <div class="user-detail">
                <span class="username">{{ userStore.username }}</span>
                <el-tag
                  size="small"
                  :type="userStore.role === 'ADMIN' ? 'danger' : 'info'"
                  effect="dark"
                  round
                  class="role-tag"
                >
                  {{ userStore.role === 'ADMIN' ? '管理员' : '运维人员' }}
                </el-tag>
              </div>
              <el-icon class="arrow-icon"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
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

const currentTime = ref('')
const wsConnected = ref(false)
let timeTimer = null

const menuItems = [
  { path: '/dashboard', label: '系统首页', icon: 'DataBoard' },
  { path: '/monitoring', label: '实时监控', icon: 'View' },
  { path: '/devices', label: '设备管理', icon: 'Setting' },
  { path: '/alerts', label: '告警管理', icon: 'Bell', badge: 'alert' },
  { path: '/messages', label: '消息中心', icon: 'Message', badge: 'message' },
  { path: '/maintenance', label: '维修记录', icon: 'Tools' },
  { path: '/model', label: '模型管理', icon: 'TrendCharts', adminOnly: true },
  { path: '/alert-statistics', label: '告警统计', icon: 'DataAnalysis' },
  { path: '/users', label: '用户管理', icon: 'User', adminOnly: true },
  { path: '/operation-log', label: '操作日志', icon: 'Document', adminOnly: true },
]

const filteredMenuItems = computed(() =>
  menuItems.filter(item => !item.adminOnly || userStore.role === 'ADMIN')
)

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta.title || '')

function getBadgeCount(type) {
  if (type === 'alert') return alertStore.unreadCount
  if (type === 'message') return messageStore.unreadCount
  return 0
}

function handleCommand(command) {
  if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}

function updateTime() {
  const now = new Date()
  const hours = String(now.getHours()).padStart(2, '0')
  const minutes = String(now.getMinutes()).padStart(2, '0')
  const seconds = String(now.getSeconds()).padStart(2, '0')
  currentTime.value = `${hours}:${minutes}:${seconds}`
}

function updateWsStatus() {
  wsConnected.value = alertStore.wsConnected
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
  updateTime()
  timeTimer = setInterval(() => {
    updateTime()
    updateWsStatus()
  }, 1000)
  loadUnreadCounts()
  updateWsStatus()
})

onUnmounted(() => {
  if (timeTimer) clearInterval(timeTimer)
})
</script>

<style scoped>
.layout-container {
  height: 100vh;
  display: flex;
}

/* 侧边栏 */
.aside {
  background: #e8e4de;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
  box-shadow: 2px 0 12px rgba(45, 42, 38, 0.08);
}

/* 侧边栏顶部装饰线 */
.aside::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, #e85d04, #0077b6, #2d936c);
}

/* Logo */
.logo-section {
  height: 85px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 0 20px;
  border-bottom: 1px solid rgba(45, 42, 38, 0.1);
}

.logo-icon {
  width: 46px;
  height: 46px;
  background: linear-gradient(135deg, #e85d04, #ff7b3d);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  box-shadow: 0 4px 15px rgba(232, 93, 4, 0.35);
}

.logo-text {
  display: flex;
  flex-direction: column;
}

.logo-title {
  color: #2d2a26;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 2px;
}

.logo-subtitle {
  color: rgba(45, 42, 38, 0.5);
  font-size: 10px;
  letter-spacing: 2px;
  text-transform: uppercase;
  margin-top: 2px;
}

/* 菜单 */
.sidebar-menu {
  flex: 1;
  background: transparent !important;
  border-right: none !important;
  padding: 16px 12px;
}

.menu-wrapper {
  margin-bottom: 4px;
}

.sidebar-menu :deep(.el-menu-item) {
  height: 48px;
  line-height: 48px;
  margin-bottom: 0;
  border-radius: 10px;
  color: #5c5750 !important;
  background: transparent !important;
  transition: all 0.25s ease !important;
  padding: 0 16px !important;
  position: relative;
}

.sidebar-menu :deep(.el-menu-item::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 0;
  background: linear-gradient(180deg, #e85d04, #ff7b3d);
  border-radius: 0 3px 3px 0;
  transition: height 0.25s ease;
  opacity: 0;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background: rgba(45, 42, 38, 0.06) !important;
  color: #2d2a26 !important;
}

.sidebar-menu :deep(.el-menu-item:hover::before) {
  height: 20px;
  opacity: 0.5;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: rgba(232, 93, 4, 0.12) !important;
  color: #e85d04 !important;
}

.sidebar-menu :deep(.el-menu-item.is-active::before) {
  height: 28px;
  opacity: 1;
}

.sidebar-menu :deep(.el-menu-item.is-active .menu-icon) {
  color: #e85d04;
}

.menu-icon {
  font-size: 18px;
  margin-right: 12px;
  transition: color 0.25s ease;
}

.menu-label {
  font-size: 14px;
  font-weight: 500;
}

.menu-badge {
  position: absolute;
  right: 14px;
  top: 50%;
  transform: translateY(-50%);
}

/* 侧边栏底部 */
.sidebar-footer {
  padding: 16px;
  margin-top: auto;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: rgba(45, 42, 38, 0.05);
  border: 1px solid rgba(45, 42, 38, 0.08);
  border-radius: 10px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-dot.success {
  background: #2d936c;
  box-shadow: 0 0 8px rgba(45, 147, 108, 0.5);
}

.status-dot.warning {
  background: #f4a261;
  box-shadow: 0 0 8px rgba(244, 162, 97, 0.5);
}

.status-text {
  color: rgba(45, 42, 38, 0.5);
  font-size: 12px;
}

/* 头部 */
.header {
  background: var(--card-bg) !important;
  box-shadow: 0 2px 12px rgba(45, 42, 38, 0.06) !important;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 30px !important;
  height: 72px !important;
  border-bottom: 1px solid var(--card-border);
}

.header-left {
  display: flex;
  align-items: center;
}

.breadcrumb :deep(.el-breadcrumb__item) {
  font-size: 14px;
}

.breadcrumb :deep(.el-breadcrumb__inner) {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  transition: color 0.2s ease;
}

.breadcrumb :deep(.el-breadcrumb__inner:hover) {
  color: var(--accent-orange);
}

.breadcrumb :deep(.el-breadcrumb__separator) {
  color: var(--text-muted);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.time-display {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 14px;
  font-family: var(--font-mono);
  padding: 8px 16px;
  background: var(--bg-secondary);
  border-radius: 20px;
  transition: all 0.2s ease;
}

.time-display:hover {
  background: var(--bg-tertiary);
}

.time-display .el-icon {
  color: var(--accent-orange);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  padding: 6px 14px;
  background: var(--bg-secondary);
  border-radius: 24px;
  transition: all 0.2s ease;
}

.user-info:hover .arrow-icon {
  transform: translateY(2px);
}

.el-dropdown-menu__item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-info:hover {
  background: var(--bg-tertiary);
}

.user-avatar {
  background: linear-gradient(135deg, #e85d04, #ff7b3d) !important;
  font-size: 16px;
}

.user-detail {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.username {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.role-tag {
  transform: scale(0.8);
  margin-left: -4px;
  margin-top: 2px;
  background: linear-gradient(135deg, #e85d04, #f4a261) !important;
  border: none !important;
  color: #fff !important;
}

.role-tag.el-tag--info {
  background: linear-gradient(135deg, #0077b6, #00a8e8) !important;
}

.arrow-icon {
  color: var(--text-muted);
  font-size: 12px;
  transition: transform 0.2s ease;
}

.user-info:hover .arrow-icon {
  transform: translateY(2px);
}

/* 主内容区 */
.main-container {
  flex-direction: column;
  background: var(--bg-primary);
}

.main {
  background: var(--bg-primary);
  padding: 24px;
  min-height: calc(100vh - 72px);
}

/* 页面过渡动画 */
.page-enter-active,
.page-leave-active {
  transition: all 0.35s ease;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(15px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
