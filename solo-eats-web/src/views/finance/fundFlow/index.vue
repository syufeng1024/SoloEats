<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  getFundFlowSummary,
  listFundFlows,
  type FundFlowSummary,
  type FundFlowView,
} from '@/api/finance'
import {
  FLOW_DIRECTION,
  FLOW_TYPE,
  FLOW_TYPE_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/financeStatus'
import { formatDateTime } from '@/utils/datetime'
import { formatYuan } from '@/utils/money'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const EMPTY_SUMMARY: FundFlowSummary = {
  commissionIncome: 0,
  settlementExpense: 0,
  subsidyCost: 0,
  discountCost: 0,
  payoutExpense: 0,
  refundOffset: 0,
  profit: 0,
}

const loading = ref(false)
const loadError = ref('')
const flows = ref<FundFlowView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const summaryLoading = ref(false)
const summaryError = ref('')
const summary = ref<FundFlowSummary>({ ...EMPTY_SUMMARY })

const filters = reactive({
  flowType: undefined as number | undefined,
  dateRange: null as string[] | null,
})

const summaryCards = computed(() => [
  { key: 'commissionIncome', label: '佣金收入', value: summary.value.commissionIncome },
  { key: 'settlementExpense', label: '结算支出', value: summary.value.settlementExpense },
  { key: 'subsidyCost', label: '补贴支出', value: summary.value.subsidyCost },
  { key: 'discountCost', label: '优惠成本', value: summary.value.discountCost },
  { key: 'payoutExpense', label: '提现支出', value: summary.value.payoutExpense },
  { key: 'refundOffset', label: '退款冲抵', value: summary.value.refundOffset },
  { key: 'profit', label: '利润', value: summary.value.profit },
])

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const range = Array.isArray(filters.dateRange) ? filters.dateRange : []
    const result = await listFundFlows({
      flowType: typeof filters.flowType === 'number' ? filters.flowType : undefined,
      dateFrom: range[0],
      dateTo: range[1],
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    flows.value = result.list
    total.value = result.total
  } catch (error) {
    loadError.value = errorMessage(error, '资金流水加载失败')
  } finally {
    loading.value = false
  }
}

async function loadSummary(): Promise<void> {
  summaryLoading.value = true
  summaryError.value = ''
  try {
    const range = Array.isArray(filters.dateRange) ? filters.dateRange : []
    summary.value = await getFundFlowSummary({
      dateFrom: range[0],
      dateTo: range[1],
    })
  } catch (error) {
    summary.value = { ...EMPTY_SUMMARY }
    summaryError.value = errorMessage(error, '资金汇总加载失败')
  } finally {
    summaryLoading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
  void loadSummary()
}

function reset(): void {
  filters.flowType = undefined
  filters.dateRange = null
  pageNum.value = 1
  void load()
  void loadSummary()
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

function openFlowDetail(row: FundFlowView): void {
  ElMessage.info(`流水 #${row.id} 对应 ${row.bizType} #${row.bizId}，业务详情页尚未接入`)
}

onMounted(() => {
  void load()
  void loadSummary()
})
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>资金流水</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/fund-flows</span> 与
          <span class="mono">/fund-flows/summary</span>。金额单位分。利润口径
          <span class="mono">commissionIncome − settlementExpense − subsidyCost − payoutExpense</span>，
          <span class="mono">discountCost</span> 与 <span class="mono">refundOffset</span> 仅展示、不参与利润计算。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="search">刷新</el-button>
      </div>
    </div>

    <el-alert
      v-if="summaryError"
      type="error"
      :closable="false"
      show-icon
      :title="summaryError"
    />

    <el-row v-loading="summaryLoading" :gutter="12" class="summary-row">
      <el-col
        v-for="card in summaryCards"
        :key="card.key"
        :xs="12"
        :sm="8"
        :md="6"
        :lg="6"
      >
        <el-card shadow="never" class="summary-card">
          <div class="summary-label">{{ card.label }}</div>
          <div
            class="summary-value"
            :class="{ 'value-negative': card.key === 'profit' && card.value < 0 }"
          >
            {{ formatYuan(card.value) }}
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-select
          v-model="filters.flowType"
          placeholder="全部流水类型"
          clearable
          class="status-select"
        >
          <el-option
            v-for="option in FLOW_TYPE_OPTIONS"
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
          @change="loadSummary"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="flows" border stripe row-key="id">
        <el-table-column label="类型" width="105">
          <template #default="{ row }">
            <el-tag :type="tagOf(FLOW_TYPE, row.flowType)" size="small">
              {{ labelOf(FLOW_TYPE, row.flowType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="方向" width="75">
          <template #default="{ row }">
            <el-tag :type="tagOf(FLOW_DIRECTION, row.direction)" size="small" effect="plain">
              {{ labelOf(FLOW_DIRECTION, row.direction) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="125" align="right">
          <template #default="{ row }">
            <span class="amount">{{ formatYuan(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="业务单据" min-width="190">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openFlowDetail(row)">
              <span class="mono">{{ row.bizType }}</span> #{{ row.bizId }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column label="发生时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有资金流水。" :image-size="80" />
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
.summary-row {
  margin-bottom: 16px;
}

.summary-card {
  margin-bottom: 12px;
}

.summary-label {
  color: var(--app-muted);
  font-size: 12px;
}

.summary-value {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.value-negative {
  color: var(--el-color-danger);
}

.filter-bar {
  margin-bottom: 14px;
}

.status-select {
  width: 165px;
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

@media (max-width: 900px) {
  .date-range {
    width: 100%;
  }
}
</style>
