<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useShopStore } from '@/stores/shop'
import {
  acceptOrder,
  callRider,
  cancelMerchantOrder,
  listMerchantOrders,
  markOrderPrepared,
  rejectOrder,
} from '@/api/orders'
import type { MerchantOrderView } from '@/api/types'
import { orderStatusMeta } from '@/constants/orderStatus'
import { formatDateTime } from '@/utils/datetime'
import { debounce } from '@/utils/debounce'
import { formatYuan } from '@/utils/money'

const STATUS_TABS = [
  { name: 'all', label: '全部', status: undefined },
  { name: 'wait_accept', label: '待接单', status: 10 },
  { name: 'preparing', label: '备餐中', status: 20 },
  { name: 'wait_rider', label: '待骑手取货', status: 30 },
  { name: 'wait_user', label: '待用户取餐', status: 31 },
  { name: 'delivering', label: '配送中', status: 40 },
  { name: 'completed', label: '已完成', status: 60 },
  { name: 'cancelled', label: '已取消', status: 70 },
] as const

const auth = useAuthStore()
const shop = useShopStore()
const router = useRouter()

const loading = ref(false)
const loadError = ref('')
const orders = ref<MerchantOrderView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const activeTab = ref<string>('all')
const statusFilter = ref<number | undefined>(undefined)
const keyword = ref('')
const appliedKeyword = ref('')
const actionKey = ref('')

const reasonVisible = ref(false)
const reasonMode = ref<'reject' | 'cancel'>('reject')
const reasonText = ref('')
const reasonOrderId = ref<number | null>(null)
const submittingReason = ref(false)

watch(
  keyword,
  debounce((value: string) => {
    appliedKeyword.value = value
    pageNum.value = 1
    void load()
  }, 300),
)

watch(
  () => shop.currentShopId,
  () => {
    pageNum.value = 1
    void load()
  },
)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listMerchantOrders({
      shopId: shop.currentShopId ?? auth.shopId ?? undefined,
      status: statusFilter.value,
      keyword: appliedKeyword.value.trim() || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    orders.value = result.list
    total.value = result.total
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '订单加载失败'
  } finally {
    loading.value = false
  }
}

function handleTabChange(name: string | number): void {
  const tab = STATUS_TABS.find((item) => item.name === String(name))
  statusFilter.value = tab?.status
  pageNum.value = 1
  void load()
}

function handlePageChange(page: number): void {
  pageNum.value = page
  void load()
}

function fulfillmentLabel(type: number): string {
  return type === 1 ? '配送' : '自取'
}

function itemSummary(row: MerchantOrderView): string {
  if (!row.items.length) {
    return '—'
  }
  const first = row.items[0]
  const count = row.items.reduce((sum, item) => sum + item.quantity, 0)
  if (row.items.length === 1) {
    return `${first.skuName} ×${first.quantity}`
  }
  return `${first.skuName} 等 ${row.items.length} 种 / ${count} 件`
}

function openReceipt(row: MerchantOrderView): void {
  void router.push(`/orders/${row.orderId}/receipt`)
}

async function handleAccept(row: MerchantOrderView): Promise<void> {
  actionKey.value = `accept-${row.orderId}`
  try {
    await acceptOrder(row.orderId)
    ElMessage.success(`订单 ${row.orderNo} 已接单`)
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    actionKey.value = ''
  }
}

async function handlePrepared(row: MerchantOrderView): Promise<void> {
  actionKey.value = `prepared-${row.orderId}`
  try {
    await markOrderPrepared(row.orderId)
    ElMessage.success(`订单 ${row.orderNo} 已置为备餐完成`)
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    actionKey.value = ''
  }
}

async function handleCallRider(row: MerchantOrderView): Promise<void> {
  actionKey.value = `rider-${row.orderId}`
  try {
    const result = await callRider(row.orderId)
    ElMessage.success(
      `已派单 ${result.deliveryNo}，第 ${result.dispatchCount} 次，有效期至 ${formatDateTime(result.dispatchExpireAt)}`,
    )
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    actionKey.value = ''
  }
}

function openReason(mode: 'reject' | 'cancel', row: MerchantOrderView): void {
  reasonMode.value = mode
  reasonOrderId.value = row.orderId
  reasonText.value = ''
  reasonVisible.value = true
}

async function submitReason(): Promise<void> {
  const orderId = reasonOrderId.value
  if (orderId === null) {
    return
  }
  const reason = reasonText.value.trim()
  if (!reason) {
    ElMessage.warning('必须填写原因')
    return
  }

  submittingReason.value = true
  try {
    if (reasonMode.value === 'reject') {
      await rejectOrder(orderId, reason)
      ElMessage.success('拒单已提交，退款流程已触发')
    } else {
      await cancelMerchantOrder(orderId, reason)
      ElMessage.success('取消请求已提交')
    }
    reasonVisible.value = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    submittingReason.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>实时订单管理</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/merchant/orders</span>，
          merchantId 由 JWT 读取，门店 ID {{ shop.currentShopId ?? auth.shopId ?? '未绑定' }} 参与过滤。
          订单状态流转仍由状态机与退款服务校验。
        </p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane
          v-for="tab in STATUS_TABS"
          :key="tab.name"
          :label="tab.label"
          :name="tab.name"
        />
      </el-tabs>

      <div class="toolbar list-toolbar">
        <el-input
          v-model="keyword"
          class="keyword-input"
          placeholder="订单号 / 收货人 / 手机号（300ms 防抖）"
          clearable
          :prefix-icon="Search"
        />
        <span class="muted">共 {{ total }} 笔</span>
        <div class="toolbar-spacer" />
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="orders" border row-key="orderId">
        <el-table-column
          prop="orderNo"
          label="订单号"
          width="165"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span class="mono">{{ row.orderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="orderStatusMeta(row.status).tag" size="small">
              {{ orderStatusMeta(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="商品 / 履约" min-width="180">
          <template #default="{ row }">
            <div class="goods-cell">
              <el-tag type="info" size="small" effect="plain">
                {{ fulfillmentLabel(row.fulfillmentType) }}
              </el-tag>
              <span class="goods-text">{{ itemSummary(row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="实付" width="90" align="right">
          <template #default="{ row }">{{ formatYuan(row.payAmount) }}</template>
        </el-table-column>
        <el-table-column label="收货人" min-width="120">
          <template #default="{ row }">
            <div>{{ row.receiverName || '—' }}</div>
            <div class="mono muted">{{ row.receiverPhone || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="145">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <template v-if="row.status === 10">
                <el-button
                  link
                  type="primary"
                  :loading="actionKey === `accept-${row.orderId}`"
                  @click="handleAccept(row)"
                >
                  接单
                </el-button>
                <el-button link type="danger" @click="openReason('reject', row)">
                  拒单
                </el-button>
              </template>
              <template v-else-if="row.status === 20">
                <el-button
                  link
                  type="primary"
                  :loading="actionKey === `prepared-${row.orderId}`"
                  @click="handlePrepared(row)"
                >
                  备餐完成
                </el-button>
                <el-button
                  v-if="row.fulfillmentType === 1"
                  link
                  type="primary"
                  :loading="actionKey === `rider-${row.orderId}`"
                  @click="handleCallRider(row)"
                >
                  呼叫骑手
                </el-button>
                <el-button link type="warning" @click="openReason('cancel', row)">
                  取消
                </el-button>
              </template>
              <template v-else-if="row.status === 30">
                <el-button
                  link
                  type="primary"
                  :loading="actionKey === `rider-${row.orderId}`"
                  @click="handleCallRider(row)"
                >
                  再次呼叫骑手
                </el-button>
              </template>
              <span v-else class="muted">—</span>
              <el-button link type="primary" @click="openReceipt(row)">小票</el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty
            description="当前筛选条件下没有订单。订单进入 WAIT_ACCEPT(10) 后会出现在这里。"
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

    <el-dialog
      v-model="reasonVisible"
      :title="reasonMode === 'reject' ? '拒单并触发退款' : '商家取消订单'"
      width="440px"
      :close-on-click-modal="false"
    >
      <p class="dialog-hint">
        原因会写入退款/取消流程，长度上限 128 字符。
      </p>
      <el-input
        v-model="reasonText"
        type="textarea"
        :rows="3"
        maxlength="128"
        show-word-limit
        placeholder="例如：食材售罄，无法出餐"
      />
      <template #footer>
        <el-button @click="reasonVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingReason" @click="submitReason">
          确认提交
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.list-toolbar {
  margin-bottom: 14px;
}

.keyword-input {
  width: 320px;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  align-items: center;
}

.goods-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.goods-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.dialog-hint {
  color: var(--app-muted);
  font-size: 13px;
  line-height: 1.7;
  margin-bottom: 12px;
}

@media (max-width: 720px) {
  .keyword-input {
    width: 100%;
  }
}
</style>
