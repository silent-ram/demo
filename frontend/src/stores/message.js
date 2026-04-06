import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useMessageStore = defineStore('message', () => {
  const messages = ref([])
  const unreadCount = ref(0)

  const unreadMessages = computed(() => messages.value.filter(m => !m.read))

  function setMessages(list) {
    messages.value = list
    unreadCount.value = list.filter(m => !m.read).length
  }

  function addMessage(message) {
    messages.value.unshift(message)
    if (!message.read) {
      unreadCount.value++
    }
  }

  function markAsRead(id) {
    const message = messages.value.find(m => m.id === id)
    if (message && !message.read) {
      message.read = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    }
  }

  function markAllAsRead() {
    messages.value.forEach(m => {
      m.read = true
    })
    unreadCount.value = 0
  }

  function clearMessages() {
    messages.value = []
    unreadCount.value = 0
  }

  return {
    messages,
    unreadCount,
    unreadMessages,
    setMessages,
    addMessage,
    markAsRead,
    markAllAsRead,
    clearMessages
  }
})