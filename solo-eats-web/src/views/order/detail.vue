<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import { getAdminOrder, type AdminOrderDetailView } from '@/api/order-admin'
import {
  EXCEPTION_TYPE,
  FULFILLMENT_TYPE,
  OPERATOR_TYPE,
  ORDER_REFUND_STATUS,
  PAYMENT_STATUS,
  REFUND_TYPE,
  SETTLEMENT_STATUS,
  labelOf,
  tagOf,
} from '@/constants/orderAdmin'
import { orderStatusMeta } from '@/constants/orderStatus'
import { formatDateTime } from '@/utils/datetime'
import { formatYuan } from '@/utils/money'

const route = useRoute()
const router = useRouter()

const rawOrderId = route.params.orderId
const parsedOrderId = Number(Array.isArray(rawOrderId) ? rawOrderId[0] : rawOrderId)
const orderId = Number.isInteger(parsedOrderId) && parsedOrderId > 0 ? parsedOrderId : null

const loading = ref(false)
const loadError = ref('')
const detail = ref<AdminOrderDetailView | null>(null)

const isPickup = computed(() => detail.value?.fulfillmentType === 2)

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

async function load(): Promise<void> {
  if (orderId === null) {
    loadError.value = '订单 ID 不合法'
    detail.value = null
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    detail.value = await getAdminOrder(orderId)
  } catch (error) {
    detail.value = null
    loadError.value = errorMessage(error, '订单详情加载失败')
  } finally {
    loading.value = false
  }
}

function backToList(): void {
  void router.push({ name: 'admin-order-list' })
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>订单详情</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/orders/{orderId}</span>。手机号由后端脱敏；
          时间轴来自 <span class="mono">t_order_status_log</span>。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="ArrowLeft" @click="backToList">返回列表</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <div v-loading="loading">
      <template v-if="detail">
        <el-card shadow="never" class="section">
          <template #header>
            <div class="card-header">
              <span class="mono order-no">{{ detail.orderNo }}</span>
              <el-tag :type="orderStatusMeta(detail.status).tag">
                {{ detail.statusName || orderStatusMeta(detail.status).label }}
              </el-tag>
            </div>
          </template>

          <el-descriptions :column="3" border>
            <el-descriptions-item label="订单 ID">{{ detail.orderId }}</el-descriptions-item>
            <el-descriptions-item label="用户 ID">{{ detail.userId }}</el-descriptions-item>
            <el-descriptions-item label="商家 / 门店">
              {{ detail.merchantId }} / {{ detail.shopId }}
            </el-descriptions-item>
            <el-descriptions-item label="履约方式">
              {{ labelOf(FULFILLMENT_TYPE, detail.fulfillmentType) }}
            </el-descriptions-item>
            <el-descriptions-item label="支付状态">
              <el-tag :type="tagOf(PAYMENT_STATUS, detail.paymentStatus)" size="small">
                {{ labelOf(PAYMENT_STATUS, detail.paymentStatus) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="退款状态">
              <el-tag :type="tagOf(ORDER_REFUND_STATUS, detail.refundStatus)" size="small">
                {{ labelOf(ORDER_REFUND_STATUS, detail.refundStatus) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="结算状态">
              <el-tag :type="tagOf(SETTLEMENT_STATUS, detail.settlementStatus)" size="small">
                {{ labelOf(SETTLEMENT_STATUS, detail.settlementStatus) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="异常类型">
              <template v-if="detail.exceptionType !== 0">
                <el-tag type="danger" size="small">
                  {{ labelOf(EXCEPTION_TYPE, detail.exceptionType) }}
                </el-tag>
              </template>
              <template v-else>—</template>
            </el-descriptions-item>
            <el-descriptions-item label="下单时间">
              {{ formatDateTime(detail.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="更新时间">
              {{ formatDateTime(detail.updatedAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="预计送达">
              {{ formatDateTime(detail.expectedDeliveryAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="完成时间">
              {{ formatDateTime(detail.completedAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="取消时间">
              {{ formatDateTime(detail.cancelledAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="取消原因" :span="3">
              {{ detail.cancelReason || '—' }}
            </el-descriptions-item>
        </el-descriptions>
        </el-card>

        <el-card shadow="never" class="section">
          <template #header>收货与自取</template>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="收货人">
              {{ detail.receiverName || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="收货手机号">
              {{ detail.receiverPhone || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="收货地址">
              {{ detail.receiverAddress || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="用户备注" :span="3">
              {{ detail.remark || '—' }}
            </el-descriptions-item>
            <template v-if="isPickup">
              <el-descriptions-item label="取餐码">
                <span class="mono">{{ detail.pickupCode || '—' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="自取人手机号">
                {{ detail.pickupPhone || '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="备餐完成时间">
                {{ formatDateTime(detail.pickupReadyAt) }}
              </el-descriptions-item>
              <el-descriptions-item label="取餐时间">
                {{ formatDateTime(detail.pickedUpAt) }}
              </el-descriptions-item>
            </template>
          </el-descriptions>
        </el-card>

        <el-card shadow="never" class="section">
          <template #header>金额（单位：分，展示按元）</template>
          <el-descriptions :column="4" border>
            <el-descriptions-item label="商品金额">
              {{ formatYuan(detail.goodsAmount) }}
            </el-descriptions-item>
            <el-descriptions-item label="打包费">
              {{ formatYuan(detail.packagingFee) }}
            </el-descriptions-item>
            <el-descriptions-item label="配送费">
              {{ formatYuan(detail.deliveryFee) }}
            </el-descriptions-item>
            <el-descriptions-item label="优惠总额">
              {{ formatYuan(detail.discountAmount) }}
            </el-descriptions-item>
            <el-descriptions-item label="平台承担优惠">
              {{ formatYuan(detail.platformDiscount) }}
            </el-descriptions-item>
            <el-descriptions-item label="商家承担优惠">
              {{ formatYuan(detail.merchantDiscount) }}
            </el-descriptions-item>
            <el-descriptions-item label="用户实付">
              <span class="amount strong">{{ formatYuan(detail.payAmount) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="已退金额">
              <span class="amount">{{ formatYuan(detail.refundedAmount) }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card shadow="never" class="section">
          <template #header>商品明细</template>
          <el-table :data="detail.items" border stripe row-key="itemId">
            <el-table-column label="商品" min-width="180">
              <template #default="{ row }">
                <div>{{ row.skuName }}</div>
                <div class="muted sub mono">{{ row.specJson || '—' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="SKU ID" width="110">
              <template #default="{ row }">{{ row.skuId ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="单价" width="110" align="right">
              <template #default="{ row }">{{ formatYuan(row.originalUnitPrice) }}</template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="80" align="right" />
            <el-table-column label="原价小计" width="120" align="right">
              <template #default="{ row }">{{ formatYuan(row.originalAmount) }}</template>
            </el-table-column>
            <el-table-column label="应付" width="120" align="right">
              <template #default="{ row }">
                <span class="amount">{{ formatYuan(row.payableAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="已退" width="140" align="right">
              <template #default="{ row }">
                {{ row.refundedQty }} 件 / {{ formatYuan(row.refundedAmount) }}
              </template>
            </el-table-column>

            <template #empty>
              <el-empty description="该订单没有商品明细。" :image-size="60" />
            </template>
          </el-table>
        </el-card>

        <el-card v-if="detail.refund" shadow="never" class="section">
          <template #header>
            <div class="card-header">
              <span>最近一笔退款</span>
              <el-tag type="warning" size="small">{{ detail.refund.statusName }}</el-tag>
            </div>
          </template>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="退款单号">
              <span class="mono">{{ detail.refund.refundNo }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="退款类型">
              {{ labelOf(REFUND_TYPE, detail.refund.refundType) }}
            </el-descriptions-item>
            <el-descriptions-item label="退款金额">
              <span class="amount strong">{{ formatYuan(detail.refund.amount) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="退款原因" :span="3">
              {{ detail.refund.reason }}
            </el-descriptions-item>
            <el-descriptions-item label="商家处理时间">
              {{ formatDateTime(detail.refund.merchantReviewAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="退款成功时间">
              {{ formatDateTime(detail.refund.successAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="申请时间">
              {{ formatDateTime(detail.refund.createdAt) }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card shadow="never" class="section">
          <template #header>状态时间轴</template>
          <el-empty
            v-if="detail.timeline.length === 0"
            description="该订单尚无状态流水。"
            :image-size="60"
          />
          <el-timeline v-else>
            <el-timeline-item
              v-for="(node, index) in detail.timeline"
              :key="`${node.createdAt}-${index}`"
              :timestamp="formatDateTime(node.createdAt)"
              placement="top"
            >
              <div class="timeline-title">
                <el-tag :type="orderStatusMeta(node.toStatus).tag" size="small">
                  {{ node.toStatusName || orderStatusMeta(node.toStatus).label }}
                </el-tag>
                <span class="mono muted">{{ node.event }}</span>
              </div>
              <div class="muted sub">
                由 {{ labelOf(OPERATOR_TYPE, node.operatorType) }} 操作 ·
                {{ node.fromStatusName || '—' }} → {{ node.toStatusName }}
              </div>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </template>
    </div>
  </div>
</template>

<style scoped>
.section {
  margin-bottom: 14px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.order-no {
  font-size: 15px;
  font-weight: 600;
}

.amount {
  font-variant-numeric: tabular-nums;
}

.strong {
  font-weight: 600;
}

.sub {
  font-size: 12px;
  margin-top: 2px;
}

.timeline-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
