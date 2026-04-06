import request from '@/utils/request'

export function getMessageList(userId) {
  return request({
    url: '/api/message/list',
    method: 'get',
    params: { userId }
  })
}

export function getUnreadMessages(userId) {
  return request({
    url: '/api/message/unread',
    method: 'get',
    params: { userId }
  })
}

export function markAsRead(id) {
  return request({
    url: `/api/message/read/${id}`,
    method: 'post'
  })
}

export function markAllAsRead(userId) {
  return request({
    url: '/api/message/read-all',
    method: 'post',
    params: { userId }
  })
}