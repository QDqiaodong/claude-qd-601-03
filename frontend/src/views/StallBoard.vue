<template>
  <div>
    <div class="eq-panel">
      <div class="eq-panel__head">
        <h2>马房栏位分布</h2>
        <div class="head__ops">
          <span v-if="snapshot" class="head__time">
            最后更新 {{ snapshot.generatedAtText }}
          </span>
          <el-button :loading="loading" @click="refresh(false)">
            {{ loading ? '刷新中…' : '手动刷新' }}
          </el-button>
          <el-button type="primary" @click="stallDialog = true">新增栏位</el-button>
        </div>
      </div>

      <!-- 刷新失败但已有可信结果：保留旧结果，顶部明确提示，绝不把失败响应当成「全部为空」 -->
      <el-alert
        v-if="errorMessage && snapshot"
        class="board-alert"
        type="error"
        show-icon
        :closable="false"
        :title="'刷新失败，当前仍展示 ' + snapshot.generatedAtText + ' 的结果'"
        :description="errorMessage + '（数字未被清空，可稍后重试）'"
      >
        <div class="alert-retry">
          <el-button size="small" type="danger" :loading="loading" @click="refresh(false)">
            重新加载
          </el-button>
        </div>
      </el-alert>

      <!-- 首次进入就失败（还没有任何可信结果）：不显示 0 / 空分组，给出错误页与重试 -->
      <el-result
        v-if="errorMessage && !snapshot"
        icon="error"
        title="栏位看板加载失败"
        :sub-title="errorMessage"
      >
        <template #extra>
          <el-button type="primary" :loading="loading" @click="refresh(false)">重新加载</el-button>
        </template>
      </el-result>

      <template v-if="snapshot">
        <!-- 空列表是合法结果：展示 0 与空态，不与「加载失败」混淆 -->
        <div v-if="!snapshot.stalls.length" class="eq-empty">
          暂无栏位，点击右上角「新增栏位」创建第一个栏位
        </div>

        <template v-else>
          <div class="summary">
            <div class="summary__item">
              <span class="summary__num">{{ snapshot.summary.total }}</span>
              <span class="summary__label">栏位总数</span>
            </div>
            <div class="summary__item summary__item--idle">
              <span class="summary__num">{{ snapshot.summary.idle }}</span>
              <span class="summary__label">空闲</span>
            </div>
            <div class="summary__item summary__item--occupied">
              <span class="summary__num">{{ snapshot.summary.occupied }}</span>
              <span class="summary__label">占用</span>
            </div>
            <div class="summary__item summary__item--maintenance">
              <span class="summary__num">{{ snapshot.summary.maintenance }}</span>
              <span class="summary__label">维护中</span>
            </div>
            <div v-if="snapshot.summary.unknown" class="summary__item summary__item--unknown">
              <span class="summary__num">{{ snapshot.summary.unknown }}</span>
              <span class="summary__label">状态异常</span>
            </div>
            <div class="summary__tip">
              规则：栏位编号唯一 · 一个栏位只放一匹马 · 一匹马只占一个栏位 · 维护中禁止入栏
            </div>
          </div>
        </template>
      </template>
    </div>

    <!-- 马房分组、卡片与顶部汇总同属 snapshot：同一轮数据一起切换，不会出现数字互相矛盾 -->
    <div v-for="barn in (snapshot ? snapshot.barns : [])" :key="barn.barnName" class="eq-panel barn">
      <div class="eq-panel__head">
        <h2>{{ barn.barnName }}</h2>
        <span>{{ barn.total }} 个栏位 · 已用 {{ barn.occupied }} 个</span>
      </div>

      <div class="stalls">
        <div
          v-for="stall in barn.stalls"
          :key="stall.id"
          class="stall"
          :class="'stall--' + statusKey(stall.status)"
        >
          <div class="stall__head">
            <span class="stall__no">{{ stall.stallNo }}</span>
            <el-tag size="small" :type="tagOf(stall.status)" effect="dark">{{ stall.statusName }}</el-tag>
          </div>
          <div class="stall__area">{{ stall.areaSqm === null || stall.areaSqm === undefined ? '面积未登记' : stall.areaSqm + ' ㎡' }}</div>
          <div class="stall__horse">
            <!-- 占用但马匹档案缺失：明确提示，不能静默当成空栏，也不能展示旧马名 -->
            <template v-if="stall.horseMissing">
              <b class="stall__horse-missing">占用马匹档案缺失</b>
              <span>编号 #{{ stall.horseId }}</span>
            </template>
            <template v-else-if="stall.horseId">
              <b>{{ stall.horseName || '马匹名称未登记' }}</b>
              <span>{{ stall.horseNo || '编号未登记' }}</span>
            </template>
            <span v-else class="eq-muted">空栏</span>
          </div>
          <div class="stall__ops">
            <el-button v-if="stall.status === 'IDLE'" size="small" type="primary" @click="openOccupy(stall)">
              入栏
            </el-button>
            <el-button v-if="stall.status === 'IDLE'" size="small" @click="toMaintenance(stall)">
              转维护
            </el-button>
            <!-- 出栏 / 转维护互斥，维护数量与按钮同一份数据驱动，转维护后立即只剩「维护完成」 -->
            <el-button v-if="stall.status === 'OCCUPIED'" size="small" type="primary" @click="release(stall)">
              出栏
            </el-button>
            <el-button v-if="stall.status === 'MAINTENANCE'" size="small" type="success" @click="restore(stall)">
              维护完成
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="stallDialog" title="新增栏位" width="440px">
      <el-form label-width="88px">
        <el-form-item label="栏位编号">
          <el-input v-model="stallForm.stallNo" placeholder="如 STM-010" />
        </el-form-item>
        <el-form-item label="所属马房">
          <el-input v-model="stallForm.barnName" placeholder="如 A 区马房" />
        </el-form-item>
        <el-form-item label="面积 ㎡">
          <el-input-number v-model="stallForm.areaSqm" :min="0" :max="500" :precision="1" controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stallDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitStall">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="occupyDialog" title="安排马匹入栏" width="440px">
      <p class="eq-muted">
        目标栏位：<b>{{ occupyForm.stallNo }}</b>（{{ occupyForm.barnName }}）
      </p>
      <el-select
        v-model="occupyForm.horseId"
        placeholder="选择要入栏的马匹"
        style="width: 100%"
        :loading="horsesLoading"
      >
        <el-option
          v-for="horse in horses"
          :key="horse.id"
          :label="horse.horseNo + ' ' + horse.name + '（' + horse.statusName + '）'"
          :value="horse.id"
        />
      </el-select>
      <p v-if="horsesError" class="horses-error">马匹清单加载失败：{{ horsesError }}，可稍后重试</p>
      <p class="eq-muted" style="margin-top: 10px">
        已在别的栏位的马匹、维护中的栏位都会在提交时被后端拒绝并返回中文原因。
      </p>
      <template #footer>
        <el-button @click="occupyDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitOccupy">确认入栏</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { normalizeBoard, statusKey, tagOf } from './stallBoardModel'

/**
 * 看板状态模型：
 *  - snapshot 是「最后一次可信结果」，整体替换。顶部汇总 / 马房分组 / 栏位卡片
 *    全部只读这一个对象（后端已在同一事务里算好），所以三者必然同轮切换；
 *  - errorMessage 只承载失败提示，任何成功加载都会清空，绝不与快照数据混用，
 *    因此失败后恢复时不会残留旧的统计缓存。
 */
const snapshot = ref(null)
const errorMessage = ref('')
const loading = ref(false)

const horses = ref([])
const horsesLoading = ref(false)
const horsesError = ref('')

const saving = ref(false)
const stallDialog = ref(false)
const occupyDialog = ref(false)

const stallForm = reactive({ stallNo: '', barnName: '', areaSqm: null })
const occupyForm = reactive({ id: null, stallNo: '', barnName: '', horseId: null })

// 防止多轮刷新乱序落地：只接受「最新发出」的那轮结果，旧响应晚回来也不能覆盖新快照
let refreshSeq = 0

/**
 * 加载看板快照。silent=true 用于操作成功后的自动刷新（按钮 loading 不闪）。
 * 成功：整包替换 snapshot 并清掉错误提示；失败：保留 snapshot 不动，只写 errorMessage。
 */
async function refresh(silent = false) {
  const seq = ++refreshSeq
  if (!silent) {
    loading.value = true
  }
  try {
    const board = await api.stallBoard()
    if (seq !== refreshSeq) {
      return // 已有更新的一轮在途 / 落地，本轮结果作废，避免旧数据覆盖新数据
    }
    snapshot.value = normalizeBoard(board)
    errorMessage.value = ''
  } catch (error) {
    if (seq !== refreshSeq) {
      return
    }
    // 关键：失败时不动 snapshot，保留最后一次可信结果，绝不把列表清空成 0
    errorMessage.value = error && error.message ? error.message : '网络异常，请稍后重试'
    if (!snapshot.value) {
      ElMessage.error('栏位看板加载失败：' + errorMessage.value)
    }
  } finally {
    if (seq === refreshSeq) {
      loading.value = false
    }
  }
}

async function submitStall() {
  saving.value = true
  try {
    await api.createStall({
      stallNo: stallForm.stallNo,
      barnName: stallForm.barnName,
      areaSqm: stallForm.areaSqm
    })
    ElMessage.success('栏位已创建')
    stallDialog.value = false
    Object.assign(stallForm, { stallNo: '', barnName: '', areaSqm: null })
    await refresh(true)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

async function loadHorses() {
  horsesLoading.value = true
  horsesError.value = ''
  try {
    const list = await api.horses()
    // 入栏下拉的失败不能影响看板本身；这里只重建下拉数据
    horses.value = Array.isArray(list) ? list : []
  } catch (error) {
    horses.value = []
    horsesError.value = error && error.message ? error.message : '网络异常'
  } finally {
    horsesLoading.value = false
  }
}

async function openOccupy(stall) {
  occupyForm.id = stall.id
  occupyForm.stallNo = stall.stallNo
  occupyForm.barnName = stall.barnName
  occupyForm.horseId = null
  occupyDialog.value = true
  await loadHorses()
}

async function submitOccupy() {
  if (occupyForm.horseId === null || occupyForm.horseId === undefined) {
    ElMessage.warning('请先选择要入栏的马匹')
    return
  }
  saving.value = true
  try {
    await api.occupyStall(occupyForm.id, occupyForm.horseId)
    ElMessage.success('入栏成功')
    occupyDialog.value = false
    await refresh(true)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

async function release(stall) {
  try {
    await api.releaseStall(stall.id)
    ElMessage.success(`栏位 ${stall.stallNo} 已出栏`)
    await refresh(true)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function toMaintenance(stall) {
  try {
    await api.maintenanceStall(stall.id)
    ElMessage.success(`栏位 ${stall.stallNo} 已转入维护`)
    await refresh(true)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function restore(stall) {
  try {
    await api.restoreStall(stall.id)
    ElMessage.success(`栏位 ${stall.stallNo} 维护完成`)
    await refresh(true)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(() => refresh(false))
</script>

<style scoped>
.head__ops {
  display: flex;
  align-items: center;
  gap: 10px;
}

.head__time {
  font-size: 12px;
  color: #9a8b85;
}

.board-alert {
  margin-bottom: 14px;
}

.alert-retry {
  margin-top: 8px;
}

.summary {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.summary__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 84px;
  padding: 10px 14px;
  border-radius: 10px;
  background: #f7f2ef;
  border: 1px solid #ece1db;
}

.summary__item--idle {
  background: #f0f7f1;
  border-color: #d6e8d9;
}

.summary__item--occupied {
  background: #f3e9e4;
  border-color: #e0cec5;
}

.summary__item--maintenance {
  background: #fdf4e7;
  border-color: #f0e0c6;
}

.summary__item--unknown {
  background: #fdecec;
  border-color: #f0caca;
}

.summary__num {
  font-size: 21px;
  font-weight: 700;
  color: #4a3129;
}

.summary__label {
  font-size: 11px;
  color: #8c7c76;
}

.summary__tip {
  flex: 1;
  min-width: 220px;
  font-size: 12px;
  color: #9a8b85;
  line-height: 1.7;
}

.barn {
  margin-top: 16px;
}

.stalls {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 12px;
}

.stall {
  border-radius: 10px;
  border: 1px solid #ece3df;
  padding: 12px;
  background: #fffdfc;
}

.stall--occupied {
  background: #fdf7f4;
  border-color: #ddc9bf;
}

.stall--maintenance {
  background: repeating-linear-gradient(45deg, #fdf8f1, #fdf8f1 8px, #f8f0e3 8px, #f8f0e3 16px);
  border-color: #e8d8be;
}

.stall--unknown {
  background: #fdf3f3;
  border-color: #e8c3c3;
}

.stall__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stall__no {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: #4a3129;
}

.stall__area {
  font-size: 11px;
  color: #a08d86;
  margin-top: 3px;
}

.stall__horse {
  display: flex;
  align-items: baseline;
  gap: 7px;
  margin: 9px 0;
  min-height: 22px;
  font-size: 13px;
  color: #4a3129;
}

.stall__horse span {
  font-size: 11px;
  color: #a08d86;
}

.stall__horse-missing {
  color: #c0392b;
}

.horses-error {
  color: #c0392b;
  font-size: 12px;
  margin: 8px 0 0;
}

.stall__ops {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  border-top: 1px dashed #efe6e1;
  padding-top: 8px;
}
</style>
