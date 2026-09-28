/**
 * 纯 Node 断言（node test/stallBoardModel.test.mjs 即可运行）：
 * 覆盖首次进入 / 空数据 / 重复刷新不累加 / 顺序变化不重复卡片 /
 * 汇总-分组-明细三者可互相追溯 / 缺马匹-空面积-空马房的稳定显示规则。
 */
import assert from 'node:assert/strict'
import { normalizeBoard, statusKey, tagOf } from '../src/views/stallBoardModel.js'

function assertConsistent(board) {
  const stalls = board.stalls
  assert.equal(board.summary.total, stalls.length, 'total 必须等于明细数')
  assert.equal(board.summary.idle, stalls.filter((s) => s.status === 'IDLE').length)
  assert.equal(board.summary.occupied, stalls.filter((s) => s.status === 'OCCUPIED').length)
  assert.equal(board.summary.maintenance, stalls.filter((s) => s.status === 'MAINTENANCE').length)
  assert.equal(
    board.summary.idle + board.summary.occupied + board.summary.maintenance + board.summary.unknown,
    board.summary.total
  )
  // 分组之和必须等于顶部
  assert.equal(board.barns.reduce((n, b) => n + b.total, 0), board.summary.total)
  assert.equal(board.barns.reduce((n, b) => n + b.occupied, 0), board.summary.occupied)
  for (const barn of board.barns) {
    assert.equal(barn.total, barn.stalls.length)
    assert.equal(barn.occupied, barn.stalls.filter((s) => s.status === 'OCCUPIED').length)
    assert.ok(barn.stalls.every((s) => s.barnName === barn.barnName))
  }
  // 明细 id 唯一
  assert.equal(new Set(stalls.map((s) => s.id)).size, stalls.length)
}

// 1. 首次进入 / 接口返回空数据：是合法的一轮，全部 0 且不抛错
const empty = normalizeBoard({ generatedAt: null, summary: null, barns: [], stalls: [] })
assertConsistent(empty)
assert.deepEqual(empty.summary, { total: 0, idle: 0, occupied: 0, maintenance: 0, unknown: 0 })
assert.deepEqual(empty.barns, [])
assert.equal(empty.generatedAtText, '刚刚')
assert.deepEqual(normalizeBoard(null).summary, empty.summary, '连 null 也不能炸页面')
assert.deepEqual(normalizeBoard(undefined).stalls, [])

// 2. 典型一轮：入栏 / 出栏 / 维护的数字全部能从明细反推
const raw = {
  generatedAt: '2026-09-28T10:20:30',
  stalls: [
    { id: 1, stallNo: 'STM-001', barnName: 'A 区马房', areaSqm: 16, status: 'OCCUPIED',
      statusName: '占用', horseId: 11, horseNo: 'H-001', horseName: '追风', horseMissing: false },
    { id: 2, stallNo: 'STM-002', barnName: 'A 区马房', areaSqm: null, status: 'IDLE',
      statusName: '空闲', horseId: null, horseNo: null, horseName: null, horseMissing: false },
    { id: 3, stallNo: 'STM-003', barnName: 'B 区马房', areaSqm: 20, status: 'MAINTENANCE',
      statusName: '维护中', horseId: null, horseNo: null, horseName: null, horseMissing: false }
  ]
}
const board = normalizeBoard(raw)
assertConsistent(board)
assert.deepEqual(board.summary, { total: 3, idle: 1, occupied: 1, maintenance: 1, unknown: 0 })
assert.equal(board.barns.find((b) => b.barnName === 'A 区马房').occupied, 1)
assert.match(board.generatedAtText, /\d{2}-\d{2} \d{2}:\d{2}:\d{2}/)
// 卡片与按钮状态由同一字段驱动
assert.equal(statusKey('MAINTENANCE'), 'maintenance')
assert.equal(tagOf('MAINTENANCE'), 'warning')

// 3. 出栏后：旧马匹信息必须随同一轮数据消失（用新一轮原始响应模拟）
const afterRelease = normalizeBoard({
  stalls: [
    { id: 1, stallNo: 'STM-001', barnName: 'A 区马房', areaSqm: 16, status: 'IDLE',
      statusName: '空闲', horseId: null, horseNo: null, horseName: null, horseMissing: false },
    ...raw.stalls.slice(1)
  ]
})
assertConsistent(afterRelease)
assert.deepEqual(
  afterRelease.summary,
  { total: 3, idle: 2, occupied: 0, maintenance: 1, unknown: 0 }
)
const released = afterRelease.stalls.find((s) => s.id === 1)
assert.equal(released.horseId, null)
assert.equal(released.horseName, null)

// 4. 重复刷新不累加：相同输入多轮结果结构完全一致，调用方整包替换不会翻倍
const again = normalizeBoard(raw)
assert.deepEqual(again.summary, board.summary)
assert.deepEqual(again.stalls.map((s) => s.id), [1, 2, 3])

// 5. 返回顺序变化 + 重复 id：不能产生重复卡片，分组仍自洽
const shuffled = normalizeBoard({
  stalls: [raw.stalls[2], raw.stalls[0], raw.stalls[1], { ...raw.stalls[0] }, raw.stalls[0]]
})
assertConsistent(shuffled)
assert.equal(shuffled.stalls.length, 3)
assert.deepEqual(shuffled.stalls.map((s) => s.id), [3, 1, 2])

// 6. 缺失马匹档案：明确提示，仍计占用，不会被当成空栏
const dirty = normalizeBoard({
  stalls: [
    { id: 9, stallNo: 'STM-009', barnName: 'A 区马房', areaSqm: 16, status: 'OCCUPIED',
      statusName: '占用', horseId: 999, horseNo: null, horseName: null, horseMissing: true }
  ]
})
assertConsistent(dirty)
const broken = dirty.stalls[0]
assert.equal(broken.horseMissing, true)
assert.equal(broken.horseId, 999)
assert.equal(dirty.summary.occupied, 1)

// 7. 空面积 / 空马房 / 未知状态：稳定兜底，不炸、不丢栏位
const weird = normalizeBoard({
  stalls: [
    { id: 10, stallNo: 'STM-010', barnName: '   ', areaSqm: null, status: 'IDLE',
      statusName: '空闲', horseId: null },
    { id: 11, stallNo: null, barnName: null, areaSqm: 12, status: 'MYSTERY',
      horseId: 5, horseNo: null, horseName: null }
  ]
})
assertConsistent(weird)
assert.equal(weird.stalls.find((s) => s.id === 10).barnName, '未分组马房')
assert.equal(weird.stalls.find((s) => s.id === 10).areaSqm, null)
assert.equal(weird.stalls.find((s) => s.id === 11).stallNo, '编号未登记')
assert.equal(weird.summary.unknown, 1)
assert.equal(statusKey('MYSTERY'), 'unknown')
assert.equal(tagOf('MYSTERY'), 'danger')

console.log('stallBoardModel: 全部断言通过')
