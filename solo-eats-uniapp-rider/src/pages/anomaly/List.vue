<template>
  <view class="anomaly rd-page">
    <view class="rd-card anomaly__notice">
      <text class="anomaly__notice-title">记录包含两类来源</text>
      <text class="anomaly__notice-line">
        主动上报与系统超时扫描都写入同一张异常表，表中没有来源字段，本页无法区分两者。
      </text>
    </view>

    <view class="anomaly__head rd-between">
      <text class="rd-subtitle">异常记录</text>
      <text class="anomaly__total">共 {{ total }} 条</text>
    </view>

    <view v-if="loading && records.length === 0" class="rd-loading">正在加载异常记录…</view>

    <view v-else-if="error && records.length === 0" class="rd-error">
      <text class="rd-error__msg">{{ error }}</text>
      <view class="rd-error__retry" @click="load(true)">重新加载</view>
    </view>

    <view v-else-if="records.length === 0" class="rd-empty">
      <text class="rd-empty__title">暂无异常记录</text>
      <text>遇到超时、联系不上用户等情况可主动上报</text>
      <view class="rd-empty__action" @click="goReport">上报异常</view>
    </view>

    <view v-else>
      <view v-if="error" class="anomaly__error">
        <text>{{ error }}</text>
        <text class="anomaly__error-retry" @click="load(true)">重试</text>
      </view>

      <view v-for="item in records" :key="item.id" class="rd-card anomaly__row">
        <view class="anomaly__row-head rd-between">
          <text class="anomaly__type">{{ item.typeName || typeLabel(item.type) }}</text>
          <text class="anomaly__time">{{ formatDateTime(item.createdAt) }}</text>
        </view>

        <text class="anomaly__remark">{{ item.remark || '无备注' }}</text>

        <view class="anomaly__row-foot">
          <text>配送单 #{{ item.deliveryId }}</text>
          <text v-if="imageCount(item.imagesJson) > 0">含 {{ imageCount(item.imagesJson) }} 张图片</text>
        </view>
      </view>

      <view v-if="hasMore" class="anomaly__more" @click="load(false)">
        {{ loading ? '加载中…' : '加载更多' }}
      </view>
      <view v-else class="anomaly__end">没有更多记录了</view>
    </view>

    <view class="rd-fixed-bottom anomaly__footer">
      <button class="rd-btn rd-btn--primary" @click="goReport">上报异常</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { fetchAnomalies } from '@/api/rider'
import type { RiderAnomalyVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/date'

/**
 * 异常记录：GET /rider/anomalies，数据源 t_rider_ping_anomaly。
 * 系统超时扫描（type=1）与骑手主动上报混在同一张表，后端未提供来源字段，
 * 页面顶部如实告知，不做来源猜测。
 */

const PAGE_SIZE = 20

const TYPE_LABELS: Record<number, string> = {
  1: '超时',
  2: '联系不上用户',
  3: '地址错误',
  4: '餐品破损',
  5: '商家出餐慢',
  6: '用户拒收',
}

const auth = useAuthStore()

const records = ref<RiderAnomalyVO[]>([])
const pageNum = ref(0)
const total = ref(0)
const hasMore = ref(false)
const loading = ref(false)
const error = ref('')

onShow(() => {
  if (!auth.ensureLogin('/pages/anomaly/List')) {
    return
  }
  void load(true)
})

onPullDownRefresh(() => {
  void (async () => {
    await load(true)
    uni.stopPullDownRefresh()
  })()
})

onReachBottom(() => {
  void load(false)
})

function typeLabel(type: number): string {
  return TYPE_LABELS[Number(type)] || '其他'
}

/** imagesJson 由后端原样下发，解析失败按无图处理，不影响列表渲染 */
function imageCount(imagesJson: string | null): number {
  if (!imagesJson) {
    return 0
  }
  try {
    const parsed = JSON.parse(imagesJson) as unknown
    return Array.isArray(parsed) ? parsed.length : 0
  } catch {
    return 0
  }
}

async function load(reset: boolean): Promise<void> {
  if (loading.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const page = await fetchAnomalies({
      pageNum: reset ? 1 : pageNum.value + 1,
      pageSize: PAGE_SIZE,
    })
    const list = Array.isArray(page?.list) ? page.list : []
    records.value = reset ? list : records.value.concat(list)
    pageNum.value = page?.pageNum || (reset ? 1 : pageNum.value + 1)
    total.value = Number(page?.total || 0)
    hasMore.value = records.value.length < total.value
  } catch (err) {
    error.value = (err as Error)?.message || '异常记录加载失败'
    if (reset) {
      // 只有在没有可展示数据时才回退分页状态，已有记录保留
      if (records.value.length === 0) {
        pageNum.value = 0
        total.value = 0
        hasMore.value = false
      }
    }
  } finally {
    loading.value = false
  }
}

function goReport(): void {
  uni.navigateTo({ url: '/pages/anomaly/Report' })
}
</script>

<style scoped>
.anomaly {
  padding-bottom: 180rpx;
}

.anomaly__notice {
  padding: 20rpx 24rpx;
  background-color: #fffaf5;
  border: 2rpx solid #ffe0c2;
}

.anomaly__notice-title {
  display: block;
  font-size: 24rpx;
  font-weight: 600;
  color: #a2520a;
}

.anomaly__notice-line {
  display: block;
  margin-top: 6rpx;
  font-size: 21rpx;
  color: #a9763d;
  line-height: 1.7;
}

.anomaly__head {
  margin: 28rpx 4rpx 14rpx;
}

.anomaly__total {
  font-size: 22rpx;
  color: #8d99aa;
}

.anomaly__row {
  margin-bottom: 16rpx;
  padding: 24rpx;
}

.anomaly__row-head {
  gap: 20rpx;
}

.anomaly__type {
  padding: 4rpx 16rpx;
  border-radius: 8rpx;
  background-color: #fff1e6;
  color: #c2410c;
  font-size: 22rpx;
  font-weight: 600;
}

.anomaly__time {
  font-size: 21rpx;
  color: #a9b4c4;
  font-variant-numeric: tabular-nums;
}

.anomaly__remark {
  display: block;
  margin-top: 14rpx;
  font-size: 24rpx;
  color: #33455f;
  line-height: 1.7;
  word-break: break-all;
}

.anomaly__row-foot {
  display: flex;
  justify-content: space-between;
  margin-top: 14rpx;
  padding-top: 14rpx;
  border-top: 1rpx solid #edf1f6;
  font-size: 21rpx;
  color: #8d99aa;
}

.anomaly__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
  padding: 16rpx 20rpx;
  border-radius: 12rpx;
  background-color: #fff1f0;
  color: #b42318;
  font-size: 24rpx;
}

.anomaly__error-retry {
  font-weight: 700;
  text-decoration: underline;
}

.anomaly__more,
.anomaly__end {
  padding: 24rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #8d99aa;
}

.anomaly__more {
  color: #e85d04;
  font-weight: 600;
}

.anomaly__footer {
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
}
</style>
