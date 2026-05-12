import request from '@/utils/request'

export function predict(data) {
  return request({
    url: '/ml/predict',
    method: 'post',
    data
  })
}

export function getTrendChart(deviceId, points = 100) {
  return request({
    url: '/ml/chart/trend',
    method: 'get',
    params: { deviceId, points }
  })
}

export function getFaultProbabilityChart(deviceId, deviceName = '', hours = 24) {
  return request({
    url: '/ml/chart/fault-probability',
    method: 'get',
    params: { deviceId, deviceName, hours }
  })
}

export function getModelMetrics() {
  return request({
    url: '/ml/model/metrics',
    method: 'get'
  })
}

export function retrainModel(deviceType) {
  return request({
    url: '/ml/model/retrain',
    method: 'post',
    headers: { 'X-API-Key': 'myDefaultMLApiKey' },
    data: deviceType ? { device_type: deviceType } : {}
  })
}

export function getModelVersions() {
  return request({
    url: '/ml/model/versions',
    method: 'get'
  })
}

export function rollbackModel(data) {
  return request({
    url: '/ml/model/rollback',
    method: 'post',
    headers: { 'X-API-Key': 'myDefaultMLApiKey' },
    data
  })
}

export function healthCheck() {
  return request({
    url: '/ml/health',
    method: 'get'
  })
}

export function getDataset(params) {
  return request({
    url: '/ml/dataset',
    method: 'get',
    params
  })
}
