import { reactive, shallowRef, markRaw } from 'vue'

import HorseWall from '../views/HorseWall.vue'
import StallBoard from '../views/StallBoard.vue'
import HealthDesk from '../views/HealthDesk.vue'
import LessonSchedule from '../views/LessonSchedule.vue'
import CoachBoard from '../views/CoachBoard.vue'
import MemberDesk from '../views/MemberDesk.vue'
import HandoverDesk from '../views/HandoverDesk.vue'

/**
 * 轻量前端路由。
 *
 * 这里的 main.js 是仓里既有的、不允许改动的入口（它只 createApp(App).use(ElementPlus).mount()），
 * 没有 app.use(router) 这一步，所以不引入 vue-router 的插件安装流程，
 * 直接导出一份响应式路由状态 + 视图组件，由 App.vue 用 <component :is> 切换。
 * 用法对页面完全透明：navigate('/stalls') 切页，route.title 给顶栏标题。
 */
export const menuGroups = [
  {
    key: 'g-handover',
    label: '值班交接',
    items: [
      { path: '/handover', label: '值班交接台', title: '值班交接台 · 未结事项承接', hint: '开班自动生成交接清单；下一班逐项确认承接，未确认事项不消失，历史班次按日期 / 负责人 / 事项状态可查' }
    ]
  },
  {
    key: 'g-horse',
    label: '马匹与马房',
    items: [
      { path: '/horses', label: '马匹卡片墙', title: '马匹卡片墙 · 训练日历', hint: '点马匹卡片，右侧出这匹马的健康事件链与训练日历；值班时先看未闭环事件和最近复查结论' },
      { path: '/health', label: '健康事件处置台', title: '马匹健康事件处置台', hint: '登记伤病、观察复查、复训放行全闭环；高风险立即休养并冻结未来排课' },
      { path: '/stalls', label: '栏位看板', title: '马房栏位看板', hint: '按马房分区看栏位占用，入栏 / 出栏 / 转维护都在卡片上完成' }
    ]
  },
  {
    key: 'g-lesson',
    label: '教学中心',
    items: [
      { path: '/lessons', label: '课程与排期', title: '骑乘课程与排期', hint: '课程容量、教练时段冲突都由后端把关，冲突时会给出中文原因' },
      { path: '/coaches', label: '教练排班', title: '教练排班视图', hint: '按教练看未来一周的时段占用，点格子可直接为该教练补排期' }
    ]
  },
  {
    key: 'g-member',
    label: '会员服务',
    items: [
      { path: '/members', label: '会员与骑乘记录', title: '会员与骑乘记录', hint: '会员卡余额与次数在这里扣减，记录必须挂在已排期的课程上' }
    ]
  }
]

const flatRoutes = menuGroups.flatMap((group) => group.items)

const first = flatRoutes[0]

export const route = reactive({
  path: first.path,
  title: first.title,
  hint: first.hint
})

export const currentView = shallowRef(markRaw(HorseWall))

const viewByPath = {
  '/handover': markRaw(HandoverDesk),
  '/horses': markRaw(HorseWall),
  '/health': markRaw(HealthDesk),
  '/stalls': markRaw(StallBoard),
  '/lessons': markRaw(LessonSchedule),
  '/coaches': markRaw(CoachBoard),
  '/members': markRaw(MemberDesk)
}

export function navigate(path) {
  if (!applyPath(path)) {
    return
  }
  // 同步到 hash，刷新和直链（#/stalls）都能落回同一页
  if (window.location.hash !== '#' + path) {
    window.location.hash = path
  }
}

function applyPath(path) {
  const target = flatRoutes.find((item) => item.path === path)
  if (!target) {
    return false
  }
  route.path = target.path
  route.title = target.title
  route.hint = target.hint
  currentView.value = viewByPath[target.path]
  return true
}

function hashPath() {
  return window.location.hash.replace(/^#/, '')
}

window.addEventListener('hashchange', () => {
  applyPath(hashPath())
})

if (hashPath()) {
  applyPath(hashPath())
}

export function defaultPath() {
  return first.path
}
