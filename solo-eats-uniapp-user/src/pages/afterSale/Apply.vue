<template>
  <view class="apply">
    <view v-if="loading" class="se-loading">正在加载订单…</view>

    <view v-else-if="error && !order" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="load">重新加载</view>
    </view>

    <template v-else-if="order">
      <view v-if="result" class="se-card apply__result">
        <view class="apply__result-mark">✓</view>
        <text class="apply__result-title">退款申请已提交</text>
        <text class="apply__result-status">{{ refundStatusLabel(result.refundStatus) }}</text>

        <view class="apply__result-rows">
          <view class="apply__result-row">
            <text class="se-label">退款单号</text>
            <text>{{ result.refundNo }}</text>
          </view>
          <view class="apply__result-row">
            <text class="se-label">退款金额</text>
            <text class="se-price">{{ fenToYuan(result.amount) }}</text>
          </view>
          <view class="apply__result-row">
            <text class="se-label">支付渠道退款号</text>
            <text>{{ result.channelRefundNo || '--' }}</text>
          </view>
          <view class="apply__result-row">
            <text class="se-label">退款原因</text>
            <text class="apply__result-reason">{{ submittedReason }}</text>
          </view>
        </view>

        <text class="apply__result-tip">
          后端按整单金额退款，库存与已使用的优惠券由系统自动回滚。
        </text>

        <button class="se-btn se-btn-primary apply__result-btn" @click="goOrderDetail">
          查看订单详情
        </button>
        <view class="apply__result-links">
          <text class="apply__link" @click="goAfterSaleDetail">售后详情</text>
          <text class="apply__link" @click="goAfterSaleList">售后记录</text>
          <text class="apply__link" @click="goPolicy">退款规则</text>
        </view>
      </view>

      <template v-else>
        <view class="se-card apply__block">
          <view class="se-between apply__order-head">
            <text class="apply__order-no">订单 {{ order.orderNo }}</text>
            <text class="apply__order-status">{{ order.statusName }}</text>
          </view>
          <view class="apply__order-line">
            <text class="se-label">实付金额</text>
            <text class="se-price apply__amount">{{ fenToYuan(order.payAmount) }}</text>
          </view>
          <view v-for="item in order.items" :key="item.skuId" class="apply__order-line">
            <text class="apply__item-name">{{ item.skuName }}</text>
            <text class="se-muted">×{{ item.quantity }}</text>
          </view>
          <view class="apply__order-line">
            <text class="se-label">下单时间</text>
            <text class="se-muted">{{ formatDateTime(order.createdAt) }}</text>
          </view>
        </view>

        <view v-if="!eligible" class="apply__blocked">
          <text class="apply__blocked-title">当前订单不可自助退款</text>
          <text class="apply__blocked-text">{{ blockedReason }}</text>
          <text class="apply__blocked-text">
            后端退款接口只接受「待接单」「备餐中」订单，且按实付金额整单退款。
          </text>
        </view>

        <view class="se-card apply__block">
          <view class="apply__block-title">退款原因</view>
          <view
            v-for="(reason, index) in REFUND_REASONS"
            :key="reason"
            class="apply__reason"
            :class="{ 'is-active': selectedIndex === index }"
            @click="selectedIndex = index"
          >
            <view class="apply__radio" :class="{ 'is-active': selectedIndex === index }">
              <view v-if="selectedIndex === index" class="apply__radio-dot" />
            </view>
            <text>{{ reason }}</text>
          </view>
        </view>

        <view class="se-card apply__block">
          <view class="se-between apply__block-title">
            <text>退款说明</text>
            <text class="se-muted">{{ remark.length }}/40</text>
          </view>
          <textarea
            v-model="remark"
            class="apply__textarea"
            maxlength="40"
            placeholder="补充说明（选填），会拼接在退款原因后提交"
            placeholder-class="apply__placeholder"
          />
          <view class="apply__remark-tip">
            后端 reason 字段为 varchar(64)，说明与原因合计不超过 64 字
          </view>
        </view>

        <view class="se-card apply__block">
          <view class="se-between apply__block-title">
            <text>上传凭证</text>
            <text class="se-muted">{{ images.length }}/3</text>
          </view>
          <view class="apply__upload-note">
            后端尚未提供文件上传接口，以下图片仅在本机预览，不会随申请提交。
          </view>
          <view class="apply__images">
            <view
              v-for="(image, index) in images"
              :key="`${image}-${index}`"
              class="apply__image-wrap"
            >
              <image :src="image" mode="aspectFill" class="apply__image" @click="previewImage(index)" />
              <text class="apply__image-remove" @click.stop="removeImage(index)">×</text>
            </view>
            <view v-if="images.length < 3" class="apply__image-add" @click="chooseImages">
              <text class="apply__image-plus">+</text>
              <text class="apply__image-text">添加图片</text>
            </view>
          </view>
        </view>

        <view class="apply__links">
          <text class="apply__link" @click="goAfterSaleList">售后记录</text>
          <text class="apply__link" @click="goPolicy">退款规则</text>
        </view>

        <button
          class="se-btn se-btn-primary apply__submit"
          :disabled="submitting || !eligible"
          @click="submit"
        >
          {{ submitting ? '提交中…' : '提交退款申请' }}
        </button>
      </template>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { applyRefund, fetchOrderDetail } from '@/api/order'
import type { OrderDetailVO } from '@/api/types'
import type { RefundResultVO } from '@/api/order'
import {
  REFUND_REASONS,
  canApplyRefund,
  refundBlockedReason,
  refundStatusLabel,
} from '@/utils/refund'
import { fenToYuan } from '@/utils/money'
import { formatDateTime } from '@/utils/date'
import { clearIdempotencyKey, issueIdempotencyKey } from '@/utils/request'
import { useAuthStore } from '@/stores/auth'

/**
 * 申请售后。
 *
 * 后端只有一个接口：POST /api/v1/orders/{orderId}/refund，
 * body 仅 { reason }，且必须带 Idempotency-Key。
 * - 退款说明只能拼进 reason 一起提交（页面已明确提示）；
 * - 凭证没有上传接口，选择图片只在本地预览，不随请求发送；
 * - 是否可退以后端 RefundServiceImpl#assertRefundable 为准，前端先做同口径拦截。
 */

const auth = useAuthStore()

const orderId = ref(0)
const order = ref<OrderDetailVO | null>(null)
const loading = ref(true)
const error = ref('')
const submitting = ref(false)

const selectedIndex = ref(0)
const remark = ref('')
const images = ref<string[]>([])
const result = ref<RefundResultVO | null>(null)
const submittedReason = ref('')

const eligible = computed(() => Boolean(order.value && canApplyRefund(order.value.status)))

const blockedReason = computed(() =>
  order.value ? refundBlockedReason(order.value.status) : '',
)

onLoad((options) => {
  const parsed = Number(options?.orderId)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    loading.value = false
    error.value = '缺少订单参数，请从订单详情进入'
    return
  }
  orderId.value = parsed
  if (!auth.ensureLogin(`/pages/afterSale/Apply?orderId=${parsed}`)) {
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

function chooseImages(): void {
  if (images.value.length >= 3) {
    return
  }
  uni.chooseImage({
    count: 3 - images.value.length,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: (res) => {
      const paths = Array.isArray(res.tempFilePaths) ? res.tempFilePaths : []
      images.value = images.value.concat(paths).slice(0, 3)
    },
  })
}

function removeImage(index: number): void {
  images.value.splice(index, 1)
}

function previewImage(index: number): void {
  if (images.value.length === 0) {
    return
  }
  uni.previewImage({
    urls: images.value,
    current: images.value[index],
  })
}

function buildReason(): string {
  const base = REFUND_REASONS[selectedIndex.value] || REFUND_REASONS[0]
  const note = remark.value.trim()
  // t_refund_order.reason 为 varchar(64)，超长会在入库时被 MySQL 拒绝
  return (note ? `${base}：${note}` : base).slice(0, 64)
}

async function submit(): Promise<void> {
  if (submitting.value) {
    return
  }
  if (!order.value) {
    return
  }
  if (!eligible.value) {
    uni.showToast({ title: blockedReason.value, icon: 'none' })
    return
  }

  const reason = buildReason()
  if (!reason) {
    uni.showToast({ title: '请选择退款原因', icon: 'none' })
    return
  }

  const scope = `refund:${orderId.value}`
  const key = issueIdempotencyKey(scope)
  submitting.value = true
  try {
    const response = await applyRefund(orderId.value, reason, key)
    clearIdempotencyKey(scope)
    submittedReason.value = reason
    result.value = response
    uni.showToast({ title: '退款申请已提交', icon: 'none' })
  } catch (err) {
    // http 层已 toast；保留幂等键，用户修改后重试仍复用同一个 key
  } finally {
    submitting.value = false
  }
}

function goOrderDetail(): void {
  uni.navigateTo({ url: `/pages/order/Detail?id=${orderId.value}` })
}

function goAfterSaleList(): void {
  uni.navigateTo({ url: '/pages/afterSale/List' })
}

function goAfterSaleDetail(): void {
  uni.navigateTo({ url: `/pages/afterSale/Detail?orderId=${orderId.value}` })
}

function goPolicy(): void {
  uni.navigateTo({ url: '/pages/afterSale/Policy' })
}
</script>

<style scoped>
.apply {
  min-height: 100vh;
  padding: 20rpx 24rpx 60rpx;
  background-color: #f5f5f7;
}

.apply__block {
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.apply__block-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #1f1f1f;
  margin-bottom: 16rpx;
}

.apply__order-head {
  padding-bottom: 16rpx;
  margin-bottom: 8rpx;
  border-bottom: 1rpx solid #f5f5f5;
}

.apply__order-no {
  font-size: 27rpx;
  font-weight: 600;
}

.apply__order-status {
  font-size: 24rpx;
  color: #ff7a00;
}

.apply__order-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 8rpx 0;
  font-size: 25rpx;
  color: #333333;
}

.apply__item-name {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.apply__amount {
  font-size: 32rpx;
}

.apply__blocked {
  margin-bottom: 16rpx;
  padding: 20rpx;
  border-radius: 8rpx;
  background-color: #fff1f0;
  border: 1rpx solid #ffccc7;
}

.apply__blocked-title {
  display: block;
  font-size: 27rpx;
  font-weight: 600;
  color: #cf1322;
  margin-bottom: 10rpx;
}

.apply__blocked-text {
  display: block;
  font-size: 23rpx;
  line-height: 1.7;
  color: #a8071a;
}

.apply__reason {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 18rpx 0;
  font-size: 26rpx;
  color: #444444;
  border-bottom: 1rpx solid #f5f5f5;
}

.apply__reason:last-child {
  border-bottom: none;
}

.apply__reason.is-active {
  color: #ff7a00;
}

.apply__radio {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 2rpx solid #d9d9d9;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.apply__radio.is-active {
  border-color: #ff7a00;
}

.apply__radio-dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  background-color: #ff7a00;
}

.apply__textarea {
  width: 100%;
  height: 170rpx;
  padding: 16rpx;
  border-radius: 8rpx;
  background-color: #fafafa;
  font-size: 26rpx;
  line-height: 1.6;
}

.apply__placeholder {
  color: #b8b8b8;
}

.apply__remark-tip {
  margin-top: 8rpx;
  font-size: 21rpx;
  line-height: 1.6;
  color: #a26a2a;
}

.apply__upload-note {
  margin-bottom: 16rpx;
  padding: 12rpx 14rpx;
  border-radius: 6rpx;
  background-color: #fff8ef;
  color: #a26a2a;
  font-size: 22rpx;
  line-height: 1.6;
}

.apply__images {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.apply__image-wrap {
  position: relative;
  width: 160rpx;
  height: 160rpx;
}

.apply__image {
  width: 160rpx;
  height: 160rpx;
  border-radius: 8rpx;
  background-color: #f5f5f5;
}

.apply__image-remove {
  position: absolute;
  top: -12rpx;
  right: -12rpx;
  width: 40rpx;
  height: 40rpx;
  line-height: 36rpx;
  text-align: center;
  border-radius: 50%;
  background-color: rgba(0, 0, 0, 0.6);
  color: #ffffff;
  font-size: 28rpx;
}

.apply__image-add {
  width: 160rpx;
  height: 160rpx;
  border-radius: 8rpx;
  border: 1rpx dashed #d9d9d9;
  background-color: #fafafa;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.apply__image-plus {
  font-size: 44rpx;
  color: #b8b8b8;
  line-height: 1;
}

.apply__image-text {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #a0a0a0;
}

.apply__links {
  display: flex;
  justify-content: center;
  gap: 48rpx;
  padding: 12rpx 0 20rpx;
}

.apply__link {
  font-size: 24rpx;
  color: #ff7a00;
}

.apply__submit {
  width: 100%;
}

.apply__result {
  padding: 48rpx 28rpx 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.apply__result-mark {
  width: 112rpx;
  height: 112rpx;
  line-height: 112rpx;
  text-align: center;
  border-radius: 50%;
  background: linear-gradient(135deg, #52c41a, #389e0d);
  color: #ffffff;
  font-size: 56rpx;
  margin-bottom: 20rpx;
}

.apply__result-title {
  font-size: 32rpx;
  font-weight: 600;
}

.apply__result-status {
  margin-top: 8rpx;
  font-size: 26rpx;
  color: #52c41a;
}

.apply__result-rows {
  width: 100%;
  margin-top: 24rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f5f5f5;
}

.apply__result-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20rpx;
  padding: 12rpx 0;
  font-size: 25rpx;
  color: #333333;
}

.apply__result-reason {
  max-width: 70%;
  text-align: right;
  word-break: break-all;
}

.apply__result-tip {
  margin-top: 16rpx;
  font-size: 22rpx;
  line-height: 1.7;
  color: #8a8a8a;
  text-align: center;
}

.apply__result-btn {
  width: 100%;
  margin-top: 24rpx;
}

.apply__result-links {
  display: flex;
  justify-content: center;
  gap: 48rpx;
  margin-top: 20rpx;
}
</style>
