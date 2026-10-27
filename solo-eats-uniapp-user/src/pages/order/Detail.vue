<template>
  <view class="detail">
    <view v-if="loading" class="se-loading">正在加载订单…</view>

    <view v-else-if="error" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="load">重新加载</view>
    </view>

    <template v-else-if="detail">
      <view class="detail__status">
        <text class="detail__status-name">{{ detail.statusName }}</text>
        <text class="detail__status-hint">{{ statusHint }}</text>
      </view>

      <view v-if="detail.fulfillmentType === 2 && detail.pickupCode" class="se-card detail__pickup">
        <view>
          <text class="se-label">取餐码</text>
          <text class="detail__pickup-code">{{ detail.pickupCode }}</text>
        </view>
        <view class="se-btn se-btn-ghost detail__pickup-btn" @click="goPickupCode">查看取餐码</view>
      </view>

      <view class="se-card detail__block">
        <view class="detail__block-title">订单进度</view>
        <view v-if="timeline.length === 0" class="se-muted">暂无进度记录</view>
        <view v-else class="timeline">
          <view
            v-for="(node, index) in timeline"
            :key="`${node.toStatus}-${index}`"
            class="timeline__item"
            :class="{ 'is-current': index === currentTimelineIndex }"
          >
            <view class="timeline__dot" />
            <view v-if="index < timeline.length - 1" class="timeline__line" />
            <view class="timeline__body">
              <text class="timeline__title">{{ timelineTitle(node) }}</text>
              <text class="timeline__time">{{ formatDateTime(node.createdAt) }}</text>
            </view>
          </view>
        </view>
      </view>

      <view class="se-card detail__block">
        <view class="detail__block-title">
          {{ detail.fulfillmentType === 2 ? '自取商品' : '配送商品' }}
        </view>
        <view v-for="item in detail.items" :key="item.skuId" class="detail__line">
          <text class="detail__line-name">{{ item.skuName }}</text>
          <text class="se-muted detail__line-qty">×{{ item.quantity }}</text>
          <text class="detail__line-amount">¥{{ fenToYuan(item.payableAmount) }}</text>
        </view>

        <view class="se-divider" />
        <view class="se-between detail__fee-row">
          <text class="se-label">商品金额</text>
          <text>¥{{ fenToYuan(detail.goodsAmount) }}</text>
        </view>
        <view class="se-between detail__fee-row">
          <text class="se-label">打包费</text>
          <text>¥{{ fenToYuan(detail.packagingFee) }}</text>
        </view>
        <view class="se-between detail__fee-row">
          <text class="se-label">配送费</text>
          <text>¥{{ fenToYuan(detail.deliveryFee) }}</text>
        </view>
        <view v-if="detail.refundedAmount && detail.refundedAmount > 0" class="se-between detail__fee-row">
          <text class="se-label">已退款</text>
          <text class="detail__refund">-¥{{ fenToYuan(detail.refundedAmount) }}</text>
        </view>
        <view class="se-between detail__fee-row">
          <text class="se-strong">实付金额</text>
          <text class="se-price detail__pay">{{ fenToYuan(detail.payAmount) }}</text>
        </view>
      </view>

      <view class="se-card detail__block">
        <view class="detail__block-title">
          {{ detail.fulfillmentType === 2 ? '自取信息' : '收货信息' }}
        </view>
        <view class="detail__info-row">
          <text class="se-label">订单类型</text>
          <text>{{ detail.fulfillmentTypeName }}</text>
        </view>
        <template v-if="detail.fulfillmentType === 1">
          <view class="detail__info-row">
            <text class="se-label">收货人</text>
            <text>{{ detail.receiverName || '--' }}</text>
          </view>
          <view class="detail__info-row">
            <text class="se-label">联系电话</text>
            <text>{{ detail.receiverPhoneMask || '--' }}</text>
          </view>
          <view class="detail__info-row">
            <text class="se-label">收货地址</text>
            <text class="detail__info-value">{{ detail.receiverAddress || '--' }}</text>
          </view>
        </template>
        <view v-else class="detail__info-row">
          <text class="se-label">取餐方式</text>
          <text>到店自取，出示取餐码由门店核销</text>
        </view>
        <view v-if="detail.remark" class="detail__info-row">
          <text class="se-label">备注</text>
          <text class="detail__info-value">{{ detail.remark }}</text>
        </view>
      </view>

      <view class="se-card detail__block">
        <view class="se-between detail__info-row">
          <text class="se-label">订单号</text>
          <text class="detail__copy" @click="copyOrderNo">{{ detail.orderNo }} · 复制</text>
        </view>
        <view class="se-between detail__info-row">
          <text class="se-label">下单时间</text>
          <text>{{ formatDateTime(detail.createdAt) }}</text>
        </view>
      </view>

      <view v-if="actions.length > 0" class="se-fixed-bottom detail__bar">
        <view
          v-for="action in actions"
          :key="action.key"
          class="detail__action"
          :class="`detail__action--${action.type}`"
          @click="handleAction(action.key)"
        >
          {{ action.label }}
        </view>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onHide, onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { ORDER_STATUS, cancelOrder, fetchOrderDetail, isTerminalStatus } from '@/api/order'
import type { OrderDetailVO, OrderTimelineVO } from '@/api/types'
import { fenToYuan } from '@/utils/money'
import { formatDateTime } from '@/utils/date'
import { clearIdempotencyKey, issueIdempotencyKey } from '@/utils/request'
import { useAuthStore } from '@/stores/auth'

interface DetailAction {
  key: 'pay' | 'cancel' | 'track' | 'pickup' | 'review' | 'refund'
  label: string
  type: 'primary' | 'ghost' | 'plain'
}

const CANCEL_REASONS = ['不想要了', '点错了', '商品信息有误', '其他原因']
const POLL_INTERVAL_MS = 10_000

const auth = useAuthStore()

const orderId = ref(0)
const detail = ref<OrderDetailVO | null>(null)
const loading = ref(true)
const error = ref('')

let timer: ReturnType<typeof setInterval> | null = null
let polling = false

const timeline = computed<OrderTimelineVO[]>(() => detail.value?.timeline || [])

const currentTimelineIndex = computed(() => {
  if (!detail.value || timeline.value.length === 0) {
    return -1
  }
  for (let index = timeline.value.length - 1; index >= 0; index -= 1) {
    if (timeline.value[index].toStatus === detail.value.status) {
      return index
    }
  }
  return timeline.value.length - 1
})

const statusHint = computed(() => {
  const status = detail.value?.status
  switch (status) {
    case ORDER_STATUS.WAIT_PAY:
      return '请在 15 分钟内完成支付，超时订单将自动取消'
    case ORDER_STATUS.WAIT_ACCEPT:
      return '已支付，等待商家接单'
    case ORDER_STATUS.PREPARING:
      return '商家正在备餐，请耐心等待'
    case ORDER_STATUS.WAIT_RIDER_PICKUP:
      return '餐品已备好，等待骑手取货'
    case ORDER_STATUS.WAIT_USER_PICKUP:
      return '餐品已备好，请凭取餐码到店取餐'
    case ORDER_STATUS.DELIVERING:
      return '骑手正在配送途中'
    case ORDER_STATUS.DELIVERED:
      return '餐品已送达，请确认收货'
    case ORDER_STATUS.PICKED_UP:
      return '已取餐，祝你用餐愉快'
    case ORDER_STATUS.COMPLETED:
      return '订单已完成，可对本次用餐评价'
    case ORDER_STATUS.CANCELLED:
      return '订单已取消，如已支付将原路退回'
    default:
      return ''
  }
})

const actions = computed<DetailAction[]>(() => {
  const current = detail.value
  if (!current) {
    return []
  }
  const result: DetailAction[] = []
  if (current.status === ORDER_STATUS.WAIT_PAY) {
    result.push({ key: 'cancel', label: '取消订单', type: 'plain' })
    result.push({ key: 'pay', label: '去支付', type: 'primary' })
  }
  if (current.status === ORDER_STATUS.WAIT_ACCEPT) {
    result.push({ key: 'cancel', label: '取消订单', type: 'plain' })
  }
  // 与后端 RefundServiceImpl#assertRefundable 一致：待接单、备餐中可整单退款
  if (
    [ORDER_STATUS.WAIT_ACCEPT, ORDER_STATUS.PREPARING].includes(current.status) &&
    !(current.refundedAmount && current.refundedAmount > 0)
  ) {
    result.push({ key: 'refund', label: '申请退款', type: 'ghost' })
  }
  if (
    current.fulfillmentType === 1 &&
    [ORDER_STATUS.PREPARING, ORDER_STATUS.WAIT_RIDER_PICKUP, ORDER_STATUS.DELIVERING].includes(
      current.status,
    )
  ) {
    result.push({ key: 'track', label: '查看配送', type: 'ghost' })
  }
  if (
    current.fulfillmentType === 2 &&
    Boolean(current.pickupCode) &&
    [ORDER_STATUS.PREPARING, ORDER_STATUS.WAIT_USER_PICKUP].includes(current.status)
  ) {
    result.push({ key: 'pickup', label: '查看取餐码', type: 'primary' })
  }
  if (
    [ORDER_STATUS.DELIVERED, ORDER_STATUS.PICKED_UP, ORDER_STATUS.COMPLETED].includes(
      current.status,
    )
  ) {
    result.push({ key: 'review', label: '去评价', type: 'ghost' })
  }
  return result
})

onLoad((options) => {
  const id = Number(options?.id)
  if (!Number.isFinite(id) || id <= 0) {
    loading.value = false
    error.value = '缺少订单参数'
    return
  }
  orderId.value = id
  if (!auth.ensureLogin(`/pages/order/Detail?id=${id}`)) {
    return
  }
  load()
})

onShow(() => {
  if (detail.value) {
    startPolling()
  }
})

onHide(() => {
  stopPolling()
})

onUnload(() => {
  stopPolling()
})

async function load(): Promise<void> {
  if (!orderId.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    detail.value = await fetchOrderDetail(orderId.value)
    startPolling()
  } catch (err) {
    error.value = (err as Error)?.message || '订单加载失败'
  } finally {
    loading.value = false
  }
}

function startPolling(): void {
  stopPolling()
  if (!detail.value || isTerminalStatus(detail.value.status)) {
    return
  }
  // 规格要求：详情页在前台每 10 秒刷新，退到后台停止；轮询失败不弹 toast
  timer = setInterval(() => {
    void refreshSilently()
  }, POLL_INTERVAL_MS)
}

function stopPolling(): void {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

async function refreshSilently(): Promise<void> {
  if (polling) {
    return
  }
  polling = true
  try {
    detail.value = await fetchOrderDetail(orderId.value, true)
    if (detail.value && isTerminalStatus(detail.value.status)) {
      stopPolling()
    }
  } catch (err) {
    // 静默失败，下一轮继续
  } finally {
    polling = false
  }
}

function timelineTitle(node: OrderTimelineVO): string {
  // 第一条状态日志 fromStatus=-1，表示订单创建
  if (node.fromStatus === -1) {
    return '下单'
  }
  return node.toStatusName || '状态更新'
}

function handleAction(key: DetailAction['key']): void {
  const current = detail.value
  if (!current) {
    return
  }
  if (key === 'pay') {
    uni.navigateTo({
      url: `/pages/order/PayResult?orderId=${current.orderId}&payAmount=${current.payAmount}&fulfillmentType=${current.fulfillmentType}${
        current.pickupCode ? `&pickupCode=${encodeURIComponent(current.pickupCode)}` : ''
      }`,
    })
    return
  }
  if (key === 'cancel') {
    handleCancel()
    return
  }
  if (key === 'track') {
    // UserOrderDetailView 未返回 deliveryId，Track 页会展示「暂无配送信息」
    uni.navigateTo({ url: `/pages/order/Track?orderId=${current.orderId}` })
    return
  }
  if (key === 'pickup') {
    uni.navigateTo({ url: `/pages/order/PickupCode?orderId=${current.orderId}` })
    return
  }
  if (key === 'review') {
    // UserOrderItemView 未返回 orderItemId，评价页会提示该缺口
    uni.navigateTo({ url: `/pages/review/ReviewForm?orderId=${current.orderId}&shopId=${current.shopId}` })
  }
  if (key === 'refund') {
    uni.navigateTo({ url: `/pages/afterSale/Apply?orderId=${current.orderId}` })
  }
}

function handleCancel(): void {
  if (!detail.value) {
    return
  }
  const current = detail.value
  uni.showActionSheet({
    itemList: CANCEL_REASONS,
    success: async (result) => {
      const reason = CANCEL_REASONS[result.tapIndex]
      if (!reason) {
        return
      }
      const scope = `cancel:${current.orderId}`
      const key = issueIdempotencyKey(scope)
      try {
        await cancelOrder(current.orderId, reason, key)
        clearIdempotencyKey(scope)
        uni.showToast({ title: '订单已取消', icon: 'none' })
        await load()
      } catch (err) {
        // http 层已提示
      }
    },
  })
}

function goPickupCode(): void {
  uni.navigateTo({ url: `/pages/order/PickupCode?orderId=${orderId.value}` })
}

function copyOrderNo(): void {
  if (!detail.value) {
    return
  }
  uni.setClipboardData({ data: detail.value.orderNo })
}
</script>

<style scoped>
.detail {
  min-height: 100vh;
  padding: 20rpx 24rpx 200rpx;
  background-color: #f5f5f7;
}

.detail__status {
  display: flex;
  flex-direction: column;
  padding: 8rpx 8rpx 24rpx;
}

.detail__status-name {
  font-size: 40rpx;
  font-weight: 600;
  color: #1f1f1f;
}

.detail__status-hint {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #8a8a8a;
}

.detail__pickup {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.detail__pickup-code {
  display: block;
  margin-top: 6rpx;
  font-size: 48rpx;
  font-weight: 700;
  letter-spacing: 8rpx;
  color: #ff7a00;
}

.detail__pickup-btn {
  height: 64rpx;
  line-height: 64rpx;
  font-size: 24rpx;
}

.detail__block {
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.detail__block-title {
  font-size: 28rpx;
  font-weight: 600;
  margin-bottom: 16rpx;
}

.timeline {
  padding-left: 8rpx;
}

.timeline__item {
  position: relative;
  padding: 0 0 28rpx 36rpx;
}

.timeline__item:last-child {
  padding-bottom: 0;
}

.timeline__dot {
  position: absolute;
  left: 0;
  top: 10rpx;
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background-color: #d9d9d9;
}

.timeline__line {
  position: absolute;
  left: 7rpx;
  top: 28rpx;
  bottom: 0;
  width: 2rpx;
  background-color: #eeeeee;
}

.timeline__item.is-current .timeline__dot {
  background-color: #ff7a00;
  box-shadow: 0 0 0 6rpx rgba(255, 122, 0, 0.16);
}

.timeline__body {
  display: flex;
  flex-direction: column;
}

.timeline__title {
  font-size: 26rpx;
  color: #666666;
}

.timeline__item.is-current .timeline__title {
  color: #ff7a00;
  font-weight: 600;
}

.timeline__time {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #a0a0a0;
}

.detail__line {
  display: flex;
  align-items: center;
  padding: 10rpx 0;
  font-size: 26rpx;
}

.detail__line-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.detail__line-qty {
  width: 80rpx;
  text-align: center;
}

.detail__line-amount {
  min-width: 130rpx;
  text-align: right;
}

.detail__fee-row {
  padding: 8rpx 0;
  font-size: 26rpx;
}

.detail__refund {
  color: #d4380d;
}

.detail__pay {
  font-size: 34rpx;
}

.detail__info-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
  padding: 8rpx 0;
  font-size: 26rpx;
}

.detail__info-value {
  flex: 1;
  text-align: right;
  color: #444444;
}

.detail__copy {
  color: #ff7a00;
}

.detail__bar {
  display: flex;
  justify-content: flex-end;
  gap: 16rpx;
}

.detail__action {
  min-width: 200rpx;
  height: 72rpx;
  line-height: 72rpx;
  text-align: center;
  border-radius: 8rpx;
  font-size: 26rpx;
}

.detail__action--primary {
  background: linear-gradient(135deg, #ffa24c, #ff7a00);
  color: #ffffff;
}

.detail__action--ghost {
  border: 1rpx solid #ff7a00;
  color: #ff7a00;
  background-color: #ffffff;
}

.detail__action--plain {
  border: 1rpx solid #e5e5e5;
  color: #666666;
  background-color: #ffffff;
}
</style>
