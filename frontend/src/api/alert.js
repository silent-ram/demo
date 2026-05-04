import request from '@/utils/request'

export function getAlertList(params) {
  return request({
    url: '/alert',
    method: 'get',
    params
  })
}

export function getAlert(id) {
  return request({
    url: `/alert/${id}`,
    method: 'get'
  })
}

export function resolveAlert(id, resolveNote = '', resolveType = 'COMPLETED', maintenanceType = '', faultCategory = '', description = '') {
  return request({
    url: `/alert/${id}/resolve`,
    method: 'put',
    params: {
      resolveNote,
      resolveType,
      maintenanceType,
      faultCategory,
      description
    }
  })
}

export function getActiveAlerts() {
  return request({
    url: '/alert/active',
    method: 'get'
  })
}

export function getResolvedAlerts() {
  return request({
    url: '/alert/resolved',
    method: 'get'
  })
}

export function getAlertStats() {
  return request({
    url: '/alert/stats',
    method: 'get'
  })
}

export function getConfig() {
  return request({
    url: '/alert/config',
    method: 'get'
  })
}

export function updateConfig(data) {
  return request({
    url: '/alert/config',
    method: 'put',
    data
  })
}

export function getThreshold() {
  return request({
    url: '/alert/threshold',
    method: 'get'
  })
}

export function updateThreshold(value) {
  return request({
    url: '/alert/threshold',
    method: 'put',
    data: value
  })
}

export function getChart(deviceId) {
  return request({
    url: `/alert/chart/${deviceId}`,
    method: 'get'
  })
}

export function getFrequencyStatistics(params) {
  return request({
    url: '/alert/statistics/frequency',
    method: 'get',
    params
  })
}

export function getFailureRank(params) {
  return request({
    url: '/alert/statistics/failure-rank',
    method: 'get',
    params
  })
}
