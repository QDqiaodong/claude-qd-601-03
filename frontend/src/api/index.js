import axios from 'axios'

/**
 * 统一走 /api 前缀：
 *  - 容器里由 nginx 把 /api/ 反代到 backend:8080
 *  - 本地 vite dev 由 vite.config.js 的 proxy 转到 127.0.0.1:8361
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json;charset=utf-8' }
})

/** 后端统一返回 {ok, message, data}；失败时把中文业务提示原样抽出来给页面用 */
function messageOf(error) {
  const body = error && error.response ? error.response.data : null
  if (body && typeof body === 'object' && body.message) {
    return body.message
  }
  if (error && error.message) {
    return error.message
  }
  return '网络异常，请稍后重试'
}

async function call(promise) {
  let res
  try {
    res = await promise
  } catch (error) {
    // 409 并发冲突：打上 conflict 标记，页面据此引导用户重新载入最新事件链
    if (error.response && error.response.status === 409) {
      const err = new Error(messageOf(error))
      err.conflict = true
      throw err
    }
    throw new Error(messageOf(error))
  }
  const body = res.data
  if (body && body.ok === false) {
    throw new Error(body.message || '操作失败')
  }
  return body ? body.data : null
}

/**
 * 为一次「点击」生成幂等键：同一次操作的重复点击 / 网络重试共用一个 requestId，
 * 后端据此保证不会产生两条记录、不会重复联动马匹状态。
 */
export function requestId(prefix = 'req') {
  const rand = Math.random().toString(36).slice(2, 10)
  return `${prefix}-${Date.now().toString(36)}-${rand}`
}

export const api = {
  // 模块一：马匹档案（horse 主表 + horse_health 副表）
  horses: () => call(http.get('/horses')),
  horse: (id) => call(http.get(`/horses/${id}`)),
  horseCalendar: (id, days = 7) => call(http.get(`/horses/${id}/calendar`, { params: { days } })),
  createHorse: (body) => call(http.post('/horses', body)),
  updateHorse: (id, body) => call(http.put(`/horses/${id}`, body)),
  changeHorseStatus: (id, status) => call(http.post(`/horses/${id}/status`, { status })),

  // 模块二：马房与栏位
  // 看板快照：顶部汇总 / 马房分组 / 栏位明细由后端同一轮返回，页面整包替换
  stallBoard: () => call(http.get('/stalls/board')),
  stalls: () => call(http.get('/stalls')),
  createStall: (body) => call(http.post('/stalls', body)),
  updateStall: (id, body) => call(http.put(`/stalls/${id}`, body)),
  occupyStall: (id, horseId) => call(http.post(`/stalls/${id}/occupy`, { horseId })),
  releaseStall: (id) => call(http.post(`/stalls/${id}/release`)),
  maintenanceStall: (id) => call(http.post(`/stalls/${id}/maintenance`)),
  restoreStall: (id) => call(http.post(`/stalls/${id}/restore`)),

  // 模块三：骑乘课程与排期
  lessons: () => call(http.get('/lessons')),
  coaches: () => call(http.get('/coaches')),
  sessions: () => call(http.get('/sessions')),
  createSession: (body) => call(http.post('/sessions', body)),
  cancelSession: (id, body) => call(http.post(`/sessions/${id}/cancel`, body)),

  // 模块四：会员与骑乘记录
  members: () => call(http.get('/members')),
  records: () => call(http.get('/riding-records')),
  memberRecords: (id) => call(http.get(`/members/${id}/records`)),
  sessionRecords: (id) => call(http.get(`/riding-records/by-session/${id}`)),
  book: (body) => call(http.post('/riding-records', body)),
  cancelRecord: (id) => call(http.post(`/riding-records/${id}/cancel`)),
  completeRecord: (id) => call(http.post(`/riding-records/${id}/complete`)),

  // 模块五：马匹健康事件闭环
  healthEvents: (params = {}) =>
    call(http.get('/health-events', { params })),
  healthEvent: (id) => call(http.get(`/health-events/${id}`)),
  horseHealth: (horseId) => call(http.get(`/health-events/horse/${horseId}/overview`)),
  createHealthEvent: (body) => call(http.post('/health-events', body)),
  supplementEvent: (id, body) => call(http.post(`/health-events/${id}/supplement`, body)),
  transitionEvent: (id, body) => call(http.post(`/health-events/${id}/transition`, body)),
  reviewEvent: (id, body) => call(http.post(`/health-events/${id}/reviews`, body)),
  releaseHorse: (horseId, body) => call(http.post(`/health-events/horse/${horseId}/release`, body)),

  // 模块六：值班交接与未结事项承接
  activeShift: () => call(http.get('/duty/shifts/active')),
  dutyHistory: (params = {}) => call(http.get('/duty/shifts', { params })),
  shift: (id) => call(http.get(`/duty/shifts/${id}`)),
  closeBlockers: (id) => call(http.get(`/duty/shifts/${id}/close-blockers`)),
  createShift: (body) => call(http.post('/duty/shifts', body)),
  updateShiftNote: (id, body) => call(http.put(`/duty/shifts/${id}/note`, body)),
  closeShift: (id, body) => call(http.post(`/duty/shifts/${id}/close`, body)),
  createItem: (shiftId, body) => call(http.post(`/duty/shifts/${shiftId}/items`, body)),
  item: (id) => call(http.get(`/duty/items/${id}`)),
  supplementItem: (id, body) => call(http.post(`/duty/items/${id}/supplement`, body)),
  updateItem: (id, body) => call(http.post(`/duty/items/${id}`, body)),
  dispatchItem: (id, body) => call(http.post(`/duty/items/${id}/dispatch`, body)),
  acceptItem: (id, body) => call(http.post(`/duty/items/${id}/accept`, body))
}

export default api
