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

export function getModelMetrics() {
  return request({
    url: '/ml/model/metrics',
    method: 'get'
  })
}

export function retrainModel() {
  return request({
    url: '/ml/model/retrain',
    method: 'post'
  })
}

export function healthCheck() {
  return request({
    url: '/ml/health',
    method: 'get'
  })
}
