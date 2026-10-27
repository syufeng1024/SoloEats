<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getReportFinance, type FinanceReportView } from '@/api/report'
import { defaultDateRange, errorMessage, formatPercent } from '@/views/report/helpers'
import { formatYuan } from '@/utils/money'

const DEFAULT_DAYS = 30

const EMPTY_FINANCE: FinanceReportView = {
  commissionIncome: 0,
  settlementExpense: 0,
  subsidyCost: 0,
  discountCost: 0,
  withdrawExpense: 0,
  refundOffset: 0,
  grossProfit: 0,
  refundRate: 0,
}

const loading = ref(false)
const loadError = ref('')
const finance = ref<FinanceReportView>({ ...EMPTY_FINANCE })

const filters = reactive<{ dateRange: string[] | null }>({
  dateRange: defaultDateRange(DEFAULT_DAYS),
})

const financeItems = computed(() => [
  {
    key: 'commissionIncome',
    label: '佣金收入',
    amount: finance.value.commissionIncome,
    description: '按抽佣规则向商家收取的服务费（t_fund_flow.flow_type = 1）。',
  },
  {
    key: 'settlementExpense',
    label: '结算支出',
    amount: finance.value.settlementExpense,
    description: '向商家支付的结算款项（flow_type = 2），仅展示，不参与毛利计算。',
  },
  {
    key: 'subsidyCost',
    label: '补贴成本',
    amount: finance.value.subsidyCost,
    description: '平台承担的配送与活动补贴（flow_type = 3），计入毛利扣减项。',
  },
  {
    key: 'discountCost',
    label: '优惠成本',
    amount: finance.value.discountCost,
    description: '平台承担的券与满减优惠（flow_type = 4），计入毛利扣减项。',
  },
  {
    key: 'withdrawExpense',
    label: '提现支出',
    amount: finance.value.withdrawExpense,
    description: '商家、骑手提现成功形成的资金流出（flow_type = 5），仅展示，不参与毛利计算。',
  },
  {
    key: 'refundOffset',
    label: '退款冲抵',
    amount: finance.value.refundOffset,
    description: '退款产生的资金回冲（flow_type = 6），仅展示，不参与毛利计算。',
  },
  {
    key: 'grossProfit',
    label: '毛利',
    amount: finance.value.grossProfit,
    description: '佣金收入 − 补贴成本 − 优惠成本；结果可以为负。',
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
    finance.value = await getReportFinance({ dateFrom: range[0], dateTo: range[1] })
  } catch (error) {
    finance.value = { ...EMPTY_FINANCE }
    loadError.value = errorMessage(error, '财务报表加载失败')
  } finally {
    loading.value = false
  }
}

function reset(): void {
  filters.dateRange = defaultDateRange(DEFAULT_DAYS)
  void load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>财务报表</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/report/finance</span>。金额单位为分，
          按元展示；退款率为百分比数值。毛利口径
          <span class="mono">佣金收入 − 补贴成本 − 优惠成本</span>，为负时标红。
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
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
    </el-card>

    <el-row :gutter="12" v-loading="loading">
      <el-col
        v-for="item in financeItems"
        :key="item.key"
        :xs="12"
        :sm="8"
        :md="6"
        :lg="6"
      >
        <el-card shadow="never" class="metric-card">
          <div class="summary-label">{{ item.label }}</div>
          <div
            class="summary-value"
            :class="{ 'value-negative': item.key === 'grossProfit' && item.amount < 0 }"
          >
            {{ formatYuan(item.amount) }}
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="rate-card">
      <div>
        <div class="summary-label">退款率</div>
        <div class="rate-value">{{ formatPercent(finance.refundRate) }}</div>
      </div>
      <div class="muted rate-note">
        退款成功单数 ÷ 支付成功单数；后端返回百分比数值（10.00 表示 10.00%）。
      </div>
    </el-card>

    <el-card shadow="never">
      <div class="section-head">
        <h3>财务明细</h3>
        <span class="muted">金额单位：元</span>
      </div>

      <el-table v-loading="loading" :data="financeItems" border stripe row-key="key">
        <el-table-column prop="label" label="项目" width="140" />
        <el-table-column label="金额" min-width="160" align="right">
          <template #default="{ row }">
            <span
              class="amount"
              :class="{ 'value-negative': row.key === 'grossProfit' && row.amount < 0 }"
            >
              {{ formatYuan(row.amount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="360" />

        <template #empty>
          <el-empty description="当前筛选条件下没有财务数据。" :image-size="80" />
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

.metric-card {
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

.value-negative {
  color: var(--el-color-danger);
}

.rate-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
}

.rate-value {
  margin-top: 6px;
  font-size: 28px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.rate-note {
  font-size: 12px;
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

@media (max-width: 900px) {
  .date-range {
    width: 100%;
  }
}
</style>
