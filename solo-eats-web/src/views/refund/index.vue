<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import {
  getAdminRefund,
  listAdminRefunds,
  type AdminRefundDetailView,
  type AdminRefundListParams,
  type AdminRefundView,
} from '@/api/order-admin'
import {
  REFUND_STATUS,
  REFUND_STATUS_OPTIONS,
  REFUND_TYPE,
  RESTOCK_FLAG,
  labelOf,
  tagOf,
} from '@/constants/orderAdmin'
import { orderStatusMeta } from '@/constants/orderStatus'
import { debounce } from '@/utils/debounce'
import { formatDateTime } from '@/utils/datetime'
import { formatYuan } from '@/utils/money'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const refunds = ref<AdminRefundView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const filters = reactive({
  status: undefined as number | undefined,
  keyword: '',
  dateRange: null as string[] | null,
})
const appliedKeyword = ref('')

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref<AdminRefundDetailView | null>(null)
let activeRefundId: number | null = null

watch(
  () => filters.keyword,
  debounce((value: string) => {
    appliedKeyword.value = value
    pageNum.value = 1
    void load()
  }, 300),
)

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const range = Array.isArray(filters.dateRange) ? filters.dateRange : []
    const params: AdminRefundListParams = {
      status: typeof filters.status === 'number' ? filters.status : undefined,
      keyword: appliedKeyword.value.trim() || undefined,
      dateFrom: range[0],
      dateTo: range[1],
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    }
    const result = await listAdminRefunds(params)
    refunds.value = result.list
    total.value = result.total
  } catch (error) {
    refunds.value = []
    total.value = 0
    loadError.value = errorMessage(error, '售后列表加载失败')
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
  filters.status = undefined
  filters.keyword = ''
  appliedKeyword.value = ''
  filters.dateRange = null
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
    const result = await getAdminRefund(id)
    if (activeRefundId === id) {
      detail.value = result
    }
  } catch (error) {
    if (activeRefundId === id) {
      detail.value = null
      detailError.value = errorMessage(error, '售后详情加载失败')
    }
  } finally {
    if (activeRefundId === id) {
      detailLoading.value = false
    }
  }
}

function openDetail(row: AdminRefundView): void {
  activeRefundId = row.refundId
  drawerVisible.value = true
  detail.value = null
  detailError.value = ''
  void loadDetail(row.refundId)
}

function handleDrawerClosed(): void {
  activeRefundId = null
  detail.value = null
  detailError.value = ''
  detailLoading.value = false
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>售后管理</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/refunds</span>。状态流转
          <span class="mono">0申请 → 10商家同意/11商家拒绝/12平台仲裁 → 20退款中 → 30成功/31失败</span>，
          40 取消、50 自动同意为独立终态。本页只读，审核动作尚未开放。
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
          placeholder="全部退款状态"
          clearable
          class="status-select"
        >
          <el-option
            v-for="option in REFUND_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-input
          v-model="filters.keyword"
          placeholder="退款单号 / 订单号"
          clearable
          class="keyword-input"
          @keyup.enter="search"
        />
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="申请开始日期"
          end-placeholder="申请结束日期"
          class="date-range"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="refunds"
        border
        stripe
        row-key="refundId"
        class="clickable-table"
        @row-click="openDetail"
      >
        <el-table-column prop="refundNo" label="退款单号" min-width="180">
          <template #default="{ row }">
            <span class="mono">{{ row.refundNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="订单" width="200">
          <template #default="{ row }">
            <div class="mono">{{ row.orderNo || `#${row.orderId}` }}</div>
            <div v-if="row.orderStatus !== null" class="muted sub">
              订单状态：{{ row.orderStatusName || orderStatusMeta(row.orderStatus).label }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ labelOf(REFUND_TYPE, row.refundType) }}</template>
        </el-table-column>
        <el-table-column label="退款金额" width="120" align="right">
          <template #default="{ row }">
            <span class="amount">{{ formatYuan(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="tagOf(REFUND_STATUS, row.status)" size="small">
              {{ labelOf(REFUND_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="原因" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason || '—' }}</template>
        </el-table-column>
        <el-table-column label="商家处理时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.merchantReviewAt) }}</template>
        </el-table-column>
        <el-table-column label="退款成功时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.successAt) }}</template>
        </el-table-column>
        <el-table-column label="申请时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有退款单。" :image-size="80" />
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
      title="售后详情"
      size="720px"
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
          <el-descriptions :column="2" border>
            <el-descriptions-item label="退款单号">
              <span class="mono">{{ detail.refund.refundNo }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="退款状态">
              <el-tag :type="tagOf(REFUND_STATUS, detail.refund.status)" size="small">
                {{ labelOf(REFUND_STATUS, detail.refund.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="订单号">
              <span class="mono">{{ detail.refund.orderNo || `#${detail.refund.orderId}` }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="订单状态">
              {{
                detail.refund.orderStatus === null
                  ? '—'
                  : detail.refund.orderStatusName ||
                    orderStatusMeta(detail.refund.orderStatus).label
              }}
            </el-descriptions-item>
            <el-descriptions-item label="退款类型">
              {{ labelOf(REFUND_TYPE, detail.refund.refundType) }}
            </el-descriptions-item>
            <el-descriptions-item label="退款金额">
              <span class="amount strong">{{ formatYuan(detail.refund.amount) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="申请时间">
              {{ formatDateTime(detail.refund.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="商家处理时间">
              {{ formatDateTime(detail.refund.merchantReviewAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="退款成功时间">
              {{ formatDateTime(detail.refund.successAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="退款原因" :span="2">
              {{ detail.refund.reason || '—' }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-title">退款明细</div>
          <el-table :data="detail.items" border stripe row-key="itemId">
            <el-table-column label="明细 ID" width="100">
              <template #default="{ row }">{{ row.itemId }}</template>
            </el-table-column>
            <el-table-column label="订单明细 ID" width="120">
              <template #default="{ row }">{{ row.orderItemId }}</template>
            </el-table-column>
            <el-table-column label="SKU ID" width="100">
              <template #default="{ row }">{{ row.skuId }}</template>
            </el-table-column>
            <el-table-column prop="qty" label="数量" width="80" align="right" />
            <el-table-column label="退款金额" width="120" align="right">
              <template #default="{ row }">
                <span class="amount">{{ formatYuan(row.refundAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="库存处理" width="110">
              <template #default="{ row }">
                <el-tag :type="tagOf(RESTOCK_FLAG, row.restockFlag)" size="small">
                  {{ labelOf(RESTOCK_FLAG, row.restockFlag) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>

            <template #empty>
              <el-empty description="该退款单没有明细记录。" :image-size="60" />
            </template>
          </el-table>
        </template>
      </div>

      <template #footer>
        <el-button @click="drawerVisible = false">关闭</el-button>
      </template>
    </el-drawer>
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

.date-range {
  width: 280px;
}

.amount {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.strong {
  font-weight: 600;
}

.sub {
  font-size: 12px;
  margin-top: 2px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.clickable-table :deep(.el-table__row) {
  cursor: pointer;
}

.drawer-body {
  min-height: 200px;
}

.section-title {
  font-weight: 600;
  margin: 18px 0 10px;
}

@media (max-width: 900px) {
  .date-range {
    width: 100%;
  }
}
</style>
