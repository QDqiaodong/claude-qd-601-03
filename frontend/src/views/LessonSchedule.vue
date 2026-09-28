<template>
  <div>
    <!-- 课程卡片 -->
    <div class="eq-panel">
      <div class="eq-panel__head">
        <h2>在售课程</h2>
        <span>进阶课与私教课要求会员先通过初级考核</span>
      </div>
      <div class="lessons">
        <div v-for="lesson in lessons" :key="lesson.id" class="lesson">
          <div class="lesson__head">
            <span class="lesson__no">{{ lesson.lessonNo }}</span>
            <el-tag size="small" :type="lesson.category === 'BASIC' ? 'success' : 'warning'" effect="dark">
              {{ lesson.categoryName }}
            </el-tag>
          </div>
          <div class="lesson__name">{{ lesson.name }}</div>
          <div class="lesson__meta">
            <span>容量 {{ lesson.capacity }} 人</span>
            <span>{{ lesson.durationMin }} 分钟</span>
          </div>
          <div class="lesson__price">
            <b>￥{{ lesson.price }}</b>
            <span>扣 {{ lesson.timesCost }} 次</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 排期时间轴 -->
    <div class="eq-panel schedule">
      <div class="eq-panel__head">
        <h2>课程排期时间轴</h2>
        <el-button type="primary" @click="openCreate">新建排期</el-button>
      </div>

      <div class="eq-toolbar">
        <el-radio-group v-model="scope">
          <el-radio-button label="upcoming">未来排期</el-radio-button>
          <el-radio-button label="all">全部</el-radio-button>
        </el-radio-group>
        <el-select v-model="statusFilter" style="width: 130px">
          <el-option label="全部状态" value="" />
          <el-option label="已排期" value="SCHEDULED" />
          <el-option label="已满员" value="FULL" />
          <el-option label="已取消" value="CANCELED" />
        </el-select>
        <span class="eq-muted">共 {{ visibleGroups.reduce((sum, g) => sum + g.items.length, 0) }} 场</span>
      </div>

      <div v-if="!visibleGroups.length" class="eq-empty">没有符合条件的排期</div>

      <div v-for="group in visibleGroups" :key="group.date" class="day">
        <div class="day__label">
          <strong>{{ group.date }}</strong>
          <span>{{ weekday(group.date) }} · {{ group.items.length }} 场</span>
        </div>
        <div class="day__items">
          <div
            v-for="session in group.items"
            :key="session.id"
            class="session"
            :class="{ 'session--canceled': session.status === 'CANCELED', 'session--affected': session.healthAffected }"
          >
            <div class="session__time">
              <b>{{ session.startTime }}</b>
              <span>{{ session.endTime }}</span>
            </div>
            <div class="session__body">
              <div class="session__title">
                {{ session.lessonName }}
                <el-tag size="small" effect="plain">{{ session.categoryName }}</el-tag>
                <el-tag size="small" :type="tagOf(session.status)">{{ session.statusName }}</el-tag>
                <el-tag v-if="session.healthAffected" type="danger" size="small" effect="dark">
                  健康事件受影响
                </el-tag>
                <el-tag
                  v-if="session.handover"
                  size="small"
                  :type="session.handover.itemStatus === 'ACCEPTED' ? 'success' : 'warning'"
                  effect="dark"
                  :title="handoverTip(session.handover)"
                >
                  交接 {{ session.handover.shiftNo }} · {{ session.handover.itemStatusName }}
                </el-tag>
              </div>
              <div class="session__meta">
                教练 {{ session.coachName }}
                <template v-if="session.horseName"> · 用马 {{ session.horseName }}（{{ session.horseNo }}）</template>
                <template v-else> · 未指定用马</template>
              </div>
              <div v-if="session.handover" class="session__meta">
                交接事项 {{ session.handover.itemNo }}（{{ session.handover.changeKindName || '当班课程' }}）
                · 属于班次 {{ session.handover.shiftNo }}（{{ session.handover.shiftDate }}）
                <template v-if="session.handover.targetShiftNo"> · 已派给 {{ session.handover.targetShiftNo }}</template>
                <template v-if="session.handover.acceptedBy">
                  · 最近承接：{{ session.handover.acceptedBy }}（{{ session.handover.acceptedShiftNo }}）
                </template>
              </div>
              <el-progress
                :percentage="percent(session)"
                :stroke-width="8"
                :show-text="false"
                :color="session.bookedCount >= session.capacity ? '#6d4c41' : '#b6a6a0'"
                style="margin-top: 6px"
              />
              <div class="session__meta">
                已约 {{ session.bookedCount }} / {{ session.capacity }}，剩余 {{ session.remain }} 个名额
              </div>
            </div>
            <div class="session__ops">
              <el-button
                size="small"
                :disabled="session.status === 'CANCELED'"
                @click="cancel(session)"
              >
                取消排期
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="createDialog" title="新建课程排期" width="520px">
      <el-form label-width="92px">
        <el-form-item label="课程">
          <el-select v-model="form.lessonId" style="width: 100%" placeholder="选课程">
            <el-option
              v-for="lesson in lessons"
              :key="lesson.id"
              :label="lesson.name + '（容量 ' + lesson.capacity + '）'"
              :value="lesson.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="教练">
          <el-select v-model="form.coachId" style="width: 100%" placeholder="选教练">
            <el-option
              v-for="coach in coaches"
              :key="coach.id"
              :label="coach.name + '（' + coach.levelName + ' · ' + coach.statusName + '）'"
              :value="coach.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="用马">
          <el-select v-model="form.horseId" style="width: 100%" clearable placeholder="可不指定">
            <el-option
              v-for="horse in horses"
              :key="horse.id"
              :label="horse.horseNo + ' ' + horse.name + '（' + horse.statusName + '）'"
              :value="horse.id"
              :disabled="horse.status !== 'ACTIVE'"
            />
          </el-select>
          <p class="eq-muted" style="margin: 4px 0 0">休养 / 退役马不能排课（有未闭环健康事件的马处于休养），后端同样强制拦截。</p>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="form.sessionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="时段">
          <el-select v-model="form.startTime" style="width: 100%" placeholder="选时段">
            <el-option v-for="slot in slots" :key="slot" :label="slot + ' - ' + nextHour(slot)" :value="slot" />
          </el-select>
        </el-form-item>
        <el-form-item label="本场容量">
          <el-input-number v-model="form.capacity" :min="1" :max="20" controls-position="right" />
        </el-form-item>
      </el-form>
      <p class="eq-muted">
        同一教练同一时段已有排期时，后端会拒绝并给出冲突的那一场的具体时间。
      </p>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">排期</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { identity } from '../identity'

const lessons = ref([])
const coaches = ref([])
const horses = ref([])
const sessions = ref([])

const scope = ref('upcoming')
const statusFilter = ref('')
const saving = ref(false)
const createDialog = ref(false)

const slots = ['09:00', '10:00', '11:00', '14:00', '15:00', '16:00']

const form = reactive({
  lessonId: null,
  coachId: null,
  horseId: null,
  sessionDate: '',
  startTime: '',
  capacity: 6
})

/** 本地日期：不能用 toISOString()，UTC+8 下会算成前一天 */
function localDate(offset = 0) {
  const day = new Date()
  day.setDate(day.getDate() + offset)
  return `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`
}

const today = localDate()

const visibleGroups = computed(() => {
  const rows = sessions.value.filter((session) => {
    if (statusFilter.value && session.status !== statusFilter.value) {
      return false
    }
    if (scope.value === 'upcoming' && session.sessionDate < today) {
      return false
    }
    return true
  })
  const grouped = new Map()
  rows.forEach((session) => {
    if (!grouped.has(session.sessionDate)) {
      grouped.set(session.sessionDate, [])
    }
    grouped.get(session.sessionDate).push(session)
  })
  return [...grouped.entries()]
    .sort((a, b) => (a[0] < b[0] ? -1 : 1))
    .map(([date, items]) => ({ date, items: items.sort((a, b) => a.startTime.localeCompare(b.startTime)) }))
})

function weekday(date) {
  const names = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return names[new Date(date + 'T00:00:00').getDay()]
}

function tagOf(status) {
  if (status === 'FULL') return 'danger'
  if (status === 'CANCELED') return 'info'
  return 'success'
}

function percent(session) {
  if (!session.capacity) {
    return 0
  }
  return Math.min(Math.round((session.bookedCount / session.capacity) * 100), 100)
}

function nextHour(slot) {
  const hour = Number(slot.slice(0, 2))
  return String((hour + 1) % 24).padStart(2, '0') + ':' + slot.slice(3)
}

/** 交接标签悬浮提示：班次归属 + 最近一次承接记录 */
function handoverTip(handover) {
  const parts = [`交接事项 ${handover.itemNo}，属于班次 ${handover.shiftNo}（${handover.shiftDate}）`]
  if (handover.targetShiftNo) parts.push(`已分派给 ${handover.targetShiftNo}`)
  if (handover.acceptedBy) {
    parts.push(`最近一次承接：${handover.acceptedBy} 于 ${handover.acceptedShiftNo}`)
  } else {
    parts.push('尚未被下一班逐项确认承接')
  }
  return parts.join('；')
}

async function load() {
  try {
    const [lessonList, coachList, horseList, sessionList] = await Promise.all([
      api.lessons(),
      api.coaches(),
      api.horses(),
      api.sessions()
    ])
    lessons.value = lessonList || []
    coaches.value = coachList || []
    horses.value = horseList || []
    sessions.value = sessionList || []
  } catch (error) {
    ElMessage.error(error.message)
  }
}

function openCreate() {
  Object.assign(form, {
    lessonId: null,
    coachId: null,
    horseId: null,
    sessionDate: today,
    startTime: '09:00',
    capacity: 6
  })
  createDialog.value = true
}

async function submit() {
  saving.value = true
  try {
    await api.createSession({
      lessonId: form.lessonId,
      coachId: form.coachId,
      horseId: form.horseId,
      sessionDate: form.sessionDate,
      startTime: form.startTime,
      endTime: nextHour(form.startTime),
      capacity: form.capacity,
      operatorName: identity.name || '值班台'
    })
    ElMessage.success('排期已创建')
    createDialog.value = false
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

async function cancel(session) {
  try {
    await ElMessageBox.confirm(
      `确认取消 ${session.sessionDate} ${session.startTime} 的「${session.lessonName}」？`,
      '取消排期',
      { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '再想想' }
    )
  } catch (error) {
    return
  }
  try {
    await api.cancelSession(session.id, { operatorName: identity.name || '值班台' })
    ElMessage.success('排期已取消')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(load)

// 课程一变就把容量同步成课程上限，避免手填一个超上限的值
watch(
  () => form.lessonId,
  (id) => {
    const lesson = lessons.value.find((item) => item.id === id)
    if (lesson) {
      form.capacity = lesson.capacity
    }
  }
)
</script>

<style scoped>
.lessons {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 12px;
}

.lesson {
  border: 1px solid #ece3df;
  border-radius: 10px;
  padding: 12px;
  background: #fffdfc;
}

.lesson__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.lesson__no {
  font-size: 11px;
  letter-spacing: 1px;
  color: #a08d86;
}

.lesson__name {
  font-size: 15px;
  font-weight: 700;
  color: #4a3129;
  margin: 7px 0 4px;
}

.lesson__meta {
  display: flex;
  gap: 10px;
  font-size: 12px;
  color: #8c7c76;
}

.lesson__price {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 9px;
  padding-top: 8px;
  border-top: 1px dashed #efe6e1;
}

.lesson__price b {
  color: #6d4c41;
  font-size: 15px;
}

.lesson__price span {
  font-size: 11px;
  color: #a08d86;
}

.schedule {
  margin-top: 16px;
}

.day {
  display: grid;
  grid-template-columns: 108px 1fr;
  gap: 12px;
  padding: 12px 0;
  border-top: 1px dashed #efe6e1;
}

.day__label {
  display: flex;
  flex-direction: column;
}

.day__label strong {
  font-size: 13px;
  color: #4a3129;
}

.day__label span {
  font-size: 11px;
  color: #a08d86;
}

.day__items {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.session {
  display: grid;
  grid-template-columns: 62px 1fr 96px;
  gap: 12px;
  align-items: center;
  border: 1px solid #ece3df;
  border-radius: 10px;
  padding: 10px 12px;
  background: #fffdfc;
}

.session--canceled {
  opacity: 0.6;
  background: #f7f7f7;
}

.session--affected {
  border-color: #e08a6f;
  background: #fdf3ef;
}

.session__time {
  display: flex;
  flex-direction: column;
  align-items: center;
  color: #6d4c41;
}

.session__time b {
  font-size: 15px;
}

.session__time span {
  font-size: 11px;
  color: #a08d86;
}

.session__title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 14px;
  font-weight: 600;
  color: #4a3129;
}

.session__meta {
  font-size: 11px;
  color: #8c7c76;
  margin-top: 3px;
}

.session__ops {
  display: flex;
  justify-content: flex-end;
}
</style>
