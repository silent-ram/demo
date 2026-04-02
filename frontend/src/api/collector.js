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

export function startDeviceSimulation(deviceId) {
  return request({
    url: `/collector/simulate/device/${deviceId}/start`,
    method: 'post'
  })
}

export function stopDeviceSimulation(deviceId) {
  return request({
    url: `/collector/simulate/device/${deviceId}/stop`,
    method: 'post'
  })
}

export function getDeviceSimulationStatus(deviceId) {
  return request({
    url: `/collector/simulate/device/${deviceId}/status`,
    method: 'get'
  })
}

export function setDeviceManualData(deviceId, values) {
  return request({
    url: `/collector/simulate/device/${deviceId}/set`,
    method: 'post',
    data: values
  })
}

export function clearDeviceManualData(deviceId) {
  return request({
    url: `/collector/simulate/device/${deviceId}/set`,
    method: 'delete'
  })
}

export function setDeviceMode(deviceId, mode) {
  return request({
    url: `/collector/simulate/device/${deviceId}/mode`,
    method: 'post',
    params: { mode }
  })
}

export function insertDeviceData(deviceId, values) {
  return request({
    url: `/collector/simulate/device/${deviceId}/insert`,
    method: 'post',
    data: values
  })
}

export function setDeviceRandomRange(deviceId, ranges) {
  return request({
    url: `/collector/simulate/device/${deviceId}/range`,
    method: 'post',
    data: ranges
  })
}

export function getDeviceMode(deviceId) {
  return request({
    url: `/collector/simulate/device/${deviceId}/mode`,
    method: 'get'
  })
}
