<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  listMemberWalletLogs,
  type WalletLogView,
} from '@/api/member-admin'
import {
  WALLET_BIZ_TYPE_OPTIONS,
  WALLET_DIRECTION,
  labelOf,
  labelOfBizType,
  tagOf,
} from '@/constants/memberStatus'
import { errorMessage } from '@/views/report/helpers'
import { formatDateTime } from '@/utils/datetime'
import { formatYuan } from '@/utils/money'
import WalletAdjustDialog from '../components/WalletAdjustDialog.vue'

/**
 * 后端没有「全站钱包列表」与独立的钱包概览接口：
 * - GET /admin/members 只返回会员资料，不含余额；
 * - WalletView 仅由调账响应返回，无法在不调账的情况下读取；
 * - 只有 GET /admin/members/{id}/wallet/logs 可按用户查流水。
 * 因此本页必须先指定用户，汇总卡口径见页面提示，不做全站伪造列表。
 */
const LOG_PAGE_SIZE_OPTIONS = [10, 20, 50]

const userIdInput = ref<number | undefined>(undefined)
const queriedUserId = ref<number | null>(null)

const filters = reactive<{
  bizType: string | undefined
  dateRange: string[] | null
}>({
  bizType: undefined,
  dateRange: null,
})

const loading = ref(false)
const loadError = ref('')
const logs = ref<WalletLogView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const adjustVisible = ref(false)

const pageIncome = computed(() =>
  logs.value
    .filter((log) => log.direction === 1)
    .reduce((sum, log) => sum + log.amount, 0),
)

const pageExpense = computed(() =>
  logs.value
    .filter((log) => log.direction === 2)
    .reduce((sum, log) => sum + log.amount, 0),
)

/** 流水按 createdAt/id 倒序，第一页首条即最近一笔；非首页不能代表当前余额。 */
const latestBalance = computed<number | null>(() => {
  if (pageNum.value !== 1 || logs.value.length === 0) {
    return null
  }
  return logs.value[0].afterBalance
})

const summaryCards = computed(() => [
  {
    key: 'balance',
    label: '当前余额',
    value: formatYuan(latestBalance.value),
    hint:
      pageNum.value === 1
        ? '取第 1 页首条流水的 after_balance'
        : '非第 1 页，回到首页查看',
  },
  {
    key: 'income',
    label: '收入合计（当前页）',
    value: formatYuan(pageIncome.value),
    hint: `第 ${pageNum.value} 页 ${logs.value.length} 条流水中 direction=1`,
  },
  {
    key: 'expense',
    label: '支出合计（当前页）',
    value: formatYuan(pageExpense.value),
    hint: `第 ${pageNum.value} 页 ${logs.value.length} 条流水中 direction=2`,
  },
])

async function load(): Promise<void> {
  const userId = queriedUserId.value
  if (userId === null) {
    return
  }
  loading.value = true
  loadError.value = ''
  const range = Array.isArray(filters.dateRange) ? filters.dateRange : []
  try {
    const result = await listMemberWalletLogs(userId, {
      bizType: filters.bizType || undefined,
      dateFrom: range[0],
      dateTo: range[1],
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    logs.value = result.list
    total.value = result.total
  } catch (error) {
    logs.value = []
    total.value = 0
    loadError.value = errorMessage(error, '钱包流水加载失败')
  } finally {
    loading.value = false
  }
}

function queryUser(): void {
  const userId = Number(userIdInput.value)
  if (!Number.isInteger(userId) || userId < 1) {
    ElMessage.warning('请输入有效的用户 ID')
    return
  }
  queriedUserId.value = userId
  pageNum.value = 1
  void load()
}

function applyFilters(): void {
  if (queriedUserId.value === null) {
    ElMessage.warning('请先输入用户 ID 并查询')
    return
  }
  pageNum.value = 1
  void load()
}

function resetFilters(): void {
  filters.bizType = undefined
  filters.dateRange = null
  applyFilters()
}

function resetAll(): void {
  userIdInput.value = undefined
  queriedUserId.value = null
  filters.bizType = undefined
  filters.dateRange = null
  logs.value = []
  total.value = 0
  pageNum.value = 1
  loadError.value = ''
}

function refresh(): void {
  if (queriedUserId.value === null) {
    ElMessage.warning('请先输入用户 ID 并查询')
    return
  }
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

function signedAmount(log: WalletLogView): string {
  return `${log.direction === 1 ? '+' : '-'}${formatYuan(log.amount)}`
}

function amountClass(log: WalletLogView): string {
  return log.direction === 1 ? 'amount-in' : 'amount-out'
}

async function handleAdjustSuccess(): Promise<void> {
  pageNum.value = 1
  await load()
}
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>钱包账单</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/members/{id}/wallet/logs</span>。
          后端未提供全站钱包列表与钱包概览接口，本页必须先指定用户 ID；
          汇总卡只按当前页流水计算，不是全量累计。
        </p>
      </div>
      <div class="toolbar">
        <el-button
          :icon="Refresh"
          :loading="loading"
          :disabled="queriedUserId === null"
          @click="refresh"
        >
          刷新
        </el-button>
        <el-button
          type="primary"
          :disabled="queriedUserId === null"
          @click="adjustVisible = true"
        >
          调账
        </el-button>
      </div>
    </div>

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-input-number
          v-model="userIdInput"
          placeholder="用户 ID"
          :min="1"
          :precision="0"
          :controls="false"
          class="user-input"
        />
        <el-button type="primary" @click="queryUser">查询</el-button>
        <el-button @click="resetAll">清空</el-button>
        <span class="muted hint">用户 ID 为 t_member_user.id</span>
      </div>

      <el-empty
        v-if="queriedUserId === null"
        description="请先输入用户 ID 并查询，后端没有全站钱包列表接口。"
        :image-size="90"
      />

      <template v-else>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="summary-note"
          title="口径说明：后端 WalletView 只随调账响应返回，无法单独读取；当前余额取最近一笔流水的 after_balance，收入/支出为本页流水合计，切换分页会变化。"
        />

        <el-row :gutter="12">
          <el-col v-for="card in summaryCards" :key="card.key" :xs="24" :sm="8">
            <el-card shadow="never" class="metric-card">
              <div class="summary-label">{{ card.label }}</div>
              <div class="summary-value">{{ card.value }}</div>
              <div class="summary-hint">{{ card.hint }}</div>
            </el-card>
          </el-col>
        </el-row>

        <el-alert
          v-if="loadError"
          type="error"
          :closable="false"
          show-icon
          :title="loadError"
        />

        <div class="toolbar filter-bar">
          <el-select
            v-model="filters.bizType"
            placeholder="全部流水类型"
            clearable
            class="type-select"
          >
            <el-option
              v-for="option in WALLET_BIZ_TYPE_OPTIONS"
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
            unlink-panels
            class="date-range"
          />
          <el-button type="primary" @click="applyFilters">查询流水</el-button>
          <el-button @click="resetFilters">重置筛选</el-button>
        </div>

        <el-table v-loading="loading" :data="logs" border stripe row-key="id">
          <el-table-column label="时间" width="155">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="类型" width="110">
            <template #default="{ row }">{{ labelOfBizType(row.bizType) }}</template>
          </el-table-column>
          <el-table-column label="方向" width="80">
            <template #default="{ row }">
              <el-tag :type="tagOf(WALLET_DIRECTION, row.direction)" size="small">
                {{ labelOf(WALLET_DIRECTION, row.direction) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="金额" width="120" align="right">
            <template #default="{ row }">
              <span :class="['amount', amountClass(row)]">{{ signedAmount(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="变动后余额" width="130" align="right">
            <template #default="{ row }">
              <span class="amount">{{ formatYuan(row.afterBalance) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="关联单号" width="110">
            <template #default="{ row }">
              <span class="mono">{{ row.bizId === null ? '—' : `#${row.bizId}` }}</span>
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.remark || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作人" width="110">
            <template #default="{ row }">{{ row.operatorName || '—' }}</template>
          </el-table-column>

          <template #empty>
            <el-empty description="该用户暂无符合条件的钱包流水。" :image-size="80" />
          </template>
        </el-table>

        <el-pagination
          class="pagination"
          layout="total, sizes, prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :page-sizes="LOG_PAGE_SIZE_OPTIONS"
          :current-page="pageNum"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </template>
    </el-card>

    <WalletAdjustDialog
      v-model:visible="adjustVisible"
      :user-id="queriedUserId"
      @success="handleAdjustSuccess"
    />
  </div>
</template>

<style scoped>
.filter-bar {
  margin-bottom: 14px;
}

.user-input {
  width: 150px;
}

.hint {
  font-size: 12px;
}

.type-select {
  width: 160px;
}

.date-range {
  width: 280px;
}

.summary-note {
  margin-bottom: 12px;
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
  font-size: 26px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.summary-hint {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.amount {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.amount-in {
  color: var(--el-color-success);
}

.amount-out {
  color: var(--el-color-danger);
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
