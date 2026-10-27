<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  getDeliveryTrack,
  listDeliveries,
  reassignDelivery,
  type DeliveryListParams,
  type DeliveryOrderView,
  type TrackPointView,
} from '@/api/delivery-admin'
import {
  DELIVERY_STATUS,
  DELIVERY_STATUS_OPTIONS,
  REASSIGNABLE_DELIVERY_STATUS,
  labelOf,
  tagOf,
} from '@/constants/deliveryStatus'
import { defaultDateRange, errorMessage } from '@/views/report/helpers'
import { formatDateTime } from '@/utils/datetime'
import { formatYuan } from '@/utils/money'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const rows = ref<DeliveryOrderView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const filters = reactive({
  status: undefined as number | undefined,
  shopId: undefined as number | null | undefined,
  riderId: undefined as number | null | undefined,
  dateRange: defaultDateRange(7) as string[] | null,
})

const trackVisible = ref(false)
const trackLoading = ref(false)
const trackError = ref('')
const trackPoints = ref<TrackPointView[]>([])
const trackDelivery = ref<DeliveryOrderView | null>(null)
let trackRequestId = 0

const reassignVisible = ref(false)
const reassignTarget = ref<DeliveryOrderView | null>(null)
const reassignReason = ref('')
const reassigning = ref(false)

const reassignRiderText = computed(() => {
  const target = reassignTarget.value
  return target === null || target.riderId === null ? '—' : `#${target.riderId}`
})

function positiveId(value: number | null | undefined): number | undefined {
  return typeof value === 'number' && Number.isInteger(value) && value > 0
    ? value
    : undefined
}

function distanceText(meters: number): string {
  const numeric = Number(meters)
  if (!Number.isFinite(numeric)) {
    return '—'
  }
  return numeric >= 1000 ? `${(numeric / 1000).toFixed(2)} km` : `${numeric} m`
}

/** 派生字段：deliveredAt ≤ promiseDeliverAt 视为准时，后端无布尔字段。 */
function onTimeText(row: DeliveryOrderView): string {
  if (!row.deliveredAt || !row.promiseDeliverAt) {
    return '—'
  }
  const delivered = Date.parse(row.deliveredAt)
  const promise = Date.parse(row.promiseDeliverAt)
  if (!Number.isFinite(delivered) || !Number.isFinite(promise)) {
    return '—'
  }
  return delivered <= promise ? '准时' : '超时'
}

function onTimeTag(row: DeliveryOrderView): 'success' | 'danger' | 'info' {
  const text = onTimeText(row)
  if (text === '准时') {
    return 'success'
  }
  return text === '超时' ? 'danger' : 'info'
}

function canReassign(row: DeliveryOrderView): boolean {
  return row.riderId !== null && REASSIGNABLE_DELIVERY_STATUS.includes(row.status)
}

function reassignDisabledReason(row: DeliveryOrderView): string {
  if (row.riderId === null) {
    return '未分配骑手，无需改派'
  }
  return `当前状态（${labelOf(DELIVERY_STATUS, row.status)}）不允许改派`
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const range = Array.isArray(filters.dateRange) ? filters.dateRange : []
    const params: DeliveryListParams = {
      status: filters.status,
      shopId: positiveId(filters.shopId),
      riderId: positiveId(filters.riderId),
      dateFrom: range[0],
      dateTo: range[1],
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    }
    const result = await listDeliveries(params)
    rows.value = result.list
    total.value = result.total
  } catch (error) {
    rows.value = []
    total.value = 0
    loadError.value = errorMessage(error, '配送单列表加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function reset(): void {
  filters.status = undefined
  filters.shopId = undefined
  filters.riderId = undefined
  filters.dateRange = defaultDateRange(7)
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

function openTrack(row: DeliveryOrderView): void {
  trackDelivery.value = row
  trackVisible.value = true
  trackPoints.value = []
  trackError.value = ''
  const requestId = ++trackRequestId
  trackLoading.value = true
  void (async () => {
    try {
      const points = await getDeliveryTrack(row.id)
      if (requestId === trackRequestId) {
        trackPoints.value = points
      }
    } catch (error) {
      if (requestId === trackRequestId) {
        trackPoints.value = []
        trackError.value = errorMessage(error, '轨迹加载失败')
      }
    } finally {
      if (requestId === trackRequestId) {
        trackLoading.value = false
      }
    }
  })()
}

function handleTrackClosed(): void {
  trackRequestId += 1
  trackDelivery.value = null
  trackPoints.value = []
  trackError.value = ''
  trackLoading.value = false
}

function openReassign(row: DeliveryOrderView): void {
  if (!canReassign(row)) {
    ElMessage.warning(reassignDisabledReason(row))
    return
  }
  reassignTarget.value = row
  reassignReason.value = ''
  reassignVisible.value = true
}

async function submitReassign(): Promise<void> {
  const target = reassignTarget.value
  if (!target || target.riderId === null) {
    return
  }
  reassigning.value = true
  try {
    await reassignDelivery(target.id, {
      riderId: target.riderId,
      reason: reassignReason.value.trim() || undefined,
    })
    ElMessage.success('配送单已释放，等待重新派单')
    reassignVisible.value = false
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    reassigning.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>配送单管理</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/deliveries</span>。日期按
          <span class="mono">createdAt</span> 过滤，后端包含 dateTo 当天。「是否准时」为派生态：
          <span class="mono">deliveredAt ≤ promiseDeliverAt</span>；视图只返回门店/骑手 ID，不含名称。
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
          v-model="filters.status"
          placeholder="全部状态"
          clearable
          class="status-select"
        >
          <el-option
            v-for="option in DELIVERY_STATUS_OPTIONS"
            :key="option.value"
            :value="option.value"
            :label="option.label"
          />
        </el-select>
        <el-input-number
          v-model="filters.shopId"
          :min="1"
          :precision="0"
          :controls="false"
          placeholder="门店 ID"
          class="id-input"
        />
        <el-input-number
          v-model="filters.riderId"
          :min="1"
          :precision="0"
          :controls="false"
          placeholder="骑手 ID"
          class="id-input"
        />
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="创建开始日期"
          end-placeholder="创建结束日期"
          class="date-range"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="id">
        <el-table-column label="配送单号" width="170">
          <template #default="{ row }">
            <span class="mono">{{ row.deliveryNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="orderId" label="订单 ID" width="90" />
        <el-table-column prop="shopId" label="门店 ID" width="90" />
        <el-table-column label="骑手" width="90">
          <template #default="{ row }">
            {{ row.riderId === null ? '未分配' : `#${row.riderId}` }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(DELIVERY_STATUS, row.status)" size="small">
              {{ labelOf(DELIVERY_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="距离" width="100" align="right">
          <template #default="{ row }">{{ distanceText(row.distanceM) }}</template>
        </el-table-column>
        <el-table-column label="配送费" width="110" align="right">
          <template #default="{ row }">
            <span class="amount">{{ formatYuan(row.deliveryFee) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="dispatchCount" label="派单次数" width="90" align="right" />
        <el-table-column label="承诺送达" width="160">
          <template #default="{ row }">{{ formatDateTime(row.promiseDeliverAt) }}</template>
        </el-table-column>
        <el-table-column label="实际送达" width="160">
          <template #default="{ row }">{{ formatDateTime(row.deliveredAt) }}</template>
        </el-table-column>
        <el-table-column label="是否准时" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="onTimeTag(row)" size="small">{{ onTimeText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openTrack(row)">轨迹</el-button>
            <el-tooltip
              :disabled="canReassign(row)"
              :content="reassignDisabledReason(row)"
              placement="top"
            >
              <span class="button-wrap">
                <el-button
                  link
                  type="warning"
                  :disabled="!canReassign(row)"
                  @click="openReassign(row)"
                >
                  改派
                </el-button>
              </span>
            </el-tooltip>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有配送单。" :image-size="80" />
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
      v-model="trackVisible"
      title="配送轨迹"
      size="680px"
      :close-on-click-modal="false"
      @closed="handleTrackClosed"
    >
      <div v-loading="trackLoading" class="drawer-body">
        <el-alert
          v-if="trackError"
          type="error"
          :closable="false"
          show-icon
          :title="trackError"
        />

        <template v-if="trackDelivery">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="未接入地图组件，按上报时间顺序列出坐标点。"
            class="block-alert"
          />
          <el-descriptions :column="2" border>
            <el-descriptions-item label="配送单号">
              <span class="mono">{{ trackDelivery.deliveryNo }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="tagOf(DELIVERY_STATUS, trackDelivery.status)" size="small">
                {{ labelOf(DELIVERY_STATUS, trackDelivery.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="订单 ID">
              {{ trackDelivery.orderId }}
            </el-descriptions-item>
            <el-descriptions-item label="骑手">
              {{ trackDelivery.riderId === null ? '未分配' : `#${trackDelivery.riderId}` }}
            </el-descriptions-item>
            <el-descriptions-item label="取货地址" :span="2">
              {{ trackDelivery.pickupAddress }}
            </el-descriptions-item>
            <el-descriptions-item label="收货地址" :span="2">
              {{ trackDelivery.receiverAddress }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-title">轨迹点（{{ trackPoints.length }}）</div>
          <el-timeline v-if="trackPoints.length > 0">
            <el-timeline-item
              v-for="point in trackPoints"
              :key="point.id"
              :timestamp="formatDateTime(point.reportAt)"
              placement="top"
            >
              <div class="track-line">
                经度 {{ point.lng }} · 纬度 {{ point.lat }}
              </div>
              <div class="muted sub">
                速度 {{ point.speed ?? '—' }} · 精度 {{ point.accuracy ?? '—' }} · 骑手
                {{ point.riderId === null ? '—' : `#${point.riderId}` }}
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无轨迹点。" :image-size="60" />
        </template>
      </div>

      <template #footer>
        <el-button @click="trackVisible = false">关闭抽屉</el-button>
      </template>
    </el-drawer>

    <el-dialog
      v-model="reassignVisible"
      title="释放配送单并重新派单"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        :title="`改派不会直接指定新骑手。提交后，系统会解除当前骑手 ${reassignRiderText} 的配送任务，配送单回到「待派单」，再由派单策略重新分配。`"
        class="block-alert"
      />
      <el-form label-width="150px">
        <el-form-item label="当前骑手 ID（校验）">
          <span class="mono">{{ reassignRiderText }}</span>
        </el-form-item>
        <el-form-item label="改派原因（选填）">
          <el-input
            v-model="reassignReason"
            type="textarea"
            :rows="3"
            maxlength="128"
            show-word-limit
            placeholder="例如：骑手车辆故障 / 超时未取货"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reassignVisible = false">取消</el-button>
        <el-button type="danger" :loading="reassigning" @click="submitReassign">
          确认释放并等待重新派单
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
  width: 140px;
}

.id-input {
  width: 130px;
}

.date-range {
  width: 280px;
}

.amount {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.drawer-body {
  min-height: 200px;
}

.block-alert {
  margin-bottom: 14px;
}

.section-title {
  font-weight: 600;
  margin: 18px 0 10px;
}

.track-line {
  font-variant-numeric: tabular-nums;
}

.sub {
  font-size: 12px;
  margin-top: 2px;
}

.button-wrap {
  display: inline-block;
  margin-left: 12px;
}

@media (max-width: 1100px) {
  .date-range {
    width: 100%;
  }
}
</style>
