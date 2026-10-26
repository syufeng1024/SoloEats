<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { monitorOrders } from '@/api/admin'
import { getDashboard } from '@/api/report'
import type { AdminOrderSummary, AdminOrderView, DashboardView } from '@/api/types'
import { ORDER_STATUS_OPTIONS, orderStatusMeta } from '@/constants/orderStatus'
import { formatDateTime } from '@/utils/datetime'
import { debounce } from '@/utils/debounce'
import { formatYuan } from '@/utils/money'

const EMPTY_SUMMARY: AdminOrderSummary = {
  total: 0,
  waitAccept: 0,
  preparing: 0,
  waitRiderPickup: 0,
  waitUserPickup: 0,
  delivering: 0,
  completed: 0,
  cancelled: 0,
  paidAmount: 0,
  refundedAmount: 0,
}

const EMPTY_DASHBOARD: DashboardView = {
  todayGmv: 0,
  orderCount: 0,
  avgOrderAmount: 0,
  onlineRiders: 0,
  exceptionOrders: 0,
  newUsers: 0,
  inProgressOrders: 0,
}

const loading = ref(false)
const loadError = ref('')
const dashboardLoading = ref(false)
const dashboardError = ref('')
const dashboard = ref<DashboardView>({ ...EMPTY_DASHBOARD })
const orders = ref<AdminOrderView[]>([])
const summary = ref<AdminOrderSummary>({ ...EMPTY_SUMMARY })
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const appliedKeyword = ref('')

const filters = reactive({
  merchantId: undefined as number | undefined,
  shopId: undefined as number | undefined,
  status: undefined as number | undefined,
  keyword: '',
  dateRange: null as string[] | null,
})

watch(
  () => filters.keyword,
  debounce((value: string) => {
    appliedKeyword.value = value
    pageNum.value = 1
    void load()
  }, 300),
)

async function loadDashboard(): Promise<void> {
  dashboardLoading.value = true
  dashboardError.value = ''
  try {
    dashboard.value = await getDashboard()
  } catch (error) {
    dashboardError.value = error instanceof Error ? error.message : '今日经营数据加载失败'
  } finally {
    dashboardLoading.value = false
  }
}

async function refreshAll(): Promise<void> {
  await Promise.all([loadDashboard(), load()])
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const range = Array.isArray(filters.dateRange) ? filters.dateRange : []
    const result = await monitorOrders({
      merchantId: typeof filters.merchantId === 'number' ? filters.merchantId : undefined,
      shopId: typeof filters.shopId === 'number' ? filters.shopId : undefined,
      status: typeof filters.status === 'number' ? filters.status : undefined,
      keyword: appliedKeyword.value.trim() || undefined,
      dateFrom: range[0],
      dateTo: range[1],
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    orders.value = result.orders
    summary.value = result.summary
    total.value = result.total
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '监控数据加载失败'
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function reset(): void {
  filters.merchantId = undefined
  filters.shopId = undefined
  filters.status = undefined
  filters.keyword = ''
  filters.dateRange = null
  appliedKeyword.value = ''
  pageNum.value = 1
  void load()
}

function handlePageChange(page: number): void {
  pageNum.value = page
  void load()
}

function fulfillmentLabel(type: number): string {
  return type === 1 ? '配送' : '自取'
}

function copyOrderNo(orderNo: string): void {
  void navigator.clipboard
    .writeText(orderNo)
    .then(() => ElMessage.success('订单号已复制'))
    .catch(() => ElMessage.warning('复制失败，请手动选择'))
}

onMounted(refreshAll)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>全局订单监控看板</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/orders/monitor</span>。
          汇总与列表使用同一组筛选条件；收件手机号在管理端按
          <span class="mono">前 3 位 + **** + 后 4 位</span> 脱敏。
        </p>
      </div>
      <el-button :icon="Refresh" :loading="loading || dashboardLoading" @click="refreshAll">
        刷新
      </el-button>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <div class="section-lead">
      <h3>今日经营</h3>
      <span class="muted">
        数据源：<span class="mono">GET /api/v1/admin/report/dashboard</span>，金额单位为分，按元展示。
      </span>
    </div>

    <el-alert
      v-if="dashboardError"
      type="warning"
      :closable="false"
      show-icon
      :title="dashboardError"
    />

    <div v-loading="dashboardLoading" class="stat-grid dashboard-grid">
      <div class="stat-card stat-card-accent">
        <div class="stat-label">今日 GMV</div>
        <div class="stat-value">{{ formatYuan(dashboard.todayGmv) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">今日订单</div>
        <div class="stat-value">{{ dashboard.orderCount }}<span class="stat-unit">笔</span></div>
      </div>
      <div class="stat-card">
        <div class="stat-label">客单价</div>
        <div class="stat-value">{{ formatYuan(dashboard.avgOrderAmount) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">进行中订单</div>
        <div class="stat-value">{{ dashboard.inProgressOrders }}<span class="stat-unit">笔</span></div>
      </div>
      <div class="stat-card">
        <div class="stat-label">异常订单</div>
        <div class="stat-value" :class="{ 'stat-danger': dashboard.exceptionOrders > 0 }">
          {{ dashboard.exceptionOrders }}<span class="stat-unit">笔</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">在线骑手</div>
        <div class="stat-value">{{ dashboard.onlineRiders }}<span class="stat-unit">人</span></div>
      </div>
      <div class="stat-card">
        <div class="stat-label">今日新增用户</div>
        <div class="stat-value">{{ dashboard.newUsers }}<span class="stat-unit">人</span></div>
      </div>
    </div>

    <div class="section-lead">
      <h3>订单监控汇总</h3>
      <span class="muted">随下方筛选条件联动</span>
    </div>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-label">总订单</div>
        <div class="stat-value">{{ summary.total }}<span class="stat-unit">笔</span></div>
      </div>
      <div class="stat-card">
        <div class="stat-label">待接单</div>
        <div class="stat-value">{{ summary.waitAccept }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">备餐中</div>
        <div class="stat-value">{{ summary.preparing }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">待骑手取货</div>
        <div class="stat-value">{{ summary.waitRiderPickup }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">待用户取餐</div>
        <div class="stat-value">{{ summary.waitUserPickup }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">配送中</div>
        <div class="stat-value">{{ summary.delivering }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已完成</div>
        <div class="stat-value">{{ summary.completed }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已取消</div>
        <div class="stat-value">{{ summary.cancelled }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">实付金额</div>
        <div class="stat-value">{{ formatYuan(summary.paidAmount) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">退款金额</div>
        <div class="stat-value">{{ formatYuan(summary.refundedAmount) }}</div>
      </div>
    </div>

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-input-number
          v-model="filters.merchantId"
          placeholder="商家 ID"
          :min="1"
          :precision="0"
          :controls="false"
          class="id-input"
        />
        <el-input-number
          v-model="filters.shopId"
          placeholder="门店 ID"
          :min="1"
          :precision="0"
          :controls="false"
          class="id-input"
        />
        <el-select
          v-model="filters.status"
          placeholder="全部状态"
          clearable
          class="status-select"
        >
          <el-option
            v-for="option in ORDER_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="date-range"
        />
        <el-input
          v-model="filters.keyword"
          class="keyword-input"
          placeholder="订单号 / 收货人 / 手机号（300ms 防抖）"
          clearable
          :prefix-icon="Search"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="orders" border row-key="orderId">
        <el-table-column
          prop="orderNo"
          label="订单号"
          width="165"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <el-button link type="primary" class="mono" @click="copyOrderNo(row.orderNo)">
              {{ row.orderNo }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="商家 / 门店" width="105">
          <template #default="{ row }">
            <span class="mono">{{ row.merchantId }} / {{ row.shopId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <div class="status-cell">
              <el-tag :type="orderStatusMeta(row.status).tag" size="small">
                {{ orderStatusMeta(row.status).label }}
              </el-tag>
              <el-tag type="info" size="small" effect="plain">
                {{ fulfillmentLabel(row.fulfillmentType) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="实付" width="90" align="right">
          <template #default="{ row }">{{ formatYuan(row.payAmount) }}</template>
        </el-table-column>
        <el-table-column label="退款" width="90" align="right">
          <template #default="{ row }">{{ formatYuan(row.refundedAmount) }}</template>
        </el-table-column>
        <el-table-column label="收货人" min-width="120">
          <template #default="{ row }">
            <div>{{ row.receiverName || '—' }}</div>
            <div class="mono muted">{{ row.receiverPhone || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="145">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="完成时间" width="145">
          <template #default="{ row }">{{ formatDateTime(row.completedAt) }}</template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有订单。" :image-size="80" />
        </template>
      </el-table>

      <el-pagination
        class="pagination"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="pageNum"
        @current-change="handlePageChange"
      />
    </el-card>
  </div>
</template>

<style scoped>
.section-lead {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin: 20px 0 10px;
}

.section-lead h3 {
  font-size: 14px;
  font-weight: 600;
}

.dashboard-grid {
  margin-bottom: 4px;
}

.stat-card-accent {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.stat-danger {
  color: var(--el-color-danger);
}

.filter-bar {
  margin-bottom: 14px;
}

.id-input {
  width: 120px;
}

.status-select {
  width: 150px;
}

.date-range {
  width: 260px;
}

.keyword-input {
  width: 300px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.status-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

@media (max-width: 900px) {
  .keyword-input,
  .date-range {
    width: 100%;
  }
}
</style>
