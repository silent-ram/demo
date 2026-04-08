import request from '@/utils/request'

export function getDeviceList(params) {
  return request({
    url: '/device',
    method: 'get',
    params
  })
}

export function getDevice(id) {
  return request({
    url: `/device/${id}`,
    method: 'get'
  })
}

export function createDevice(data) {
  return request({
    url: '/device',
    method: 'post',
    data
  })
}

export function updateDevice(id, data) {
  return request({
    url: `/device/${id}`,
    method: 'put',
    data
  })
}

export function deleteDevice(id) {
  return request({
    url: `/device/${id}`,
    method: 'delete'
  })
}

export function updateDeviceStatus(id, status) {
  return request({
    url: `/device/${id}/status`,
    method: 'put',
    params: { status }
  })
}

export function updateDeviceSimulation(id, enabled) {
  return request({
    url: `/device/${id}/simulation`,
    method: 'put',
    params: { enabled }
  })
}

export function searchDevices(keyword) {
  return request({
    url: '/device/search',
    method: 'get',
    params: { keyword }
  })
}

export function getMaintenanceList(params) {
  return request({
    url: '/maintenance',
    method: 'get',
    params
  })
}

export function getMaintenance(id) {
  return request({
    url: `/maintenance/${id}`,
    method: 'get'
  })
}

export function createMaintenance(data) {
  return request({
    url: '/maintenance',
    method: 'post',
    data
  })
}

export function updateMaintenance(id, data) {
  return request({
    url: `/maintenance/${id}`,
    method: 'put',
    data
  })
}

export function deleteMaintenance(id) {
  return request({
    url: `/maintenance/${id}`,
    method: 'delete'
  })
}

export function getMaintenancesByDevice(deviceId) {
  return request({
    url: `/maintenance/device/${deviceId}`,
    method: 'get'
  })
}
