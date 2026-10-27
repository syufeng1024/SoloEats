<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import {
  getReportOverview,
  type OverviewSeries,
  type ReportGranularity,
} from '@/api/report'
import {
  defaultDateRange,
  errorMessage,
  formatCount,
  formatRatioPercent,
} from '@/views/report/helpers'
import { formatYuan } from '@/utils/money'

const DEFAULT_DAYS = 7

const loading = ref(false)
const loadError = ref('')
const series = ref<OverviewSeries | null>(null)

const filters = reactive<{
  dateRange: string[] | null
  granularity: ReportGranularity
}>({
  dateRange: defaultDateRange(DEFAULT_DAYS),
  granularity: 'day',
})

const points = computed(() => series.value?.points ?? [])

const summary = computed(() => {
  const total = points.value.reduce(
    (result, point) => {
      result.gmv += point.gmv
      result.orderCount += point.orderCount
      result.refundAmount += point.refundAmount
      return result
    },
    { gmv: 0, orderCount: 0, refundAmount: 0 },
  )
  return {
    ...total,
    // 各期 avgOrderAmount 不能相加，必须用区间总 GMV / 总订单数重算。
    avgOrderAmount: total.orderCount > 0 ? Math.round(total.gmv / total.orderCount) : 0,
  }
})

const summaryCards = computed(() => [
  { key: 'gmv', label: '总 GMV', value: formatYuan(summary.value.gmv), unit: '' },
  {
    key: 'orders',
    label: '总订单数',
    value: formatCount(summary.value.orderCount),
    unit: '笔',
  },
  {
    key: 'avg',
    label: '平均客单价',
    value: formatYuan(summary.value.avgOrderAmount),
    unit: '',
  },
  {
    key: 'refund',
    label: '退款金额',
    value: formatYuan(summary.value.refundAmount),
    unit: '',
  },
])

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  const range =
    Array.isArray(filters.dateRange) && filters.dateRange.length === 2
      ? filters.dateRange
      : defaultDateRange(DEFAULT_DAYS)
  filters.dateRange = range
  try {
    series.value = await getReportOverview({
      dateFrom: range[0],
      dateTo: range[1],
      granularity: filters.granularity,
    })
  } catch (error) {
    series.value = null
    loadError.value = errorMessage(error, '经营总览加载失败')
  } finally {
    loading.value = false
  }
}

function reset(): void {
  filters.dateRange = defaultDateRange(DEFAULT_DAYS)
  filters.granularity = 'day'
  void load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>经营总览</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/report/overview</span>。GMV 与退款金额单位为分，
          客单价按区间总 GMV ÷ 总订单数重算；退款率、复购率由后端按 0~1 比例返回。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          unlink-panels
          class="date-range"
        />
        <el-radio-group v-model="filters.granularity">
          <el-radio-button value="day">日</el-radio-button>
          <el-radio-button value="week">周</el-radio-button>
          <el-radio-button value="month">月</el-radio-button>
        </el-radio-group>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
    </el-card>

    <el-row :gutter="12">
      <el-col v-for="card in summaryCards" :key="card.key" :xs="12" :sm="12" :md="6">
        <el-card shadow="never" class="summary-card">
          <div class="summary-label">{{ card.label }}</div>
          <div class="summary-value">
            {{ card.value }}<span v-if="card.unit" class="summary-unit">{{ card.unit }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <div class="section-head">
        <h3>趋势明细</h3>
        <span class="muted">按{{ series?.granularity === 'week' ? '自然周' : series?.granularity === 'month' ? '自然月' : '日' }}汇总，缺失区间由后端补 0</span>
      </div>

      <el-table v-loading="loading" :data="points" border stripe row-key="period">
        <el-table-column label="期间" min-width="150">
          <template #default="{ row }">
            <div class="mono">{{ row.period }}</div>
            <div class="muted sub">{{ row.periodStart }} ~ {{ row.periodEnd }}</div>
          </template>
        </el-table-column>
        <el-table-column label="GMV" min-width="130" align="right">
          <template #default="{ row }">
            <span class="amount">{{ formatYuan(row.gmv) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="订单数" width="100" align="right">
          <template #default="{ row }">{{ formatCount(row.orderCount) }}</template>
        </el-table-column>
        <el-table-column label="客单价" min-width="120" align="right">
          <template #default="{ row }">{{ formatYuan(row.avgOrderAmount) }}</template>
        </el-table-column>
        <el-table-column label="退款金额" min-width="130" align="right">
          <template #default="{ row }">{{ formatYuan(row.refundAmount) }}</template>
        </el-table-column>
        <el-table-column label="退款单数" width="100" align="right">
          <template #default="{ row }">{{ formatCount(row.refundOrderCount) }}</template>
        </el-table-column>
        <el-table-column label="退款率" width="100" align="right">
          <template #default="{ row }">{{ formatRatioPercent(row.refundRate) }}</template>
        </el-table-column>
        <el-table-column label="复购率" width="100" align="right">
          <template #default="{ row }">{{ formatRatioPercent(row.repurchaseRate) }}</template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有经营数据。" :image-size="80" />
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.filter-bar {
  margin-bottom: 0;
}

.date-range {
  width: 280px;
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
  font-size: 28px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.summary-unit {
  margin-left: 4px;
  color: var(--app-muted);
  font-size: 13px;
  font-weight: 400;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.section-head h3 {
  font-size: 15px;
  font-weight: 600;
}

.amount {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.sub {
  margin-top: 2px;
  font-size: 12px;
}

@media (max-width: 900px) {
  .date-range {
    width: 100%;
  }
}
</style>
