<template>
  <div class="desk">
    <!-- 左：事件处置台 -->
    <div class="desk__list eq-panel">
      <div class="eq-panel__head">
        <h2>健康事件处置台</h2>
        <el-button type="primary" @click="openCreate">登记健康事件</el-button>
      </div>

      <div class="eq-toolbar">
        <el-select v-model="horseFilter" placeholder="全部马匹" clearable filterable style="width: 170px">
          <el-option v-for="horse in horses" :key="horse.id" :label="horse.horseNo + ' ' + horse.name" :value="horse.id" />
        </el-select>
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 128px">
          <el-option label="待处理" value="PENDING" />
          <el-option label="观察中" value="OBSERVING" />
          <el-option label="待复查" value="REVIEW_PENDING" />
          <el-option label="已关闭" value="CLOSED" />
        </el-select>
        <el-checkbox v-model="overdueOnly">仅看逾期</el-checkbox>
        <el-button size="small" @click="reload">刷新</el-button>
        <span class="eq-muted">共 {{ events.length }} 条</span>
      </div>

      <div v-if="!events.length" class="eq-empty">没有符合条件的健康事件</div>

      <div class="evlist">
        <div
          v-for="event in events"
          :key="event.id"
          class="ev"
          :class="{ 'ev--on': event.id === selectedId, 'ev--closed': event.status === 'CLOSED' }"
          @click="selectEvent(event.id)"
        >
          <div class="ev__top">
            <span class="ev__no">{{ event.eventNo }}</span>
            <el-tag :type="severityTag(event.severity)" size="small" effect="dark">
              {{ event.severityName }}
            </el-tag>
          </div>
          <div class="ev__horse">
            {{ event.horseNo }} {{ event.horseName }}
            <el-tag size="small" effect="plain">{{ event.horseStatusName }}</el-tag>
          </div>
          <div class="ev__symptom">{{ event.symptom }}</div>
          <div class="ev__foot">
            <el-tag :type="statusTag(event.status)" size="small">{{ event.statusName }}</el-tag>
            <el-tag v-if="event.overdue" type="danger" size="small" effect="dark">已逾期</el-tag>
            <span class="eq-muted">复查 {{ event.expectedReviewDate || '—' }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右：事件链详情 -->
    <div class="desk__detail eq-panel">
      <div v-if="!detail" class="eq-empty">左侧选择一条事件，这里展示完整处置链；新登记的伤病、别人的复查都会实时反映</div>

      <template v-else>
        <div class="eq-panel__head">
          <h2>
            {{ detail.eventNo }}
            <el-tag :type="statusTag(detail.status)" size="small" style="margin-left: 8px">
              {{ detail.statusName }}
            </el-tag>
            <el-tag :type="severityTag(detail.severity)" size="small" effect="plain" style="margin-left: 6px">
              {{ detail.severityName }}
            </el-tag>
            <el-tag v-if="detail.overdue" type="danger" size="small" effect="dark" style="margin-left: 6px">已逾期</el-tag>
          </h2>
          <el-button size="small" @click="reloadDetail">重新载入最新事件链</el-button>
        </div>

        <el-alert
          v-if="stale"
          type="warning"
          show-icon
          :closable="false"
          title="该马的事件链已被其他人更新，当前展示的是你载入时的旧版本，请点「重新载入最新事件链」后再提交"
          style="margin-bottom: 12px"
        />

        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="马匹">
            {{ detail.horseNo }} {{ detail.horseName }}
            <el-tag size="small" effect="plain">{{ detail.horseStatusName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="发生时间">{{ formatTime(detail.occurredAt) }}</el-descriptions-item>
          <el-descriptions-item label="症状说明" :span="2">{{ detail.symptom }}</el-descriptions-item>
          <el-descriptions-item label="处置建议" :span="2">{{ detail.treatmentAdvice }}</el-descriptions-item>
          <el-descriptions-item label="预计复查日">
            {{ detail.expectedReviewDate || '—' }}
            <el-tag v-if="detail.overdue" type="danger" size="small" effect="dark" style="margin-left: 6px">已逾期</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="登记人">{{ detail.createdBy }}（{{ roleName(detail.createdRole) }}）</el-descriptions-item>
        </el-descriptions>

        <!-- 操作区：已关闭事件只能看不能动 -->
        <div v-if="detail.status !== 'CLOSED'" class="ops">
          <el-button size="small" @click="openSupplement">补充处置</el-button>
          <el-button
            v-for="target in availableTargets"
            :key="target"
            size="small"
            @click="openTransition(target)"
          >
            流转为{{ eventStatusName(target) }}
          </el-button>
          <el-button v-if="detail.status === 'REVIEW_PENDING'" size="small" type="primary" plain @click="openReview">
            提交复查
          </el-button>
        </div>
        <el-alert
          v-else
          type="info"
          :closable="false"
          :title="`事件已于 ${formatTime(detail.closedAt)} 由负责人 ${detail.closedBy || ''} 复训放行关闭，处置历史只可查看`"
          style="margin: 12px 0"
        />

        <!-- 受影响的未来排期：只标记、不删除，可追溯 -->
        <template v-if="detail.affectedSessions.length">
          <h3 class="sec-title">受影响的未来排期（{{ detail.affectedSessions.length }} 场，已标记未删除）</h3>
          <div
            v-for="row in detail.affectedSessions"
            :key="row.sessionId"
            class="aff"
          >
            <el-tag type="danger" size="small" effect="dark">受影响</el-tag>
            <span>{{ row.sessionDate }} {{ row.startTime }}-{{ row.endTime }}</span>
            <span>{{ row.lessonName }} · {{ row.coachName }}教练</span>
            <span class="eq-muted">已约 {{ row.bookedCount }} 人</span>
            <span class="eq-muted">标记于 {{ formatTime(row.markedAt) }} · {{ row.markedBy }}</span>
          </div>
        </template>

        <!-- 复查历史 -->
        <h3 class="sec-title">复查记录（{{ detail.reviews.length }}）</h3>
        <div v-if="!detail.reviews.length" class="eq-muted" style="padding: 4px 0 8px">还没有复查记录</div>
        <div v-for="review in detail.reviews" :key="review.id" class="review">
          <div class="review__top">
            <el-tag :type="reviewTag(review.result)" size="small">{{ review.resultName }}</el-tag>
            <span>{{ review.reviewDate }}</span>
            <span class="eq-muted">{{ review.reviewer }}（{{ review.roleName }}）</span>
            <el-tag v-if="review.expired" type="danger" size="small" effect="dark">结论已过期</el-tag>
          </div>
          <div class="review__body">{{ review.conclusion }}</div>
          <div v-if="review.nextReviewDate" class="eq-muted">下次复查日：{{ review.nextReviewDate }}</div>
        </div>

        <!-- 处置历史（只追加，永不覆盖） -->
        <h3 class="sec-title">处置历史（{{ detail.logs.length }}，只追加不可覆盖）</h3>
        <el-timeline>
          <el-timeline-item
            v-for="log in detail.logs"
            :key="log.id"
            :timestamp="formatTime(log.operatedAt) + ' · ' + log.operator + '（' + log.roleName + '）'"
            :type="log.action === 'RELEASE' ? 'success' : log.action === 'REGISTER' ? 'primary' : undefined"
          >
            <div class="log__action">
              <el-tag size="small" effect="plain">{{ log.actionName }}</el-tag>
              <span v-if="log.fromStatusName || log.toStatusName" class="eq-muted">
                {{ log.fromStatusName || '—' }} → {{ log.toStatusName || '—' }}
              </span>
            </div>
            <div>{{ log.note }}</div>
          </el-timeline-item>
        </el-timeline>
      </template>
    </div>

    <!-- 登记事件 -->
    <el-dialog v-model="createDialog" title="登记马匹健康事件" width="560px">
      <el-form label-width="104px">
        <el-form-item label="马匹">
          <el-select v-model="createForm.horseId" filterable placeholder="选择马匹" style="width: 100%">
            <el-option
              v-for="horse in horses"
              :key="horse.id"
              :label="horse.horseNo + ' ' + horse.name + '（' + horse.statusName + '）'"
              :value="horse.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="发生时间">
          <el-date-picker
            v-model="createForm.occurredAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="不选则取当前时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="严重程度">
          <el-radio-group v-model="createForm.severity">
            <el-radio-button label="HIGH">高风险</el-radio-button>
            <el-radio-button label="MEDIUM">中风险</el-radio-button>
            <el-radio-button label="LOW">低风险</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-alert
          v-if="createForm.severity === 'HIGH'"
          type="warning"
          show-icon
          :closable="false"
          title="高风险事件登记后马匹将立即进入休养：不能再安排新课程，其名下未来排期会被标记为受影响（不会删除），并在本事件中可追溯。"
          style="margin-bottom: 12px"
        />
        <el-form-item label="症状说明">
          <el-input v-model="createForm.symptom" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="处置建议">
          <el-input v-model="createForm.treatmentAdvice" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="预计复查日">
          <el-date-picker v-model="createForm.expectedReviewDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="identity.name" placeholder="值班操作人姓名" style="width: 200px" />
          <el-radio-group v-model="identity.role" size="small" style="margin-left: 10px">
            <el-radio-button label="STAFF">工作人员</el-radio-button>
            <el-radio-button label="MANAGER">负责人</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">登记</el-button>
      </template>
    </el-dialog>

    <!-- 流转 -->
    <el-dialog v-model="transitionDialog" :title="'流转为「' + (transitionTargetName) + '」'" width="460px">
      <el-form label-width="84px">
        <el-form-item label="说明">
          <el-input v-model="transitionForm.note" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <p class="eq-muted">每次流转都会追加一条带操作时间与处理人的历史，历史不可覆盖。</p>
      <template #footer>
        <el-button @click="transitionDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitTransition">确认流转</el-button>
      </template>
    </el-dialog>

    <!-- 补充处置 -->
    <el-dialog v-model="supplementDialog" title="补充处置说明" width="460px">
      <el-input v-model="supplementNote" type="textarea" :rows="3" maxlength="500" show-word-limit />
      <p class="eq-muted" style="margin-top: 8px">补充只追加历史，不会修改症状、处置建议或任何既有记录。</p>
      <template #footer>
        <el-button @click="supplementDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitSupplement">提交补充</el-button>
      </template>
    </el-dialog>

    <!-- 复查 -->
    <el-dialog v-model="reviewDialog" title="提交复查结论" width="520px">
      <el-form label-width="104px">
        <el-form-item label="复查日期">
          <el-date-picker v-model="reviewForm.reviewDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="复查结论">
          <el-radio-group v-model="reviewForm.result">
            <el-radio label="OBSERVE">继续观察</el-radio>
            <el-radio label="RESCHEDULE">调整下次复查日</el-radio>
            <el-radio label="PASS">复查合格·申请放行</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="复查说明">
          <el-input v-model="reviewForm.conclusion" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item :label="reviewForm.result === 'PASS' ? '合格有效期至' : '下次复查日'">
          <el-date-picker
            v-model="reviewForm.nextReviewDate"
            type="date"
            value-format="YYYY-MM-DD"
            :placeholder="reviewForm.result === 'PASS' ? '不填=长期有效；填写则过了该日结论失效' : '必填'"
            style="width: 100%"
          />
        </el-form-item>
        <el-alert
          v-if="reviewForm.result === 'PASS'"
          type="info"
          show-icon
          :closable="false"
          title="复查合格后事件停在「待复查」，须由负责人确认复训放行；若期间该马又登记了更新的伤病，本次合格结论不能作为放行依据。"
        />
      </el-form>
      <template #footer>
        <el-button @click="reviewDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReview">提交复查</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api, { requestId } from '../api'
import { identity, withIdentity } from '../identity'

const horses = ref([])
const events = ref([])
const detail = ref(null)
const selectedId = ref(null)

const horseFilter = ref(null)
const statusFilter = ref('')
const overdueOnly = ref(false)

const saving = ref(false)
const createDialog = ref(false)
const transitionDialog = ref(false)
const supplementDialog = ref(false)
const reviewDialog = ref(false)
const stale = ref(false)

const createForm = reactive(emptyCreate())
const transitionForm = reactive({ target: '', note: '' })
const reviewForm = reactive(emptyReview())
const supplementNote = ref('')

function emptyCreate() {
  return {
    horseId: null,
    occurredAt: '',
    severity: 'MEDIUM',
    symptom: '',
    treatmentAdvice: '',
    expectedReviewDate: ''
  }
}

function emptyReview() {
  return { reviewDate: today(), result: 'OBSERVE', conclusion: '', nextReviewDate: '' }
}

function today() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const availableTargets = computed(() => {
  if (!detail.value) {
    return []
  }
  const edges = {
    PENDING: ['OBSERVING', 'REVIEW_PENDING'],
    OBSERVING: ['REVIEW_PENDING'],
    REVIEW_PENDING: ['OBSERVING']
  }
  return edges[detail.value.status] || []
})

const transitionTargetName = computed(() => eventStatusName(transitionForm.target))

function eventStatusName(status) {
  return { PENDING: '待处理', OBSERVING: '观察中', REVIEW_PENDING: '待复查', CLOSED: '已关闭' }[status] || status
}

function roleName(role) {
  return role === 'MANAGER' ? '负责人' : '工作人员'
}

function statusTag(status) {
  if (status === 'PENDING') return 'danger'
  if (status === 'OBSERVING') return 'warning'
  if (status === 'REVIEW_PENDING') return 'primary'
  return 'info'
}

function severityTag(severity) {
  if (severity === 'HIGH') return 'danger'
  if (severity === 'MEDIUM') return 'warning'
  return 'info'
}

function reviewTag(result) {
  if (result === 'PASS') return 'success'
  if (result === 'OBSERVE') return 'warning'
  return 'primary'
}

function formatTime(text) {
  if (!text) {
    return '—'
  }
  return String(text).replace('T', ' ').slice(0, 16)
}

async function loadHorses() {
  horses.value = (await api.horses()) || []
}

async function reload() {
  try {
    const params = {}
    if (horseFilter.value) {
      params.horseId = horseFilter.value
    }
    if (statusFilter.value) {
      params.status = statusFilter.value
    }
    if (overdueOnly.value) {
      params.overdue = true
    }
    events.value = (await api.healthEvents(params)) || []
    if (selectedId.value) {
      await reloadDetail()
    }
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function selectEvent(id) {
  selectedId.value = id
  await reloadDetail()
}

async function reloadDetail() {
  if (!selectedId.value) {
    return
  }
  try {
    detail.value = await api.healthEvent(selectedId.value)
    stale.value = false
  } catch (error) {
    ElMessage.error(error.message)
  }
}

function openCreate() {
  Object.assign(createForm, emptyCreate())
  createForm.horseId = horseFilter.value || (horses.value.length ? horses.value[0].id : null)
  createDialog.value = true
}

async function submitCreate() {
  if (!identity.name) {
    ElMessage.warning('请先填写操作人姓名')
    return
  }
  saving.value = true
  try {
    const created = await api.createHealthEvent(
      withIdentity({
        requestId: requestId('register'),
        horseId: createForm.horseId,
        occurredAt: createForm.occurredAt || null,
        severity: createForm.severity,
        symptom: createForm.symptom,
        treatmentAdvice: createForm.treatmentAdvice,
        expectedReviewDate: createForm.expectedReviewDate
      })
    )
    createDialog.value = false
    ElMessage.success(
      createForm.severity === 'HIGH'
        ? '高风险事件已登记，马匹已立即休养，未来排期已标记受影响（未删除）'
        : '健康事件已登记'
    )
    await loadHorses()
    await reload()
    if (created && created.id) {
      await selectEvent(created.id)
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

function openTransition(target) {
  transitionForm.target = target
  transitionForm.note = ''
  transitionDialog.value = true
}

async function submitTransition() {
  if (!identity.name) {
    ElMessage.warning('请先填写操作人姓名')
    return
  }
  if (!transitionForm.note.trim()) {
    ElMessage.warning('请填写本次流转说明')
    return
  }
  saving.value = true
  try {
    await api.transitionEvent(
      selectedId.value,
      withIdentity({
        requestId: requestId('transition'),
        targetStatus: transitionForm.target,
        note: transitionForm.note,
        expectedVersion: detail.value.horseHealthVersion
      })
    )
    transitionDialog.value = false
    ElMessage.success('事件状态已流转')
    await reload()
  } catch (error) {
    await handleConflict(error)
  } finally {
    saving.value = false
  }
}

function openSupplement() {
  supplementNote.value = ''
  supplementDialog.value = true
}

async function submitSupplement() {
  if (!supplementNote.value.trim()) {
    ElMessage.warning('请填写要补充的处置说明')
    return
  }
  saving.value = true
  try {
    await api.supplementEvent(
      selectedId.value,
      withIdentity({
        requestId: requestId('supplement'),
        note: supplementNote.value,
        expectedVersion: detail.value.horseHealthVersion
      })
    )
    supplementDialog.value = false
    ElMessage.success('处置说明已补充')
    await reload()
  } catch (error) {
    await handleConflict(error)
  } finally {
    saving.value = false
  }
}

function openReview() {
  Object.assign(reviewForm, emptyReview())
  reviewDialog.value = true
}

async function submitReview() {
  if (!reviewForm.conclusion.trim()) {
    ElMessage.warning('请填写复查结论')
    return
  }
  if (reviewForm.result !== 'PASS' && !reviewForm.nextReviewDate) {
    ElMessage.warning('继续观察 / 调整复查日必须填写下次复查日')
    return
  }
  saving.value = true
  try {
    await api.reviewEvent(
      selectedId.value,
      withIdentity({
        requestId: requestId('review'),
        reviewDate: reviewForm.reviewDate,
        result: reviewForm.result,
        conclusion: reviewForm.conclusion,
        nextReviewDate: reviewForm.nextReviewDate || null,
        expectedVersion: detail.value.horseHealthVersion
      })
    )
    reviewDialog.value = false
    ElMessage.success('复查记录已追加')
    await reload()
  } catch (error) {
    await handleConflict(error)
  } finally {
    saving.value = false
  }
}

/**
 * 并发冲突统一处理：后端 409 表示打开页面后该马事件链被别人动过。
 * 不重试旧提交，先拉最新数据并显式提示，让用户重新决定。
 */
async function handleConflict(error) {
  saving.value = false
  if (error.conflict) {
    stale.value = true
    ElMessage.error(error.message)
    await Promise.all([reloadDetail(), loadHorses()])
    stale.value = true
  } else {
    ElMessage.error(error.message)
  }
}

onMounted(async () => {
  try {
    await loadHorses()
    // 先拉全量事件，判断从马匹页跳来的焦点事件属于哪匹马
    events.value = (await api.healthEvents({})) || []
    let focusId = null
    try {
      const raw = sessionStorage.getItem('eq-health-focus')
      sessionStorage.removeItem('eq-health-focus')
      focusId = raw ? Number(raw) : null
    } catch (error) {
      // 忽略
    }
    if (focusId) {
      const focused = events.value.find((event) => event.id === focusId)
      if (focused) {
        horseFilter.value = focused.horseId
      }
    }
    await reload()
    if (focusId && events.value.some((event) => event.id === focusId)) {
      await selectEvent(focusId)
    }
  } catch (error) {
    ElMessage.error(error.message)
  }
})

watch([horseFilter, statusFilter, overdueOnly], () => {
  if (!saving.value) {
    reload()
  }
})
</script>

<style scoped>
.desk {
  display: grid;
  grid-template-columns: minmax(300px, 380px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.evlist {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 70vh;
  overflow-y: auto;
  padding-right: 2px;
}

.ev {
  border: 1px solid #ece3df;
  border-radius: 10px;
  padding: 11px 12px;
  cursor: pointer;
  background: #fffdfc;
}

.ev:hover {
  border-color: #cbb4a9;
  box-shadow: 0 2px 10px rgba(109, 76, 65, 0.1);
}

.ev--on {
  border-color: #6d4c41;
  box-shadow: 0 0 0 2px rgba(109, 76, 65, 0.15);
  background: #fdf7f4;
}

.ev--closed {
  opacity: 0.7;
  background: #f7f7f7;
}

.ev__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ev__no {
  font-size: 12px;
  letter-spacing: 1px;
  color: #a08d86;
}

.ev__horse {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #4a3129;
  margin: 5px 0 3px;
}

.ev__symptom {
  font-size: 12px;
  color: #6b5b55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.ev__foot {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  font-size: 11px;
}

.ops {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 14px 0 4px;
  padding: 10px;
  background: #faf6f4;
  border-radius: 8px;
}

.sec-title {
  margin: 18px 0 8px;
  font-size: 14px;
  color: #4a3129;
}

.aff {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 10px;
  border: 1px solid #f0d9d2;
  background: #fdf5f3;
  border-radius: 8px;
  margin-bottom: 6px;
  font-size: 12px;
}

.review {
  border: 1px solid #ece3df;
  border-radius: 8px;
  padding: 9px 11px;
  margin-bottom: 8px;
  background: #fffdfc;
}

.review__top {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #6b5b55;
}

.review__body {
  margin-top: 5px;
  font-size: 13px;
}

.log__action {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 2px;
}
</style>
