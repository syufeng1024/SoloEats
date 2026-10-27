<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  arrivePayout,
  auditPayout,
  listPayouts,
  payPayout,
  type PayoutView,
} from '@/api/finance'
import {
  PAYOUT_CHANNEL,
  PAYOUT_STATUS,
  PAYOUT_STATUS_OPTIONS,
  PAYOUT_SUBJECT,
  PAYOUT_SUBJECT_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/financeStatus'
import { formatDateTime } from '@/utils/datetime'
import { formatYuan } from '@/utils/money'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const payouts = ref<PayoutView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const filters = reactive({
  subjectType: undefined as number | undefined,
  status: undefined as number | undefined,
  dateRange: null as string[] | null,
})

const auditVisible = ref(false)
const auditTarget = ref<PayoutView | null>(null)
const auditPass = ref(true)
const auditRemark = ref('')
const auditing = ref(false)

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const range = Array.isArray(filters.dateRange) ? filters.dateRange : []
    const result = await listPayouts({
      subjectType:
        typeof filters.subjectType === 'number' ? filters.subjectType : undefined,
      status: typeof filters.status === 'number' ? filters.status : undefined,
      dateFrom: range[0],
      dateTo: range[1],
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    payouts.value = result.list
    total.value = result.total
  } catch (error) {
    loadError.value = errorMessage(error, '提现列表加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function reset(): void {
  filters.subjectType = undefined
  filters.status = undefined
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

function subjectText(row: PayoutView): string {
  return `${labelOf(PAYOUT_SUBJECT, row.subjectType)} #${row.subjectId}`
}

function openAudit(row: PayoutView): void {
  auditTarget.value = row
  auditPass.value = true
  auditRemark.value = ''
  auditVisible.value = true
}

async function submitAudit(): Promise<void> {
  const target = auditTarget.value
  if (!target) {
    return
  }
  auditing.value = true
  try {
    await auditPayout(target.id, {
      pass: auditPass.value,
      remark: auditRemark.value.trim() || null,
    })
    ElMessage.success(auditPass.value ? '提现单已审核通过' : '提现单已驳回')
    auditVisible.value = false
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹（否则同一条错误会弹两次）
  } finally {
    auditing.value = false
  }
}

async function confirmPay(row: PayoutView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认对提现单 ${row.payoutNo} 打款 ${formatYuan(row.amount)}？`,
      '确认打款',
      { confirmButtonText: '确认打款', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  try {
    await payPayout(row.id)
    ElMessage.success('已提交打款，状态更新为打款中')
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  }
}

async function confirmArrive(row: PayoutView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认提现单 ${row.payoutNo} 已完成到账登记？`,
      '登记到账',
      { confirmButtonText: '确认到账', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  try {
    await arrivePayout(row.id)
    ElMessage.success('已登记到账')
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>提现管理</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/payouts</span>。状态流转
          <span class="mono">0待审核 → 10审核通过 → 20打款中 → 30已到账</span>，驳回终态
          <span class="mono">40</span>。金额单位分，展示按元。
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
          v-model="filters.subjectType"
          placeholder="全部主体"
          clearable
          class="status-select"
        >
          <el-option
            v-for="option in PAYOUT_SUBJECT_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select
          v-model="filters.status"
          placeholder="全部状态"
          clearable
          class="status-select"
        >
          <el-option
            v-for="option in PAYOUT_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
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

      <el-table v-loading="loading" :data="payouts" border stripe row-key="id">
        <el-table-column prop="payoutNo" label="提现单号" min-width="180">
          <template #default="{ row }">
            <span class="mono">{{ row.payoutNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="主体" width="110">
          <template #default="{ row }">{{ subjectText(row) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">
            <span class="amount">{{ formatYuan(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="渠道" width="90">
          <template #default="{ row }">{{ labelOf(PAYOUT_CHANNEL, row.channel) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="105">
          <template #default="{ row }">
            <el-tag :type="tagOf(PAYOUT_STATUS, row.status)" size="small">
              {{ labelOf(PAYOUT_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="审核人 / 时间" width="150">
          <template #default="{ row }">
            <div>{{ row.auditBy === null ? '—' : `用户 #${row.auditBy}` }}</div>
            <div class="muted sub">{{ formatDateTime(row.auditAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="打款时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.paidAt) }}</template>
        </el-table-column>
        <el-table-column label="审核备注" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.auditRemark || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              link
              type="primary"
              @click="openAudit(row)"
            >
              审核
            </el-button>
            <el-button
              v-else-if="row.status === 10"
              link
              type="primary"
              @click="confirmPay(row)"
            >
              确认打款
            </el-button>
            <el-button
              v-else-if="row.status === 20"
              link
              type="primary"
              @click="confirmArrive(row)"
            >
              登记到账
            </el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="当前筛选条件下没有提现单。" :image-size="80" />
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

    <el-dialog
      v-model="auditVisible"
      title="提现审核"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form label-width="90px">
        <el-form-item label="提现单">
          <span class="mono">{{ auditTarget?.payoutNo ?? '—' }}</span>
        </el-form-item>
        <el-form-item label="金额">
          <span class="amount">{{ formatYuan(auditTarget?.amount) }}</span>
        </el-form-item>
        <el-form-item label="审核结果" required>
          <el-radio-group v-model="auditPass">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="auditRemark"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            :placeholder="auditPass ? '可填写审核说明' : '驳回原因将写入 audit_remark'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" :loading="auditing" @click="submitAudit">
          提交审核
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filter-bar {
  margin-bottom: 14px;
}

.status-select {
  width: 150px;
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

@media (max-width: 900px) {
  .date-range {
    width: 100%;
  }
}
</style>
