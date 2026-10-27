<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Back, Printer, Refresh } from '@element-plus/icons-vue'
import { getMerchantOrderDetail } from '@/api/orders'
import type { MerchantOrderDetailView } from '@/api/types'
import { orderStatusMeta } from '@/constants/orderStatus'
import { formatDateTime } from '@/utils/datetime'
import { formatYuan } from '@/utils/money'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const loadError = ref('')
const detail = ref<MerchantOrderDetailView | null>(null)

const orderId = computed(() => {
  const raw = route.params.orderId
  const value = Array.isArray(raw) ? raw[0] : raw
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
})

const isPickup = computed(() => detail.value?.fulfillmentType === 2)
const statusMeta = computed(() => {
  const meta = orderStatusMeta(detail.value?.status)
  // 后端已按 refund_status 纠正文案（全额退款 → 「已退款」），优先用它
  const name = detail.value?.statusName
  return name ? { ...meta, label: name } : meta
})

async function load(): Promise<void> {
  const id = orderId.value
  if (id === null) {
    loadError.value = '订单 ID 非法，请从订单列表重新进入'
    detail.value = null
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    detail.value = await getMerchantOrderDetail(id)
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '订单详情加载失败'
    detail.value = null
  } finally {
    loading.value = false
  }
}

function printReceipt(): void {
  window.print()
}

function goBack(): void {
  void router.push('/orders')
}

watch(orderId, () => {
  void load()
})

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead no-print">
      <div>
        <h2>接单小票</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/merchant/orders/{{ orderId ?? '—' }}</span>。
          金额单位为分，页面统一分转元展示；小票用于打印，不展示脱敏手机号以外的隐私字段。
        </p>
      </div>
      <div class="lead-actions">
        <el-button :icon="Back" @click="goBack">返回订单</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Printer" :disabled="!detail" @click="printReceipt">
          打印
        </el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-empty v-if="!loading && !detail && !loadError" description="没有可展示的订单" />

    <el-card v-if="detail" v-loading="loading" shadow="never" class="receipt">
      <div class="receipt-head">
        <div>
          <div class="receipt-title">数创星光外卖 · 接单小票</div>
          <div class="mono receipt-no">{{ detail.orderNo }}</div>
        </div>
        <div class="receipt-head-right">
          <el-tag :type="statusMeta.tag" size="large">{{ statusMeta.label }}</el-tag>
          <div class="muted fulfillment">{{ detail.fulfillmentTypeName }}</div>
        </div>
      </div>

      <div v-if="isPickup" class="pickup-block">
        <span class="pickup-label">取餐码</span>
        <span class="pickup-code mono">{{ detail.pickupCode || '—' }}</span>
        <span class="pickup-ready muted">
          备餐完成：{{ formatDateTime(detail.pickupReadyAt) }}
        </span>
      </div>

      <el-descriptions :column="2" border class="section">
        <el-descriptions-item label="下单时间">
          {{ formatDateTime(detail.createdAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="支付时间">
          {{ formatDateTime(detail.paidAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="顾客">
          {{ detail.receiverName || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="联系电话">
          {{ detail.receiverPhoneMask || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="地址" :span="2">
          {{ detail.receiverAddress || '—' }}
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.remark" label="备注" :span="2">
          {{ detail.remark }}
        </el-descriptions-item>
      </el-descriptions>

      <table class="goods-table">
        <thead>
          <tr>
            <th>商品</th>
            <th class="num">单价</th>
            <th class="num">数量</th>
            <th class="num">小计</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in detail.items" :key="item.orderItemId">
            <td>{{ item.skuName }}</td>
            <td class="num">{{ formatYuan(item.unitPrice) }}</td>
            <td class="num">{{ item.quantity }}</td>
            <td class="num">{{ formatYuan(item.payableAmount) }}</td>
          </tr>
          <tr v-if="!detail.items.length">
            <td colspan="4" class="empty-row">无商品明细</td>
          </tr>
        </tbody>
      </table>

      <div class="amounts">
        <div class="amount-row">
          <span>商品金额</span>
          <span class="amount-value">{{ formatYuan(detail.goodsAmount) }}</span>
        </div>
        <div class="amount-row">
          <span>打包费</span>
          <span class="amount-value">{{ formatYuan(detail.packagingFee) }}</span>
        </div>
        <div class="amount-row">
          <span>配送费</span>
          <span class="amount-value">{{ formatYuan(detail.deliveryFee) }}</span>
        </div>
        <div class="amount-row total">
          <span>实付</span>
          <span class="amount-value">{{ formatYuan(detail.payAmount) }}</span>
        </div>
      </div>

      <div v-if="detail.timeline.length" class="timeline no-print">
        <div class="card-title">订单时间轴</div>
        <el-timeline>
          <el-timeline-item
            v-for="(node, index) in detail.timeline"
            :key="`${node.toStatus}-${index}`"
            :timestamp="formatDateTime(node.createdAt)"
            placement="top"
          >
            {{ node.toStatusName }} · {{ node.event }}
            <span v-if="node.fromStatusName" class="muted">
              （由 {{ node.fromStatusName }} 变更）
            </span>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.lead-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.receipt {
  max-width: 820px;
}

.receipt-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px dashed var(--app-border);
}

.receipt-title {
  font-size: 18px;
  font-weight: 600;
}

.receipt-no {
  margin-top: 6px;
  font-size: 14px;
  letter-spacing: 0.5px;
}

.receipt-head-right {
  text-align: right;
}

.fulfillment {
  margin-top: 8px;
  font-size: 13px;
}

.pickup-block {
  margin: 16px 0;
  padding: 14px 18px;
  border-radius: 10px;
  background: var(--el-color-primary-light-9);
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
}

.pickup-label {
  font-size: 13px;
  color: var(--app-muted);
}

.pickup-code {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 4px;
  color: var(--el-color-primary);
}

.pickup-ready {
  font-size: 12px;
}

.section {
  margin: 16px 0;
}

.goods-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.goods-table th,
.goods-table td {
  border-bottom: 1px solid var(--app-border);
  padding: 10px 8px;
  text-align: left;
}

.goods-table th {
  background: #f4f9f8;
  font-weight: 600;
}

.goods-table .num {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.empty-row {
  text-align: center;
  color: var(--app-muted);
}

.amounts {
  margin-top: 16px;
  margin-left: auto;
  max-width: 300px;
  font-size: 13px;
}

.amount-row {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  color: var(--app-muted);
}

.amount-value {
  color: var(--app-ink);
  font-variant-numeric: tabular-nums;
}

.amount-row.total {
  margin-top: 6px;
  padding-top: 12px;
  border-top: 1px dashed var(--app-border);
  color: var(--app-ink);
  font-size: 16px;
  font-weight: 600;
}

.timeline {
  margin-top: 24px;
}

@media print {
  :global(.app-sidebar),
  :global(.app-header),
  .no-print {
    display: none !important;
  }

  :global(.app-content) {
    padding: 0 !important;
    max-width: none !important;
  }

  .receipt {
    border: none;
    box-shadow: none;
    max-width: none;
  }
}
</style>
