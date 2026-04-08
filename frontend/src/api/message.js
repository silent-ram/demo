import request from '@/utils/request'

export function getMessageList(userId) {
  return request({
    url: '/message/list',
    method: 'get',
    params: { userId }
  })
}

export function getUnreadMessages(userId) {
  return request({
    url: '/message/unread',
    method: 'get',
    params: { userId }
  })
}

export function markAsRead(id) {
  return request({
    url: `/message/read/${id}`,
    method: 'post'
  })
}

export function markAllAsRead(userId) {
  return request({
    url: '/message/read-all',
    method: 'post',
    params: { userId }
  })
}
