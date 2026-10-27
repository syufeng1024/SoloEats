<template>
  <view class="detail rd-page">
    <view v-if="!record" class="rd-empty">
      <text class="rd-empty__title">无法显示提现详情</text>
      <text>{{ error || '请从提现记录列表重新进入' }}</text>
      <view class="rd-empty__action" @click="goList">返回提现记录</view>
    </view>

    <view v-else>
      <view class="detail__hero">
        <text class="detail__hero-label">提现金额（元）</text>
        <text class="detail__hero-value">{{ fenToYuan(record.amount) }}</text>
        <text class="detail__hero-status">{{ statusLabel(record.status) }}</text>
      </view>

      <view class="rd-card detail__info">
        <view class="detail__row">
          <text class="detail__label">提现单号</text>
          <text class="detail__value">{{ record.payoutNo }}</text>
        </view>
        <view class="rd-divider" />
        <view class="detail__row">
          <text class="detail__label">到账渠道</text>
          <text class="detail__value">{{ channelLabel(record.channel) }}</text>
        </view>
        <view class="rd-divider" />
        <view class="detail__row">
          <text class="detail__label">申请时间</text>
          <text class="detail__value">{{ formatDateTime(record.createdAt) }}</text>
        </view>
        <view class="rd-divider" />
        <view class="detail__row">
          <text class="detail__label">手续费</text>
          <text class="detail__value">后端未返回手续费字段</text>
        </view>
      </view>

      <view class="rd-card detail__timeline">
        <text class="detail__section-title">处理进度</text>

        <view v-for="(step, index) in steps" :key="step.key" class="detail__step">
          <view class="detail__step-rail">
            <view class="detail__dot" :class="{ 'is-done': step.done, 'is-reject': step.reject }" />
            <view
              v-if="index < steps.length - 1"
              class="detail__line"
              :class="{ 'is-done': step.done }"
            />
          </view>
          <view class="detail__step-body">
            <view class="detail__step-head">
              <text class="detail__step-label" :class="{ 'is-done': step.done }">
                {{ step.label }}
              </text>
              <text class="detail__step-time">{{ step.timeText }}</text>
            </view>
            <text v-if="step.note" class="detail__step-note">{{ step.note }}</text>
          </view>
        </view>
      </view>

      <view v-if="isRejected && record.auditRemark" class="detail__reject">
        <text class="detail__reject-title">驳回原因</text>
        <text class="detail__reject-text">{{ record.auditRemark }}</text>
      </view>

      <view class="detail__note">
        <text>
          状态流：待审核 → 审核通过 → 打款中 → 已到账；审核不通过流转为已驳回。
          到账时间后端当前未返回，因此「已到账」节点只显示完成状态。
        </text>
      </view>

      <view class="detail__buttons">
        <button class="rd-btn rd-btn--plain" @click="goList">查看全部记录</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import type { WithdrawOrderVO } from '@/api/types'
import { WITHDRAW_STATUS } from '@/api/types'
import { formatDateTime } from '@/utils/date'
import { fenToYuan } from '@/utils/money'

/**
 * 提现详情。
 * 后端没有 GET /rider/payouts/{id}，列表页把整条 PayoutView 作为 query 快照传入；
 * 刷新页面后 query 仍在，因此不依赖内存缓存。
 *
 * 时间字段来源：createdAt 申请；auditAt 审核（通过/驳回）；paidAt 打款；
 * PayoutView 没有到账时间字段，状态 30 不展示时间，避免编造。
 */

interface TimelineStep {
  key: string
  label: string
  timeText: string
  done: boolean
  reject?: boolean
  note?: string
}

const record = ref<WithdrawOrderVO | null>(null)
const error = ref('')

const isRejected = computed(() => Number(record.value?.status) === WITHDRAW_STATUS.REJECTED)

const steps = computed<TimelineStep[]>(() => {
  const order = record.value
  if (!order) {
    return []
  }
  const status = Number(order.status)
  if (status === WITHDRAW_STATUS.REJECTED) {
    return [
      {
        key: 'created',
        label: '提交申请',
        timeText: formatDateTime(order.createdAt),
        done: true,
      },
      {
        key: 'rejected',
        label: '平台驳回',
        timeText: formatDateTime(order.auditAt),
        done: true,
        reject: true,
        note: order.auditRemark || '未填写驳回原因',
      },
    ]
  }

  const approved = status >= WITHDRAW_STATUS.APPROVED
  const paying = status >= WITHDRAW_STATUS.PAYING
  const arrived = status === WITHDRAW_STATUS.ARRIVED

  return [
    {
      key: 'created',
      label: '提交申请',
      timeText: formatDateTime(order.createdAt),
      done: true,
    },
    {
      key: 'approved',
      label: '审核通过',
      timeText: approved ? formatDateTime(order.auditAt) : '待审核',
      done: approved,
    },
    {
      key: 'paying',
      label: '打款中',
      timeText: paying ? formatDateTime(order.paidAt) : '待打款',
      done: paying,
    },
    {
      key: 'arrived',
      label: '已到账',
      timeText: arrived ? '到账时间后端未返回' : '待到账',
      done: arrived,
    },
  ]
})

onLoad((options) => {
  const raw = options?.data
  if (!raw) {
    error.value = '缺少提现单参数，请从提现记录列表进入'
    return
  }
  const parsed = parsePayload(String(raw))
  if (!parsed || typeof parsed !== 'object' || parsed.id === undefined) {
    error.value = '提现单参数格式不正确，请返回列表重新进入'
    return
  }
  record.value = parsed
})

/**
 * H5 与其他端对 query 的解码时机不一致：先按原样解析，失败再 decode 一次，
 * 避免对已解码的 JSON 二次 decode 造成 URIError。
 */
function parsePayload(raw: string): WithdrawOrderVO | null {
  try {
    return JSON.parse(raw) as WithdrawOrderVO
  } catch (err) {
    try {
      return JSON.parse(decodeURIComponent(raw)) as WithdrawOrderVO
    } catch (decodeErr) {
      error.value = '提现单参数解析失败，请返回列表重新进入'
      return null
    }
  }
}

function statusLabel(status: number): string {
  switch (Number(status)) {
    case WITHDRAW_STATUS.PENDING:
      return '待审核'
    case WITHDRAW_STATUS.APPROVED:
      return '审核通过'
    case WITHDRAW_STATUS.PAYING:
      return '打款中'
    case WITHDRAW_STATUS.ARRIVED:
      return '已到账'
    case WITHDRAW_STATUS.REJECTED:
      return '已驳回'
    default:
      return `状态 ${status}`
  }
}

function channelLabel(channel: number): string {
  if (Number(channel) === 1) {
    return '支付宝'
  }
  if (Number(channel) === 2) {
    return '银行卡'
  }
  return '未指定渠道'
}

function goList(): void {
  uni.redirectTo({ url: '/pages/withdraw/List' })
}
</script>

<style scoped>
.detail {
  padding-bottom: 60rpx;
}

.detail__hero {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  padding: 36rpx 32rpx;
  border-radius: 24rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #2c4467, #16233a);
}

.detail__hero-label {
  font-size: 24rpx;
  opacity: 0.85;
}

.detail__hero-value {
  font-size: 64rpx;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.detail__hero-status {
  align-self: flex-start;
  margin-top: 6rpx;
  padding: 6rpx 18rpx;
  border-radius: 999rpx;
  background-color: rgba(255, 255, 255, 0.16);
  font-size: 22rpx;
}

.detail__info {
  margin-top: 20rpx;
  padding: 8rpx 24rpx;
}

.detail__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
  min-height: 92rpx;
}

.detail__label {
  font-size: 26rpx;
  color: #7a8699;
}

.detail__value {
  flex: 1;
  font-size: 26rpx;
  color: #16233a;
  text-align: right;
  word-break: break-all;
}

.detail__timeline {
  margin-top: 20rpx;
  padding: 24rpx;
}

.detail__section-title {
  display: block;
  margin-bottom: 20rpx;
  font-size: 30rpx;
  font-weight: 700;
  color: #16233a;
}

.detail__step {
  display: flex;
  gap: 20rpx;
}

.detail__step-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 24rpx;
}

.detail__dot {
  width: 18rpx;
  height: 18rpx;
  margin-top: 10rpx;
  border-radius: 50%;
  background-color: #dfe6ef;
}

.detail__dot.is-done {
  background-color: #ff7a00;
}

.detail__dot.is-reject {
  background-color: #d93a2b;
}

.detail__line {
  flex: 1;
  width: 2rpx;
  min-height: 56rpx;
  background-color: #edf1f6;
}

.detail__line.is-done {
  background-color: #ffc79a;
}

.detail__step-body {
  flex: 1;
  padding-bottom: 26rpx;
}

.detail__step-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16rpx;
}

.detail__step-label {
  font-size: 27rpx;
  color: #8d99aa;
}

.detail__step-label.is-done {
  color: #16233a;
  font-weight: 600;
}

.detail__step-time {
  font-size: 22rpx;
  color: #a9b4c4;
  text-align: right;
}

.detail__step-note {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #7a8699;
  line-height: 1.6;
}

.detail__reject {
  margin-top: 20rpx;
  padding: 20rpx 24rpx;
  border-radius: 16rpx;
  background-color: #fff1f0;
}

.detail__reject-title {
  display: block;
  font-size: 24rpx;
  font-weight: 700;
  color: #b42318;
}

.detail__reject-text {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #7a2a22;
  line-height: 1.6;
}

.detail__note {
  margin-top: 24rpx;
  padding: 16rpx 20rpx;
  border-radius: 12rpx;
  background-color: #f6f8fb;
  font-size: 21rpx;
  color: #7a8699;
  line-height: 1.6;
}

.detail__buttons {
  margin-top: 28rpx;
}
</style>
