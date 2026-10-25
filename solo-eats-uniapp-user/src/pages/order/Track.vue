<template>
  <view class="se-page track">
    <view v-if="loading" class="se-loading">正在加载配送轨迹…</view>

    <view v-else-if="!deliveryId" class="se-empty">
      <text class="se-empty__title">暂无配送信息</text>
      <text>订单详情接口未返回 deliveryId，无法定位配送单</text>
      <view class="se-error__retry track__back" @click="goBack">返回订单详情</view>
    </view>

    <view v-else-if="error && !track" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="load">重新加载</view>
    </view>

    <template v-else>
      <view class="se-card track__rider">
        <view class="se-between">
          <text class="track__title">骑手实时位置</text>
          <text class="se-muted">{{ riderPingText }}</text>
        </view>
        <view v-if="riderPosition" class="track__position">
          <text class="track__coord">经度 {{ riderPosition.lng }}</text>
          <text class="track__coord">纬度 {{ riderPosition.lat }}</text>
        </view>
        <text v-else class="se-muted track__position-empty">骑手尚未上报位置</text>
      </view>

      <view class="se-card track__points">
        <view class="se-between">
          <text class="track__title">轨迹点（最近 100 个）</text>
          <text class="track__refresh" @click="manualRefresh">{{ refreshing ? '刷新中…' : '刷新' }}</text>
        </view>

        <view v-if="points.length === 0" class="se-muted track__empty">暂无轨迹点</view>
        <view v-else>
          <view v-for="point in recentPoints" :key="point.id" class="track__point">
            <view class="track__point-dot" />
            <view class="track__point-body">
              <text class="track__point-time">{{ formatDateTime(point.reportAt) }}</text>
              <text class="se-muted">
                {{ point.lng }}, {{ point.lat }}
                <text v-if="point.speed !== null"> · {{ point.speed }}m/s</text>
              </text>
            </view>
          </view>
        </view>
      </view>

      <text class="track__note">
        H5 阶段未接入地图 SDK（uni-app H5 地图需额外地图密钥），当前按坐标与时间展示轨迹，页面每 15 秒自动刷新。
      </text>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onHide, onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { fetchDeliveryTrack } from '@/api/order'
import type { DeliveryTrackVO, TrackPointVO } from '@/api/types'
import { formatDateTime } from '@/utils/date'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const deliveryId = ref(0)
const orderId = ref(0)
const track = ref<DeliveryTrackVO | null>(null)
const loading = ref(true)
const error = ref('')
const refreshing = ref(false)

let timer: ReturnType<typeof setInterval> | null = null
let polling = false

const points = computed<TrackPointVO[]>(() => track.value?.points || [])
const riderPosition = computed(() => track.value?.riderPosition || null)
const recentPoints = computed(() => [...points.value].reverse().slice(0, 20))
const riderPingText = computed(() =>
  riderPosition.value?.lastPing ? `心跳 ${formatDateTime(riderPosition.value.lastPing)}` : '--',
)

onLoad((options) => {
  orderId.value = Number(options?.orderId) || 0
  const id = Number(options?.deliveryId)
  if (Number.isFinite(id) && id > 0) {
    deliveryId.value = id
  }

  if (!deliveryId.value) {
    loading.value = false
    return
  }
  if (!auth.ensureLogin(`/pages/order/Track?orderId=${orderId.value}&deliveryId=${deliveryId.value}`)) {
    return
  }
  load()
})

onShow(() => {
  startPolling()
})

onHide(() => {
  stopPolling()
})

onUnload(() => {
  stopPolling()
})

async function load(): Promise<void> {
  if (!deliveryId.value) {
    loading.value = false
    return
  }
  loading.value = true
  error.value = ''
  try {
    track.value = await fetchDeliveryTrack(deliveryId.value)
    startPolling()
  } catch (err) {
    error.value = (err as Error)?.message || '配送轨迹加载失败'
  } finally {
    loading.value = false
  }
}

function startPolling(): void {
  stopPolling()
  if (!deliveryId.value) {
    return
  }
  timer = setInterval(() => {
    void refreshSilently()
  }, 15_000)
}

function stopPolling(): void {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

async function refreshSilently(): Promise<void> {
  if (polling) {
    return
  }
  polling = true
  try {
    track.value = await fetchDeliveryTrack(deliveryId.value, true)
  } catch (err) {
    // 静默失败，下一轮继续
  } finally {
    polling = false
  }
}

async function manualRefresh(): Promise<void> {
  if (refreshing.value) {
    return
  }
  refreshing.value = true
  try {
    track.value = await fetchDeliveryTrack(deliveryId.value)
  } catch (err) {
    // http 层已提示
  } finally {
    refreshing.value = false
  }
}

function goBack(): void {
  if (orderId.value) {
    uni.redirectTo({ url: `/pages/order/Detail?id=${orderId.value}` })
    return
  }
  uni.switchTab({ url: '/pages/order/List' })
}
</script>

<style scoped>
.track__back {
  margin-top: 24rpx;
}

.track__rider,
.track__points {
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.track__title {
  font-size: 28rpx;
  font-weight: 600;
}

.track__position {
  display: flex;
  gap: 32rpx;
  margin-top: 16rpx;
}

.track__coord {
  font-size: 24rpx;
  color: #444444;
}

.track__position-empty {
  display: block;
  margin-top: 16rpx;
}

.track__refresh {
  font-size: 24rpx;
  color: #ff7a00;
}

.track__empty {
  display: block;
  padding: 32rpx 0;
  text-align: center;
}

.track__point {
  display: flex;
  padding-top: 20rpx;
  position: relative;
}

.track__point-dot {
  width: 12rpx;
  height: 12rpx;
  margin-top: 12rpx;
  margin-right: 16rpx;
  border-radius: 50%;
  background-color: #ffb066;
  flex-shrink: 0;
}

.track__point-body {
  display: flex;
  flex-direction: column;
}

.track__point-time {
  font-size: 24rpx;
  color: #444444;
}

.track__note {
  display: block;
  padding: 8rpx;
  font-size: 20rpx;
  line-height: 1.7;
  color: #a0a0a0;
}
</style>
