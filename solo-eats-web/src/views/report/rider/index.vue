<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getReportRiders, type RiderAnalysisView } from '@/api/report'
import {
  defaultDateRange,
  errorMessage,
  formatCount,
  formatPercent,
  progressValue,
} from '@/views/report/helpers'
import { formatYuan } from '@/utils/money'

const DEFAULT_DAYS = 30
const TOP_N_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const rows = ref<RiderAnalysisView[]>([])

const filters = reactive<{
  dateRange: string[] | null
  topN: number
}>({
  dateRange: defaultDateRange(DEFAULT_DAYS),
  topN: 10,
})

const summary = computed(() => {
  const totalDeliveryCount = rows.value.reduce(
    (total, row) => total + row.deliveryCount,
    0,
  )
  const totalOnTimeCount = rows.value.reduce((total, row) => total + row.onTimeCount, 0)
  return {
    riderCount: rows.value.length,
    totalDeliveryCount,
    totalIncome: rows.value.reduce((total, row) => total + row.incomeAmount, 0),
    avgOnTimeRate:
      totalDeliveryCount > 0 ? (totalOnTimeCount / totalDeliveryCount) * 100 : 0,
  }
})

const summaryCards = computed(() => [
  {
    key: 'riders',
    label: '骑手数',
    value: formatCount(summary.value.riderCount),
    unit: '人',
  },
  {
    key: 'delivery',
    label: '总配送单量',
    value: formatCount(summary.value.totalDeliveryCount),
    unit: '单',
  },
  {
    key: 'rate',
    label: '平均准时率',
    value: formatPercent(summary.value.avgOnTimeRate),
    unit: '',
  },
  {
    key: 'income',
    label: '总配送收入',
    value: formatYuan(summary.value.totalIncome),
    unit: '',
  },
])

function ratingText(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—'
  }
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric.toFixed(2) : '—'
}

function formatOnlineHours(minutes: number | null | undefined): string {
  const numeric = Number(minutes)
  return Number.isFinite(numeric) ? (numeric / 60).toFixed(1) : '—'
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  const range =
    Array.isArray(filters.dateRange) && filters.dateRange.length === 2
      ? filters.dateRange
      : defaultDateRange(DEFAULT_DAYS)
  filters.dateRange = range
  try {
    rows.value = await getReportRiders({
      dateFrom: range[0],
      dateTo: range[1],
      topN: filters.topN,
    })
  } catch (error) {
    rows.value = []
    loadError.value = errorMessage(error, '骑手分析加载失败')
  } finally {
    loading.value = false
  }
}

function reset(): void {
  filters.dateRange = defaultDateRange(DEFAULT_DAYS)
  filters.topN = 10
  void load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>骑手分析</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/report/riders</span>。按送达单量倒序取 TopN；
          准时率由后端按 <span class="mono">percentage(onTimeCount, deliveryCount)</span> 返回 0~100
          的百分比数值，汇总卡的平均准时率按总准时单量 ÷ 总配送单量重算。
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
        <el-select v-model="filters.topN" class="topn-select">
          <el-option
            v-for="option in TOP_N_OPTIONS"
            :key="option"
            :label="`Top ${option}`"
            :value="option"
          />
        </el-select>
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
        <h3>骑手明细</h3>
        <span class="muted">在线时长按 onlineMinutes ÷ 60 换算，保留 1 位小数</span>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="riderId">
        <el-table-column label="排名" width="76" align="center">
          <template #default="{ $index }">
            <span class="rank">{{ $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="riderName" label="骑手名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="配送单量" width="110" align="right">
          <template #default="{ row }">{{ formatCount(row.deliveryCount) }}</template>
        </el-table-column>
        <el-table-column label="准时单量" width="110" align="right">
          <template #default="{ row }">{{ formatCount(row.onTimeCount) }}</template>
        </el-table-column>
        <el-table-column label="准时率" min-width="180">
          <template #default="{ row }">
            <div class="rate-cell">
              <el-progress
                class="rate-progress"
                :percentage="progressValue(row.onTimeRate)"
                :stroke-width="6"
                :show-text="false"
              />
              <span class="rate-text">{{ formatPercent(row.onTimeRate) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="评分" width="90" align="right">
          <template #default="{ row }">{{ ratingText(row.rating) }}</template>
        </el-table-column>
        <el-table-column label="配送收入" min-width="130" align="right">
          <template #default="{ row }">
            <span class="amount">{{ formatYuan(row.incomeAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="在线时长（小时）" width="150" align="right">
          <template #default="{ row }">{{ formatOnlineHours(row.onlineMinutes) }}</template>
        </el-table-column>
        <el-table-column label="平均配送时长" width="150" align="right">
          <template #default="{ row }">{{ row.avgDeliveryMinutes }} 分钟</template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有骑手分析数据。" :image-size="80" />
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

.topn-select {
  width: 120px;
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

.rate-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rate-progress {
  flex: 1;
  min-width: 70px;
}

.rate-text {
  width: 62px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.rank {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.amount {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 900px) {
  .date-range {
    width: 100%;
  }
}
</style>
