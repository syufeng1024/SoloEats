<template>
  <view class="ticket">
    <view v-if="loading && !detail" class="se-loading">正在加载工单详情…</view>

    <view v-else-if="error && !detail" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="loadDetail">重新加载</view>
    </view>

    <view v-else-if="detail" class="ticket__body">
      <view class="se-card ticket__head">
        <view class="se-between ticket__head-row">
          <text class="ticket__subject">{{ detail.ticket.subject }}</text>
          <text class="ticket__status" :class="statusClass(detail.ticket.status)">
            {{ statusLabel(detail.ticket.status) }}
          </text>
        </view>
        <view class="ticket__meta">
          <text class="se-tag">{{ typeLabel(detail.ticket.ticketType) }}</text>
          <text v-if="detail.ticket.overdue" class="ticket__overdue">已超时</text>
          <text v-if="detail.ticket.priority && detail.ticket.priority > 0" class="ticket__priority">
            {{ priorityLabel(detail.ticket.priority) }}
          </text>
        </view>
        <view class="ticket__info-row">
          <text class="ticket__label">工单号</text>
          <text class="ticket__value">{{ detail.ticket.ticketNo }}</text>
        </view>
        <view class="ticket__info-row">
          <text class="ticket__label">提交时间</text>
          <text class="ticket__value">{{ formatDateTime(detail.ticket.createdAt) }}</text>
        </view>
        <view v-if="detail.ticket.finishAt" class="ticket__info-row">
          <text class="ticket__label">办结时间</text>
          <text class="ticket__value">{{ formatDateTime(detail.ticket.finishAt) }}</text>
        </view>
        <view
          v-if="detail.ticket.orderId"
          class="ticket__info-row ticket__info-row--link"
          @click="openOrder"
        >
          <text class="ticket__label">关联订单</text>
          <text class="ticket__value ticket__value--link">订单 {{ detail.ticket.orderId }} ›</text>
        </view>
      </view>

      <view class="se-card ticket__card">
        <text class="ticket__card-title">问题描述</text>
        <text class="ticket__content">{{ detail.ticket.content }}</text>
        <view v-if="ticketImages.length > 0" class="ticket__images">
          <image
            v-for="(url, index) in ticketImages"
            :key="`${url}-${index}`"
            class="ticket__image"
            :src="url"
            mode="aspectFill"
          />
        </view>
      </view>

      <view class="se-card ticket__card">
        <text class="ticket__card-title">流转记录</text>
        <view v-if="detail.records.length === 0" class="ticket__timeline-empty">
          暂无流转记录
        </view>
        <view v-else class="ticket__timeline">
          <view v-for="(record, index) in detail.records" :key="record.id" class="ticket__record">
            <view class="ticket__record-rail">
              <view class="ticket__dot" :class="{ 'is-current': index === detail.records.length - 1 }" />
              <view v-if="index < detail.records.length - 1" class="ticket__line" />
            </view>
            <view class="ticket__record-body">
              <view class="se-between ticket__record-head">
                <text class="ticket__record-title">{{ recordTitle(record) }}</text>
                <text class="ticket__record-time">{{ formatDateTime(record.createdAt) }}</text>
              </view>
              <text v-if="record.content" class="ticket__record-content">{{ record.content }}</text>
              <text class="ticket__record-operator">
                {{ record.operatorName || '系统' }}{{ transitionText(record) }}
              </text>
            </view>
          </view>
        </view>
      </view>

      <view v-if="detail.ticket.status === 3" class="se-card ticket__card">
        <text class="ticket__card-title">服务满意度</text>

        <view v-if="detail.ticket.satisfaction" class="ticket__rated">
          <text class="ticket__stars">{{ starText(detail.ticket.satisfaction) }}</text>
          <text class="ticket__rated-text">{{ detail.ticket.satisfaction }} 分</text>
          <text v-if="detail.ticket.satisfactionRemark" class="ticket__rated-remark">
            {{ detail.ticket.satisfactionRemark }}
          </text>
        </view>

        <view v-else>
          <view class="ticket__rate-stars">
            <text
              v-for="value in STAR_VALUES"
              :key="value"
              class="ticket__rate-star"
              :class="{ 'is-on': value <= satisfaction }"
              @click="satisfaction = value"
            >
              ★
            </text>
            <text class="ticket__rate-hint">{{ satisfaction ? `${satisfaction} 分` : '请选择评分' }}</text>
          </view>
          <textarea
            v-model="satisfactionRemark"
            class="ticket__textarea"
            maxlength="500"
            placeholder="补充说明（选填，最多 500 字）"
            placeholder-class="ticket__placeholder"
          />
          <view class="ticket__counter">{{ satisfactionRemark.length }}/500</view>
          <button
            class="se-btn se-btn-primary ticket__submit"
            :disabled="submitting || satisfaction === 0"
            @click="submitSatisfaction"
          >
            {{ submitting ? '提交中…' : '提交评价' }}
          </button>
        </view>
      </view>

      <view v-if="detail.ticket.status === 0" class="ticket__actions">
        <button class="se-btn se-btn-plain" :disabled="closing" @click="handleClose">
          {{ closing ? '撤销中…' : '撤销工单' }}
        </button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  closeTicket,
  fetchTicketDetail,
  submitTicketSatisfaction,
} from '@/api/mine'
import type { TicketDetailVO, TicketRecordVO } from '@/api/mine'
import { formatDateTime } from '@/utils/date'
import { useAuthStore } from '@/stores/auth'

/**
 * 工单详情。
 * 接口：GET /c/tickets/{id} 返回 { ticket, records, complaint }；
 * 评价：PUT /c/tickets/{id}/satisfaction（仅 status=3 且未评价时后端接受）；
 * 撤单：PUT /c/tickets/{id}/close（仅 status=0 时后端接受）。
 */

const STATUS_LABELS: Record<number, string> = {
  0: '待受理',
  1: '处理中',
  2: '待回访',
  3: '已办结',
  4: '已关闭',
}

const TYPE_LABELS: Record<number, string> = {
  1: '订单问题',
  2: '退款',
  3: '配送',
  4: '投诉',
  5: '建议',
}

const PRIORITY_LABELS: Record<number, string> = {
  0: '普通',
  1: '紧急',
  2: '特急',
}

const ACTION_LABELS: Record<string, string> = {
  CREATED: '提交工单',
  ACCEPT: '客服受理',
  ASSIGN: '工单转派',
  HANDLE: '处理中',
  CALLBACK: '进入回访',
  FINISH: '工单办结',
  CLOSE: '关闭工单',
  USER_CLOSE: '用户撤单',
  SATISFACTION: '满意度评价',
}

const STAR_VALUES = [1, 2, 3, 4, 5]

const auth = useAuthStore()

const ticketId = ref(0)
const detail = ref<TicketDetailVO | null>(null)
const loading = ref(false)
const error = ref('')
const satisfaction = ref(0)
const satisfactionRemark = ref('')
const submitting = ref(false)
const closing = ref(false)

const ticketImages = computed(() => {
  const images = detail.value?.ticket.images
  return Array.isArray(images) ? images.filter((url) => Boolean(url)) : []
})

onLoad((options) => {
  const raw = Number(options?.id)
  if (!Number.isFinite(raw) || raw <= 0) {
    error.value = '工单编号无效'
    return
  }
  ticketId.value = raw
  if (!auth.ensureLogin(`/pages/mine/TicketDetail?id=${raw}`)) {
    return
  }
  loadDetail()
})

async function loadDetail(): Promise<void> {
  if (loading.value || ticketId.value <= 0) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    detail.value = await fetchTicketDetail(ticketId.value)
  } catch (err) {
    // 失败不清空已加载的详情，只展示错误
    error.value = (err as Error)?.message || '工单详情加载失败'
  } finally {
    loading.value = false
  }
}

function statusLabel(status: number | null): string {
  if (status === null || status === undefined) {
    return '--'
  }
  return STATUS_LABELS[status] || `状态 ${status}`
}

function typeLabel(type: number): string {
  return TYPE_LABELS[type] || `类型 ${type}`
}

function priorityLabel(priority: number): string {
  return PRIORITY_LABELS[priority] || '普通'
}

function statusClass(status: number): string {
  if (status === 3) {
    return 'is-done'
  }
  if (status === 4) {
    return 'is-muted'
  }
  return 'is-active'
}

function recordTitle(record: TicketRecordVO): string {
  if (record.fromStatus === null) {
    return '提交工单'
  }
  return `${statusLabel(record.toStatus)} · ${ACTION_LABELS[record.action || ''] || '状态变更'}`
}

/** 仅在状态真正发生变化时展示「旧状态 → 新状态」，同状态操作（转派、评价）不重复。 */
function transitionText(record: TicketRecordVO): string {
  if (record.fromStatus === null || record.toStatus === null) {
    return ''
  }
  if (record.fromStatus === record.toStatus) {
    return ''
  }
  return ` · ${statusLabel(record.fromStatus)} → ${statusLabel(record.toStatus)}`
}

function starText(score: number): string {
  const filled = Math.max(0, Math.min(5, Number(score) || 0))
  return '★'.repeat(filled) + '☆'.repeat(5 - filled)
}

function openOrder(): void {
  const orderId = detail.value?.ticket.orderId
  if (!orderId) {
    return
  }
  uni.navigateTo({ url: `/pages/order/Detail?id=${orderId}` })
}

async function submitSatisfaction(): Promise<void> {
  if (submitting.value) {
    return
  }
  if (satisfaction.value < 1 || satisfaction.value > 5) {
    uni.showToast({ title: '请选择 1~5 星评分', icon: 'none' })
    return
  }
  if (satisfactionRemark.value.length > 500) {
    uni.showToast({ title: '补充说明最多 500 字', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await submitTicketSatisfaction(
      ticketId.value,
      satisfaction.value,
      satisfactionRemark.value.trim() || null,
    )
    uni.showToast({ title: '评价已提交', icon: 'none' })
    await loadDetail()
  } catch (err) {
    // 仅已办结且未评价的工单可评价，业务错误由 http 层提示
  } finally {
    submitting.value = false
  }
}

function handleClose(): void {
  if (closing.value) {
    return
  }
  uni.showModal({
    title: '撤销工单',
    content: '仅待受理工单可以撤销，撤销后无法恢复。确定继续？',
    confirmColor: '#ff7a00',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      closing.value = true
      try {
        await closeTicket(ticketId.value)
        uni.showToast({ title: '工单已撤销', icon: 'none' })
        await loadDetail()
      } catch (err) {
        // http 层已提示
      } finally {
        closing.value = false
      }
    },
  })
}
</script>

<style scoped>
.ticket {
  min-height: 100vh;
  background-color: #f5f5f7;
}

.ticket__body {
  padding: 20rpx 24rpx 40rpx;
}

.ticket__head,
.ticket__card {
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.ticket__head-row {
  gap: 16rpx;
}

.ticket__subject {
  flex: 1;
  min-width: 0;
  font-size: 32rpx;
  font-weight: 600;
  line-height: 1.5;
}

.ticket__status {
  flex-shrink: 0;
  font-size: 24rpx;
}

.ticket__status.is-active {
  color: #ff7a00;
}

.ticket__status.is-done {
  color: #52c41a;
}

.ticket__status.is-muted {
  color: #999999;
}

.ticket__meta {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin: 10rpx 0 6rpx;
}

.ticket__overdue {
  padding: 2rpx 10rpx;
  border-radius: 4rpx;
  background-color: #fff1f0;
  color: #ff4d4f;
  font-size: 20rpx;
}

.ticket__priority {
  color: #fa8c16;
  font-size: 20rpx;
}

.ticket__info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12rpx;
}

.ticket__info-row--link {
  margin-top: 12rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f5f5f5;
}

.ticket__label {
  color: #8a8a8a;
  font-size: 24rpx;
}

.ticket__value {
  color: #444444;
  font-size: 24rpx;
}

.ticket__value--link {
  color: #ff7a00;
}

.ticket__card-title {
  display: block;
  margin-bottom: 16rpx;
  font-size: 28rpx;
  font-weight: 600;
}

.ticket__content {
  display: block;
  color: #444444;
  font-size: 26rpx;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-all;
}

.ticket__images {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
}

.ticket__image {
  width: 160rpx;
  height: 160rpx;
  border-radius: 8rpx;
  background-color: #f5f5f7;
}

.ticket__timeline-empty {
  padding: 20rpx 0;
  color: #a0a0a0;
  font-size: 24rpx;
}

.ticket__record {
  display: flex;
}

.ticket__record-rail {
  width: 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
}

.ticket__dot {
  width: 14rpx;
  height: 14rpx;
  margin-top: 12rpx;
  border-radius: 50%;
  background-color: #d8d8dc;
}

.ticket__dot.is-current {
  background-color: #ff7a00;
  box-shadow: 0 0 0 6rpx rgba(255, 122, 0, 0.15);
}

.ticket__line {
  flex: 1;
  width: 2rpx;
  margin: 6rpx 0;
  background-color: #f0f0f0;
}

.ticket__record-body {
  flex: 1;
  min-width: 0;
  padding: 0 0 26rpx 16rpx;
}

.ticket__record-head {
  gap: 16rpx;
}

.ticket__record-title {
  font-size: 26rpx;
  font-weight: 600;
  color: #1f1f1f;
}

.ticket__record-time {
  color: #a0a0a0;
  font-size: 22rpx;
  flex-shrink: 0;
}

.ticket__record-content {
  display: block;
  margin-top: 6rpx;
  color: #6f6f6f;
  font-size: 24rpx;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.ticket__record-operator {
  display: block;
  margin-top: 6rpx;
  color: #a0a0a0;
  font-size: 22rpx;
}

.ticket__rated {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.ticket__stars {
  color: #ff7a00;
  font-size: 34rpx;
  letter-spacing: 4rpx;
}

.ticket__rated-text {
  color: #444444;
  font-size: 26rpx;
}

.ticket__rated-remark {
  color: #6f6f6f;
  font-size: 24rpx;
  line-height: 1.6;
}

.ticket__rate-stars {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.ticket__rate-star {
  color: #d8d8dc;
  font-size: 52rpx;
  line-height: 1;
}

.ticket__rate-star.is-on {
  color: #ff7a00;
}

.ticket__rate-hint {
  margin-left: 8rpx;
  color: #8a8a8a;
  font-size: 24rpx;
}

.ticket__textarea {
  width: 100%;
  height: 160rpx;
  margin-top: 20rpx;
  padding: 16rpx;
  border-radius: 8rpx;
  background-color: #f7f7f8;
  font-size: 26rpx;
  color: #1f1f1f;
}

.ticket__placeholder {
  color: #b0b0b0;
  font-size: 24rpx;
}

.ticket__counter {
  margin-top: 8rpx;
  text-align: right;
  color: #a0a0a0;
  font-size: 22rpx;
}

.ticket__submit {
  margin-top: 20rpx;
}

.ticket__actions {
  padding: 12rpx 0 24rpx;
}
</style>
