import { reactive, watch } from 'vue'

/**
 * 当前值班身份（前端仅做展示与按钮体验，真正的角色校验一律在后端）。
 * 没有登录体系，处置台 / 马匹页统一从这里取「操作人 + 角色」，并持久化到 localStorage。
 */
const STORAGE_KEY = 'eq-operator'

function load() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      return {
        name: typeof parsed.name === 'string' ? parsed.name : '',
        role: parsed.role === 'MANAGER' ? 'MANAGER' : 'STAFF'
      }
    }
  } catch (error) {
    // 本地存储不可用时退回默认身份
  }
  return { name: '', role: 'STAFF' }
}

export const identity = reactive(load())

watch(
  identity,
  (value) => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ name: value.name, role: value.role }))
    } catch (error) {
      // 忽略写入失败
    }
  },
  { deep: true }
)

/** 给写接口统一附带身份字段 */
export function withIdentity(body = {}) {
  return {
    operatorName: identity.name,
    operatorRole: identity.role,
    ...body
  }
}
