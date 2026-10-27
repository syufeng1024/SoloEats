<template>
  <view class="asd">
    <view class="asd__notice">
      售后详情接口后端尚未提供，本页只展示 GET /api/v1/orders/{id} 真实返回的退款字段
    </view>

    <view v-if="loading" class="se-loading">正在加载订单…</view>

    <view v-else-if="error && !order" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="load">重新加载</view>
      <view class="se-error__retry asd__second-btn" @click="goOrders">去订单列表</view>
    </view>

    <template v-else-if="order">
      <view class="se-card asd__block">
        <view class="se-between asd__head">
          <text class="asd__order-no">订单 {{ order.orderNo }}</text>
          <text class="asd__order-status">{{ order.statusName }}</text>
        </view>
        <view class="asd__row">
          <text class="se-label">实付金额</text>
          <text class="se-price asd__amount">{{ fenToYuan(order.payAmount) }}</text>
        </view>
        <view class="asd__row">
          <text class="se-label">下单时间</text>
          <text>{{ formatDateTime(order.createdAt) }}</text>
        </view>
        <view class="asd__row">
          <text class="se-label">订单状态</text>
          <text>{{ order.statusName }}（{{ order.status }}）</text>
        </view>
      </view>

      <view class="se-card asd__block">
        <text class="asd__block-title">退款信息（订单详情返回值）</text>

        <view class="asd__row">
          <text class="se-label">退款状态</text>
          <text class="asd__refund-status" :class="refundStatusClass">
            {{ refundStatusText }}
          </text>
        </view>
        <view class="asd__row">
          <text class="se-label">已退款金额</text>
          <text>{{ refundedAmountText }}</text>
        </view>
        <view class="asd__row">
          <text class="se-label">退款进度</text>
          <text class="asd__summary">{{ refundSummary }}</text>
        </view>

        <view v-if="showRefundMissing" class="asd__gap">
          <text class="asd__gap-title">以下字段后端未提供，无法展示</text>
          <text class="asd__gap-text">退款单号、退款原因、审核/拒绝记录、预计到账时间、凭证图片</text>
          <text class="asd__gap-text">
            当前退款接口只返回申请结果，没有按退款单查询的接口；
            上述字段不能从订单详情推导，本页不作推测。
          </text>
        </view>
      </view>

      <view class="se-card asd__block">
        <text class="asd__block-title">订单状态时间轴</text>
        <view v-if="order.timeline.length === 0" class="se-muted">订单详情未返回时间轴记录</view>
        <view v-else class="asd__timeline">
          <view
            v-for="(node, index) in order.timeline"
            :key="`${node.toStatus}-${index}`"
            class="asd__timeline-item"
          >
            <view class="asd__timeline-dot" />
            <view v-if="index < order.timeline.length - 1" class="asd__timeline-line" />
            <view class="asd__timeline-body">
              <text class="asd__timeline-title">{{ timelineTitle(node) }}</text>
              <text class="asd__timeline-time">{{ formatDateTime(node.createdAt) }}</text>
            </view>
          </view>
        </view>
      </view>

      <view class="asd__actions">
        <button class="se-btn se-btn-ghost asd__btn" @click="goOrderDetail">查看订单详情</button>
        <button
          v-if="canApply"
          class="se-btn se-btn-primary asd__btn"
          @click="goApply"
        >
          申请退款
        </button>
        <button class="se-btn se-btn-plain asd__btn" @click="goPolicy">退款规则</button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchOrderDetail } from '@/api/order'
import type { OrderDetailVO, OrderTimelineVO } from '@/api/types'
import {
  ORDER_REFUND_STATUS,
  canApplyRefund,
  orderRefundStatusLabel,
} from '@/utils/refund'
import { fenToYuan } from '@/utils/money'
import { formatDateTime } from '@/utils/date'
import { useAuthStore } from '@/stores/auth'

/**
 * 售后详情（降级实现）。
 *
 * 后端没有售后单查询接口，只能根据 GET /orders/{id} 的
 * refundStatus / refundedAmount / status / timeline 展示真实信息。
 * 退款单号、原因、审核记录、到账时间一律标注「未提供」，不自行推断。
 */

const auth = useAuthStore()

const orderId = ref(0)
const order = ref<OrderDetailVO | null>(null)
const loading = ref(true)
const error = ref('')

const refundStatusText = computed(() => orderRefundStatusLabel(order.value?.refundStatus))

const refundStatusClass = computed(() => {
  const status = order.value?.refundStatus
  if (status === ORDER_REFUND_STATUS.FULL) {
    return 'is-success'
  }
  if (status === ORDER_REFUND_STATUS.PARTIAL) {
    return 'is-processing'
  }
  return 'is-muted'
})

const refundedAmountText = computed(() => {
  if (!order.value || order.value.refundedAmount === null || order.value.refundedAmount === undefined) {
    return '订单详情未返回退款金额'
  }
  return `¥${fenToYuan(order.value.refundedAmount)}`
})

const refundSummary = computed(() => {
  const current = order.value
  if (!current) {
    return ''
  }
  if (current.refundStatus === ORDER_REFUND_STATUS.FULL) {
    return `订单已全额退款，refundedAmount = ¥${fenToYuan(current.refundedAmount)}`
  }
  if (current.refundStatus === ORDER_REFUND_STATUS.PARTIAL) {
    return `订单存在部分退款记录，refundedAmount = ¥${fenToYuan(current.refundedAmount)}`
  }
  if (current.refundStatus === ORDER_REFUND_STATUS.NONE) {
    return '订单级退款标识为 0（无退款）'
  }
  return `订单级退款标识为未知值（${current.refundStatus}），不作推断`
})

const showRefundMissing = computed(() => {
  const status = order.value?.refundStatus
  return status === ORDER_REFUND_STATUS.PARTIAL || status === ORDER_REFUND_STATUS.FULL
})

const canApply = computed(() => Boolean(order.value && canApplyRefund(order.value.status)))

onLoad((options) => {
  const parsed = Number(options?.orderId)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    loading.value = false
    error.value = '缺少订单参数，请从订单详情或退款申请成功页进入'
    return
  }
  orderId.value = parsed
  if (!auth.ensureLogin(`/pages/afterSale/Detail?orderId=${parsed}`)) {
    return
  }
  load()
})

async function load(): Promise<void> {
  if (!orderId.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    order.value = await fetchOrderDetail(orderId.value)
  } catch (err) {
    error.value = (err as Error)?.message || '订单加载失败'
  } finally {
    loading.value = false
  }
}

function timelineTitle(node: OrderTimelineVO): string {
  if (node.fromStatus === -1) {
    return '下单'
  }
  return node.toStatusName || '状态更新'
}

function goOrderDetail(): void {
  uni.navigateTo({ url: `/pages/order/Detail?id=${orderId.value}` })
}

function goApply(): void {
  uni.navigateTo({ url: `/pages/afterSale/Apply?orderId=${orderId.value}` })
}

function goPolicy(): void {
  uni.navigateTo({ url: '/pages/afterSale/Policy' })
}

function goOrders(): void {
  uni.switchTab({ url: '/pages/order/List' })
}
</script>

<style scoped>
.asd {
  min-height: 100vh;
  padding: 20rpx 24rpx 60rpx;
  background-color: #f5f5f7;
}

.asd__notice {
  margin-bottom: 16rpx;
  padding: 14rpx 18rpx;
  border-radius: 8rpx;
  background-color: #fff8ef;
  color: #a26a2a;
  font-size: 22rpx;
  line-height: 1.6;
}

.asd__second-btn {
  margin-left: 16rpx;
}

.asd__block {
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.asd__head {
  padding-bottom: 16rpx;
  margin-bottom: 8rpx;
  border-bottom: 1rpx solid #f5f5f5;
}

.asd__order-no {
  font-size: 27rpx;
  font-weight: 600;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  max-width: 70%;
}

.asd__order-status {
  font-size: 24rpx;
  color: #ff7a00;
}

.asd__block-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #1f1f1f;
  margin-bottom: 12rpx;
}

.asd__row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20rpx;
  padding: 10rpx 0;
  font-size: 25rpx;
  color: #333333;
}

.asd__amount {
  font-size: 32rpx;
}

.asd__refund-status {
  font-weight: 600;
}

.asd__refund-status.is-success {
  color: #52c41a;
}

.asd__refund-status.is-failed {
  color: #e5484d;
}

.asd__refund-status.is-processing {
  color: #ff7a00;
}

.asd__refund-status.is-muted {
  color: #8a8a8a;
}

.asd__summary {
  max-width: 68%;
  text-align: right;
  color: #555555;
  line-height: 1.6;
}

.asd__gap {
  margin-top: 12rpx;
  padding: 14rpx 16rpx;
  border-radius: 6rpx;
  background-color: #fff8ef;
}

.asd__gap-title {
  display: block;
  font-size: 23rpx;
  font-weight: 600;
  color: #a26a2a;
  margin-bottom: 6rpx;
}

.asd__gap-text {
  display: block;
  font-size: 22rpx;
  line-height: 1.7;
  color: #a26a2a;
}

.asd__timeline {
  padding-top: 8rpx;
}

.asd__timeline-item {
  position: relative;
  padding: 0 0 24rpx 36rpx;
}

.asd__timeline-item:last-child {
  padding-bottom: 0;
}

.asd__timeline-dot {
  position: absolute;
  left: 0;
  top: 10rpx;
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background-color: #ff7a00;
}

.asd__timeline-line {
  position: absolute;
  left: 7rpx;
  top: 30rpx;
  bottom: 0;
  width: 2rpx;
  background-color: #f0d7bf;
}

.asd__timeline-body {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.asd__timeline-title {
  font-size: 25rpx;
  color: #333333;
}

.asd__timeline-time {
  font-size: 22rpx;
  color: #a0a0a0;
}

.asd__actions {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-top: 8rpx;
}

.asd__btn {
  width: 100%;
}
</style>
