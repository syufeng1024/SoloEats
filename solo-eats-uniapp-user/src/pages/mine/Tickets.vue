<template>
  <view class="tickets">
    <scroll-view scroll-x class="tickets__tabs" :show-scrollbar="false">
      <view
        v-for="tab in STATUS_TABS"
        :key="tab.key"
        class="tickets__tab"
        :class="{ 'is-active': tab.key === activeKey }"
        @click="changeTab(tab.key)"
      >
        {{ tab.label }}
      </view>
    </scroll-view>

    <view class="tickets__body">
      <view v-if="loading && tickets.length === 0" class="se-loading">正在加载工单…</view>

      <view v-else-if="error && tickets.length === 0" class="se-error">
        <text>{{ error }}</text>
        <view class="se-error__retry" @click="load(true)">重新加载</view>
      </view>

      <view v-else-if="tickets.length === 0" class="se-empty">
        <text class="se-empty__title">{{ emptyTitle }}</text>
        <text>订单、退款、配送问题都可以提交工单</text>
        <view class="se-error__retry tickets__cta" @click="goFeedback">去反馈</view>
      </view>

      <view v-else>
        <view v-if="error" class="tickets__error">
          <text>{{ error }}</text>
          <text class="tickets__error-retry" @click="load(true)">重试</text>
        </view>

        <view
          v-for="ticket in tickets"
          :key="ticket.id"
          class="se-card tickets__card"
          @click="openDetail(ticket)"
        >
          <view class="se-between tickets__head">
            <text class="tickets__subject">{{ ticket.subject }}</text>
            <text class="tickets__status" :class="statusClass(ticket.status)">
              {{ statusLabel(ticket.status) }}
            </text>
          </view>

          <view class="tickets__meta">
            <text class="se-tag">{{ typeLabel(ticket.ticketType) }}</text>
            <text v-if="ticket.overdue" class="tickets__overdue">已超时</text>
            <text v-if="ticket.priority && ticket.priority > 0" class="tickets__priority">
              {{ priorityLabel(ticket.priority) }}
            </text>
          </view>

          <view class="tickets__no">工单号 {{ ticket.ticketNo }}</view>

          <view class="se-between tickets__foot">
            <text class="se-muted">{{ formatDateTime(ticket.createdAt) }}</text>
            <text class="tickets__arrow">查看详情 ›</text>
          </view>
        </view>

        <view v-if="hasMore" class="tickets__more" @click="load(false)">
          {{ loading ? '加载中…' : '加载更多' }}
        </view>
        <view v-else class="tickets__end">没有更多工单了</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { fetchTickets } from '@/api/mine'
import type { TicketVO } from '@/api/mine'
import { formatDateTime } from '@/utils/date'
import { useAuthStore } from '@/stores/auth'

/**
 * 我的工单。
 * GET /c/tickets 只接受单个 status 精确过滤，分页参数 pageNum 从 1 开始。
 * 状态：0待受理 1处理中 2待回访 3已办结 4已关闭。
 */

interface StatusTab {
  key: string
  label: string
  status: number | undefined
}

const PAGE_SIZE = 10

const STATUS_TABS: StatusTab[] = [
  { key: 'all', label: '全部', status: undefined },
  { key: 'pending', label: '待受理', status: 0 },
  { key: 'processing', label: '处理中', status: 1 },
  { key: 'callback', label: '待回访', status: 2 },
  { key: 'finished', label: '已办结', status: 3 },
  { key: 'closed', label: '已关闭', status: 4 },
]

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

const auth = useAuthStore()

const activeKey = ref('all')
const tickets = ref<TicketVO[]>([])
const pageNum = ref(0)
const hasMore = ref(false)
const loading = ref(false)
const error = ref('')

let loadSequence = 0

const currentTab = computed<StatusTab>(
  () => STATUS_TABS.find((tab) => tab.key === activeKey.value) || STATUS_TABS[0],
)

const emptyTitle = computed(() =>
  activeKey.value === 'all' ? '暂无工单记录' : `暂无${currentTab.value.label}的工单`,
)

onShow(() => {
  if (!auth.ensureLogin('/pages/mine/Tickets')) {
    return
  }
  load(true)
})

onPullDownRefresh(() => {
  if (loading.value) {
    uni.stopPullDownRefresh()
    return
  }
  load(true)
})

function changeTab(key: string): void {
  if (activeKey.value === key) {
    return
  }
  activeKey.value = key
  tickets.value = []
  pageNum.value = 0
  hasMore.value = false
  error.value = ''
  load(true)
}

async function load(reset: boolean): Promise<void> {
  // 切换筛选/下拉刷新允许并发，用 sequence 只采纳最后一次结果；
  // 「加载更多」仍要求上一页完成，避免同一页重复追加。
  if (loading.value && !reset) {
    return
  }
  const sequence = ++loadSequence
  loading.value = true
  error.value = ''
  try {
    const page = await fetchTickets(
      currentTab.value.status,
      reset ? 1 : pageNum.value + 1,
      PAGE_SIZE,
    )
    if (sequence !== loadSequence) {
      return
    }
    tickets.value = reset ? page.list : tickets.value.concat(page.list)
    pageNum.value = page.pageNum
    hasMore.value = tickets.value.length < page.total
  } catch (err) {
    // 失败时保留已有列表，仅在顶部展示错误条
    if (sequence === loadSequence) {
      error.value = (err as Error)?.message || '工单加载失败'
      if (reset && tickets.value.length === 0) {
        hasMore.value = false
      }
    }
  } finally {
    if (sequence === loadSequence) {
      loading.value = false
      uni.stopPullDownRefresh()
    }
  }
}

function statusLabel(status: number): string {
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

function openDetail(ticket: TicketVO): void {
  uni.navigateTo({ url: `/pages/mine/TicketDetail?id=${ticket.id}` })
}

function goFeedback(): void {
  uni.navigateTo({ url: '/pages/mine/Feedback' })
}
</script>

<style scoped>
.tickets {
  min-height: 100vh;
  background-color: #f5f5f7;
}

.tickets__tabs {
  position: sticky;
  top: 0;
  z-index: 10;
  white-space: nowrap;
  background-color: #ffffff;
  box-shadow: 0 2rpx 8rpx rgba(20, 20, 20, 0.04);
}

.tickets__tab {
  display: inline-block;
  padding: 24rpx 0;
  margin: 0 24rpx;
  font-size: 28rpx;
  color: #555555;
  position: relative;
}

.tickets__tab.is-active {
  color: #ff7a00;
  font-weight: 600;
}

.tickets__tab.is-active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 8rpx;
  width: 40rpx;
  height: 6rpx;
  margin-left: -20rpx;
  border-radius: 3rpx;
  background-color: #ff7a00;
}

.tickets__body {
  padding: 20rpx 24rpx 40rpx;
}

.tickets__cta {
  margin-top: 24rpx;
}

.tickets__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
  padding: 14rpx 20rpx;
  border-radius: 8rpx;
  background-color: #fff8ef;
  color: #a26a2a;
  font-size: 22rpx;
}

.tickets__error-retry {
  color: #ff7a00;
  margin-left: 16rpx;
}

.tickets__card {
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.tickets__head {
  gap: 16rpx;
  margin-bottom: 10rpx;
}

.tickets__subject {
  flex: 1;
  min-width: 0;
  font-size: 30rpx;
  font-weight: 600;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.tickets__status {
  flex-shrink: 0;
  font-size: 24rpx;
}

.tickets__status.is-active {
  color: #ff7a00;
}

.tickets__status.is-done {
  color: #52c41a;
}

.tickets__status.is-muted {
  color: #999999;
}

.tickets__meta {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 8rpx;
}

.tickets__overdue {
  padding: 2rpx 10rpx;
  border-radius: 4rpx;
  background-color: #fff1f0;
  color: #ff4d4f;
  font-size: 20rpx;
}

.tickets__priority {
  color: #fa8c16;
  font-size: 20rpx;
}

.tickets__no {
  color: #a0a0a0;
  font-size: 22rpx;
}

.tickets__foot {
  margin-top: 14rpx;
  padding-top: 14rpx;
  border-top: 1rpx solid #f5f5f5;
}

.tickets__arrow {
  color: #8a8a8a;
  font-size: 22rpx;
}

.tickets__more,
.tickets__end {
  padding: 24rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #8a8a8a;
}

.tickets__more {
  color: #ff7a00;
}
</style>
