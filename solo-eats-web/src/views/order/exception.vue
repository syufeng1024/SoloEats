<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import {
  listExceptionOrders,
  type AdminExceptionListParams,
  type AdminExceptionOrderView,
} from '@/api/order-admin'
import { EXCEPTION_TYPE, EXCEPTION_TYPE_OPTIONS, labelOf, tagOf } from '@/constants/orderAdmin'
import { ORDER_STATUS_OPTIONS, orderStatusMeta } from '@/constants/orderStatus'
import { debounce } from '@/utils/debounce'
import { formatDateTime } from '@/utils/datetime'
import { formatYuan } from '@/utils/money'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const router = useRouter()

const loading = ref(false)
const loadError = ref('')
const rows = ref<AdminExceptionOrderView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const filters = reactive({
  exceptionType: undefined as number | undefined,
  status: undefined as number | undefined,
  merchantId: undefined as number | undefined,
  shopId: undefined as number | undefined,
  keyword: '',
  dateRange: null as string[] | null,
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

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const range = Array.isArray(filters.dateRange) ? filters.dateRange : []
    const params: AdminExceptionListParams = {
      exceptionType:
        typeof filters.exceptionType === 'number' ? filters.exceptionType : undefined,
      status: typeof filters.status === 'number' ? filters.status : undefined,
      merchantId:
        typeof filters.merchantId === 'number' ? filters.merchantId : undefined,
      shopId: typeof filters.shopId === 'number' ? filters.shopId : undefined,
      keyword: appliedKeyword.value.trim() || undefined,
      dateFrom: range[0],
      dateTo: range[1],
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    }
    const result = await listExceptionOrders(params)
    rows.value = result.list
    total.value = result.total
  } catch (error) {
    rows.value = []
    total.value = 0
    loadError.value = errorMessage(error, '异常订单池加载失败')
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
  filters.exceptionType = undefined
  filters.status = undefined
  filters.merchantId = undefined
  filters.shopId = undefined
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

function openDetail(row: AdminExceptionOrderView): void {
  void router.push({ name: 'admin-order-detail', params: { orderId: row.orderId } })
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>异常订单池</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/orders/exceptions</span>，筛选条件
          <span class="mono">t_order.exception_type != 0</span>。仅展示，处理动作不在本页。
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
          v-model="filters.exceptionType"
          placeholder="全部异常类型"
          clearable
          class="status-select"
        >
          <el-option
            v-for="option in EXCEPTION_TYPE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select
          v-model="filters.status"
          placeholder="全部订单状态"
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
        <el-input-number
          v-model="filters.merchantId"
          :min="1"
          :controls="false"
          placeholder="商家 ID"
          class="id-input"
        />
        <el-input-number
          v-model="filters.shopId"
          :min="1"
          :controls="false"
          placeholder="门店 ID"
          class="id-input"
        />
        <el-input
          v-model="filters.keyword"
          placeholder="订单号 / 收货人 / 手机号"
          clearable
          class="keyword-input"
          @keyup.enter="search"
        />
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="下单开始日期"
          end-placeholder="下单结束日期"
          class="date-range"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        border
        stripe
        row-key="orderId"
        class="clickable-table"
        @row-click="openDetail"
      >
        <el-table-column prop="orderNo" label="订单号" min-width="180">
          <template #default="{ row }">
            <span class="mono">{{ row.orderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="商家 / 门店" width="120">
          <template #default="{ row }">
            <div>{{ row.merchantId }}</div>
            <div class="muted sub">门店 {{ row.shopId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="订单状态" width="110">
          <template #default="{ row }">
            <el-tag :type="orderStatusMeta(row.status).tag" size="small">
              {{ row.statusName || orderStatusMeta(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="异常类型" width="120">
          <template #default="{ row }">
            <el-tag :type="tagOf(EXCEPTION_TYPE, row.exceptionType)" size="small">
              {{ row.exceptionTypeName || labelOf(EXCEPTION_TYPE, row.exceptionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="实付金额" width="120" align="right">
          <template #default="{ row }">
            <span class="amount">{{ formatYuan(row.payAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有异常订单。" :image-size="80" />
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
  width: 110px;
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

@media (max-width: 1100px) {
  .date-range {
    width: 100%;
  }
}
</style>
