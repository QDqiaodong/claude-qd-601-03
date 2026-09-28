<template>
  <div class="desk">
    <!-- 左：会员卡片 -->
    <div class="desk__members eq-panel">
      <div class="eq-panel__head">
        <h2>会员卡</h2>
        <span>{{ members.length }} 位</span>
      </div>

      <el-button
        class="all-btn"
        :type="selectedMemberId === null ? 'primary' : 'default'"
        size="small"
        @click="showAll"
      >
        看全部会员的骑乘记录
      </el-button>

      <div class="mcards">
        <div
          v-for="member in members"
          :key="member.id"
          class="mcard"
          :class="{ 'mcard--on': member.id === selectedMemberId }"
          @click="selectMember(member)"
        >
          <div class="mcard__head">
            <span class="mcard__no">{{ member.memberNo }}</span>
            <el-tag size="small" effect="dark" :type="member.level === 'NORMAL' ? 'info' : 'warning'">
              {{ member.levelName }}
            </el-tag>
          </div>
          <div class="mcard__name">{{ member.name }}</div>
          <div class="mcard__card">
            <span>余额 ￥{{ member.cardBalance }}</span>
            <span>次数 {{ member.cardTimes }}</span>
          </div>
          <div class="mcard__foot">
            <el-tag size="small" :type="member.passedBasic ? 'success' : 'danger'" effect="plain">
              初级考核 {{ member.passedBasicText }}
            </el-tag>
            <span>{{ member.phone }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右：骑乘记录 -->
    <div class="desk__records eq-panel">
      <div class="eq-panel__head">
        <h2>{{ selectedMemberId === null ? '全部骑乘记录' : currentMemberName + ' 的骑乘记录' }}</h2>
        <el-button type="primary" @click="openBook">新增预约</el-button>
      </div>

      <div v-if="!records.length" class="eq-empty">还没有骑乘记录</div>

      <div class="rlist">
        <div v-for="record in records" :key="record.id" class="rrow" :class="'rrow--' + record.status.toLowerCase()">
          <div class="rrow__date">
            <b>{{ record.recordDate.slice(5) }}</b>
            <span>{{ record.startTime }}-{{ record.endTime }}</span>
          </div>
          <div class="rrow__body">
            <div class="rrow__title">
              {{ record.lessonName }}
              <el-tag size="small" effect="plain">{{ record.categoryName }}</el-tag>
              <el-tag size="small" :type="tagOf(record.status)">{{ record.statusName }}</el-tag>
            </div>
            <div class="rrow__meta">
              {{ record.memberName }}（{{ record.memberNo }}） · 教练 {{ record.coachName }}
              <template v-if="record.horseName"> · 用马 {{ record.horseName }}</template>
            </div>
            <div class="rrow__meta">
              实收 ￥{{ record.fee }} · 扣 {{ record.timesUsed }} 次
              <template v-if="record.remark"> · 备注：{{ record.remark }}</template>
            </div>
          </div>
          <div class="rrow__ops">
            <el-button v-if="record.status === 'BOOKED'" size="small" @click="complete(record)">标记完成</el-button>
            <el-button v-if="record.status === 'BOOKED'" size="small" type="primary" @click="cancel(record)">
              取消预约
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>

  <el-dialog v-model="bookDialog" title="新增预约" width="540px">
    <el-form label-width="92px">
      <el-form-item label="会员">
        <el-select v-model="form.memberId" style="width: 100%" placeholder="选会员">
          <el-option
            v-for="member in members"
            :key="member.id"
            :label="member.name + '（余 ' + member.cardTimes + ' 次 / ￥' + member.cardBalance + '）'"
            :value="member.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="课程场次">
        <el-select v-model="form.sessionId" style="width: 100%" placeholder="选一条已排期的课程">
          <el-option
            v-for="session in openSessions"
            :key="session.id"
            :label="session.sessionDate + ' ' + session.startTime + ' ' + session.lessonName + '（剩 ' + session.remain + '）'"
            :value="session.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" placeholder="可空" />
      </el-form-item>
    </el-form>
    <p class="eq-muted">
      未通过初级考核不能约进阶课；次数或余额不足、同一会员同一天同一时段重复约，都会被后端拒绝并给出中文原因。
    </p>
    <template #footer>
      <el-button @click="bookDialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">确认预约</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const members = ref([])
const records = ref([])
const sessions = ref([])
const selectedMemberId = ref(null)
const saving = ref(false)
const bookDialog = ref(false)

const form = reactive({ memberId: null, sessionId: null, remark: '' })

const currentMemberName = computed(() => {
  const member = members.value.find((item) => item.id === selectedMemberId.value)
  return member ? member.name : ''
})

const openSessions = computed(() =>
  sessions.value.filter((session) => session.status !== 'CANCELED' && session.remain > 0)
)

function tagOf(status) {
  if (status === 'COMPLETED') return 'success'
  if (status === 'CANCELED') return 'info'
  return 'warning'
}

async function loadAll() {
  try {
    const [memberList, recordList, sessionList] = await Promise.all([
      api.members(),
      api.records(),
      api.sessions()
    ])
    members.value = memberList || []
    records.value = recordList || []
    sessions.value = sessionList || []
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function selectMember(member) {
  selectedMemberId.value = member.id
  try {
    records.value = (await api.memberRecords(member.id)) || []
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function showAll() {
  selectedMemberId.value = null
  try {
    records.value = (await api.records()) || []
  } catch (error) {
    ElMessage.error(error.message)
  }
}

function openBook() {
  form.memberId = selectedMemberId.value
  form.sessionId = null
  form.remark = ''
  bookDialog.value = true
}

async function submit() {
  saving.value = true
  try {
    await api.book({ memberId: form.memberId, sessionId: form.sessionId, remark: form.remark })
    ElMessage.success('预约成功')
    bookDialog.value = false
    await loadAll()
    if (selectedMemberId.value !== null) {
      await selectMember({ id: selectedMemberId.value })
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

async function cancel(record) {
  try {
    await ElMessageBox.confirm(
      `取消 ${record.memberName} 在 ${record.recordDate} ${record.startTime} 的「${record.lessonName}」？次数与余额会退回。`,
      '取消预约',
      { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '再想想' }
    )
  } catch (error) {
    return
  }
  try {
    await api.cancelRecord(record.id)
    ElMessage.success('已取消，次数与余额已退回会员卡')
    await loadAll()
    if (selectedMemberId.value !== null) {
      await selectMember({ id: selectedMemberId.value })
    }
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function complete(record) {
  try {
    await api.completeRecord(record.id)
    ElMessage.success('已标记完成')
    await showAll()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(loadAll)
</script>

<style scoped>
.desk {
  display: grid;
  grid-template-columns: minmax(300px, 340px) 1fr;
  gap: 16px;
  align-items: start;
}

.all-btn {
  width: 100%;
  margin-bottom: 12px;
}

.mcards {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 620px;
  overflow-y: auto;
}

.mcard {
  border: 1px solid #ece3df;
  border-radius: 10px;
  padding: 12px;
  cursor: pointer;
  background: linear-gradient(135deg, #fffdfc 0%, #faf4f1 100%);
  transition: border-color 0.15s, box-shadow 0.15s;
}

.mcard:hover {
  border-color: #cbb4a9;
  box-shadow: 0 2px 10px rgba(109, 76, 65, 0.12);
}

.mcard--on {
  border-color: #6d4c41;
  box-shadow: 0 0 0 2px rgba(109, 76, 65, 0.16);
}

.mcard__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.mcard__no {
  font-size: 11px;
  letter-spacing: 1px;
  color: #a08d86;
}

.mcard__name {
  font-size: 16px;
  font-weight: 700;
  color: #4a3129;
  margin: 6px 0;
}

.mcard__card {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: #6d4c41;
  background: #f3e9e4;
  border-radius: 7px;
  padding: 6px 10px;
}

.mcard__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 11px;
  color: #a08d86;
}

.rlist {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rrow {
  display: grid;
  grid-template-columns: 78px 1fr 150px;
  gap: 12px;
  align-items: center;
  border: 1px solid #ece3df;
  border-left: 3px solid #d9c3b8;
  border-radius: 10px;
  padding: 11px 12px;
  background: #fffdfc;
}

.rrow--completed {
  border-left-color: #a8c6a8;
}

.rrow--canceled {
  border-left-color: #cfcfcf;
  opacity: 0.7;
}

.rrow__date {
  display: flex;
  flex-direction: column;
  align-items: center;
  color: #6d4c41;
}

.rrow__date b {
  font-size: 14px;
}

.rrow__date span {
  font-size: 11px;
  color: #a08d86;
}

.rrow__title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 14px;
  font-weight: 600;
  color: #4a3129;
}

.rrow__meta {
  font-size: 11px;
  color: #8c7c76;
  margin-top: 3px;
}

.rrow__ops {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}
</style>
