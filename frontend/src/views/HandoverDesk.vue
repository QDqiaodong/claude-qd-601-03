<template>
  <div>
    <!-- 身份条：前端只做体验，真正权限在后端 -->
    <div class="eq-panel hd-identity">
      <div class="hd-identity__who">
        <span class="eq-muted">当前值班身份</span>
        <el-input v-model="identity.name" placeholder="操作人姓名" style="width: 190px" />
        <el-radio-group v-model="identity.role" size="small">
          <el-radio-button label="STAFF">普通教练</el-radio-button>
          <el-radio-button label="MANAGER">值班负责人</el-radio-button>
        </el-radio-group>
      </div>
      <div class="hd-identity__ops">
        <el-button @click="reload">刷新</el-button>
        <el-button type="primary" :disabled="!isManager" @click="openCreateShift">建立值班班次</el-button>
      </div>
    </div>

    <!-- 没有进行中班次 -->
    <div v-if="!loading && !active" class="eq-panel hd-empty-shift">
      <h2>当前没有进行中的值班班次</h2>
      <p class="eq-muted">由值班负责人建立班次后，系统会从当班课程、马匹状态与栏位安排自动生成交接清单。</p>
      <el-button type="primary" :disabled="!isManager" @click="openCreateShift">建立值班班次</el-button>
    </div>

    <template v-if="active">
      <!-- 下一班首页：待承接来项（未确认不消失） -->
      <!-- 下一班首页：待承接来项（未确认不消失）；查看历史班时不显示承接面板 -->
      <div class="eq-panel hd-incoming" v-if="shiftOpen && incomingItems.length">
        <div class="eq-panel__head">
          <h2>
            待我承接 · {{ incomingItems.length }} 项
            <el-tag type="danger" size="small" effect="dark" style="margin-left: 8px">
              上一班交接、尚未逐项确认
            </el-tag>
          </h2>
          <span>只有本班负责人能逐项确认；未确认事项会一直显示在这里，不会因班次结束消失</span>
        </div>
        <div v-for="item in incomingItems" :key="'in-' + item.id" class="hd-item hd-item--incoming">
          <div class="hd-item__main">
            <div class="hd-item__title">
              <el-tag size="small" :type="typeTag(item.itemType)" effect="plain">{{ item.typeName }}</el-tag>
              <el-tag v-if="item.changeKindName" size="small" type="warning" effect="dark">
                {{ item.changeKindName }}
              </el-tag>
              <el-tag size="small" :type="priorityTag(item.priority)" effect="plain">
                {{ item.priorityName }}优先
              </el-tag>
              <span>{{ item.title }}</span>
            </div>
            <div class="hd-item__meta">
              来自班次 {{ item.shiftNo }}
              <template v-if="item.delayReason"> · 上一班延期原因：{{ item.delayReason }}</template>
              <template v-if="item.ownerName"> · 负责教练 {{ item.ownerName }}</template>
            </div>
          </div>
          <div class="hd-item__ops">
            <el-button size="small" @click="openItem(item)">明细 / 补充</el-button>
            <el-button size="small" type="success" :disabled="!isManager || !shiftOpen" @click="openAccept(item, true)">
              逐项确认承接
            </el-button>
          </div>
        </div>
      </div>

      <!-- 当前班次头 -->
      <div class="eq-panel hd-shift">
        <div class="hd-shift__head">
          <div>
            <h2>
              {{ active.shiftNo }} · {{ active.shiftDate }}
              <el-tag size="small" type="success" effect="dark" style="margin-left: 8px">进行中</el-tag>
            </h2>
            <p class="eq-muted" style="margin: 6px 0 0">
              值班负责人 {{ active.leaderName }}
              · 本班事项 {{ active.items.length }} 项，待承接 {{ active.pendingCount }} 项
              <template v-if="active.incomingPendingCount">
                · 待我承接 {{ active.incomingPendingCount }} 项
              </template>
              · 清单版本 v{{ active.itemVersion }}
            </p>
          </div>
          <div class="hd-shift__ops">
            <el-button size="small" :disabled="!isManager || !shiftOpen" @click="openNote">补充交接说明</el-button>
            <el-button size="small" type="primary" plain :disabled="!isManager || !shiftOpen" @click="openManualItem">
              手工新增事项
            </el-button>
            <el-button size="small" type="danger" plain :disabled="!isManager || !shiftOpen" @click="tryClose">
              结束班次
            </el-button>
          </div>
        </div>
        <el-alert
          v-if="active.handoverNote"
          type="info"
          :closable="false"
          :title="'当班交接说明：' + active.handoverNote"
          style="margin-top: 10px"
        />
        <el-alert
          v-if="active.nextShift"
          type="success"
          :closable="false"
          :title="'下一班已建立：' + active.nextShift.shiftNo + '（负责人 ' + active.nextShift.leaderName + '），分派与承接可同时进行'"
          style="margin-top: 10px"
        />
        <el-alert
          v-if="!shiftOpen"
          type="info"
          show-icon
          :closable="false"
          style="margin-top: 10px"
        >
          <template #title>
            该班次已于 {{ fmt(active.closedAt) }} 结束，此页为历史记录（只读）。
            <el-button link type="primary" size="small" @click="reload">返回当前进行中班</el-button>
          </template>
        </el-alert>
      </div>

      <!-- 本班交接清单 -->
      <div class="eq-panel hd-list">
        <div class="eq-panel__head">
          <h2>交接清单（当班课程 / 马匹状态 / 栏位安排）</h2>
          <span>事项由 {{ active.leaderName }} 分派；普通教练只能补充自己负责的事项</span>
        </div>
        <div v-if="!active.items.length" class="eq-empty">本班暂无交接事项</div>
        <div v-for="item in active.items" :key="item.id" class="hd-item"
             :class="{ 'hd-item--accepted': item.status === 'ACCEPTED' }">
          <div class="hd-item__main">
            <div class="hd-item__title">
              <el-tag size="small" :type="typeTag(item.itemType)" effect="plain">{{ item.typeName }}</el-tag>
              <el-tag v-if="item.changeKindName" size="small" type="warning" effect="dark">
                {{ item.changeKindName }}
              </el-tag>
              <el-tag size="small" :type="priorityTag(item.priority)" effect="plain">
                {{ item.priorityName }}优先
              </el-tag>
              <el-tag size="small" :type="item.status === 'ACCEPTED' ? 'success' : 'info'" effect="dark">
                {{ item.statusName }}
              </el-tag>
              <span>{{ item.title }}</span>
            </div>
            <div class="hd-item__meta">
              {{ item.itemNo }} · v{{ item.expectedVersion }}
              <template v-if="item.ownerName"> · 负责教练 {{ item.ownerName }}</template>
              <template v-else> · 未分派负责人</template>
              <template v-if="item.targetShiftNo"> · 已派给 {{ item.targetShiftNo }}</template>
              <template v-if="item.acceptedBy"> · {{ item.acceptedBy }} 已承接</template>
              <template v-if="item.delayReason"> · 延期：{{ item.delayReason }}</template>
            </div>
          </div>
          <div class="hd-item__ops">
            <el-button size="small" @click="openItem(item)">明细 / 补充</el-button>
            <template v-if="item.status === 'PENDING' && shiftOpen">
              <el-button size="small" :disabled="!isManager" @click="openPriority(item)">优先级 / 分派</el-button>
              <el-button size="small" type="warning" plain :disabled="!isManager" @click="openDispatch(item)">
                分派下一班
              </el-button>
              <el-button
                v-if="isLeaderOfTarget(item)"
                size="small"
                type="success"
                @click="openAccept(item, false)"
              >
                确认承接
              </el-button>
            </template>
          </div>
        </div>
      </div>
    </template>

    <!-- 历史班次查询 -->
    <div class="eq-panel hd-history">
      <div class="eq-panel__head">
        <h2>历史班次查询</h2>
        <span>按日期、负责人、事项状态筛选</span>
      </div>
      <div class="eq-toolbar">
        <el-date-picker
          v-model="historyDate"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        />
        <el-input v-model="historyLeader" placeholder="负责人姓名" style="width: 150px" clearable />
        <el-select v-model="historyStatus" style="width: 150px" clearable placeholder="事项状态">
          <el-option label="待承接" value="PENDING" />
          <el-option label="已承接" value="ACCEPTED" />
        </el-select>
        <el-button type="primary" @click="loadHistory">查询</el-button>
      </div>
      <el-table :data="history" size="small" stripe>
        <el-table-column prop="shiftNo" label="班次编号" width="120" />
        <el-table-column prop="shiftDate" label="值班日期" width="120" />
        <el-table-column prop="leaderName" label="负责人" width="110" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'OPEN' ? 'success' : 'info'">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pendingCount" label="本班未承接" width="100" />
        <el-table-column prop="incomingPendingCount" label="待承接来项" width="100" />
        <el-table-column label="时间" min-width="160">
          <template #default="{ row }">
            {{ fmt(row.startedAt) }} ~ {{ row.closedAt ? fmt(row.closedAt) : '进行中' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewShift(row.id)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 开班弹窗 -->
    <el-dialog v-model="createDialog" title="建立值班班次" width="480px">
      <el-form label-width="92px">
        <el-form-item label="值班日期">
          <el-date-picker v-model="createForm.shiftDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input :model-value="identity.name || '（请先在顶部填写操作人姓名）'" disabled />
        </el-form-item>
        <el-form-item label="交接说明">
          <el-input v-model="createForm.handoverNote" type="textarea" :rows="3" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false"
        title="开班即自动定格交接清单：当班课程、未来 7 天满员 / 健康受影响场次、休养马匹、维护中栏位。" />
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreateShift">开班</el-button>
      </template>
    </el-dialog>

    <!-- 事项明细 + 操作流水 + 承接记录 -->
    <el-dialog v-model="detailDialog" title="交接事项明细" width="640px">
      <div v-if="detailItem">
        <h3 style="margin: 0 0 8px; color: #4a3129">{{ detailItem.title }}</h3>
        <p class="eq-muted" style="white-space: pre-wrap">{{ detailItem.detail }}</p>
        <el-descriptions :column="2" border size="small" style="margin: 8px 0">
          <el-descriptions-item label="事项编号">{{ detailItem.itemNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detailItem.statusName }}</el-descriptions-item>
          <el-descriptions-item label="来源班次">{{ detailItem.shiftNo }}</el-descriptions-item>
          <el-descriptions-item label="承接班次">{{ detailItem.targetShiftNo || '未分派' }}</el-descriptions-item>
          <el-descriptions-item label="优先级">{{ detailItem.priorityName }}</el-descriptions-item>
          <el-descriptions-item label="负责教练">{{ detailItem.ownerName || '未分派' }}</el-descriptions-item>
        </el-descriptions>
        <el-alert
          v-if="detailItem.acceptance"
          type="success"
          :closable="false"
          :title="'最近一次承接：' + detailItem.acceptance.acceptor + ' 于 ' + fmt(detailItem.acceptance.createdAt) + ' 在班次 ' + detailItem.acceptance.shiftNo + ' 确认' + (detailItem.acceptance.note ? '（' + detailItem.acceptance.note + '）' : '')"
          style="margin: 8px 0"
        />
        <el-alert
          v-if="detailItem.delayReason"
          type="warning"
          :closable="false"
          :title="'延期原因（' + (detailItem.delayedBy || '') + '）：' + detailItem.delayReason"
          style="margin: 8px 0"
        />
        <el-divider content-position="left">补充说明（仅负责人可改优先级 / 分派，普通教练只能补自己负责的事项）</el-divider>
        <el-input v-model="supplementNote" type="textarea" :rows="2" maxlength="500" show-word-limit
                  :disabled="detailItem.status === 'ACCEPTED' || !shiftOpen"
                  :placeholder="!shiftOpen
                    ? '该班次已结束，历史记录为只读'
                    : (detailItem.status === 'ACCEPTED' ? '已承接事项为终态，不能再补充或改回未处理' : '')" />
        <div style="margin-top: 8px; text-align: right">
          <el-button size="small" type="primary" plain :loading="saving"
                     :disabled="detailItem.status === 'ACCEPTED' || !shiftOpen" @click="submitSupplement">
            追加补充说明
          </el-button>
        </div>
        <el-divider content-position="left">操作流水（只追加，不可覆盖）</el-divider>
        <el-timeline v-if="detailItem.logs && detailItem.logs.length">
          <el-timeline-item
            v-for="log in detailItem.logs"
            :key="log.id"
            :timestamp="fmt(log.createdAt) + ' · ' + log.operator + '（' + log.roleName + '）'"
            placement="top"
          >
            <el-tag size="small" effect="plain" style="margin-right: 6px">{{ log.actionName }}</el-tag>
            {{ log.note }}
          </el-timeline-item>
        </el-timeline>
        <div v-else class="eq-muted">暂无流水</div>
      </div>
      <template #footer>
        <el-button @click="detailDialog = false">关闭</el-button>
        <el-button v-if="shiftOpen && detailItem && detailItem.status === 'PENDING' && isLeaderOfTarget(detailItem)"
                   type="success" @click="openAccept(detailItem, false)">确认承接</el-button>
      </template>
    </el-dialog>

    <!-- 优先级 / 分派负责人 -->
    <el-dialog v-model="priorityDialog" title="调整优先级 / 分派负责教练" width="420px">
      <el-form label-width="84px" v-if="priorityForm.id">
        <el-form-item label="优先级">
          <el-radio-group v-model="priorityForm.priority">
            <el-radio label="HIGH">高</el-radio>
            <el-radio label="NORMAL">普通</el-radio>
            <el-radio label="LOW">低</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="负责教练">
          <el-input v-model="priorityForm.ownerName" placeholder="留空=清除分派" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="priorityDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitPriority">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分派下一班 -->
    <el-dialog v-model="dispatchDialog" title="分派给下一班" width="440px">
      <el-alert
        :closable="false"
        :title="active && active.nextShift
          ? '将分派给下一班 ' + active.nextShift.shiftNo + '（负责人 ' + active.nextShift.leaderName + '）'
          : '还没有建立下一班：请先请下一班负责人开班，或在结束班次时记录延期原因自动转交'"
        :type="active && active.nextShift ? 'success' : 'warning'"
        style="margin-bottom: 12px"
      />
      <el-input v-model="dispatchNote" type="textarea" :rows="2" maxlength="500" show-word-limit
                placeholder="分派说明（可空）" />
      <template #footer>
        <el-button @click="dispatchDialog = false">取消</el-button>
        <el-button type="warning" :loading="saving" :disabled="!(active && active.nextShift)" @click="submitDispatch">
          分派
        </el-button>
      </template>
    </el-dialog>

    <!-- 逐项承接 -->
    <el-dialog v-model="acceptDialog" :title="(acceptFromIncoming ? '待我承接 · ' : '确认承接 · ') + (acceptForm.title || '')" width="460px">
      <el-alert type="info" :closable="false" style="margin-bottom: 10px"
        title="承接由下一班负责人逐项确认；重复点击不会产生重复记录，已承接事项不能被旧班次改回未处理。" />
      <el-input v-model="acceptForm.note" type="textarea" :rows="3" maxlength="500" show-word-limit
                placeholder="承接说明（可空）" />
      <template #footer>
        <el-button @click="acceptDialog = false">取消</el-button>
        <el-button type="success" :loading="saving" @click="submitAccept">逐项确认承接</el-button>
      </template>
    </el-dialog>

    <!-- 交接说明 -->
    <el-dialog v-model="noteDialog" title="补充当班交接说明" width="480px">
      <el-input v-model="noteText" type="textarea" :rows="4" maxlength="1000" show-word-limit />
      <template #footer>
        <el-button @click="noteDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitNote">保存</el-button>
      </template>
    </el-dialog>

    <!-- 手工事项 -->
    <el-dialog v-model="manualDialog" title="手工新增交接事项" width="480px">
      <el-form label-width="84px">
        <el-form-item label="标题">
          <el-input v-model="manualForm.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="manualForm.detail" type="textarea" :rows="3" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="manualForm.priority">
            <el-radio label="HIGH">高</el-radio>
            <el-radio label="NORMAL">普通</el-radio>
            <el-radio label="LOW">低</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="负责教练">
          <el-input v-model="manualForm.ownerName" placeholder="可空" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitManual">新增</el-button>
      </template>
    </el-dialog>

    <!-- 结束班次：逐项延期 -->
    <el-dialog v-model="closeDialog" title="结束班次 · 逐项核对未承接事项" width="720px" :close-on-click-modal="false">
      <el-alert
        v-if="!closeForm.length"
        type="success"
        :closable="false"
        title="所有事项均已承接，可以直接结束班次。"
        style="margin-bottom: 10px"
      />
      <el-alert
        v-else
        type="warning"
        :closable="false"
        style="margin-bottom: 10px"
        :title="'还有 ' + closeForm.length + ' 项未承接：必须逐项记录延期原因才能结束班次。未分派事项将自动转交给下一班；若下一班尚未建立，请先取消并请下一班负责人开班。'"
      />
      <div v-for="row in closeForm" :key="row.itemId" class="hd-delay">
        <div class="hd-delay__title">
          <el-tag v-if="row.incoming" size="small" type="danger" effect="plain">上一班转入</el-tag>
          <el-tag v-else size="small" :type="row.dispatched ? 'warning' : 'danger'" effect="plain">
            {{ row.dispatched ? '已派 ' + row.targetShiftNo : '未分派' }}
          </el-tag>
          <span>{{ row.title }}</span>
        </div>
        <el-input v-model="row.reason" type="textarea" :rows="2" maxlength="500" show-word-limit
                  placeholder="必填：为什么本项未能承接、需要下一班继续做什么" />
        <p v-if="!row.dispatched && !(active && active.nextShift)" class="hd-delay__warn">
          下一班尚未建立，该项无法继续交接——请先取消结班并建立下一班。
        </p>
      </div>
      <template #footer>
        <el-button @click="closeDialog = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="submitClose">记录延期并结束班次</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api, { requestId } from '../api'
import { identity, withIdentity } from '../identity'

const loading = ref(false)
const saving = ref(false)
const active = ref(null)
const history = ref([])
const historyDate = ref([])
const historyLeader = ref('')
const historyStatus = ref('')

const createDialog = ref(false)
const detailDialog = ref(false)
const priorityDialog = ref(false)
const dispatchDialog = ref(false)
const acceptDialog = ref(false)
const noteDialog = ref(false)
const manualDialog = ref(false)
const closeDialog = ref(false)

const createForm = reactive({ shiftDate: todayStr(), handoverNote: '' })
const detailItem = ref(null)
const supplementNote = ref('')
const priorityForm = reactive({ id: null, priority: 'NORMAL', ownerName: '', version: 0 })
const dispatchNote = ref('')
const acceptForm = reactive({ id: null, title: '', note: '', version: 0 })
const acceptFromIncoming = ref(false)
const noteText = ref('')
const manualForm = reactive({ title: '', detail: '', priority: 'NORMAL', ownerName: '' })
const closeForm = ref([])

const isManager = computed(() => identity.role === 'MANAGER')
const incomingItems = computed(() => active.value?.incomingItems || [])
/** 只有进行中班次才能关班 / 改清单；历史班次仅查看，写操作由后端兜底拒绝 */
const shiftOpen = computed(() => active.value?.status === 'OPEN')

function todayStr() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function fmt(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

function typeTag(type) {
  return { SESSION: 'primary', HORSE: 'danger', STALL: 'warning', MANUAL: 'info' }[type] || 'info'
}

function priorityTag(priority) {
  return { HIGH: 'danger', NORMAL: 'warning', LOW: 'info' }[priority] || 'info'
}

/** 当前身份是否为某事项「承接班次」的负责人（用于显示确认承接按钮；后端还会再校验） */
function isLeaderOfTarget(item) {
  return isManager.value
    && item.targetShiftId === active.value?.id
    && active.value?.leaderName === identity.name
}

async function reload() {
  loading.value = true
  try {
    active.value = await api.activeShift()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function refreshShiftOrBoard() {
  // 操作后优先刷当前打开的班详情，让 409 后的页面与库一致
  await reload()
  await loadHistory()
}

// ---------------- 开班 ----------------

function openCreateShift() {
  if (!identity.name) {
    ElMessage.warning('请先在顶部填写操作人姓名')
    return
  }
  createForm.shiftDate = todayStr()
  createForm.handoverNote = ''
  createDialog.value = true
}

async function submitCreateShift() {
  saving.value = true
  try {
    await api.createShift(withIdentity({
      shiftDate: createForm.shiftDate,
      handoverNote: createForm.handoverNote,
      requestId: requestId('shift-create')
    }))
    ElMessage.success('值班班次已建立，交接清单已生成')
    createDialog.value = false
    await reload()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

// ---------------- 事项明细 / 补充 ----------------

async function openItem(item) {
  try {
    detailItem.value = await api.item(item.id)
    supplementNote.value = ''
    detailDialog.value = true
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function submitSupplement() {
  if (!supplementNote.value.trim()) {
    ElMessage.warning('请填写补充说明')
    return
  }
  saving.value = true
  try {
    const updated = await api.supplementItem(detailItem.value.id, withIdentity({
      note: supplementNote.value,
      expectedVersion: detailItem.value.expectedVersion,
      requestId: requestId('item-supplement')
    }))
    ElMessage.success('说明已补充，历史未被覆盖')
    detailItem.value = updated
    supplementNote.value = ''
    await refreshShiftOrBoard()
  } catch (error) {
    await handleError(error, '补充')
  } finally {
    saving.value = false
  }
}

// ---------------- 优先级 / 分派负责人 ----------------

function openPriority(item) {
  priorityForm.id = item.id
  priorityForm.priority = item.priority
  priorityForm.ownerName = item.ownerName || ''
  priorityForm.version = item.expectedVersion
  priorityDialog.value = true
}

async function submitPriority() {
  saving.value = true
  try {
    await api.updateItem(priorityForm.id, withIdentity({
      priority: priorityForm.priority,
      ownerName: priorityForm.ownerName,
      expectedVersion: priorityForm.version,
      requestId: requestId('item-update')
    }))
    ElMessage.success('事项已更新')
    priorityDialog.value = false
    await refreshShiftOrBoard()
  } catch (error) {
    await handleError(error, '更新事项')
  } finally {
    saving.value = false
  }
}

// ---------------- 分派下一班 ----------------

function openDispatch(item) {
  dispatchTarget.value = item
  dispatchNote.value = ''
  dispatchDialog.value = true
}

const dispatchTarget = ref(null)

async function submitDispatch() {
  saving.value = true
  try {
    await api.dispatchItem(dispatchTarget.value.id, withIdentity({
      note: dispatchNote.value,
      expectedVersion: dispatchTarget.value.expectedVersion,
      requestId: requestId('item-dispatch')
    }))
    ElMessage.success('事项已分派给下一班')
    dispatchDialog.value = false
    await refreshShiftOrBoard()
  } catch (error) {
    await handleError(error, '分派')
  } finally {
    saving.value = false
  }
}

// ---------------- 逐项承接 ----------------

function openAccept(item, fromIncoming) {
  acceptForm.id = item.id
  acceptForm.title = item.title
  acceptForm.note = ''
  acceptForm.version = item.expectedVersion
  acceptFromIncoming.value = fromIncoming
  acceptDialog.value = true
}

async function submitAccept() {
  saving.value = true
  try {
    await api.acceptItem(acceptForm.id, withIdentity({
      note: acceptForm.note,
      expectedVersion: acceptForm.version,
      requestId: requestId('item-accept')
    }))
    ElMessage.success('事项已逐项确认承接')
    acceptDialog.value = false
    detailDialog.value = false
    await refreshShiftOrBoard()
  } catch (error) {
    await handleError(error, '承接')
  } finally {
    saving.value = false
  }
}

// ---------------- 交接说明 / 手工事项 ----------------

function openNote() {
  noteText.value = active.value.handoverNote || ''
  noteDialog.value = true
}

async function submitNote() {
  saving.value = true
  try {
    await api.updateShiftNote(active.value.id, withIdentity({
      handoverNote: noteText.value,
      expectedVersion: active.value.itemVersion,
      requestId: requestId('shift-note')
    }))
    ElMessage.success('交接说明已更新')
    noteDialog.value = false
    await reload()
  } catch (error) {
    await handleError(error, '保存说明')
  } finally {
    saving.value = false
  }
}

function openManualItem() {
  manualForm.title = ''
  manualForm.detail = ''
  manualForm.priority = 'NORMAL'
  manualForm.ownerName = ''
  manualDialog.value = true
}

async function submitManual() {
  if (!manualForm.title.trim()) {
    ElMessage.warning('请填写事项标题')
    return
  }
  saving.value = true
  try {
    await api.createItem(active.value.id, withIdentity({
      ...manualForm,
      requestId: requestId('item-create')
    }))
    ElMessage.success('交接事项已新增')
    manualDialog.value = false
    await reload()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

// ---------------- 结束班次（逐项延期） ----------------

async function tryClose() {
  try {
    const blockers = await api.closeBlockers(active.value.id)
    closeForm.value = blockers.map((b) => ({
      itemId: b.itemId,
      title: b.itemNo + ' ' + b.title,
      dispatched: b.dispatched,
      incoming: b.incoming,
      targetShiftNo: b.targetShiftNo,
      reason: ''
    }))
    closeDialog.value = true
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function submitClose() {
  const missing = closeForm.value.filter((row) => !row.reason.trim())
  if (missing.length) {
    ElMessage.error('还有 ' + missing.length + ' 项未填写延期原因，请逐项填写（系统会阻止误关班）')
    return
  }
  if (closeForm.value.some((row) => !row.dispatched || row.incoming) && !active.value.nextShift) {
    ElMessage.error('存在未分派 / 待顺延事项且下一班尚未建立，无法结束班次')
    return
  }
  saving.value = true
  try {
    await api.closeShift(active.value.id, withIdentity({
      expectedVersion: active.value.itemVersion,
      delays: closeForm.value.map((row) => ({ itemId: row.itemId, reason: row.reason })),
      requestId: requestId('shift-close')
    }))
    ElMessage.success('班次已结束，未承接事项已带着延期原因转入下一班')
    closeDialog.value = false
    await reload()
  } catch (error) {
    await handleError(error, '结束班次')
  } finally {
    saving.value = false
  }
}

// ---------------- 历史 ----------------

async function loadHistory() {
  try {
    const params = {}
    if (historyDate.value && historyDate.value.length === 2) {
      params.from = historyDate.value[0]
      params.to = historyDate.value[1]
    }
    if (historyLeader.value.trim()) params.leaderName = historyLeader.value.trim()
    if (historyStatus.value) params.itemStatus = historyStatus.value
    history.value = await api.dutyHistory(params)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function viewShift(id) {
  try {
    const data = await api.shift(id)
    active.value = data
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (error) {
    ElMessage.error(error.message)
  }
}

/**
 * 409 数据已变化：清单被别人动过，必须重新载入而不是重试旧提交。
 * 关闭所有弹窗并用最新数据覆盖页面，避免继续在过期版本上操作。
 */
async function handleError(error, scene) {
  if (error.conflict) {
    ElMessage.error(scene + '失败：' + error.message)
    detailDialog.value = false
    priorityDialog.value = false
    dispatchDialog.value = false
    noteDialog.value = false
    await reload()
  } else {
    ElMessage.error(error.message)
  }
}

onMounted(async () => {
  await reload()
  await loadHistory()
})
</script>

<style scoped>
.hd-identity {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.hd-identity__who {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.hd-empty-shift {
  text-align: center;
  padding: 40px 16px;
}

.hd-empty-shift h2 {
  margin: 0 0 8px;
  color: #4a3129;
}

.hd-incoming {
  margin-bottom: 14px;
  border-color: #e0b8a8;
  background: #fdf8f5;
}

.hd-shift {
  margin-bottom: 14px;
}

.hd-shift__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.hd-shift__head h2 {
  margin: 0;
  font-size: 16px;
  color: #4a3129;
}

.hd-shift__ops {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.hd-list {
  margin-bottom: 14px;
}

.hd-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid #ece3df;
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 8px;
  background: #fffdfc;
}

.hd-item--incoming {
  border-color: #e0a48c;
  background: #fff6f0;
}

.hd-item--accepted {
  opacity: 0.72;
  background: #f6f7f6;
}

.hd-item__main {
  min-width: 0;
}

.hd-item__title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 14px;
  font-weight: 600;
  color: #4a3129;
  flex-wrap: wrap;
}

.hd-item__meta {
  font-size: 12px;
  color: #8c7c76;
  margin-top: 4px;
}

.hd-item__ops {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.hd-history {
  margin-top: 4px;
}

.hd-delay {
  border: 1px solid #ece3df;
  border-radius: 8px;
  padding: 10px;
  margin-bottom: 10px;
  background: #fffdfc;
}

.hd-delay__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #4a3129;
  margin-bottom: 6px;
}

.hd-delay__warn {
  color: #c4563f;
  font-size: 12px;
  margin: 6px 0 0;
}
</style>
