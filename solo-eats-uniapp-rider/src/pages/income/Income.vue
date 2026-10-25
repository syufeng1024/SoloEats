<template>
  <view class="income">
    <view class="income__hero">
      <text class="income__hero-label">今日收入（元）</text>
      <text class="income__hero-value">{{ overview ? fenToYuan(overview.todayAmount) : '--' }}</text>
      <view class="income__hero-sub">
        <text>今日 {{ overview ? overview.todayOrders : '--' }} 单</text>
        <text class="income__hero-dot">·</text>
        <text>准时率 {{ onTimeRateText }}</text>
      </view>
    </view>

    <view class="income__stats">
      <view class="rd-card income__stat">
        <text class="income__stat-value">{{ overview ? `¥${fenToYuan(overview.weekAmount)}` : '--' }}</text>
        <text class="income__stat-label">本周收入</text>
      </view>
      <view class="rd-card income__stat">
        <text class="income__stat-value">{{ overview ? `¥${fenToYuan(overview.monthAmount)}` : '--' }}</text>
        <text class="income__stat-label">本月收入</text>
      </view>
    </view>

    <view v-if="overviewError" class="income__error">
      <text>{{ overviewError }}</text>
      <text class="income__error-retry" @click="loadOverview">重试</text>
    </view>

    <view class="income__section-head">
      <text class="income__section-title">收入明细</text>
      <text class="income__section-extra">{{ detailTotal }} 笔</text>
    </view>

    <view v-if="loading && details.length === 0" class="rd-loading">正在加载收入明细…</view>

    <view v-else-if="error && details.length === 0" class="rd-error">
      <text class="rd-error__msg">{{ error }}</text>
      <view class="rd-error__retry" @click="loadDetails(true)">重新加载</view>
    </view>

    <view v-else-if="details.length === 0" class="rd-empty income__empty">
      <text class="rd-empty__title">暂无收入明细</text>
      <text>完成配送并送达后，收入会按单结算到这里</text>
    </view>

    <view v-else>
      <view v-for="item in details" :key="item.id" class="rd-card income__row">
        <view class="rd-between income__row-head">
          <text class="income__row-date">{{ item.settlementDate || '--' }}</text>
          <text
            class="income__row-status"
            :class="item.status === 1 ? 'is-settled' : 'is-pending'"
          >
            {{ item.status === 1 ? '已结算' : '待结算' }}
          </text>
        </view>

        <view class="income__row-main">
          <view class="income__parts">
            <text v-if="item.baseFee" class="income__part">基础 {{ fenToYuan(item.baseFee) }}</text>
            <text v-if="item.distanceFee" class="income__part">
              距离 {{ fenToYuan(item.distanceFee) }}
            </text>
            <text v-if="item.timeSubsidy" class="income__part">
              时段 {{ fenToYuan(item.timeSubsidy) }}
            </text>
            <text v-if="item.weatherSubsidy" class="income__part">
              天气 {{ fenToYuan(item.weatherSubsidy) }}
            </text>
            <text v-if="item.tipAmount" class="income__part">小费 {{ fenToYuan(item.tipAmount) }}</text>
            <text v-if="item.compensationAmount" class="income__part">
              补偿 {{ fenToYuan(item.compensationAmount) }}
            </text>
            <text v-if="item.penaltyAmount" class="income__part is-negative">
              罚款 -{{ fenToYuan(item.penaltyAmount) }}
            </text>
          </view>
          <text class="income__row-total rd-price">{{ fenToYuan(item.totalAmount) }}</text>
        </view>

        <view class="income__row-foot">
          配送单 #{{ item.deliveryOrderId }}
          <text v-if="item.createdAt" class="income__row-time">
            · {{ formatDateTime(item.createdAt) }}
          </text>
        </view>
      </view>

      <view v-if="hasMore" class="income__more" @click="loadMore">
        {{ loading ? '加载中…' : '加载更多' }}
      </view>
      <view v-else class="income__end">没有更多明细了</view>
    </view>

    <view class="income__note">
      明细为逐单构成，收入总额以顶部概览接口为准，前端不对明细求和。
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { fetchIncomeDetails, fetchIncomeOverview } from '@/api/rider'
import type { IncomeDetailVO, IncomeOverviewVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useRiderStore } from '@/stores/rider'
import { fenToYuan } from '@/utils/money'
import { formatDateTime } from '@/utils/date'

const PAGE_SIZE = 20

const auth = useAuthStore()
const rider = useRiderStore()

const overview = ref<IncomeOverviewVO | null>(null)
const overviewError = ref('')
const details = ref<IncomeDetailVO[]>([])
const detailTotal = ref(0)
const pageNum = ref(0)
const hasMore = ref(false)
const loading = ref(false)
const error = ref('')

const onTimeRateText = computed(() => {
  if (!overview.value) {
    return '--'
  }
  const rate = Number(overview.value.onTimeRate ?? 0)
  return `${Number.isFinite(rate) ? rate.toFixed(1) : '0.0'}%`
})

onShow(() => {
  if (!auth.ensureLogin('/pages/income/Income')) {
    return
  }
  void loadOverview()
  void loadDetails(true)
})

onPullDownRefresh(() => {
  void (async () => {
    await Promise.all([loadOverview(), loadDetails(true)])
    uni.stopPullDownRefresh()
  })()
})

onReachBottom(() => {
  loadMore()
})

async function loadOverview(): Promise<void> {
  overviewError.value = ''
  try {
    const data = await fetchIncomeOverview()
    overview.value = data
    // 首页的今日单量/收入与收入页同源，顺手同步一次
    rider.applyIncomeOverview(data)
  } catch (err) {
    overviewError.value = (err as Error)?.message || '收入概览加载失败'
  }
}

async function loadDetails(reset: boolean): Promise<void> {
  if (loading.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const page = await fetchIncomeDetails({
      pageNum: reset ? 1 : pageNum.value + 1,
      pageSize: PAGE_SIZE,
    })
    const list = Array.isArray(page.list) ? page.list : []
    details.value = reset ? list : details.value.concat(list)
    pageNum.value = page.pageNum || (reset ? 1 : pageNum.value + 1)
    detailTotal.value = Number(page.total || 0)
    hasMore.value = details.value.length < detailTotal.value
  } catch (err) {
    error.value = (err as Error)?.message || '收入明细加载失败'
    if (reset) {
      details.value = []
      detailTotal.value = 0
      hasMore.value = false
    }
  } finally {
    loading.value = false
  }
}

function loadMore(): void {
  if (!hasMore.value || loading.value) {
    return
  }
  void loadDetails(false)
}
</script>

<style scoped>
.income {
  min-height: 100vh;
  padding: 24rpx 24rpx 60rpx;
  background-color: #f1f4f8;
}

.income__hero {
  padding: 36rpx 32rpx;
  border-radius: 24rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #2c4467, #16233a);
}

.income__hero-label {
  font-size: 24rpx;
  opacity: 0.85;
}

.income__hero-value {
  display: block;
  margin-top: 10rpx;
  font-size: 64rpx;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.income__hero-sub {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 12rpx;
  font-size: 24rpx;
  opacity: 0.85;
}

.income__hero-dot {
  opacity: 0.6;
}

.income__stats {
  display: flex;
  gap: 20rpx;
  margin-top: 20rpx;
}

.income__stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.income__stat-value {
  font-size: 34rpx;
  font-weight: 700;
  color: #16233a;
}

.income__stat-label {
  font-size: 22rpx;
  color: #7a8699;
}

.income__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20rpx;
  padding: 16rpx 20rpx;
  border-radius: 12rpx;
  background-color: #fff1f0;
  color: #b42318;
  font-size: 24rpx;
}

.income__error-retry {
  color: #b42318;
  font-weight: 700;
  text-decoration: underline;
}

.income__section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 36rpx 4rpx 16rpx;
}

.income__section-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #16233a;
}

.income__section-extra {
  font-size: 22rpx;
  color: #7a8699;
}

.income__empty {
  background-color: #ffffff;
  border-radius: 20rpx;
}

.income__row {
  margin-bottom: 18rpx;
  padding: 22rpx 24rpx;
}

.income__row-head {
  padding-bottom: 12rpx;
}

.income__row-date {
  font-size: 26rpx;
  font-weight: 600;
  color: #33455f;
}

.income__row-status {
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
}

.income__row-status.is-settled {
  color: #067647;
  background-color: #dcfae6;
}

.income__row-status.is-pending {
  color: #a2520a;
  background-color: #fff6ea;
}

.income__row-main {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20rpx;
}

.income__parts {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx 18rpx;
  flex: 1;
}

.income__part {
  font-size: 22rpx;
  color: #7a8699;
}

.income__part.is-negative {
  color: #b42318;
}

.income__row-total {
  font-size: 34rpx;
  white-space: nowrap;
}

.income__row-foot {
  margin-top: 12rpx;
  font-size: 21rpx;
  color: #a9b4c4;
}

.income__row-time {
  margin-left: 4rpx;
}

.income__more,
.income__end {
  padding: 24rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #8d99aa;
}

.income__more {
  color: #e85d04;
  font-weight: 600;
}

.income__note {
  margin-top: 28rpx;
  text-align: center;
  font-size: 21rpx;
  color: #a9b4c4;
  line-height: 1.6;
}
</style>
