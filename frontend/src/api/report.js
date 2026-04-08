import request from '@/utils/request'

export function exportDeviceReport() {
  return request({
    url: '/report/export/device',
    method: 'get',
    responseType: 'blob'
  })
}

export function exportAlertReport(params) {
  return request({
    url: '/report/export/alert',
    method: 'get',
    params,
    responseType: 'blob'
  })
}

export function exportMaintenanceReport(params) {
  return request({
    url: '/report/export/maintenance',
    method: 'get',
    params,
    responseType: 'blob'
  })
}
