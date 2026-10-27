<template>
  <view class="growth">
    <view class="growth__card">
      <text class="growth__label">当前成长值</text>
      <text class="growth__value">{{ profile ? profile.growth : '--' }}</text>
      <view class="growth__meta">
        <text>{{ levelName }}</text>
        <text v-if="profile && !profile.maxLevel && profile.nextThreshold > 0">
          距离下一等级还差 {{ Math.max(0, profile.growthToNext) }}
        </text>
        <text v-else-if="profile">已达最高等级</text>
      </view>
    </view>

    <view class="se-between growth__section-head">
      <text class="growth__section-title">成长值明细</text>
      <text v-if="logs.length > 0" class="growth__refresh" @click="refreshAll">刷新</text>
    </view>

    <view v-if="error" class="growth__error">
      <text>{{ error }}</text>
      <text class="growth__error-retry" @click="refreshAll">重试</text>
    </view>

    <view v-if="loading && logs.length === 0" class="se-loading">正在加载成长值明细…</view>

    <view v-else-if="logs.length === 0" class="se-empty">
      <text class="se-empty__title">暂无成长值记录</text>
      <text>签到、下单、评价与邀请后会在这里产生记录</text>
    </view>

    <view v-else class="se-card growth__list">
      <view v-for="log in logs" :key="log.id" class="growth__row">
        <view class="growth__row-main">
          <text class="growth__row-source">{{ sourceLabel(log.source) }}</text>
          <text v-if="log.remark" class="growth__row-remark">{{ log.remark }}</text>
          <text class="growth__row-time">{{ formatDateTime(log.createdAt) }}</text>
        </view>
        <text class="growth__row-value" :class="log.growth < 0 ? 'is-out' : 'is-in'">
          {{ log.growth > 0 ? '+' : '' }}{{ log.growth }}
        </text>
      </view>
    </view>

    <view v-if="hasMore" class="growth__more" @click="loadMore">
      {{ loading ? '加载中…' : '加载更多' }}
    </view>
    <view v-else-if="logs.length > 0" class="growth__end">没有更多记录了</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { fetchMemberProfile } from '@/api/mine'
import { fetchGrowthLogs } from '@/api/member'
import type { GrowthLogVO } from '@/api/member'
import type { MemberProfileVO } from '@/api/mine'
import { formatDateTime } from '@/utils/date'
import { useAuthStore } from '@/stores/auth'

/**
 * 成长值明细。
 *
 * /c/member/growth-logs 返回裸数组且没有 total，因此「还有下一页」只能靠
 * 本次返回条数是否等于 pageSize 判断；末页刚好整页时会多一次空请求，
 * 结果为空后 hasMore 置 false，不会无限加载。
 */

const PAGE_SIZE = 20

const SOURCE_LABELS: Record<number, string> = {
  1: '消费奖励',
  2: '签到奖励',
  3: '评价奖励',
  4: '邀请奖励',
  5: '手工调整',
}

const auth = useAuthStore()

const profile = ref<MemberProfileVO | null>(null)
const logs = ref<GrowthLogVO[]>([])
const pageNum = ref(0)
const hasMore = ref(false)
const loading = ref(false)
const error = ref('')

let sequence = 0

const levelName = computed(() => profile.value?.levelName || '普通会员')

onShow(() => {
  if (!auth.ensureLogin('/pages/member/Growth')) {
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
  const [profileResult, listResult] = await Promise.allSettled([
    fetchMemberProfile(true),
    fetchGrowthLogs(1, PAGE_SIZE, true),
  ])
  if (current !== sequence) {
    loading.value = false
    uni.stopPullDownRefresh()
    return
  }
  if (profileResult.status === 'fulfilled') {
    profile.value = profileResult.value
  }
  if (listResult.status === 'fulfilled') {
    logs.value = listResult.value
    pageNum.value = 1
    hasMore.value = listResult.value.length === PAGE_SIZE
  } else {
    // 保留已有列表，只提示失败
    error.value = (listResult.reason as Error)?.message || '成长值明细加载失败'
    uni.showToast({ title: error.value, icon: 'none' })
  }
  if (profileResult.status === 'rejected' && !error.value) {
    error.value = (profileResult.reason as Error)?.message || '会员信息加载失败'
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
    const list = await fetchGrowthLogs(pageNum.value + 1, PAGE_SIZE, true)
    if (current !== sequence) {
      return
    }
    logs.value = logs.value.concat(list)
    pageNum.value += 1
    hasMore.value = list.length === PAGE_SIZE
  } catch (err) {
    if (current === sequence) {
      error.value = (err as Error)?.message || '成长值明细加载失败'
      uni.showToast({ title: error.value, icon: 'none' })
    }
  } finally {
    if (current === sequence) {
      loading.value = false
    }
  }
}

function sourceLabel(source: number): string {
  return SOURCE_LABELS[source] || '成长值变动'
}
</script>

<style scoped>
.growth {
  min-height: 100vh;
  padding: 24rpx;
  background-color: #f5f5f7;
}

.growth__card {
  padding: 32rpx;
  border-radius: 12rpx;
  background: linear-gradient(120deg, #ff8a1f 0%, #ff5c00 100%);
  color: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(255, 92, 0, 0.22);
}

.growth__label {
  font-size: 24rpx;
  opacity: 0.9;
}

.growth__value {
  display: block;
  margin-top: 8rpx;
  font-size: 64rpx;
  font-weight: 600;
  letter-spacing: 2rpx;
}

.growth__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 24rpx;
  margin-top: 16rpx;
  font-size: 22rpx;
  opacity: 0.92;
}

.growth__section-head {
  padding: 32rpx 8rpx 16rpx;
}

.growth__section-title {
  font-size: 30rpx;
  font-weight: 600;
}

.growth__refresh {
  color: #ff7a00;
  font-size: 24rpx;
}

.growth__error {
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

.growth__error-retry {
  color: #ff7a00;
  margin-left: 16rpx;
}

.growth__list {
  padding: 0 24rpx;
}

.growth__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f5f5f5;
}

.growth__row:last-child {
  border-bottom: none;
}

.growth__row-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.growth__row-source {
  font-size: 27rpx;
  color: #1f1f1f;
  font-weight: 500;
}

.growth__row-remark {
  color: #6f6f6f;
  font-size: 22rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.growth__row-time {
  color: #a0a0a0;
  font-size: 22rpx;
}

.growth__row-value {
  margin-left: 20rpx;
  font-size: 30rpx;
  font-weight: 600;
  flex-shrink: 0;
}

.growth__row-value.is-in {
  color: #52c41a;
}

.growth__row-value.is-out {
  color: #ff4d4f;
}

.growth__more,
.growth__end {
  padding: 28rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #8a8a8a;
}

.growth__more {
  color: #ff7a00;
}
</style>
