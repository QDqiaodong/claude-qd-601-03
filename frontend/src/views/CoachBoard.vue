<template>
  <div class="eq-panel">
    <div class="eq-panel__head">
      <h2>教练排班视图</h2>
      <span>同一教练同一时段不重叠，冲突会在这里直观看到</span>
    </div>

    <div class="eq-toolbar">
      <el-select v-model="coachId" style="width: 220px" @change="() => {}">
        <el-option
          v-for="coach in coaches"
          :key="coach.id"
          :label="coach.name + '（' + coach.levelName + ' · ' + coach.statusName + '）'"
          :value="coach.id"
        />
      </el-select>
      <el-button @click="load">刷新</el-button>
      <span class="eq-muted">点空格子可为该教练补排期，点有课的格子看名单</span>
    </div>

    <div class="grid">
      <div class="grid__corner">时段 \ 日期</div>
      <div v-for="date in dates" :key="date" class="grid__head">
        <strong>{{ date.slice(5) }}</strong>
        <span>{{ weekday(date) }}</span>
      </div>

      <template v-for="slot in slots" :key="slot">
        <div class="grid__slot">{{ slot }}</div>
        <div
          v-for="date in dates"
          :key="date + slot"
          class="grid__cell"
          :class="cellOf(date, slot) ? 'grid__cell--busy' : 'grid__cell--free'"
          @click="onCell(date, slot)"
        >
          <template v-if="cellOf(date, slot)">
            <div class="grid__lesson">{{ cellOf(date, slot).lessonName }}</div>
            <div class="grid__line">{{ cellOf(date, slot).bookedCount }}/{{ cellOf(date, slot).capacity }} 人</div>
            <div class="grid__line">{{ cellOf(date, slot).horseName || '未指定用马' }}</div>
          </template>
          <div v-else class="grid__free">+ 排期</div>
        </div>
      </template>
    </div>

    <div v-if="!coachId" class="eq-empty">请选择一位教练</div>

    <el-dialog v-model="dialog" title="为该教练补一场排期" width="500px">
      <el-form label-width="92px">
        <el-form-item label="教练">
          <el-input :model-value="coachName" disabled />
        </el-form-item>
        <el-form-item label="日期 / 时段">
          <el-input :model-value="form.sessionDate + ' ' + form.startTime + ' - ' + nextHour(form.startTime)" disabled />
        </el-form-item>
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
        </el-form-item>
        <el-form-item label="本场容量">
          <el-input-number v-model="form.capacity" :min="1" :max="20" controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">排期</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const coaches = ref([])
const lessons = ref([])
const horses = ref([])
const sessions = ref([])
const coachId = ref(null)
const saving = ref(false)
const dialog = ref(false)

const slots = ['09:00', '10:00', '11:00', '14:00', '15:00', '16:00']

const form = reactive({ lessonId: null, horseId: null, sessionDate: '', startTime: '', capacity: 6 })

/** 本地日期：不能用 toISOString()，UTC+8 下会算成前一天 */
function localDate(offset = 0) {
  const day = new Date()
  day.setDate(day.getDate() + offset)
  return `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`
}

const dates = computed(() => {
  const list = []
  for (let i = 0; i < 7; i++) {
    list.push(localDate(i))
  }
  return list
})

const coachName = computed(() => {
  const coach = coaches.value.find((item) => item.id === coachId.value)
  return coach ? coach.name : ''
})

function weekday(date) {
  const names = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return names[new Date(date + 'T00:00:00').getDay()]
}

function cellOf(date, slot) {
  return (
    sessions.value.find(
      (item) => item.coachId === coachId.value && item.sessionDate === date && item.startTime === slot
    ) || null
  )
}

function nextHour(slot) {
  const hour = Number(slot.slice(0, 2))
  return String((hour + 1) % 24).padStart(2, '0') + ':' + slot.slice(3)
}

async function load() {
  try {
    const [coachList, lessonList, horseList, sessionList] = await Promise.all([
      api.coaches(),
      api.lessons(),
      api.horses(),
      api.sessions()
    ])
    coaches.value = coachList || []
    lessons.value = lessonList || []
    horses.value = horseList || []
    sessions.value = sessionList || []
    if (coachId.value === null && coaches.value.length) {
      coachId.value = coaches.value[0].id
    }
  } catch (error) {
    ElMessage.error(error.message)
  }
}

function onCell(date, slot) {
  const cell = cellOf(date, slot)
  if (cell) {
    ElMessage.info(
      `${date} ${slot} 已排「${cell.lessonName}」，已约 ${cell.bookedCount}/${cell.capacity} 人`
    )
    return
  }
  Object.assign(form, {
    lessonId: null,
    horseId: null,
    sessionDate: date,
    startTime: slot,
    capacity: 6
  })
  dialog.value = true
}

async function submit() {
  saving.value = true
  try {
    await api.createSession({
      lessonId: form.lessonId,
      coachId: coachId.value,
      horseId: form.horseId,
      sessionDate: form.sessionDate,
      startTime: form.startTime,
      endTime: nextHour(form.startTime),
      capacity: form.capacity
    })
    ElMessage.success('排期已创建')
    dialog.value = false
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

onMounted(load)

// 课程一变就把容量同步成课程上限
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
.grid {
  display: grid;
  grid-template-columns: 62px repeat(7, minmax(0, 1fr));
  gap: 5px;
}

.grid__corner,
.grid__head {
  font-size: 11px;
  color: #8c7c76;
  text-align: center;
  padding: 6px 0;
}

.grid__head strong {
  display: block;
  font-size: 13px;
  color: #4a3129;
}

.grid__head span {
  font-size: 11px;
  color: #a89a94;
}

.grid__slot {
  font-size: 12px;
  color: #8c7c76;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f7f2ef;
  border-radius: 8px;
}

.grid__cell {
  min-height: 58px;
  border-radius: 8px;
  border: 1px dashed #ded3ce;
  padding: 5px 6px;
  cursor: pointer;
  overflow: hidden;
  transition: transform 0.12s, box-shadow 0.12s;
}

.grid__cell:hover {
  transform: translateY(-1px);
  box-shadow: 0 3px 10px rgba(109, 76, 65, 0.15);
}

.grid__cell--free {
  background: #fbfaf9;
}

.grid__cell--busy {
  background: #f3e9e4;
  border-style: solid;
  border-color: #dcc7bd;
}

.grid__lesson {
  font-size: 12px;
  font-weight: 600;
  color: #4a3129;
}

.grid__line {
  font-size: 11px;
  color: #8c7c76;
}

.grid__free {
  font-size: 11px;
  color: #b6a6a0;
  padding-top: 16px;
  text-align: center;
}
</style>
