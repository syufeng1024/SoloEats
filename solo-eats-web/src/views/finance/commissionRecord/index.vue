<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  listCommissionRecords,
  type CommissionRecordView,
} from '@/api/finance'
import { RULE_SCOPE, labelOf } from '@/constants/financeStatus'
import { formatDateTime } from '@/utils/datetime'
import { formatYuan } from '@/utils/money'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const records = ref<CommissionRecordView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const filters = reactive({
  merchantId: undefined as number | undefined,
  categoryId: undefined as number | undefined,
  dateRange: null as string[] | null,
})

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const range = Array.isArray(filters.dateRange) ? filters.dateRange : []
    const result = await listCommissionRecords({
      merchantId:
        typeof filters.merchantId === 'number' ? filters.merchantId : undefined,
      categoryId:
        typeof filters.categoryId === 'number' ? filters.categoryId : undefined,
      dateFrom: range[0],
      dateTo: range[1],
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    records.value = result.list
    total.value = result.total
  } catch (error) {
    loadError.value = errorMessage(error, '抽佣明细加载失败')
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
  filters.categoryId = undefined
  filters.dateRange = null
  pageNum.value = 1
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

function rateText(rate: number | null | undefined): string {
  if (rate === null || rate === undefined || !Number.isFinite(Number(rate))) {
    return '—'
  }
  return `${(Number(rate) * 100).toFixed(2)}%`
}

function ruleText(row: CommissionRecordView): string {
  if (row.ruleId === null) {
    return '默认费率'
  }
  return `${labelOf(RULE_SCOPE, row.ruleScope)} #${row.ruleId}`
}

function openSettlementBill(row: CommissionRecordView): void {
  if (row.settlementBillId === null) {
    return
  }
  ElMessage.info(`结算单 #${row.settlementBillId} 详情页尚未接入`)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>抽佣明细</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/commission-records</span>。一单一条，
          <span class="mono">base_amount</span> 与 <span class="mono">commission_amount</span>
          单位为分。<span class="mono">rule_id</span> 为 NULL 表示未命中规则、按默认费率计算。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

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
          v-model="filters.categoryId"
          placeholder="品类 ID"
          :min="1"
          :precision="0"
          :controls="false"
          class="id-input"
        />
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="抽佣开始日期"
          end-placeholder="抽佣结束日期"
          class="date-range"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="records" border stripe row-key="id">
        <el-table-column prop="orderId" label="订单 ID" width="95" />
        <el-table-column label="商家" width="85">
          <template #default="{ row }">
            {{ row.merchantId === null ? '—' : row.merchantId }}
          </template>
        </el-table-column>
        <el-table-column label="门店" width="85">
          <template #default="{ row }">
            {{ row.shopId === null ? '—' : row.shopId }}
          </template>
        </el-table-column>
        <el-table-column label="品类" width="85">
          <template #default="{ row }">
            {{ row.categoryId === null ? '—' : row.categoryId }}
          </template>
        </el-table-column>
        <el-table-column label="抽佣基数" width="120" align="right">
          <template #default="{ row }">{{ formatYuan(row.baseAmount) }}</template>
        </el-table-column>
        <el-table-column label="命中规则" min-width="120">
          <template #default="{ row }">{{ ruleText(row) }}</template>
        </el-table-column>
        <el-table-column label="费率" width="90" align="right">
          <template #default="{ row }">{{ rateText(row.commissionRate) }}</template>
        </el-table-column>
        <el-table-column label="佣金金额" width="120" align="right">
          <template #default="{ row }">
            <span class="amount">{{ formatYuan(row.commissionAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结算单 ID" width="115">
          <template #default="{ row }">
            <el-link
              v-if="row.settlementBillId !== null"
              type="primary"
              :underline="false"
              @click="openSettlementBill(row)"
            >
              #{{ row.settlementBillId }}
            </el-link>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="抽佣时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有抽佣明细。" :image-size="80" />
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

.id-input {
  width: 120px;
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
