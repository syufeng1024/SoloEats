<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  auditRiderCertification,
  getRiderDetail,
  listRiders,
  updateRiderLimits,
  updateRiderStatus,
  type RiderDetailView,
  type RiderListParams,
  type RiderView,
} from '@/api/delivery-admin'
import {
  CERT_RECORD_STATUS,
  RIDER_CERT_STATUS,
  RIDER_CERT_STATUS_OPTIONS,
  RIDER_ONLINE_STATUS,
  RIDER_STATUS,
  RIDER_STATUS_OPTIONS,
  VEHICLE_TYPE,
  labelOf,
  tagOf,
} from '@/constants/deliveryStatus'
import { debounce } from '@/utils/debounce'
import { formatDateTime } from '@/utils/datetime'
import { errorMessage, formatPercent } from '@/views/report/helpers'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const riders = ref<RiderView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const filters = reactive({
  certStatus: undefined as number | undefined,
  status: undefined as number | undefined,
  keyword: '',
})
const appliedKeyword = ref('')

watch(
  () => filters.keyword,
  debounce((value: string) => {
    appliedKeyword.value = value
    pageNum.value = 1
    void load()
  }, 300),
)

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref<RiderDetailView | null>(null)
const activeRiderId = ref<number | null>(null)
const acting = ref(false)

const auditVisible = ref(false)
const auditStatus = ref<1 | 2>(1)
const auditRemark = ref('')

const statusVisible = ref(false)
const statusValue = ref<0 | 1 | 2>(0)

/** 列表行直接打开状态/限制弹框时使用，不依赖抽屉。 */
const actionRiderId = ref<number | null>(null)

const limitsVisible = ref(false)
const limitsForm = reactive({
  maxConcurrentOrders: undefined as number | null | undefined,
  pickupRadiusM: undefined as number | null | undefined,
})

/** 后端按 id 倒序返回认证记录，第一条即最新。 */
const latestCertification = computed(() => detail.value?.certifications[0] ?? null)
const canAudit = computed(() => latestCertification.value?.status === 0)

function ratingText(value: number | null | undefined): string {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric.toFixed(2) : '—'
}

function durationText(seconds: number): string {
  const numeric = Number(seconds)
  if (!Number.isFinite(numeric) || numeric < 0) {
    return '—'
  }
  const hours = Math.floor(numeric / 3600)
  const minutes = Math.floor((numeric % 3600) / 60)
  return `${hours} 小时 ${minutes} 分钟`
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const params: RiderListParams = {
      certStatus: filters.certStatus,
      status: filters.status,
      keyword: appliedKeyword.value.trim() || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    }
    const result = await listRiders(params)
    riders.value = result.list
    total.value = result.total
  } catch (error) {
    riders.value = []
    total.value = 0
    loadError.value = errorMessage(error, '骑手列表加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  appliedKeyword.value = filters.keyword
  pageNum.value = 1
  void load()
}

function reset(): void {
  filters.certStatus = undefined
  filters.status = undefined
  filters.keyword = ''
  appliedKeyword.value = ''
  pageNum.value = 1
  pageSize.value = 20
  void load()
}

function handlePageChange(page: number): void {
  pageNum.value = page
  void load()
}

function handleSizeChange(size: number): void {
  pageSize.value = size
  pageNum.value = 1
  void load()
}

async function loadDetail(id: number): Promise<void> {
  detailLoading.value = true
  detailError.value = ''
  try {
    const result = await getRiderDetail(id)
    if (activeRiderId.value === id) {
      detail.value = result
    }
  } catch (error) {
    if (activeRiderId.value === id) {
      detail.value = null
      detailError.value = errorMessage(error, '骑手详情加载失败')
    }
  } finally {
    if (activeRiderId.value === id) {
      detailLoading.value = false
    }
  }
}

function openDetail(row: RiderView): void {
  activeRiderId.value = row.id
  drawerVisible.value = true
  detail.value = null
  detailError.value = ''
  void loadDetail(row.id)
}

function handleDrawerClosed(): void {
  activeRiderId.value = null
  detail.value = null
  detailError.value = ''
  detailLoading.value = false
}

async function refreshAfterAction(): Promise<void> {
  await load()
  if (activeRiderId.value !== null) {
    await loadDetail(activeRiderId.value)
  }
}

function openAudit(): void {
  if (!canAudit.value) {
    ElMessage.warning('仅最新一条待审认证记录可以审核')
    return
  }
  auditStatus.value = 1
  auditRemark.value = ''
  auditVisible.value = true
}

async function submitAudit(): Promise<void> {
  const riderId = activeRiderId.value
  if (riderId === null) {
    return
  }
  const remark = auditRemark.value.trim()
  if (auditStatus.value === 2 && !remark) {
    ElMessage.warning('驳回必须填写原因')
    return
  }

  acting.value = true
  try {
    await auditRiderCertification(riderId, {
      status: auditStatus.value,
      remark: remark || undefined,
    })
    ElMessage.success(auditStatus.value === 1 ? '认证已通过' : '认证已驳回')
    auditVisible.value = false
    await refreshAfterAction()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    acting.value = false
  }
}

function openStatus(row: RiderView): void {
  actionRiderId.value = row.id
  statusValue.value = row.status === 1 ? 2 : row.status === 2 ? 0 : 1
  statusVisible.value = true
}

async function submitStatus(): Promise<void> {
  const riderId = actionRiderId.value
  if (riderId === null) {
    return
  }
  if (statusValue.value !== 0) {
    try {
      await ElMessageBox.confirm(
        '停用或拉黑会强制该骑手下线，且立即影响其接单，确认继续？',
        '确认调整账号状态',
        { confirmButtonText: '确认调整', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
  }

  acting.value = true
  try {
    await updateRiderStatus(riderId, { status: statusValue.value })
    ElMessage.success('骑手账号状态已更新')
    statusVisible.value = false
    await refreshAfterAction()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    acting.value = false
  }
}

function openLimits(row: RiderView): void {
  actionRiderId.value = row.id
  limitsForm.maxConcurrentOrders = undefined
  limitsForm.pickupRadiusM = undefined
  limitsVisible.value = true
}

async function submitLimits(): Promise<void> {
  const riderId = actionRiderId.value
  if (riderId === null) {
    return
  }
  const maxConcurrentOrders = limitsForm.maxConcurrentOrders
  const pickupRadiusM = limitsForm.pickupRadiusM
  if (
    typeof maxConcurrentOrders !== 'number' ||
    !Number.isInteger(maxConcurrentOrders) ||
    maxConcurrentOrders < 1
  ) {
    ElMessage.warning('最大同时接单量必须是 ≥1 的整数')
    return
  }
  if (
    typeof pickupRadiusM !== 'number' ||
    !Number.isInteger(pickupRadiusM) ||
    pickupRadiusM < 100
  ) {
    ElMessage.warning('接单半径必须是 ≥100 的整数（米）')
    return
  }

  acting.value = true
  try {
    await updateRiderLimits(riderId, { maxConcurrentOrders, pickupRadiusM })
    ElMessage.success('接单限制已覆盖保存；本页不显示当前值')
    limitsVisible.value = false
    await refreshAfterAction()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>骑手管理</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/riders</span>。手机号字段为
          <span class="mono">phoneMask</span>（后端只返回掩码）；认证状态对应
          <span class="mono">t_rider.cert_status</span>，与认证记录状态是两套编码。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-select
          v-model="filters.certStatus"
          placeholder="全部认证状态"
          clearable
          class="status-select"
        >
          <el-option
            v-for="option in RIDER_CERT_STATUS_OPTIONS"
            :key="option.value"
            :value="option.value"
            :label="option.label"
          />
        </el-select>
        <el-select
          v-model="filters.status"
          placeholder="全部账号状态"
          clearable
          class="status-select"
        >
          <el-option
            v-for="option in RIDER_STATUS_OPTIONS"
            :key="option.value"
            :value="option.value"
            :label="option.label"
          />
        </el-select>
        <el-input
          v-model="filters.keyword"
          placeholder="骑手编号 / 姓名 / 手机尾号"
          clearable
          class="keyword-input"
          @keyup.enter="search"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="riders" border stripe row-key="id">
        <el-table-column label="骑手编号" width="120">
          <template #default="{ row }">
            <span class="mono">{{ row.riderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="姓名" width="110">
          <template #default="{ row }">{{ row.realName || '—' }}</template>
        </el-table-column>
        <el-table-column label="手机号" width="130">
          <template #default="{ row }">{{ row.phoneMask || '—' }}</template>
        </el-table-column>
        <el-table-column label="车型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(VEHICLE_TYPE, row.vehicleType)" size="small">
              {{ labelOf(VEHICLE_TYPE, row.vehicleType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="认证状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(RIDER_CERT_STATUS, row.certStatus)" size="small">
              {{ labelOf(RIDER_CERT_STATUS, row.certStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="账号状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(RIDER_STATUS, row.status)" size="small">
              {{ labelOf(RIDER_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="在线状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(RIDER_ONLINE_STATUS, row.onlineStatus)" size="small">
              {{ labelOf(RIDER_ONLINE_STATUS, row.onlineStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="累计单量" width="95" align="right">
          <template #default="{ row }">{{ row.totalOrders }}</template>
        </el-table-column>
        <el-table-column label="评分" width="80" align="right">
          <template #default="{ row }">{{ ratingText(row.rating) }}</template>
        </el-table-column>
        <el-table-column label="准时率" width="95" align="right">
          <template #default="{ row }">{{ formatPercent(row.onTimeRate) }}</template>
        </el-table-column>
        <el-table-column label="注册时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="warning" @click="openStatus(row)">
              启用/停用
            </el-button>
            <el-button link type="primary" @click="openLimits(row)">接单限制</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有骑手。" :image-size="80" />
        </template>
      </el-table>

      <el-pagination
        class="pagination"
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :page-sizes="PAGE_SIZE_OPTIONS"
        :current-page="pageNum"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>

    <el-drawer
      v-model="drawerVisible"
      title="骑手详情"
      size="760px"
      :close-on-click-modal="false"
      @closed="handleDrawerClosed"
    >
      <div v-loading="detailLoading" class="drawer-body">
        <el-alert
          v-if="detailError"
          type="error"
          :closable="false"
          show-icon
          :title="detailError"
        />

        <template v-if="detail">
          <div class="section-title">基本信息</div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="骑手编号">
              <span class="mono">{{ detail.rider.riderNo }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="姓名">
              {{ detail.rider.realName || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="手机号">
              {{ detail.rider.phoneMask || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="车型">
              <el-tag :type="tagOf(VEHICLE_TYPE, detail.rider.vehicleType)" size="small">
                {{ labelOf(VEHICLE_TYPE, detail.rider.vehicleType) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="认证状态">
              <el-tag
                :type="tagOf(RIDER_CERT_STATUS, detail.rider.certStatus)"
                size="small"
              >
                {{ labelOf(RIDER_CERT_STATUS, detail.rider.certStatus) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="账号状态">
              <el-tag :type="tagOf(RIDER_STATUS, detail.rider.status)" size="small">
                {{ labelOf(RIDER_STATUS, detail.rider.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="在线状态">
              <el-tag
                :type="tagOf(RIDER_ONLINE_STATUS, detail.rider.onlineStatus)"
                size="small"
              >
                {{ labelOf(RIDER_ONLINE_STATUS, detail.rider.onlineStatus) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="评分">
              {{ ratingText(detail.rider.rating) }}
            </el-descriptions-item>
            <el-descriptions-item label="准时率">
              {{ formatPercent(detail.rider.onTimeRate) }}
            </el-descriptions-item>
            <el-descriptions-item label="累计单量">
              {{ detail.rider.totalOrders }}
            </el-descriptions-item>
            <el-descriptions-item label="最近上线">
              {{ formatDateTime(detail.rider.lastOnlineAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="注册时间">
              {{ formatDateTime(detail.rider.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="今日在线时长" :span="2">
              {{ durationText(detail.todayOnlineSeconds) }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-title">认证记录</div>
          <el-table :data="detail.certifications" border stripe row-key="id">
            <el-table-column label="提交时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column prop="realName" label="姓名" width="100" />
            <el-table-column label="健康证到期" width="120">
              <template #default="{ row }">{{ row.healthCertExpireAt || '—' }}</template>
            </el-table-column>
            <el-table-column label="记录状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="tagOf(CERT_RECORD_STATUS, row.status)" size="small">
                  {{ labelOf(CERT_RECORD_STATUS, row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="审核人" width="90">
              <template #default="{ row }">
                {{ row.auditBy === null ? '—' : `#${row.auditBy}` }}
              </template>
            </el-table-column>
            <el-table-column label="审核时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.auditAt) }}</template>
            </el-table-column>
            <el-table-column label="审核备注" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.auditRemark || '—' }}</template>
            </el-table-column>

            <template #empty>
              <el-empty description="暂无认证记录。" :image-size="60" />
            </template>
          </el-table>
        </template>
      </div>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">关闭抽屉</el-button>
          <div class="toolbar-spacer" />
          <template v-if="detail">
            <el-tooltip
              :disabled="canAudit"
              content="仅最新一条待审认证记录可审核"
              placement="top"
            >
              <span class="button-wrap">
                <el-button :disabled="!canAudit" :loading="acting" @click="openAudit">
                  认证审核
                </el-button>
              </span>
            </el-tooltip>
            <el-button :loading="acting" @click="openStatus(detail.rider)">
              调整状态
            </el-button>
            <el-button type="primary" :loading="acting" @click="openLimits(detail.rider)">
              调整接单限制
            </el-button>
          </template>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      v-model="auditVisible"
      title="认证审核"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form label-width="90px">
        <el-form-item label="审核结论" required>
          <el-radio-group v-model="auditStatus">
            <el-radio :value="1">通过</el-radio>
            <el-radio :value="2">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="auditStatus === 2 ? '驳回原因' : '备注'" :required="auditStatus === 2">
          <el-input
            v-model="auditRemark"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            :placeholder="auditStatus === 2 ? '驳回原因将写入 audit_remark' : '可填写审核说明'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitAudit">
          提交审核
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="statusVisible"
      title="调整骑手账号状态"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="停用或拉黑后后端会强制该骑手下线，并立即影响接单。"
        class="dialog-alert"
      />
      <el-form label-width="90px">
        <el-form-item label="账号状态" required>
          <el-radio-group v-model="statusValue">
            <el-radio
              v-for="option in RIDER_STATUS_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitStatus">
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="limitsVisible"
      title="调整接单限制（覆盖式保存）"
      width="540px"
      :close-on-click-modal="false"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="接口不返回骑手现有接单限制，无法回填。保存会同时覆盖两个字段，请确认后填写完整数值。"
        class="dialog-alert"
      />
      <el-form label-width="130px">
        <el-form-item label="最大同时接单量" required>
          <el-input-number
            v-model="limitsForm.maxConcurrentOrders"
            :min="1"
            :precision="0"
            :controls="false"
            placeholder="≥1"
            class="limit-input"
          />
          <div class="form-hint">后端新骑手默认值为 3，仅供参考，不代表当前值。</div>
        </el-form-item>
        <el-form-item label="接单半径（米）" required>
          <el-input-number
            v-model="limitsForm.pickupRadiusM"
            :min="100"
            :precision="0"
            :controls="false"
            placeholder="≥100"
            class="limit-input"
          />
          <div class="form-hint">后端新骑手默认值为 3000，仅供参考，不代表当前值。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="limitsVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitLimits">
          覆盖保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filter-bar {
  margin-bottom: 14px;
}

.status-select {
  width: 150px;
}

.keyword-input {
  width: 220px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.drawer-body {
  min-height: 200px;
}

.section-title {
  font-weight: 600;
  margin: 18px 0 10px;
}

.section-title:first-child {
  margin-top: 0;
}

.drawer-footer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.button-wrap {
  display: inline-block;
}

.dialog-alert {
  margin-bottom: 14px;
}

.limit-input {
  width: 200px;
}

.form-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
  margin-top: 4px;
}
</style>
