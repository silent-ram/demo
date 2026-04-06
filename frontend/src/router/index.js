import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/components/Layout.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/DashboardView.vue'),
        meta: { title: '系统首页' }
      },
      {
        path: 'devices',
        name: 'Devices',
        component: () => import('@/views/DevicesView.vue'),
        meta: { title: '设备管理' }
      },
      {
        path: 'device/:id',
        name: 'DeviceDetail',
        component: () => import('@/views/DeviceDetailView.vue'),
        meta: { title: '设备详情' }
      },
      {
        path: 'monitoring',
        name: 'Monitoring',
        component: () => import('@/views/MonitoringView.vue'),
        meta: { title: '实时监控' }
      },
      {
        path: 'alerts',
        name: 'Alerts',
        component: () => import('@/views/AlertsView.vue'),
        meta: { title: '告警管理' }
      },
      {
        path: 'messages',
        name: 'Messages',
        component: () => import('@/views/MessageCenterView.vue'),
        meta: { title: '消息中心' }
      },
      {
        path: 'maintenance',
        name: 'Maintenance',
        component: () => import('@/views/MaintenanceView.vue'),
        meta: { title: '维修记录' }
      },
      {
        path: 'model',
        name: 'Model',
        component: () => import('@/views/ModelView.vue'),
        meta: { title: '模型管理', requiresAdmin: true }
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('@/views/UsersView.vue'),
        meta: { title: '用户管理', requiresAdmin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  const token = userStore.token || localStorage.getItem('token')
  
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.meta.requiresAdmin && userStore.role !== 'ADMIN') {
    next('/dashboard')
  } else if (to.path === '/login' && token) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
