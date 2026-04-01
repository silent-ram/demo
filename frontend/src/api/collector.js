import request from '@/utils/request'

export function getMetrics(deviceId, params) {
  return request({
    url: `/collector/metrics/${deviceId}`,
    method: 'get',
    params
  })
}

export function getLatestMetric(deviceId) {
  return request({
    url: `/collector/latest/${deviceId}`,
    method: 'get'
  })
}

export function getAllMetrics() {
  return request({
    url: '/collector/metrics',
    method: 'get'
  })
}

export function startSimulation() {
  return request({
    url: '/collector/simulate/start',
    method: 'post'
  })
}

export function stopSimulation() {
  return request({
    url: '/collector/simulate/stop',
    method: 'post'
  })
}

export function getSimulationStatus() {
  return request({
    url: '/collector/simulate/status',
    method: 'get'
  })
}
