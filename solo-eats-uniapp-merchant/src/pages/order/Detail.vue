<template>
  <view class="detail">
    <view v-if="loading && !order" class="mt-loading">正在加载订单详情…</view>

    <view v-else-if="error && !order" class="mt-error">
      <text class="mt-error__msg">{{ error }}</text>
      <view class="mt-error__retry" @click="load(false)">重新加载</view>
    </view>

    <template v-else-if="order">
      <view class="detail__body">
        <!-- 头部：状态 + 单号 + 小票入口 -->
        <view class="detail__head">
          <view class="detail__head-main">
            <view class="detail__status-line">
              <text class="detail__status">{{ order.statusName }}</text>
              <text class="mt-tag" :class="order.fulfillmentType === 2 ? 'mt-tag--info' : ''">
                {{ order.fulfillmentTypeName || fulfillmentFallback }}
              </text>
            </view>
            <text class="detail__no">{{ order.orderNo }}</text>
            <text class="detail__time">下单 {{ formatDateTime(order.createdAt) }}</text>
          </view>
          <text class="detail__receipt" @click="goReceipt">打印小票</text>
        </view>

        <view v-if="overtimeText" class="detail__overtime">{{ overtimeText }}</view>

        <!-- 取餐码 -->
        <view v-if="order.pickupCode" class="detail__pickup">
          <view>
            <text class="detail__pickup-label">取餐码</text>
            <text class="detail__pickup-code">{{ order.pickupCode }}</text>
          </view>
          <view class="detail__pickup-side">
            <text class="detail__pickup-tip">
              {{ order.pickupReadyAt ? `备餐完成 ${formatDateTime(order.pickupReadyAt)}` : '备餐完成后请提醒顾客取餐' }}
            </text>
            <button
              v-if="order.status === 31"
              class="mt-btn mt-btn--primary mt-btn--small"
              @click="goVerify"
            >
              核销取餐码
            </button>
          </view>
        </view>

        <!-- 商品明细 -->
        <view class="mt-card detail__card">
          <view class="detail__card-head">
            <text class="mt-section-title">商品明细</text>
            <text class="mt-muted">共 {{ itemCount }} 件</text>
          </view>

          <view v-if="items.length === 0" class="detail__empty">暂无商品明细</view>

          <view v-for="item in items" :key="item.orderItemId" class="detail__item">
            <text class="detail__item-name">{{ item.skuName }}</text>
            <text class="detail__item-qty">×{{ item.quantity }}</text>
            <text class="detail__item-amount">¥{{ fenToYuan(item.payableAmount) }}</text>
          </view>

          <view class="mt-divider detail__divider" />
          <view class="detail__fee">
            <text class="detail__fee-label">商品金额</text>
            <text>¥{{ fenToYuan(order.goodsAmount) }}</text>
          </view>
          <view class="detail__fee">
            <text class="detail__fee-label">打包费</text>
            <text>¥{{ fenToYuan(order.packagingFee) }}</text>
          </view>
          <view class="detail__fee">
            <text class="detail__fee-label">配送费</text>
            <text>¥{{ fenToYuan(order.deliveryFee) }}</text>
          </view>
          <view class="detail__fee detail__fee--total">
            <text class="mt-strong">实付金额</text>
            <text class="mt-price detail__pay">{{ fenToYuan(order.payAmount) }}</text>
          </view>
        </view>

        <!-- 收货 / 自取信息 -->
        <view class="mt-card detail__card">
          <text class="mt-section-title">
            {{ order.fulfillmentType === 2 ? '自取信息' : '收货信息' }}
          </text>
          <template v-if="order.fulfillmentType === 1">
            <view class="detail__info">
              <text class="detail__info-label">收货人</text>
              <text class="detail__info-value">{{ order.receiverName || '--' }}</text>
            </view>
            <view class="detail__info">
              <text class="detail__info-label">联系电话</text>
              <text class="detail__info-value">{{ order.receiverPhoneMask || '--' }}</text>
            </view>
            <view class="detail__info">
              <text class="detail__info-label">收货地址</text>
              <text class="detail__info-value detail__info-value--wrap">{{ order.receiverAddress || '--' }}</text>
            </view>
          </template>
          <view v-else class="detail__info">
            <text class="detail__info-label">取餐方式</text>
            <text class="detail__info-value">到店自取，凭取餐码核销</text>
          </view>
          <view class="detail__info">
            <text class="detail__info-label">备注</text>
            <text class="detail__info-value detail__info-value--wrap">{{ order.remark || '无' }}</text>
          </view>
        </view>

        <!-- 时间轴 -->
        <view class="mt-card detail__card">
          <text class="mt-section-title">订单进度</text>
          <view v-if="timeline.length === 0" class="detail__empty">暂无进度记录</view>
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
                <text class="timeline__title">{{ node.toStatusName || '状态更新' }}</text>
                <text class="timeline__time">{{ formatDateTime(node.createdAt) }}</text>
              </view>
            </view>
          </view>
        </view>

        <view class="detail__spacer" />
      </view>

      <!-- 底部操作 -->
      <view class="mt-fixed-bottom">
        <template v-if="order.status === 10">
          <button class="mt-btn mt-btn--danger" :disabled="acting" @click="openReject">
            拒单
          </button>
          <button class="mt-btn mt-btn--primary" :disabled="acting" @click="handleAccept">
            {{ acting ? '处理中…' : '接单' }}
          </button>
        </template>
        <template v-else-if="order.status === 20">
          <button class="mt-btn mt-btn--primary" :disabled="acting" @click="handlePrepared">
            {{ acting ? '处理中…' : '出餐完成' }}
          </button>
        </template>
        <template v-else-if="order.status === 31">
          <button class="mt-btn mt-btn--primary" :disabled="acting" @click="goVerify">
            核销取餐码
          </button>
        </template>
        <view v-else class="detail__bar-note">
          当前状态「{{ order.statusName }}」，无需操作
        </view>
      </view>
    </template>

    <!-- 拒单浮层 -->
    <view v-if="rejectVisible" class="mt-mask" @click="closeReject" />
    <view v-if="rejectVisible" class="mt-sheet">
      <text class="mt-sheet__title">拒单原因</text>
      <view class="mt-sheet__tip">原因必填，提交后订单取消并自动退款，用户端可见。</view>

      <view class="detail__reasons">
        <view
          v-for="reason in REJECT_REASONS"
          :key="reason"
          class="detail__reason"
          :class="{ 'is-active': rejectReason === reason }"
          @click="rejectReason = reason"
        >
          {{ reason }}
        </view>
      </view>

      <textarea
        v-model="rejectReason"
        class="detail__textarea"
        maxlength="100"
        placeholder="也可直接填写具体原因（最多 100 字）"
        placeholder-class="mt-placeholder"
      />

      <view class="mt-sheet__actions">
        <button class="mt-btn mt-btn--plain" :disabled="rejectSubmitting" @click="closeReject">
          取消
        </button>
        <button
          class="mt-btn mt-btn--danger"
          :disabled="rejectSubmitting || !rejectReason.trim()"
          @click="submitReject"
        >
          {{ rejectSubmitting ? '提交中…' : '确认拒单' }}
        </button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { ApiError } from '@/api/http'
import { acceptOrder, getMerchantOrder, preparedOrder, rejectOrder } from '@/api/order'
import type { MerchantOrderDetailVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { fenToYuan } from '@/utils/money'
import { formatDateTime, parseServerDateTime } from '@/utils/date'

/**
 * 订单详情 + 状态动作。
 * - 动作按钮只看数字状态，展示中文一律用接口 statusName
 * - 接单/出餐失败若为 20001（状态已变更，如重复点击），提示后刷新而不是静默
 * - 拒单必须填原因 + 二次确认；后端按 Idempotency-Key 防重复退款
 */

const REJECT_REASONS = ['商品已售罄', '门店太忙，当前无法接单', '门店已打烊', '超出配送范围']
const ACCEPT_TIMEOUT_MINUTES = 10

const auth = useAuthStore()

const orderId = ref(0)
const order = ref<MerchantOrderDetailVO | null>(null)
const loading = ref(false)
const error = ref('')
const acting = ref(false)
const now = ref(Date.now())

const rejectVisible = ref(false)
const rejectReason = ref('')
const rejectSubmitting = ref(false)

let autoOpenReject = false
let loadedOnce = false
let clock: ReturnType<typeof setInterval> | null = null

const items = computed(() => order.value?.items || [])
const timeline = computed(() => order.value?.timeline || [])

const itemCount = computed(() =>
  items.value.reduce((sum, item) => sum + (item.quantity || 0), 0),
)

const fulfillmentFallback = computed(() => (order.value?.fulfillmentType === 2 ? '自取' : '配送'))

const currentTimelineIndex = computed(() => {
  const current = order.value
  if (!current || timeline.value.length === 0) {
    return -1
  }
  for (let index = timeline.value.length - 1; index >= 0; index -= 1) {
    if (timeline.value[index].toStatus === current.status) {
      return index
    }
  }
  return timeline.value.length - 1
})

const overtimeText = computed(() => {
  const current = order.value
  if (!current || current.status !== 10) {
    return ''
  }
  const created = parseServerDateTime(current.createdAt)
  if (!created) {
    return ''
  }
  const minutes = Math.max(0, Math.floor((now.value - created.getTime()) / 60_000))
  if (minutes < ACCEPT_TIMEOUT_MINUTES) {
    return ''
  }
  return `已等待 ${minutes} 分钟未接单，请尽快处理（超时规则由后端执行，前端不会自动拒单）`
})

onLoad((options) => {
  const id = Number(options?.id)
  autoOpenReject = String(options?.action || '') === 'reject'
  if (!Number.isFinite(id) || id <= 0) {
    error.value = '缺少订单参数，请从订单列表重新进入'
    return
  }
  orderId.value = id
  if (!auth.ensureLogin(`/pages/order/Detail?id=${id}`)) {
    return
  }
  startClock()
  void load(false)
})

onShow(() => {
  // 从核销页返回时刷新状态；首次进入由 onLoad 负责，避免重复请求
  if (loadedOnce) {
    void load(true)
  }
})

onUnload(() => stopClock())

function startClock(): void {
  stopClock()
  now.value = Date.now()
  clock = setInterval(() => {
    now.value = Date.now()
  }, 30_000)
}

function stopClock(): void {
  if (clock) {
    clearInterval(clock)
    clock = null
  }
}

async function load(silent: boolean): Promise<void> {
  if (!orderId.value) {
    return
  }
  if (!silent) {
    loading.value = true
    error.value = ''
  }
  try {
    order.value = await getMerchantOrder(orderId.value, silent)
    error.value = ''
    loadedOnce = true
    if (autoOpenReject) {
      autoOpenReject = false
      if (order.value.status === 10) {
        openReject()
      }
    }
  } catch (err) {
    error.value = (err as Error)?.message || '订单详情加载失败'
  } finally {
    loading.value = false
  }
}

function toast(message: string): void {
  uni.showToast({ title: message, icon: 'none', duration: 2400 })
}

function showConfirm(options: {
  title: string
  content: string
  confirmText?: string
  cancelText?: string
}): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title: options.title,
      content: options.content,
      confirmText: options.confirmText,
      cancelText: options.cancelText,
      success: (result) => resolve(Boolean(result.confirm)),
      fail: () => resolve(false),
    })
  })
}

function handleActionError(err: unknown, fallback: string): void {
  if (err instanceof ApiError) {
    if (err.code === 20001) {
      toast('订单状态已变更，正在刷新')
      void load(true)
      return
    }
    if (err.code === 10004) {
      toast('无权操作该订单')
      return
    }
  }
  toast((err as Error)?.message || fallback)
}

async function handleAccept(): Promise<void> {
  if (acting.value || !order.value) {
    return
  }
  const confirmed = await showConfirm({
    title: '确认接单',
    content: '接单后订单进入备餐中，请尽快完成出餐。',
    confirmText: '接单并备餐',
    cancelText: '取消',
  })
  if (!confirmed) {
    return
  }
  acting.value = true
  try {
    await acceptOrder(orderId.value)
    toast('已接单，开始备餐')
    await load(true)
  } catch (err) {
    handleActionError(err, '接单失败，请重试')
  } finally {
    acting.value = false
  }
}

async function handlePrepared(): Promise<void> {
  if (acting.value || !order.value) {
    return
  }
  const confirmed = await showConfirm({
    title: '确认出餐',
    content:
      order.value.fulfillmentType === 2
        ? '出餐后订单进入下一环节，请把餐品放到取餐区并提醒顾客取餐。'
        : '出餐后订单进入下一环节，请把餐品按订单号放好，等待骑手取货。',
    confirmText: '出餐完成',
    cancelText: '再检查一下',
  })
  if (!confirmed) {
    return
  }
  acting.value = true
  try {
    await preparedOrder(orderId.value)
    toast('已标记出餐完成')
    await load(true)
  } catch (err) {
    handleActionError(err, '出餐失败，请重试')
  } finally {
    acting.value = false
  }
}

function openReject(): void {
  rejectReason.value = ''
  rejectVisible.value = true
}

function closeReject(): void {
  if (rejectSubmitting.value) {
    return
  }
  rejectVisible.value = false
}

async function submitReject(): Promise<void> {
  if (rejectSubmitting.value) {
    return
  }
  const reason = rejectReason.value.trim()
  if (!reason) {
    toast('请填写拒单原因')
    return
  }
  const confirmed = await showConfirm({
    title: '确认拒单',
    content: `拒单原因：${reason}\n订单将取消并自动退款，操作不可撤销。`,
    confirmText: '确认拒单',
    cancelText: '返回',
  })
  if (!confirmed) {
    return
  }
  rejectSubmitting.value = true
  try {
    await rejectOrder(orderId.value, reason)
    rejectVisible.value = false
    toast('已拒单，订单已取消并发起退款')
    await load(true)
  } catch (err) {
    handleActionError(err, '拒单失败，请重试')
  } finally {
    rejectSubmitting.value = false
  }
}

function goVerify(): void {
  const current = order.value
  if (!current) {
    return
  }
  uni.navigateTo({
    url: `/pages/pickup/Verify?orderId=${current.orderId}&orderNo=${encodeURIComponent(current.orderNo)}`,
  })
}

function goReceipt(): void {
  const current = order.value
  if (!current) {
    return
  }
  uni.navigateTo({ url: `/pages/order/Receipt?orderId=${current.orderId}` })
}
</script>

<style scoped>
.detail {
  min-height: 100vh;
  background-color: #f2f4f7;
}

.detail__body {
  padding: 18rpx 20rpx 0;
}

.detail__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
  padding: 20rpx;
  border-radius: 12rpx;
  background: linear-gradient(135deg, #1f3a4d, #16233a);
  color: #ffffff;
}

.detail__head-main {
  flex: 1;
  min-width: 0;
}

.detail__status-line {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.detail__status {
  font-size: 34rpx;
  font-weight: 700;
}

.detail__no {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.82);
  font-variant-numeric: tabular-nums;
}

.detail__time {
  display: block;
  margin-top: 2rpx;
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.6);
}

.detail__receipt {
  flex: none;
  padding: 8rpx 14rpx;
  border-radius: 8rpx;
  background-color: rgba(255, 255, 255, 0.16);
  font-size: 22rpx;
  font-weight: 600;
}

.detail__overtime {
  margin-top: 12rpx;
  padding: 12rpx 16rpx;
  border-radius: 8rpx;
  background-color: #fee4e2;
  color: #b42318;
  font-size: 21rpx;
  line-height: 1.6;
}

.detail__pickup {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-top: 14rpx;
  padding: 18rpx 20rpx;
  border-radius: 12rpx;
  background-color: #fff6ea;
  border: 2rpx solid #ffd0a8;
}

.detail__pickup-label {
  display: block;
  font-size: 20rpx;
  color: #a2520a;
}

.detail__pickup-code {
  display: block;
  margin-top: 2rpx;
  font-size: 44rpx;
  font-weight: 700;
  letter-spacing: 4rpx;
  color: #e85d04;
  font-variant-numeric: tabular-nums;
}

.detail__pickup-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10rpx;
  max-width: 52%;
}

.detail__pickup-tip {
  font-size: 20rpx;
  color: #a2520a;
  text-align: right;
  line-height: 1.5;
}

.detail__card {
  margin-top: 14rpx;
}

.detail__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8rpx;
}

.detail__empty {
  padding: 18rpx 0;
  font-size: 22rpx;
  color: #8d99aa;
}

.detail__item {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 10rpx 0;
  border-bottom: 1rpx solid #f4f6f9;
}

.detail__item:last-of-type {
  border-bottom: none;
}

.detail__item-name {
  flex: 1;
  min-width: 0;
  font-size: 24rpx;
  color: #33455f;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.detail__item-qty {
  flex: none;
  width: 72rpx;
  text-align: center;
  font-size: 22rpx;
  color: #7a8699;
}

.detail__item-amount {
  flex: none;
  min-width: 110rpx;
  text-align: right;
  font-size: 23rpx;
  color: #1b2432;
  font-variant-numeric: tabular-nums;
}

.detail__divider {
  margin: 10rpx 0 4rpx;
}

.detail__fee {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6rpx 0;
  font-size: 23rpx;
  color: #33455f;
}

.detail__fee-label {
  color: #7a8699;
}

.detail__fee--total {
  margin-top: 4rpx;
  padding-top: 10rpx;
  border-top: 1rpx solid #f4f6f9;
  font-size: 25rpx;
}

.detail__pay {
  font-size: 32rpx;
}

.detail__info {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  padding: 8rpx 0;
  font-size: 23rpx;
}

.detail__info-label {
  width: 128rpx;
  flex: none;
  color: #7a8699;
}

.detail__info-value {
  flex: 1;
  text-align: right;
  color: #33455f;
}

.detail__info-value--wrap {
  word-break: break-all;
  line-height: 1.55;
}

.timeline {
  margin-top: 10rpx;
  padding-left: 6rpx;
}

.timeline__item {
  position: relative;
  padding: 0 0 22rpx 30rpx;
}

.timeline__item:last-child {
  padding-bottom: 0;
}

.timeline__dot {
  position: absolute;
  left: 0;
  top: 8rpx;
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background-color: #d9dde4;
}

.timeline__line {
  position: absolute;
  left: 5rpx;
  top: 22rpx;
  bottom: 0;
  width: 2rpx;
  background-color: #eef1f5;
}

.timeline__item.is-current .timeline__dot {
  background-color: #ff7a00;
  box-shadow: 0 0 0 5rpx rgba(255, 122, 0, 0.16);
}

.timeline__body {
  display: flex;
  flex-direction: column;
}

.timeline__title {
  font-size: 23rpx;
  color: #55637a;
}

.timeline__item.is-current .timeline__title {
  color: #e85d04;
  font-weight: 700;
}

.timeline__time {
  margin-top: 2rpx;
  font-size: 20rpx;
  color: #a0a8b5;
}

.detail__spacer {
  height: calc(140rpx + env(safe-area-inset-bottom));
}

.detail__bar-note {
  flex: 1;
  text-align: center;
  font-size: 24rpx;
  color: #7a8699;
  padding: 18rpx 0;
}

.detail__reasons {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
}

.detail__reason {
  padding: 12rpx 20rpx;
  border-radius: 8rpx;
  background-color: #f2f5f9;
  color: #44536b;
  font-size: 23rpx;
  border: 2rpx solid transparent;
}

.detail__reason.is-active {
  background-color: #fff1e6;
  border-color: #ff9a3c;
  color: #c2410c;
  font-weight: 600;
}

.detail__textarea {
  width: 100%;
  height: 140rpx;
  margin-top: 16rpx;
  padding: 16rpx;
  border-radius: 10rpx;
  background-color: #f7f9fc;
  font-size: 24rpx;
  color: #1b2432;
  line-height: 1.6;
}
</style>
