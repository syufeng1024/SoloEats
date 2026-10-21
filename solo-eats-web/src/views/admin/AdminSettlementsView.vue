<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Money, Refresh } from '@element-plus/icons-vue'
import { generateSettlements } from '@/api/admin'
import { getAdminSettlementStats } from '@/api/settlements'
import type { AdminSettlementSummary, SettlementBillView } from '@/api/types'
import {
  SETTLEMENT_STATUS_OPTIONS,
  settlementStatusMeta,
} from '@/constants/settlementStatus'
import { formatDateTime } from '@/utils/datetime'
import { formatYuan } from '@/utils/money'

const EMPTY_SUMMARY: AdminSettlementSummary = {
  billCount: 0,
  grossAmount: 0,
  merchantDiscount: 0,
  platformDiscount: 0,
  commissionBase: 0,
  platformCommission: 0,
  performanceFee: 0,
  refundAmount: 0,
  payableAmount: 0,
  paidAmount: 0,
  paidCount: 0,
  pendingCount: 0,
}

const loading = ref(false)
const generating = ref(false)
const loadError = ref('')
const bills = ref<SettlementBillView[]>([])
const summary = ref<AdminSettlementSummary>({ ...EMPTY_SUMMARY })
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const filters = reactive({
  merchantId: undefined as number | undefined,
  status: undefined as number | undefined,
  dateRange: null as string[] | null,
})

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const range = Array.isArray(filters.dateRange) ? filters.dateRange : []
    const result = await getAdminSettlementStats({
      merchantId: typeof filters.merchantId === 'number' ? filters.merchantId : undefined,
      status: typeof filters.status === 'number' ? filters.status : undefined,
      dateFrom: range[0],
      dateTo: range[1],
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    bills.value = result.bills
    summary.value = result.summary
    total.value = result.total
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '结算统计加载失败'
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
  filters.status = undefined
  filters.dateRange = null
  pageNum.value = 1
  void load()
}

function handlePageChange(page: number): void {
  pageNum.value = page
  void load()
}

async function handleGenerate(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      '将归集当前所有 COMPLETED、已过结算等待期、未退款且 settlement_status=0 的订单，按商家生成当日账单。同一商家同一天只会生成一张账单。',
      '生成当日结算账单',
      {
        confirmButtonText: '执行生成',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  generating.value = true
  try {
    const result = await generateSettlements()
    ElMessage.success(
      `生成完成：${result.billCount} 张账单，覆盖 ${result.orderCount} 笔订单`,
    )
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    generating.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>平台抽成与结算统计</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/settlements/stats</span>。
          汇总与账单列表使用同一组筛选条件，金额单位为分。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Money" :loading="generating" @click="handleGenerate">
          生成当日账单
        </el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-label">账单数</div>
        <div class="stat-value">{{ summary.billCount }}<span class="stat-unit">张</span></div>
      </div>
      <div class="stat-card">
        <div class="stat-label">商品 + 打包</div>
        <div class="stat-value">{{ formatYuan(summary.grossAmount) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">平台佣金</div>
        <div class="stat-value">{{ formatYuan(summary.platformCommission) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">商家应结</div>
        <div class="stat-value">{{ formatYuan(summary.payableAmount) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已打款</div>
        <div class="stat-value">
          {{ formatYuan(summary.paidAmount) }}
          <span class="stat-unit">/ {{ summary.paidCount }} 张</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">待处理账单</div>
        <div class="stat-value">{{ summary.pendingCount }}<span class="stat-unit">张</span></div>
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
        <el-select
          v-model="filters.status"
          placeholder="全部状态"
          clearable
          class="status-select"
        >
          <el-option
            v-for="option in SETTLEMENT_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="账单开始日期"
          end-placeholder="账单结束日期"
          class="date-range"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="bills" border row-key="billId">
        <el-table-column prop="billNo" label="账单号" min-width="190">
          <template #default="{ row }">
            <span class="mono">{{ row.billNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="merchantId" label="商家 ID" width="80" />
        <el-table-column prop="billDate" label="账单日期" width="105" />
        <el-table-column label="商品+打包" width="105" align="right">
          <template #default="{ row }">{{ formatYuan(row.grossAmount) }}</template>
        </el-table-column>
        <el-table-column label="平台佣金" width="100" align="right">
          <template #default="{ row }">{{ formatYuan(row.platformCommission) }}</template>
        </el-table-column>
        <el-table-column label="商家应结" width="110" align="right">
          <template #default="{ row }">{{ formatYuan(row.merchantAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="settlementStatusMeta(row.status).tag" size="small">
              {{ settlementStatusMeta(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生成时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>

        <template #empty>
          <el-empty
            description="当前筛选条件下没有结算账单。可点击右上角生成当日账单。"
            :image-size="80"
          />
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

    <el-card shadow="never">
      <template #header>
        <span class="card-title">佣金计算口径</span>
      </template>
      <el-table
        :data="[
          {
            step: '佣金基数',
            formula: 'goods_amount + packaging_fee - merchant_discount',
            note: '不含配送费；平台承担优惠不冲减基数',
          },
          {
            step: '平台佣金',
            formula: 'round(佣金基数 × commissionRate)',
            note: '默认 0.0600，对应 app.settlement.default-commission-rate',
          },
          {
            step: '商家应结',
            formula: 'goods_amount + packaging_fee - merchant_discount + platform_discount - 平台佣金',
            note: '写入 t_settlement_bill.payable_amount',
          },
          {
            step: '幂等约束',
            formula: 'UNIQUE KEY uk_merchant_date (merchant_id, bill_date)',
            note: '重复生成会跳过该商家，不覆盖已有账单',
          },
        ]"
        size="small"
        border
      >
        <el-table-column prop="step" label="步骤" width="120" />
        <el-table-column prop="formula" label="公式 / 约束" min-width="360">
          <template #default="{ row }">
            <span class="mono">{{ row.formula }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="note" label="说明" min-width="280" />
      </el-table>
      <p class="card-note">
        计算逻辑来自 <span class="mono">SettlementServiceImpl.generateForMerchant</span> 与
        <span class="mono">V1__init_schema.sql</span>，金额全部为分，四舍五入使用
        <span class="mono">RoundingMode.HALF_UP</span>。
      </p>
    </el-card>
  </div>
</template>

<style scoped>
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
  width: 280px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.card-note {
  margin-top: 12px;
  color: var(--app-muted);
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .date-range {
    width: 100%;
  }
}
</style>
