<template>
  <div class="wall">
    <!-- 左：马匹卡片墙 -->
    <div class="wall__cards eq-panel">
      <div class="eq-panel__head">
        <h2>马匹卡片墙</h2>
        <span>共 {{ filtered.length }} / {{ horses.length }} 匹</span>
      </div>

      <div class="eq-toolbar">
        <el-input
          v-model="keyword"
          placeholder="按编号 / 名字 / 品种搜"
          clearable
          style="width: 190px"
        />
        <el-select v-model="statusFilter" style="width: 120px">
          <el-option label="全部状态" value="" />
          <el-option label="在役" value="ACTIVE" />
          <el-option label="休养" value="RESTING" />
          <el-option label="退役" value="RETIRED" />
        </el-select>
        <el-button type="primary" @click="openHorseDialog(null)">新增马匹</el-button>
      </div>

      <div v-if="!filtered.length" class="eq-empty">没有符合条件的马匹</div>

      <div class="cards">
        <div
          v-for="horse in filtered"
          :key="horse.id"
          class="card"
          :class="{ 'card--on': horse.id === selectedHorseId }"
          @click="selectHorse(horse)"
        >
          <div class="card__top">
            <span class="card__no">{{ horse.horseNo }}</span>
            <el-tag :type="statusTag(horse.status)" effect="dark" size="small">
              {{ horse.statusName }}
            </el-tag>
          </div>
          <div class="card__name">{{ horse.name }}</div>
          <div class="card__meta">
            {{ horse.breed || '未登记品种' }} · {{ horse.age === null ? '年龄未知' : horse.age + ' 岁' }}
          </div>
          <div class="card__tags">
            <el-tag size="small" effect="plain">{{ horse.rideLevelName }}</el-tag>
            <el-tag size="small" type="info" effect="plain">
              疫苗 {{ horse.vaccineCount === null ? '—' : horse.vaccineCount }} 次
            </el-tag>
          </div>
          <div class="card__health">
            <span>上次体检 {{ horse.lastCheckDate || '—' }}</span>
            <span>体重 {{ horse.weightKg === null ? '—' : horse.weightKg }} kg</span>
          </div>
          <div v-if="horse.healthSummary" class="card__risk">
            <el-tag v-if="horse.healthSummary.openCount" type="danger" size="small" effect="dark">
              {{ horse.healthSummary.openCount }} 起未闭环
            </el-tag>
            <el-tag v-if="horse.healthSummary.overdueCount" type="danger" size="small" effect="plain">
              {{ horse.healthSummary.overdueCount }} 起逾期
            </el-tag>
            <el-tag v-if="!horse.healthSummary.openCount && horse.status === 'RESTING'" type="warning" size="small">
              休养中
            </el-tag>
            <el-tag v-if="horse.healthSummary.releasable" type="success" size="small" effect="dark">
              可复训放行
            </el-tag>
          </div>
          <div class="card__ops" @click.stop>
            <el-button size="small" text type="primary" @click="openHorseDialog(horse)">改档案</el-button>
            <el-button size="small" text type="primary" @click="gotoHealth(horse)">健康事件</el-button>
            <el-button size="small" text type="primary" @click="openStatusDialog(horse)">流转状态</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 右：该马的训练日历 -->
    <div class="wall__calendar eq-panel">
      <div class="eq-panel__head">
        <h2>
          训练日历
          <template v-if="calendar"> · {{ calendar.horseName }}（{{ calendar.horseNo }}）</template>
        </h2>
        <el-button size="small" text type="primary" @click="loadCalendar">刷新</el-button>
      </div>

      <div v-if="calendar" class="cal-legend">
        <el-tag size="small" effect="plain">{{ calendar.statusName }}</el-tag>
        <el-tag size="small" effect="plain">{{ calendar.rideLevelName }}</el-tag>
        <el-button size="small" text type="primary" @click="goHealthDesk">前往健康事件处置台 →</el-button>
        <span class="eq-muted">点空格子=排课，点有课的格子=看名单并约课</span>
      </div>

      <!-- 健康风险一屏看清：未闭环事件 + 最近复查结论 + 复训放行 -->
      <div v-if="health" class="health">
        <div class="health__head">
          <h3 class="health__title">健康事件总览</h3>
          <el-radio-group v-model="identity.role" size="small">
            <el-radio-button label="STAFF">工作人员</el-radio-button>
            <el-radio-button label="MANAGER">负责人</el-radio-button>
          </el-radio-group>
          <el-input v-model="identity.name" size="small" placeholder="操作人姓名" style="width: 120px" />
          <el-button size="small" @click="loadHealth">刷新</el-button>
        </div>

        <el-alert
          v-if="health.horseStatus === 'RESTING'"
          type="warning"
          :closable="false"
          :title="`该马正在休养：不能安排新的骑乘课程${health.openCount ? '，仍有 ' + health.openCount + ' 起未闭环健康事件' : ''}`"
          style="margin-bottom: 8px"
        />

        <div v-if="health.openEvents.length" class="health__events">
          <div
            v-for="event in health.openEvents"
            :key="event.id"
            class="he-row"
            @click="goHealthDesk(event.id)"
          >
            <el-tag :type="eventSeverityTag(event.severity)" size="small" effect="dark">
              {{ event.severityName }}
            </el-tag>
            <el-tag :type="eventStatusTag(event.status)" size="small">{{ event.statusName }}</el-tag>
            <el-tag v-if="event.overdue" type="danger" size="small" effect="dark">已逾期</el-tag>
            <span class="he-row__no">{{ event.eventNo }}</span>
            <span class="he-row__symptom">{{ event.symptom }}</span>
            <span class="eq-muted">复查日 {{ event.expectedReviewDate || '—' }}</span>
          </div>
        </div>
        <div v-else class="eq-muted" style="margin-bottom: 8px">没有未闭环的健康事件。</div>

        <div class="health__review">
          <b>最近一次复查结论：</b>
          <template v-if="health.latestReview">
            <el-tag :type="health.latestReview.result === 'PASS' ? 'success' : 'warning'" size="small">
              {{ health.latestReview.resultName }}
            </el-tag>
            {{ health.latestReview.reviewDate }} · {{ health.latestReview.conclusion }}
            <el-tag v-if="health.latestReview.expired" type="danger" size="small" effect="dark">结论已过期</el-tag>
            <span class="eq-muted">复查人 {{ health.latestReview.reviewer }}</span>
          </template>
          <span v-else class="eq-muted">暂无复查记录</span>
        </div>

        <!-- 放行区：按钮显隐只是体验，角色与全部业务条件都在后端强校验 -->
        <div class="health__release">
          <template v-if="identity.role === 'MANAGER'">
            <el-button
              type="primary"
              size="small"
              :disabled="!health.releasable"
              :loading="saving"
              @click="openRelease"
            >
              负责人复训放行
            </el-button>
          </template>
          <el-tag v-else type="info" size="small" effect="plain">复训放行仅负责人可确认</el-tag>
          <span
            v-for="(blocker, idx) in health.releaseBlockers"
            :key="idx"
            class="health__blocker"
          >
            ⚠ {{ blocker }}
          </span>
        </div>
      </div>

      <div v-if="calendar" class="cal">
        <div class="cal__corner">时段 \ 日期</div>
        <div v-for="date in calendar.dates" :key="date" class="cal__head">
          <strong>{{ date.slice(5) }}</strong>
          <span>{{ weekday(date) }}</span>
        </div>

        <template v-for="slot in calendar.slots" :key="slot">
          <div class="cal__slot">{{ slot }}</div>
          <div
            v-for="date in calendar.dates"
            :key="date + slot"
            class="cal__cell"
            :class="cellClass(date, slot)"
            @click="onCellClick(date, slot)"
          >
            <template v-if="cellOf(date, slot)">
              <div v-if="cellOf(date, slot).healthAffected" class="cal__affected">⚠ 健康事件受影响</div>
              <div class="cal__lesson">{{ cellOf(date, slot).lessonName }}</div>
              <div class="cal__line">{{ cellOf(date, slot).coachName }} 教练</div>
              <div class="cal__line">
                {{ cellOf(date, slot).bookedCount }}/{{ cellOf(date, slot).capacity }} 人
                <span class="cal__names">{{ memberNames(cellOf(date, slot)) }}</span>
              </div>
            </template>
            <div v-else class="cal__free">+ 排课</div>
          </div>
        </template>
      </div>

      <div v-else class="eq-empty">左侧点一匹马，这里显示它的训练日历</div>
    </div>
  </div>

  <!-- 马匹档案表单：基础信息 + 健康档案（副表） -->
  <el-dialog v-model="horseDialog" :title="horseForm.id ? '编辑马匹档案' : '新增马匹'" width="640px">
    <el-form label-width="96px">
      <el-divider content-position="left">基础信息（horse 主表）</el-divider>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="马匹编号">
            <el-input v-model="horseForm.horseNo" placeholder="如 H-007" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="马匹名">
            <el-input v-model="horseForm.name" placeholder="如 流星" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="品种">
            <el-input v-model="horseForm.breed" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="性别">
            <el-select v-model="horseForm.gender" clearable>
              <el-option label="公" value="MALE" />
              <el-option label="母" value="FEMALE" />
              <el-option label="骟" value="GELDING" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="出生年份">
            <el-input-number v-model="horseForm.birthYear" :min="1990" :max="2030" controls-position="right" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="骑乘等级">
            <el-select v-model="horseForm.rideLevel" clearable>
              <el-option label="初级安全马" value="BEGINNER_SAFE" />
              <el-option label="中级马" value="INTERMEDIATE" />
              <el-option label="高级马" value="ADVANCED" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col v-if="!horseForm.id" :span="12">
          <el-form-item label="初始状态">
            <el-select v-model="horseForm.status" clearable placeholder="不选则默认在役">
              <el-option label="在役" value="ACTIVE" />
              <el-option label="休养" value="RESTING" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">健康档案（horse_health 副表）</el-divider>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="上次体检">
            <el-date-picker v-model="horseForm.lastCheckDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="疫苗次数">
            <el-input-number v-model="horseForm.vaccineCount" :min="0" :max="99" controls-position="right" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="体重 kg">
            <el-input-number v-model="horseForm.weightKg" :min="0" :max="1200" :precision="1" controls-position="right" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="健康备注">
            <el-input v-model="horseForm.healthNote" type="textarea" :rows="2" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="horseDialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitHorse">保存</el-button>
    </template>
  </el-dialog>

  <!-- 状态机流转 -->
  <el-dialog v-model="statusDialog" title="马匹在役状态流转" width="420px">
    <p class="eq-muted">
      当前：<b>{{ statusForm.name }}</b> · {{ statusForm.currentName }}
    </p>
    <el-radio-group v-model="statusForm.target">
      <el-radio-button label="ACTIVE">在役</el-radio-button>
      <el-radio-button label="RESTING">休养</el-radio-button>
      <el-radio-button label="RETIRED">退役</el-radio-button>
    </el-radio-group>
    <p class="eq-muted" style="margin-top: 10px">退役是终态，流转过去之后不可回退。</p>
    <template #footer>
      <el-button @click="statusDialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitStatus">确认流转</el-button>
    </template>
  </el-dialog>

  <!-- 排课 -->
  <el-dialog v-model="sessionDialog" title="为这匹马排一节课" width="520px">
    <el-form label-width="92px">
      <el-form-item label="日期 / 时段">
        <el-input :model-value="sessionForm.sessionDate + ' ' + sessionForm.startTime" disabled />
      </el-form-item>
      <el-form-item label="马匹">
        <el-input :model-value="calendar ? calendar.horseName + '（' + calendar.horseNo + '）' : ''" disabled />
      </el-form-item>
      <el-form-item label="课程">
        <el-select v-model="sessionForm.lessonId" style="width: 100%" placeholder="选课程">
          <el-option
            v-for="lesson in lessons"
            :key="lesson.id"
            :label="lesson.name + '（' + lesson.categoryName + ' · 容量 ' + lesson.capacity + '）'"
            :value="lesson.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="教练">
        <el-select v-model="sessionForm.coachId" style="width: 100%" placeholder="选教练">
          <el-option
            v-for="coach in coaches"
            :key="coach.id"
            :label="coach.name + '（' + coach.levelName + ' · ' + coach.statusName + '）'"
            :value="coach.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="本场容量">
        <el-input-number v-model="sessionForm.capacity" :min="1" :max="20" controls-position="right" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="sessionDialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitSession">排课</el-button>
    </template>
  </el-dialog>

  <!-- 格子详情：已有排期时看名单 + 直接约课 -->
  <el-drawer v-model="cellDrawer" size="420px" :title="cellTitle">
    <template v-if="activeCell">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="课程">{{ activeCell.lessonName }}</el-descriptions-item>
        <el-descriptions-item label="教练">{{ activeCell.coachName }}</el-descriptions-item>
        <el-descriptions-item label="人数">
          {{ activeCell.bookedCount }} / {{ activeCell.capacity }}（{{ activeCell.statusName }}）
        </el-descriptions-item>
      </el-descriptions>

      <h4 class="drawer-title">已约会员</h4>
      <div v-if="!activeCell.records.length" class="eq-muted">还没有会员预约这一段</div>
      <div v-for="item in activeCell.records" :key="item.recordId" class="drawer-row">
        <span>{{ item.memberName }}</span>
        <el-tag size="small" effect="plain">{{ item.statusName }}</el-tag>
      </div>

      <h4 class="drawer-title">现场给会员约这节课</h4>
      <el-select v-model="bookForm.memberId" placeholder="选会员" style="width: 100%; margin-bottom: 10px">
        <el-option
          v-for="member in members"
          :key="member.id"
          :label="member.name + '（' + member.levelName + ' · 余 ' + member.cardTimes + ' 次）'"
          :value="member.id"
        />
      </el-select>
      <el-input v-model="bookForm.remark" placeholder="备注（可空）" style="margin-bottom: 10px" />
      <el-button type="primary" :loading="saving" style="width: 100%" @click="submitBook">确认预约</el-button>
    </template>
  </el-drawer>

  <!-- 负责人复训放行确认 -->
  <el-dialog v-model="releaseDialog" title="复训放行确认（负责人）" width="480px">
    <el-alert
      type="warning"
      show-icon
      :closable="false"
      title="放行将关闭该马全部未闭环事件并恢复在役；后端会再次校验角色与全部放行条件，条件不满足会明确拒绝。"
      style="margin-bottom: 10px"
    />
    <el-form label-width="84px">
      <el-form-item label="负责人">
        <el-input v-model="identity.name" placeholder="负责人姓名" style="width: 220px" />
      </el-form-item>
      <el-form-item label="放行说明">
        <el-input v-model="releaseNote" type="textarea" :rows="2" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>
    <div v-if="health && health.releaseBlockers.length" class="release-blockers">
      <div v-for="(blocker, idx) in health.releaseBlockers" :key="idx">⚠ {{ blocker }}</div>
    </div>
    <template #footer>
      <el-button @click="releaseDialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitRelease">确认放行，恢复在役</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api, { requestId } from '../api'
import { identity, withIdentity } from '../identity'
import { navigate } from '../router'

const horses = ref([])
const lessons = ref([])
const coaches = ref([])
const members = ref([])
const calendar = ref(null)
const health = ref(null)

const keyword = ref('')
const statusFilter = ref('')
const selectedHorseId = ref(null)

const saving = ref(false)
const horseDialog = ref(false)
const statusDialog = ref(false)
const sessionDialog = ref(false)
const cellDrawer = ref(false)
const activeCell = ref(null)
const releaseDialog = ref(false)
const releaseNote = ref('')

const horseForm = reactive(emptyHorse())
const statusForm = reactive({ id: null, name: '', currentName: '', target: '' })
const sessionForm = reactive({ lessonId: null, coachId: null, sessionDate: '', startTime: '', endTime: '', capacity: 6 })
const bookForm = reactive({ memberId: null, remark: '' })

function emptyHorse() {
  return {
    id: null,
    horseNo: '',
    name: '',
    breed: '',
    gender: '',
    birthYear: null,
    status: '',
    rideLevel: '',
    lastCheckDate: '',
    vaccineCount: null,
    weightKg: null,
    healthNote: ''
  }
}

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return horses.value.filter((horse) => {
    if (statusFilter.value && horse.status !== statusFilter.value) {
      return false
    }
    if (!kw) {
      return true
    }
    return [horse.horseNo, horse.name, horse.breed]
      .filter(Boolean)
      .some((text) => String(text).toLowerCase().includes(kw))
  })
})

const cellTitle = computed(() => {
  if (!activeCell.value) {
    return '排期详情'
  }
  return `${activeCell.value.date} ${activeCell.value.startTime} ${activeCell.value.lessonName}`
})

function statusTag(status) {
  if (status === 'ACTIVE') return 'success'
  if (status === 'RESTING') return 'warning'
  return 'info'
}

function weekday(date) {
  const names = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return names[new Date(date + 'T00:00:00').getDay()]
}

function cellOf(date, slot) {
  if (!calendar.value) {
    return null
  }
  return calendar.value.cells.find((cell) => cell.date === date && cell.startTime === slot) || null
}

function memberNames(cell) {
  if (!cell.records || !cell.records.length) {
    return ''
  }
  return '· ' + cell.records.map((item) => item.memberName).join('、')
}

function cellClass(date, slot) {
  const cell = cellOf(date, slot)
  if (!cell) {
    return 'cal__cell--free'
  }
  if (cell.status === 'CANCELED') {
    return 'cal__cell--canceled'
  }
  if (cell.healthAffected) {
    return 'cal__cell--affected'
  }
  if (cell.bookedCount >= cell.capacity) {
    return 'cal__cell--full'
  }
  return 'cal__cell--booked'
}

async function loadBase() {
  try {
    const [horseList, lessonList, coachList, memberList] = await Promise.all([
      api.horses(),
      api.lessons(),
      api.coaches(),
      api.members()
    ])
    horses.value = horseList || []
    lessons.value = lessonList || []
    coaches.value = coachList || []
    members.value = memberList || []
    await loadHealthSummaries()
    if (horses.value.length && selectedHorseId.value === null) {
      await selectHorse(horses.value[0])
    }
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function loadCalendar() {
  if (selectedHorseId.value === null) {
    return
  }
  try {
    calendar.value = await api.horseCalendar(selectedHorseId.value, 7)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function loadHealth() {
  if (selectedHorseId.value === null) {
    return
  }
  try {
    health.value = await api.horseHealth(selectedHorseId.value)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

/** 卡片墙上直接标出每匹马的未闭环 / 逾期 / 可放行风险，值班不用逐匹点进去猜 */
async function loadHealthSummaries() {
  await Promise.all(
    horses.value.map(async (horse) => {
      try {
        horse.healthSummary = await api.horseHealth(horse.id)
      } catch (error) {
        horse.healthSummary = null
      }
    })
  )
}

function gotoHealth(event) {
  navigate('/health')
}

function goHealthDesk(eventId) {
  try {
    sessionStorage.setItem('eq-health-focus', eventId ? String(eventId) : '')
  } catch (error) {
    // 忽略
  }
  navigate('/health')
}

function eventSeverityTag(severity) {
  if (severity === 'HIGH') return 'danger'
  if (severity === 'MEDIUM') return 'warning'
  return 'info'
}

function eventStatusTag(status) {
  if (status === 'PENDING') return 'danger'
  if (status === 'OBSERVING') return 'warning'
  if (status === 'REVIEW_PENDING') return 'primary'
  return 'info'
}

function openRelease() {
  if (!identity.name) {
    ElMessage.warning('请先填写负责人姓名')
    return
  }
  if (health.value && health.value.releaseBlockers.length) {
    ElMessage.error('当前不满足放行条件：' + health.value.releaseBlockers.join('；'))
    return
  }
  releaseNote.value = ''
  releaseDialog.value = true
}

async function submitRelease() {
  if (!identity.name) {
    ElMessage.warning('请先填写负责人姓名')
    return
  }
  saving.value = true
  try {
    await api.releaseHorse(
      selectedHorseId.value,
      withIdentity({
        requestId: requestId('release'),
        expectedVersion: health.value.healthVersion,
        note: releaseNote.value || null
      })
    )
    ElMessage.success('复训放行已确认：全部未闭环事件关闭，马匹恢复在役')
    releaseDialog.value = false
    await loadBase()
    await loadCalendar()
    await loadHealth()
  } catch (error) {
    if (error.conflict) {
      ElMessage.error(error.message)
      await Promise.all([loadHealth(), loadBase()])
    } else {
      ElMessage.error(error.message)
    }
  } finally {
    saving.value = false
  }
}

async function selectHorse(horse) {
  selectedHorseId.value = horse.id
  await Promise.all([loadCalendar(), loadHealth()])
}

function openHorseDialog(horse) {
  Object.assign(horseForm, emptyHorse())
  if (horse) {
    Object.assign(horseForm, {
      id: horse.id,
      horseNo: horse.horseNo,
      name: horse.name,
      breed: horse.breed,
      gender: horse.gender,
      birthYear: horse.birthYear,
      status: horse.status,
      rideLevel: horse.rideLevel,
      lastCheckDate: horse.lastCheckDate,
      vaccineCount: horse.vaccineCount,
      weightKg: horse.weightKg,
      healthNote: horse.healthNote
    })
  }
  horseDialog.value = true
}

async function submitHorse() {
  saving.value = true
  try {
    const payload = {
      horseNo: horseForm.horseNo,
      name: horseForm.name,
      breed: horseForm.breed,
      gender: horseForm.gender,
      birthYear: horseForm.birthYear,
      rideLevel: horseForm.rideLevel,
      lastCheckDate: horseForm.lastCheckDate || null,
      vaccineCount: horseForm.vaccineCount,
      weightKg: horseForm.weightKg,
      healthNote: horseForm.healthNote
    }
    if (horseForm.id) {
      // 局部更新：状态走状态机，不在编辑表单里改
      await api.updateHorse(horseForm.id, payload)
      ElMessage.success('马匹档案已更新（含健康档案副表）')
    } else {
      payload.status = horseForm.status || null
      const created = await api.createHorse(payload)
      ElMessage.success('马匹已建档')
      selectedHorseId.value = created.id
    }
    horseDialog.value = false
    await loadBase()
    await loadCalendar()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

function openStatusDialog(horse) {
  statusForm.id = horse.id
  statusForm.name = horse.name
  statusForm.currentName = horse.statusName
  statusForm.target = horse.status
  statusDialog.value = true
}

async function submitStatus() {
  saving.value = true
  try {
    await api.changeHorseStatus(statusForm.id, statusForm.target)
    ElMessage.success('状态已流转')
    statusDialog.value = false
    await loadBase()
    await loadCalendar()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

function onCellClick(date, slot) {
  // 休养 / 退役马不能再排新课（后端也强校验，这里提前挡住并解释原因）
  if (calendar.value && calendar.value.status !== 'ACTIVE') {
    ElMessage.warning(
      `马匹「${calendar.value.horseName}」当前为「${calendar.value.statusName}」状态，不能安排新课程；请先在健康事件处置台闭环并由负责人复训放行`
    )
    return
  }
  const cell = cellOf(date, slot)
  if (cell) {
    activeCell.value = cell
    bookForm.memberId = null
    bookForm.remark = ''
    cellDrawer.value = true
    return
  }
  sessionForm.lessonId = null
  sessionForm.coachId = null
  sessionForm.sessionDate = date
  sessionForm.startTime = slot
  sessionForm.endTime = nextHour(slot)
  sessionForm.capacity = 6
  sessionDialog.value = true
}

function nextHour(slot) {
  const hour = Number(slot.slice(0, 2))
  return String((hour + 1) % 24).padStart(2, '0') + ':' + slot.slice(3)
}

async function submitSession() {
  saving.value = true
  try {
    await api.createSession({
      lessonId: sessionForm.lessonId,
      coachId: sessionForm.coachId,
      horseId: selectedHorseId.value,
      sessionDate: sessionForm.sessionDate,
      startTime: sessionForm.startTime,
      endTime: sessionForm.endTime,
      capacity: sessionForm.capacity
    })
    ElMessage.success('排课成功')
    sessionDialog.value = false
    await loadCalendar()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

async function submitBook() {
  saving.value = true
  try {
    await api.book({
      memberId: bookForm.memberId,
      sessionId: activeCell.value.sessionId,
      horseId: selectedHorseId.value,
      remark: bookForm.remark
    })
    ElMessage.success('预约成功，已按会员卡规则扣次扣费')
    cellDrawer.value = false
    await loadCalendar()
    members.value = (await api.members()) || members.value
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

onMounted(loadBase)

// 选了课程就把本场容量跟课程容量对齐，避免默认值超过课程容量上限
watch(
  () => sessionForm.lessonId,
  (id) => {
    const lesson = lessons.value.find((item) => item.id === id)
    if (lesson) {
      sessionForm.capacity = lesson.capacity
    }
  }
)
</script>

<style scoped>
.wall {
  display: grid;
  grid-template-columns: minmax(280px, 340px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 640px;
  overflow-y: auto;
  padding-right: 2px;
}

.card {
  border: 1px solid #ece3df;
  border-radius: 10px;
  padding: 12px;
  cursor: pointer;
  background: #fffdfc;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.card:hover {
  border-color: #cbb4a9;
  box-shadow: 0 2px 10px rgba(109, 76, 65, 0.12);
}

.card--on {
  border-color: #6d4c41;
  box-shadow: 0 0 0 2px rgba(109, 76, 65, 0.16);
  background: #fdf7f4;
}

.card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card__no {
  font-size: 12px;
  letter-spacing: 1px;
  color: #a08d86;
}

.card__name {
  font-size: 17px;
  font-weight: 700;
  color: #4a3129;
  margin: 6px 0 2px;
}

.card__meta {
  font-size: 12px;
  color: #8c7c76;
}

.card__tags {
  display: flex;
  gap: 6px;
  margin-top: 8px;
}

.card__health {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
  font-size: 11px;
  color: #9a8b85;
}

.card__risk {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 7px;
}

.cal__affected {
  font-size: 10px;
  font-weight: 700;
  color: #d9480f;
  background: rgba(255, 255, 255, 0.82);
  border-radius: 4px;
  padding: 0 4px;
  margin-bottom: 2px;
}

.health {
  margin: 12px 0 4px;
  border: 1px solid #e4d5ce;
  border-radius: 10px;
  padding: 12px;
  background: #fffdfc;
}

.health__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.health__title {
  margin: 0;
  font-size: 14px;
  color: #4a3129;
  flex: 1;
}

.health__events {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.he-row {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 6px 8px;
  border: 1px solid #ece3df;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  font-size: 12px;
}

.he-row:hover {
  border-color: #cbb4a9;
}

.he-row__no {
  color: #a08d86;
  font-size: 11px;
}

.he-row__symptom {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #4a3129;
}

.health__review {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.9;
  color: #4a3129;
  border-top: 1px dashed #efe6e1;
  padding-top: 8px;
}

.health__release {
  margin-top: 10px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.health__blocker {
  font-size: 12px;
  color: #c0392b;
}

.release-blockers {
  color: #c0392b;
  font-size: 12px;
  line-height: 1.9;
  margin-top: 6px;
}

.card__ops {
  display: flex;
  justify-content: flex-end;
  gap: 2px;
  margin-top: 6px;
  border-top: 1px dashed #efe6e1;
  padding-top: 6px;
}

.cal-legend {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.cal {
  display: grid;
  grid-template-columns: 62px repeat(7, minmax(0, 1fr));
  gap: 5px;
}

.cal__corner,
.cal__head {
  font-size: 11px;
  color: #8c7c76;
  text-align: center;
  padding: 6px 0;
}

.cal__head strong {
  display: block;
  font-size: 13px;
  color: #4a3129;
}

.cal__head span {
  font-size: 11px;
  color: #a89a94;
}

.cal__slot {
  font-size: 12px;
  color: #8c7c76;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f7f2ef;
  border-radius: 8px;
}

.cal__cell {
  min-height: 58px;
  border-radius: 8px;
  border: 1px dashed #ded3ce;
  padding: 5px 6px;
  cursor: pointer;
  overflow: hidden;
  transition: transform 0.12s, box-shadow 0.12s;
}

.cal__cell:hover {
  transform: translateY(-1px);
  box-shadow: 0 3px 10px rgba(109, 76, 65, 0.15);
}

.cal__cell--free {
  background: #fbfaf9;
}

.cal__cell--booked {
  background: #f3e9e4;
  border-style: solid;
  border-color: #dcc7bd;
}

.cal__cell--full {
  background: #6d4c41;
  border-style: solid;
  border-color: #573d34;
  color: #fff;
}

.cal__cell--affected {
  background: #fde8e2;
  border-style: solid;
  border-color: #e08a6f;
}

.cal__cell--canceled {
  background: #f4f4f5;
  border-style: dashed;
  color: #a0a0a0;
  text-decoration: line-through;
}

.cal__lesson {
  font-size: 12px;
  font-weight: 600;
  line-height: 1.3;
}

.cal__line {
  font-size: 11px;
  opacity: 0.85;
  line-height: 1.35;
}

.cal__names {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cal__free {
  font-size: 11px;
  color: #b6a6a0;
  padding-top: 16px;
  text-align: center;
}

.drawer-title {
  margin: 18px 0 8px;
  font-size: 13px;
  color: #4a3129;
}

.drawer-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 0;
  border-bottom: 1px dashed #eee6e2;
  font-size: 13px;
}
</style>
