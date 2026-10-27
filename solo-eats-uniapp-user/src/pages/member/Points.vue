<template>
  <view class="points">
    <view class="points__card">
      <text class="points__label">积分余额</text>
      <text class="points__balance">{{ account ? account.balance : '--' }}</text>
      <view class="points__meta">
        <text>累计获得 {{ account ? account.totalEarned : '--' }}</text>
        <text>累计消耗 {{ account ? account.totalConsumed : '--' }}</text>
      </view>
    </view>

    <view class="se-between points__section-head">
      <text class="points__section-title">积分明细</text>
      <text v-if="logs.length > 0" class="points__refresh" @click="refreshAll">刷新</text>
    </view>

    <view v-if="error" class="points__error">
      <text>{{ error }}</text>
      <text class="points__error-retry" @click="refreshAll">重试</text>
    </view>

    <view v-if="loading && logs.length === 0" class="se-loading">正在加载积分明细…</view>

    <view v-else-if="logs.length === 0" class="se-empty">
      <text class="se-empty__title">暂无积分记录</text>
      <text>签到、下单、评价与邀请后会在这里产生记录</text>
    </view>

    <view v-else class="se-card points__list">
      <view v-for="log in logs" :key="log.id" class="points__row">
        <view class="points__row-main">
          <text class="points__row-source">{{ sourceLabel(log.source) }}</text>
          <text v-if="log.remark" class="points__row-remark">{{ log.remark }}</text>
          <text class="points__row-time">{{ formatDateTime(log.createdAt) }}</text>
        </view>
        <view class="points__row-side">
          <text class="points__row-value" :class="log.point < 0 ? 'is-out' : 'is-in'">
            {{ log.point > 0 ? '+' : '' }}{{ log.point }}
          </text>
          <text class="points__row-balance">余额 {{ log.afterBalance }}</text>
        </view>
      </view>
    </view>

    <view v-if="hasMore" class="points__more" @click="loadMore">
      {{ loading ? '加载中…' : '加载更多' }}
    </view>
    <view v-else-if="logs.length > 0" class="points__end">没有更多记录了</view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { fetchPointAccount, fetchPointLogs } from '@/api/mine'
import type { PointAccountVO, PointLogVO } from '@/api/mine'
import { formatDateTime } from '@/utils/date'
import { useAuthStore } from '@/stores/auth'

/**
 * 积分明细。
 *
 * 余额卡与明细分两个接口：/c/points 给账户汇总，/c/points/logs 分页给流水。
 * point 可负（兑换、过期），颜色按收入绿、支出红。
 */

const PAGE_SIZE = 20

const SOURCE_LABELS: Record<number, string> = {
  1: '下单奖励',
  2: '签到奖励',
  3: '评价奖励',
  4: '邀请奖励',
  5: '积分兑换',
  6: '积分过期',
  7: '手工调整',
}

const auth = useAuthStore()

const account = ref<PointAccountVO | null>(null)
const logs = ref<PointLogVO[]>([])
const pageNum = ref(0)
const hasMore = ref(false)
const loading = ref(false)
const error = ref('')

let sequence = 0

onShow(() => {
  if (!auth.ensureLogin('/pages/member/Points')) {
    return
  }
  refreshAll()
})

onPullDownRefresh(() => {
  if (loading.value) {
    uni.stopPullDownRefresh()
    return
  }
  refreshAll()
})

async function refreshAll(): Promise<void> {
  const current = ++sequence
  loading.value = true
  error.value = ''
  const results = await Promise.allSettled([
    fetchPointAccount(true),
    fetchPointLogs(1, PAGE_SIZE, true),
  ])
  if (current !== sequence) {
    loading.value = false
    uni.stopPullDownRefresh()
    return
  }
  const [accountResult, logResult] = results
  if (accountResult.status === 'fulfilled') {
    account.value = accountResult.value
  }
  if (logResult.status === 'fulfilled') {
    logs.value = logResult.value.list
    pageNum.value = logResult.value.pageNum
    hasMore.value = logResult.value.list.length < logResult.value.total
  } else {
    error.value = (logResult.reason as Error)?.message || '积分明细加载失败'
    uni.showToast({ title: error.value, icon: 'none' })
  }
  if (accountResult.status === 'rejected' && !error.value) {
    error.value = (accountResult.reason as Error)?.message || '积分账户加载失败'
  }
  loading.value = false
  uni.stopPullDownRefresh()
}

async function loadMore(): Promise<void> {
  if (loading.value || !hasMore.value) {
    return
  }
  const current = ++sequence
  loading.value = true
  error.value = ''
  try {
    const page = await fetchPointLogs(pageNum.value + 1, PAGE_SIZE, true)
    if (current !== sequence) {
      return
    }
    logs.value = logs.value.concat(page.list)
    pageNum.value = page.pageNum
    hasMore.value = logs.value.length < page.total
  } catch (err) {
    if (current === sequence) {
      error.value = (err as Error)?.message || '积分明细加载失败'
      uni.showToast({ title: error.value, icon: 'none' })
    }
  } finally {
    if (current === sequence) {
      loading.value = false
    }
  }
}

function sourceLabel(source: number): string {
  return SOURCE_LABELS[source] || '积分变动'
}
</script>

<style scoped>
.points {
  min-height: 100vh;
  padding: 24rpx;
  background-color: #f5f5f7;
}

.points__card {
  padding: 32rpx;
  border-radius: 12rpx;
  background: linear-gradient(120deg, #ffb347 0%, #ff8a1f 100%);
  color: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(255, 138, 31, 0.24);
}

.points__label {
  font-size: 24rpx;
  opacity: 0.9;
}

.points__balance {
  display: block;
  margin-top: 8rpx;
  font-size: 64rpx;
  font-weight: 600;
  letter-spacing: 2rpx;
}

.points__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 24rpx;
  margin-top: 16rpx;
  font-size: 22rpx;
  opacity: 0.92;
}

.points__section-head {
  padding: 32rpx 8rpx 16rpx;
}

.points__section-title {
  font-size: 30rpx;
  font-weight: 600;
}

.points__refresh {
  color: #ff7a00;
  font-size: 24rpx;
}

.points__error {
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

.points__error-retry {
  color: #ff7a00;
  margin-left: 16rpx;
}

.points__list {
  padding: 0 24rpx;
}

.points__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f5f5f5;
}

.points__row:last-child {
  border-bottom: none;
}

.points__row-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.points__row-source {
  font-size: 27rpx;
  color: #1f1f1f;
  font-weight: 500;
}

.points__row-remark {
  color: #6f6f6f;
  font-size: 22rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.points__row-time {
  color: #a0a0a0;
  font-size: 22rpx;
}

.points__row-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4rpx;
  margin-left: 20rpx;
  flex-shrink: 0;
}

.points__row-value {
  font-size: 30rpx;
  font-weight: 600;
}

.points__row-value.is-in {
  color: #52c41a;
}

.points__row-value.is-out {
  color: #ff4d4f;
}

.points__row-balance {
  color: #a0a0a0;
  font-size: 20rpx;
}

.points__more,
.points__end {
  padding: 28rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #8a8a8a;
}

.points__more {
  color: #ff7a00;
}
</style>
