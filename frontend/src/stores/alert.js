import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAlertStore = defineStore('alert', () => {
  const alerts = ref([])
  const unreadCount = ref(0)
  const wsConnected = ref(false)

  const activeAlerts = computed(() => alerts.value.filter(a => !a.resolved))
  const resolvedAlerts = computed(() => alerts.value.filter(a => a.resolved))

  function addAlert(alert) {
    alerts.value.unshift(alert)
    if (!alert.resolved) {
      unreadCount.value++
    }
  }

  function setAlerts(list) {
    alerts.value = list
    unreadCount.value = list.filter(a => !a.resolved).length
  }

  function resolveAlert(id) {
    const alert = alerts.value.find(a => a.id === id)
    if (alert) {
      alert.resolved = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    }
  }

  function setWsConnected(connected) {
    wsConnected.value = connected
  }

  function clearAlerts() {
    alerts.value = []
    unreadCount.value = 0
  }

  return {
    alerts,
    unreadCount,
    wsConnected,
    activeAlerts,
    resolvedAlerts,
    addAlert,
    setAlerts,
    resolveAlert,
    setWsConnected,
    clearAlerts
  }
})
