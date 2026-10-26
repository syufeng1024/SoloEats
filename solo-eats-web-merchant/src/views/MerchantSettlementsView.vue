<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import {
  getMerchantSettlementDetail,
  listMerchantSettlements,
} from '@/api/settlements'
import type {
  SettlementBillView,
  SettlementDetailResult,
  SettlementSummary,
} from '@/api/types'
import {
  SETTLEMENT_STATUS_OPTIONS,
  settlementStatusMeta,
} from '@/constants/settlementStatus'
import { formatDateTime } from '@/utils/datetime'
import { debounce } from '@/utils/debounce'
import { formatYuan } from '@/utils/money'

const AUTH_REQUIRED_MESSAGE =
  '当前登录态缺少 merchantId，无法查询结算账单。请退出后使用商家角色重新登录。'

const ITEM_TYPE_LABELS: Record<number, string> = {
  1: '商品',
  2: '佣金',
  3: '履约费',
  4: '退款',
  5: '调整',
}

const auth = useAuthStore()

const loading = ref(false)
const loadError = ref('')
const bills = ref<SettlementBillView[]>([])
const summary = ref<SettlementSummary>({
  billCount: 0,
  grossAmount: 0,
  merchantAmount: 0,
  platformCommission: 0,
  payableAmount: 0,
})

const statusFilter = ref<number | undefined>(undefined)
const keyword = ref('')
const appliedKeyword = ref('')

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<SettlementDetailResult | null>(null)

watch(
  keyword,
  debounce((value: string) => {
    appliedKeyword.value = value
  }, 300),
)

const filteredBills = computed(() => {
  const query = appliedKeyword.value.trim().toLowerCase()
  if (!query) {
    return bills.value
  }
  return bills.value.filter((bill) => bill.billNo.toLowerCase().includes(query))
})

async function loadBills(): Promise<void> {
  const merchantId = auth.merchantId
  if (merchantId === null) {
    loadError.value = AUTH_REQUIRED_MESSAGE
    ElMessage.error('登录态缺少 merchantId')
    return
  }

  loading.value = true
  loadError.value = ''
  try {
    const status = typeof statusFilter.value === 'number' ? statusFilter.value : undefined
    const result = await listMerchantSettlements(merchantId, status)
    bills.value = result.bills
    summary.value = result.summary
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '结算账单加载失败'
  } finally {
    loading.value = false
  }
}

async function openDetail(bill: SettlementBillView): Promise<void> {
  const merchantId = auth.merchantId
  if (merchantId === null) {
    ElMessage.error('登录态缺少 merchantId')
    return
  }

  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getMerchantSettlementDetail(bill.billId, merchantId)
  } catch {
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

function itemTypeLabel(itemType: number): string {
  return ITEM_TYPE_LABELS[itemType] ?? `类型 ${itemType}`
}

onMounted(loadBills)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>财务对账与结算账单</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/merchant/settlements?merchantId={{ auth.merchantId ?? '?' }}&amp;status=</span>。
          金额单位为分，页面统一按元展示；账单由平台侧每日 02:00 定时任务或管理端手动生成。
        </p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadBills">刷新</el-button>
    </div>

    <el-alert
      v-if="loadError"
      type="error"
      :closable="false"
      show-icon
      :title="loadError"
    />

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-label">账单数</div>
        <div class="stat-value">
          {{ summary.billCount }}<span class="stat-unit">笔</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">商品 + 打包总额</div>
        <div class="stat-value">{{ formatYuan(summary.grossAmount) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">商家应结</div>
        <div class="stat-value">{{ formatYuan(summary.merchantAmount) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">平台佣金</div>
        <div class="stat-value">{{ formatYuan(summary.platformCommission) }}</div>
      </div>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span class="card-title">账单列表</span>
          <div class="toolbar">
            <el-select
              v-model="statusFilter"
              placeholder="全部状态"
              clearable
              class="status-select"
              @change="loadBills"
            >
              <el-option
                v-for="option in SETTLEMENT_STATUS_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-input
              v-model="keyword"
              class="keyword-input"
              placeholder="按账单号过滤（300ms 防抖）"
              clearable
              :prefix-icon="Search"
            />
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="filteredBills" border>
        <el-table-column prop="billNo" label="账单号" min-width="200">
          <template #default="{ row }">
            <span class="mono">{{ row.billNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="billDate" label="账单日期" width="110" />
        <el-table-column label="商品+打包" width="110" align="right">
          <template #default="{ row }">{{ formatYuan(row.grossAmount) }}</template>
        </el-table-column>
        <el-table-column label="佣金" width="100" align="right">
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
        <el-table-column label="生成时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openDetail(row)">明细</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty
            description="暂无结算账单。管理员执行 POST /api/v1/admin/settlements/generate 后会出现在这里。"
            :image-size="80"
          />
        </template>
      </el-table>

      <p class="table-note">
        后端列表接口不分页，返回该商家全部账单；账单号过滤在浏览器内完成，不会重复请求接口。
      </p>
    </el-card>

    <el-drawer v-model="detailVisible" title="结算账单明细" size="760px">
      <div v-loading="detailLoading" class="drawer-body">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="账单号" :span="2">
              <span class="mono">{{ detail.bill.billNo }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="账单日期">{{ detail.bill.billDate }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="settlementStatusMeta(detail.bill.status).tag" size="small">
                {{ settlementStatusMeta(detail.bill.status).label }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="商品+打包">
              {{ formatYuan(detail.bill.grossAmount) }}
            </el-descriptions-item>
            <el-descriptions-item label="商家承担优惠">
              {{ formatYuan(detail.bill.merchantDiscount) }}
            </el-descriptions-item>
            <el-descriptions-item label="平台承担优惠">
              {{ formatYuan(detail.bill.platformDiscount) }}
            </el-descriptions-item>
            <el-descriptions-item label="佣金基数">
              {{ formatYuan(detail.bill.commissionBase) }}
            </el-descriptions-item>
            <el-descriptions-item label="平台佣金">
              {{ formatYuan(detail.bill.platformCommission) }}
            </el-descriptions-item>
            <el-descriptions-item label="商家应结">
              {{ formatYuan(detail.bill.merchantAmount) }}
            </el-descriptions-item>
          </el-descriptions>

          <el-tabs class="detail-tabs">
            <el-tab-pane :label="`关联订单（${detail.orders.length}）`">
              <el-table :data="detail.orders" size="small" border>
                <el-table-column prop="orderNo" label="订单号" min-width="190">
                  <template #default="{ row }">
                    <span class="mono">{{ row.orderNo }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="用户实付" width="110" align="right">
                  <template #default="{ row }">{{ formatYuan(row.payAmount) }}</template>
                </el-table-column>
                <el-table-column label="佣金" width="100" align="right">
                  <template #default="{ row }">{{ formatYuan(row.commissionAmount) }}</template>
                </el-table-column>
                <el-table-column label="商家金额" width="110" align="right">
                  <template #default="{ row }">{{ formatYuan(row.merchantAmount) }}</template>
                </el-table-column>
                <el-table-column label="完成时间" width="170">
                  <template #default="{ row }">{{ formatDateTime(row.completedAt) }}</template>
                </el-table-column>
                <template #empty>
                  <el-empty description="该账单没有关联订单" :image-size="60" />
                </template>
              </el-table>
            </el-tab-pane>

            <el-tab-pane :label="`结算明细（${detail.items.length}）`">
              <el-table :data="detail.items" size="small" border>
                <el-table-column label="类型" width="100">
                  <template #default="{ row }">{{ itemTypeLabel(row.itemType) }}</template>
                </el-table-column>
                <el-table-column prop="orderId" label="订单 ID" width="110" />
                <el-table-column label="金额" width="120" align="right">
                  <template #default="{ row }">{{ formatYuan(row.amount) }}</template>
                </el-table-column>
                <el-table-column prop="remark" label="备注" min-width="220" />
                <template #empty>
                  <el-empty description="该账单没有结算明细" :image-size="60" />
                </template>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </template>

        <el-empty v-else-if="!detailLoading" description="明细加载失败" :image-size="70" />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.card-head .card-title {
  margin-bottom: 0;
}

.status-select {
  width: 140px;
}

.keyword-input {
  width: 260px;
}

.table-note {
  margin-top: 12px;
  color: var(--app-muted);
  font-size: 12px;
  line-height: 1.6;
}

.drawer-body {
  min-height: 240px;
}

.detail-tabs {
  margin-top: 18px;
}

@media (max-width: 720px) {
  .keyword-input {
    width: 100%;
  }
}
</style>
