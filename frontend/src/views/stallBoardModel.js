/**
 * 栏位看板的纯展示模型：把后端一轮 board 快照规范化成页面唯一数据源。
 *
 * 关键约束（与 StallService.board() 对齐）：
 *  - 每次都是整轮重建，绝不与上一轮合并 / 累加，所以重复刷新数量不会翻倍；
 *  - 按 id 去重，返回顺序变化也不会产生重复卡片；
 *  - 顶部汇总、马房分组都从同一批去重后的 stalls 现算，数字可互相追溯；
 *  - 空面积 / 空马房 / 缺失马匹档案等合法或脏数据在这里给出稳定兜底。
 *
 * 这里故意不依赖 Vue，方便用 node 直接跑单测。
 */

/** 后端马房名为空时返回的兜底分组名（与 StallService.UNASSIGNED_BARN 保持一致） */
export const UNASSIGNED_BARN = '未分组马房'

const VALID_STATUS = ['IDLE', 'OCCUPIED', 'MAINTENANCE']

export function statusKey(status) {
  if (VALID_STATUS.includes(status)) {
    return status.toLowerCase()
  }
  return 'unknown'
}

export function tagOf(status) {
  if (status === 'IDLE') return 'success'
  if (status === 'OCCUPIED') return 'primary'
  if (status === 'MAINTENANCE') return 'warning'
  return 'danger'
}

export function formatTime(value) {
  if (!value) {
    return '刚刚'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value).replace('T', ' ').slice(0, 19)
  }
  const pad = (num) => String(num).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

/**
 * 规范化一轮后端结果。返回 { generatedAtText, summary, barns, stalls }，
 * 页面只展示这一个对象，保证汇总、分组、卡片同轮切换。
 */
export function normalizeBoard(raw) {
  const data = raw && typeof raw === 'object' ? raw : {}
  const rawStalls = Array.isArray(data.stalls) ? data.stalls : []

  // 按 id 去重：保留第一次出现，后端返回顺序再怎么变也不会出现重复卡片
  const seen = new Set()
  const stalls = []
  for (const item of rawStalls) {
    if (!item || item.id === null || item.id === undefined || seen.has(item.id)) {
      continue
    }
    seen.add(item.id)
    const rawBarn = typeof item.barnName === 'string' ? item.barnName.trim() : ''
    stalls.push({
      id: item.id,
      stallNo: item.stallNo || '编号未登记',
      barnName: rawBarn || UNASSIGNED_BARN,
      areaSqm: item.areaSqm === undefined ? null : item.areaSqm,
      status: item.status || 'UNKNOWN',
      statusName: item.statusName || item.status || '未知状态',
      horseId: item.horseId === undefined ? null : item.horseId,
      horseNo: item.horseNo || null,
      horseName: item.horseName || null,
      horseMissing: item.horseMissing === true
    })
  }

  // 分组与汇总都从同一批 stalls 现算（不再信后端给的 barns/summary，
  // 这样即使中途有人改了组装顺序，三处数字仍然由同一批明细推导得出）
  const groupMap = new Map()
  const summary = { total: stalls.length, idle: 0, occupied: 0, maintenance: 0, unknown: 0 }
  for (const stall of stalls) {
    let group = groupMap.get(stall.barnName)
    if (!group) {
      group = { barnName: stall.barnName, total: 0, occupied: 0, stalls: [] }
      groupMap.set(stall.barnName, group)
    }
    group.total += 1
    if (stall.status === 'OCCUPIED') {
      group.occupied += 1
    }
    group.stalls.push(stall)

    if (stall.status === 'IDLE') {
      summary.idle += 1
    } else if (stall.status === 'OCCUPIED') {
      summary.occupied += 1
    } else if (stall.status === 'MAINTENANCE') {
      summary.maintenance += 1
    } else {
      summary.unknown += 1
    }
  }

  return {
    generatedAtText: formatTime(data.generatedAt),
    summary,
    barns: [...groupMap.values()],
    stalls
  }
}
