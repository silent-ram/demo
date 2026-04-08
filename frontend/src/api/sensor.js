import request from '@/utils/request'

export function getSensorConfigList(params) {
  return request({
    url: '/sensor-config',
    method: 'get',
    params
  })
}

export function getSensorConfig(id) {
  return request({
    url: `/sensor-config/${id}`,
    method: 'get'
  })
}

export function createSensorConfig(data) {
  return request({
    url: '/sensor-config',
    method: 'post',
    data
  })
}

export function updateSensorConfig(id, data) {
  return request({
    url: `/sensor-config/${id}`,
    method: 'put',
    data
  })
}

export function deleteSensorConfig(id) {
  return request({
    url: `/sensor-config/${id}`,
    method: 'delete'
  })
}
