<template>
  <view class="rating rd-page">
    <view v-if="loading && !stats" class="rd-loading">正在加载评分…</view>

    <view v-else-if="error && !stats" class="rd-error">
      <text class="rd-error__msg">{{ error }}</text>
      <view class="rd-error__retry" @click="load">重新加载</view>
    </view>

    <view v-else-if="stats">
      <view class="rating__hero">
        <text class="rating__hero-label">综合评分</text>
        <view class="rating__score-row">
          <text class="rating__score">{{ ratingText }}</text>
          <text class="rating__score-max">/ 5.0</text>
        </view>
        <text class="rating__hero-sub">{{ ratingComment }}</text>
      </view>

      <view class="rating__grid">
        <view class="rd-card rating__cell">
          <text class="rating__cell-value">{{ onTimeText }}</text>
          <text class="rating__cell-label">准时率</text>
        </view>
        <view class="rd-card rating__cell">
          <text class="rating__cell-value">{{ totalOrdersText }}</text>
          <text class="rating__cell-label">累计完成单量</text>
        </view>
        <view class="rd-card rating__cell">
          <text class="rating__cell-value">{{ certText }}</text>
          <text class="rating__cell-label">认证状态</text>
        </view>
      </view>

      <view class="rd-card rating__explain">
        <view class="rating__explain-head rd-between">
          <text class="rating__explain-title">评分说明</text>
          <text class="rating__link" @click="goDetail">查看构成 ›</text>
        </view>
        <text class="rating__explain-line">数据来源：t_rider 的 rating / on_time_rate / total_orders 字段。</text>
        <text class="rating__explain-line">
          准时率为百分比数值（0~100），本页直接按接口返回值展示，不再乘除换算。
        </text>
        <text class="rating__explain-line">
          评分与准时率由平台统计任务更新，骑手端只读；单笔订单的评价明细接口后端尚未提供。
        </text>
      </view>

      <view v-if="error" class="rating__error">
        <text>{{ error }}</text>
        <text class="rating__error-retry" @click="load">重试</text>
      </view>
    </view>

    <view class="rating__foot">
      评分影响派单优先级由平台规则决定，本页不展示未开放的权重数值。
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchRiderStats } from '@/api/rider'
import type { RiderStatsVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'

/** 我的评分：/rider/stats 返回 t_rider 的汇总字段，页面不做二次加权计算。 */

const CERT_TEXT: Record<number, string> = {
  0: '未提交',
  1: '待审核',
  2: '已通过',
  3: '已驳回',
}

const auth = useAuthStore()

const stats = ref<RiderStatsVO | null>(null)
const loading = ref(false)
const error = ref('')

const ratingText = computed(() => {
  const value = Number(stats.value?.rating)
  return Number.isFinite(value) ? value.toFixed(2) : '--'
})

const onTimeText = computed(() => {
  const value = Number(stats.value?.onTimeRate)
  return Number.isFinite(value) ? `${value.toFixed(1)}%` : '--'
})

const totalOrdersText = computed(() => {
  const value = Number(stats.value?.totalOrders)
  return Number.isFinite(value) ? String(Math.trunc(value)) : '--'
})

const certText = computed(() => {
  const status = Number(stats.value?.certStatus)
  return CERT_TEXT[status] || '未知'
})

const ratingComment = computed(() => {
  const value = Number(stats.value?.rating)
  if (!Number.isFinite(value)) {
    return '暂无评分数据'
  }
  if (value >= 4.8) {
    return '评分优秀，继续保持准时送达'
  }
  if (value >= 4.5) {
    return '评分良好，注意减少超时与差评'
  }
  if (value >= 4.0) {
    return '评分一般，建议关注准时率与用户反馈'
  }
  return '评分偏低，请检查履约流程与用户评价'
})

onShow(() => {
  if (!auth.ensureLogin('/pages/rating/Rating')) {
    return
  }
  void load()
})

async function load(): Promise<void> {
  if (loading.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    stats.value = await fetchRiderStats()
  } catch (err) {
    error.value = (err as Error)?.message || '评分加载失败'
  } finally {
    loading.value = false
  }
}

function goDetail(): void {
  uni.navigateTo({ url: '/pages/rating/RatingDetail' })
}
</script>

<style scoped>
.rating {
  padding-bottom: 60rpx;
}

.rating__hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
  padding: 44rpx 32rpx 40rpx;
  border-radius: 24rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #2c4467, #16233a);
}

.rating__hero-label {
  font-size: 24rpx;
  opacity: 0.85;
}

.rating__score-row {
  display: flex;
  align-items: baseline;
  gap: 10rpx;
  margin-top: 8rpx;
}

.rating__score {
  font-size: 96rpx;
  font-weight: 700;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.rating__score-max {
  font-size: 28rpx;
  opacity: 0.7;
}

.rating__hero-sub {
  margin-top: 10rpx;
  font-size: 24rpx;
  opacity: 0.85;
}

.rating__grid {
  display: flex;
  gap: 16rpx;
  margin-top: 20rpx;
}

.rating__cell {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 24rpx 10rpx;
}

.rating__cell-value {
  font-size: 32rpx;
  font-weight: 700;
  color: #16233a;
  font-variant-numeric: tabular-nums;
}

.rating__cell-label {
  font-size: 21rpx;
  color: #7a8699;
}

.rating__explain {
  margin-top: 20rpx;
  padding: 24rpx;
}

.rating__explain-head {
  margin-bottom: 16rpx;
}

.rating__explain-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #16233a;
}

.rating__link {
  font-size: 24rpx;
  color: #e85d04;
}

.rating__explain-line {
  display: block;
  margin-bottom: 10rpx;
  font-size: 23rpx;
  color: #7a8699;
  line-height: 1.7;
}

.rating__error {
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

.rating__error-retry {
  font-weight: 700;
  text-decoration: underline;
}

.rating__foot {
  margin-top: 30rpx;
  text-align: center;
  font-size: 21rpx;
  color: #a9b4c4;
  line-height: 1.6;
}
</style>
