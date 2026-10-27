<template>
  <view class="detail">
    <view v-if="loading && !task" class="rd-loading">正在加载任务详情…</view>

    <view v-else-if="error && !task" class="rd-error">
      <text class="rd-error__msg">{{ error }}</text>
      <view class="rd-error__retry" @click="load()">重新加载</view>
    </view>

    <template v-else-if="task">
      <view class="detail__body">
        <view class="rd-card detail__head">
          <view class="rd-between">
            <text class="detail__status">{{ task.statusName }}</text>
            <text class="detail__no">配送单 {{ task.deliveryNo }}</text>
          </view>

          <view class="detail__head-meta">
            <view class="detail__meta-item">
              <text class="detail__meta-value">{{ formatDistance(task.distanceM) }}</text>
              <text class="detail__meta-label">配送距离</text>
            </view>
            <view class="detail__meta-item">
              <text class="detail__meta-value">{{ task.itemCount }}</text>
              <text class="detail__meta-label">商品件数</text>
            </view>
            <view class="detail__meta-item">
              <text class="detail__meta-value rd-price">{{ fenToYuan(task.deliveryFee) }}</text>
              <text class="detail__meta-label">配送费</text>
            </view>
          </view>

          <view v-if="promiseInfo" class="detail__promise" :class="promiseInfo.className">
            {{ promiseInfo.text }}
          </view>
        </view>

        <view class="rd-card detail__card">
          <view class="detail__card-title">
            <text class="detail__dot detail__dot--pickup">取</text>
            <text>取货点</text>
          </view>
          <text class="detail__addr">{{ task.pickupAddress || '取货地址待更新' }}</text>
        </view>

        <view class="rd-card detail__card">
          <view class="detail__card-title">
            <text class="detail__dot detail__dot--drop">送</text>
            <text>送达点</text>
          </view>
          <text class="detail__addr">{{ task.receiverAddress || '收货地址待更新' }}</text>

          <view class="detail__addr-actions">
            <view class="detail__nav-btn" @click="navigateToReceiver">
              {{ receiverCoord ? '导航到收货点' : '复制收货地址' }}
            </view>
          </view>
          <text v-if="!receiverCoord" class="detail__nav-tip">
            当前接口未下发收货坐标，点击后复制地址，可在高德/腾讯地图 App 中粘贴搜索。
          </text>

          <view class="detail__protect">
            收货人姓名与电话由平台保护，按地址送达即可。如需协助请联系平台客服。
          </view>
        </view>

        <view class="rd-card detail__card">
          <view class="detail__card-title">
            <text>商品明细</text>
            <text class="detail__card-extra">共 {{ task.itemCount }} 件</text>
          </view>

          <view v-if="items.length === 0" class="detail__empty-items">暂无商品明细</view>

          <view v-for="item in items" :key="item.skuId" class="detail__item">
            <view class="detail__item-main">
              <text class="detail__item-name">{{ item.skuName }}</text>
              <text class="detail__item-qty">×{{ item.quantity }}</text>
            </view>
            <text class="detail__item-amount">{{ fenToYuan(item.payableAmount) }}</text>
          </view>
        </view>

        <view class="rd-card detail__card">
          <view class="detail__card-title"><text>时间节点</text></view>
          <view class="detail__time-row">
            <text class="detail__time-label">接单时间</text>
            <text class="detail__time-value">{{ formatDateTime(task.acceptedAt) }}</text>
          </view>
          <view class="detail__time-row">
            <text class="detail__time-label">取货时间</text>
            <text class="detail__time-value">{{ formatDateTime(task.pickedUpAt) }}</text>
          </view>
          <view class="detail__time-row">
            <text class="detail__time-label">送达时间</text>
            <text class="detail__time-value">{{ formatDateTime(task.deliveredAt) }}</text>
          </view>
        </view>

        <view v-if="task.status === 40" class="detail__track-tip">
          轨迹上报已开启：每 5 秒采样一次，按 20 点批量上传，送达后自动停止。
        </view>

        <view class="detail__spacer" />
      </view>

      <view class="rd-fixed-bottom">
        <template v-if="task.status === 20">
          <button class="rd-btn rd-btn--primary" :disabled="acting" @click="handlePickup">
            {{ acting ? '处理中…' : '我已取货' }}
          </button>
        </template>

        <template v-else-if="task.status === 40">
          <button class="rd-btn rd-btn--plain" :disabled="acting" @click="openAnomaly">
            异常上报
          </button>
          <button class="rd-btn rd-btn--primary" :disabled="acting" @click="handleDeliver">
            {{ acting ? '处理中…' : '确认送达' }}
          </button>
        </template>

        <view v-else class="detail__bar-note">
          当前状态「{{ task.statusName }}」，无需操作
        </view>
      </view>
    </template>

    <!-- 异常上报浮层 -->
    <view v-if="anomalyVisible" class="anomaly">
      <view class="anomaly__mask" @click="closeAnomaly" />
      <view class="anomaly__sheet">
        <view class="anomaly__title">异常上报</view>
        <view class="anomaly__tip">上报后平台会记录并跟进，请如实选择异常类型。</view>

        <view class="anomaly__types">
          <view
            v-for="item in ANOMALY_TYPES"
            :key="item.value"
            class="anomaly__type"
            :class="{ 'is-active': anomalyType === item.value }"
            @click="anomalyType = item.value"
          >
            {{ item.label }}
          </view>
        </view>

        <textarea
          v-model="anomalyRemark"
          class="anomaly__remark"
          maxlength="255"
          placeholder="补充说明（选填，最多 255 字）"
          placeholder-class="anomaly__placeholder"
        />

        <view class="anomaly__actions">
          <button class="rd-btn rd-btn--plain" :disabled="anomalySubmitting" @click="closeAnomaly">
            取消
          </button>
          <button
            class="rd-btn rd-btn--primary"
            :disabled="anomalySubmitting"
            @click="submitAnomaly"
          >
            {{ anomalySubmitting ? '提交中…' : '提交上报' }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { getMyDelivery } from '@/api/order'
import { deliverDelivery, pickupDelivery, reportAnomaly } from '@/api/rider'
import type { RiderTaskDetailVO } from '@/api/types'
import { UPLOAD_BIZ_TYPE, uploadFile } from '@/api/upload'
import { useAuthStore } from '@/stores/auth'
import { useRiderStore } from '@/stores/rider'
import { startTrackReport, stopTrackReport, getCurrentLocation } from '@/utils/location'
import { fenToYuan } from '@/utils/money'
import { formatDateTime, parseServerDateTime } from '@/utils/date'

const ANOMALY_TYPES = [
  { value: 1, label: '超时' },
  { value: 2, label: '联系不上用户' },
  { value: 3, label: '地址错误' },
  { value: 4, label: '餐品破损' },
  { value: 5, label: '商家出餐慢' },
  { value: 6, label: '用户拒收' },
]

const auth = useAuthStore()
const rider = useRiderStore()

const deliveryId = ref(0)
const task = ref<RiderTaskDetailVO | null>(null)
const loading = ref(false)
const error = ref('')
const acting = ref(false)
const now = ref(Date.now())

const anomalyVisible = ref(false)
const anomalyType = ref(2)
const anomalyRemark = ref('')
const anomalySubmitting = ref(false)

let clock: ReturnType<typeof setInterval> | null = null

const items = computed(() => task.value?.items || [])

/**
 * 收货坐标：t_delivery_order 有 receiver_lng/lat，但 RiderDeliveryView 当前未下发，
 * 接口恒为 null。保留这段读取逻辑，后端补齐字段后无需改页面即可启用系统地图。
 */
const receiverCoord = computed<{ lng: number; lat: number } | null>(() => {
  const current = task.value
  if (
    !current ||
    current.receiverLng === null ||
    current.receiverLng === undefined ||
    current.receiverLat === null ||
    current.receiverLat === undefined
  ) {
    return null
  }
  const lng = Number(current.receiverLng)
  const lat = Number(current.receiverLat)
  if (!Number.isFinite(lng) || !Number.isFinite(lat) || lng === 0 || lat === 0) {
    return null
  }
  return { lng, lat }
})

interface PromiseInfo {
  text: string
  className: string
}

const promiseInfo = computed<PromiseInfo | null>(() => {
  const current = task.value
  if (!current || !current.promiseDeliverAt) {
    return null
  }
  if (current.status === 50) {
    return {
      text: `已于 ${formatDateTime(current.deliveredAt)} 送达`,
      className: 'is-done',
    }
  }
  const target = parseServerDateTime(current.promiseDeliverAt)
  if (!target) {
    return { text: `承诺 ${formatDateTime(current.promiseDeliverAt)} 前送达`, className: '' }
  }
  const diffMinutes = Math.round((target.getTime() - now.value) / 60_000)
  if (diffMinutes < 0) {
    return {
      text: `已超时 ${Math.abs(diffMinutes)} 分钟，请优先配送`,
      className: 'is-overdue',
    }
  }
  if (diffMinutes <= 5) {
    return {
      text: `仅剩 ${diffMinutes} 分钟，即将超时`,
      className: 'is-urgent',
    }
  }
  return {
    text: `承诺 ${formatDateTime(current.promiseDeliverAt)} 前送达（剩余 ${diffMinutes} 分钟）`,
    className: '',
  }
})

onLoad((options) => {
  const id = Number(options?.id)
  if (!Number.isFinite(id) || id <= 0) {
    error.value = '任务参数缺失，请从任务列表重新进入'
    return
  }
  deliveryId.value = id
  const redirect = `/pages/task/Detail?id=${id}`
  if (!auth.ensureLogin(redirect)) {
    return
  }
  startClock()
  void load()
})

onUnload(() => {
  stopClock()
  // 离开详情页即停止该单轨迹采样，未送达的点位留在本地下次补传
  stopTrackReport()
})

function startClock(): void {
  stopClock()
  now.value = Date.now()
  clock = setInterval(() => {
    now.value = Date.now()
  }, 30_000)
}

function stopClock(): void {
  if (clock) {
    clearInterval(clock)
    clock = null
  }
}

async function load(silent = false): Promise<void> {
  if (loading.value) {
    return
  }
  if (!silent) {
    loading.value = true
    error.value = ''
  }
  try {
    const detail = await getMyDelivery(deliveryId.value, silent)
    task.value = detail
    error.value = ''
    syncTracking(detail.status)
  } catch (err) {
    error.value = (err as Error)?.message || '任务详情加载失败'
  } finally {
    loading.value = false
  }
}

/** 40 配送中开启轨迹上报；其它状态确保停止 */
function syncTracking(status: number): void {
  if (status === 40) {
    startTrackReport(deliveryId.value)
  } else {
    stopTrackReport()
  }
}

function toast(message: string): void {
  uni.showToast({ title: message, icon: 'none', duration: 2400 })
}

function formatDistance(distanceM: number | null | undefined): string {
  if (distanceM === null || distanceM === undefined) {
    return '--'
  }
  const meters = Number(distanceM)
  if (!Number.isFinite(meters)) {
    return '--'
  }
  return meters < 1000 ? `${Math.round(meters)} m` : `${(meters / 1000).toFixed(1)} km`
}

/**
 * 导航到收货点：
 * - 有坐标 → uni.openLocation 调起系统地图（名称取收货地址）；
 * - 无坐标（当前后端未下发）→ 复制地址并提示到地图 App 粘贴搜索，不伪造坐标。
 */
function navigateToReceiver(): void {
  const address = (task.value?.receiverAddress || '').trim()
  const coord = receiverCoord.value
  if (coord) {
    uni.openLocation({
      longitude: coord.lng,
      latitude: coord.lat,
      name: '收货地址',
      address,
      scale: 18,
      fail: () => copyReceiverAddress(address),
    })
    return
  }
  copyReceiverAddress(address)
}

function copyReceiverAddress(address: string): void {
  if (!address) {
    toast('收货地址为空，无法导航')
    return
  }
  uni.setClipboardData({
    data: address,
    success: () => {
      uni.showModal({
        title: '已复制收货地址',
        content: '当前接口未下发收货坐标，无法直接调起地图。请打开高德或腾讯地图 App 粘贴搜索。',
        showCancel: false,
        confirmText: '知道了',
      })
    },
    fail: () => toast('复制失败，请手动记录地址'),
  })
}

function showConfirm(options: {
  title: string
  content: string
  confirmText?: string
  cancelText?: string
}): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title: options.title,
      content: options.content,
      confirmText: options.confirmText,
      cancelText: options.cancelText,
      success: (result) => resolve(Boolean(result.confirm)),
      fail: () => resolve(false),
    })
  })
}

/* ------------------------------ 取货 ------------------------------ */

async function handlePickup(): Promise<void> {
  if (acting.value) {
    return
  }
  const confirmed = await showConfirm({
    title: '确认取货',
    content: '请确认已从商家取到全部餐品。取货后任务进入配送中，需尽快送达。',
    confirmText: '已取货',
    cancelText: '再核对',
  })
  if (!confirmed) {
    return
  }

  acting.value = true
  try {
    await pickupDelivery(deliveryId.value)
    toast('取货成功，请注意配送时效')
    await load(true)
  } catch (err) {
    // pickupDelivery 走 silent，这里统一提示
    toast((err as Error)?.message || '取货失败，请重试')
  } finally {
    acting.value = false
  }
}

/* ------------------------------ 送达 ------------------------------ */

interface DeliverEvidence {
  lng: number | null
  lat: number | null
  photoFileId: number | null
}

/**
 * 送达前的凭证收集（前端兜底，服务端仍会校验状态机）：
 * 1. 优先取定位，成功则带坐标送达；
 * 2. 定位失败时给「拍照留证」或「重试定位」两个选择；
 * 3. 连续两次定位失败则必须上传留证照片，无定位又无照片一律不放行。
 */
async function resolveDeliverEvidence(): Promise<DeliverEvidence | null> {
  for (let attempt = 0; attempt < 2; attempt += 1) {
    try {
      const location = await getCurrentLocation()
      rider.rememberLocation(location)
      return { lng: location.lng, lat: location.lat, photoFileId: null }
    } catch (err) {
      const message = (err as Error)?.message || '无法获取定位'
      const capture = await showConfirm({
        title: '定位不可用',
        content: `${message}。无定位送达必须拍照留证，是否现在拍照？`,
        confirmText: '拍照留证',
        cancelText: '重试定位',
      })
      if (capture) {
        const photoFileId = await captureEvidencePhoto()
        if (!photoFileId) {
          return null
        }
        return { lng: null, lat: null, photoFileId }
      }
    }
  }

  const proceed = await showConfirm({
    title: '需要留证照片',
    content: '连续两次定位失败。无定位送达必须拍照留证，请拍摄餐品已交付的照片。',
    confirmText: '拍照留证',
    cancelText: '取消',
  })
  if (!proceed) {
    return null
  }
  const photoFileId = await captureEvidencePhoto()
  if (!photoFileId) {
    return null
  }
  return { lng: null, lat: null, photoFileId }
}

function captureEvidencePhoto(): Promise<number | null> {
  return new Promise((resolve) => {
    uni.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['camera', 'album'],
      success: async (result) => {
        const localPath = result.tempFilePaths?.[0]
        if (!localPath) {
          resolve(null)
          return
        }
        uni.showLoading({ title: '上传留证…', mask: true })
        try {
          const file = await uploadFile(localPath, UPLOAD_BIZ_TYPE.DELIVERY_EVIDENCE)
          resolve(file.fileId)
        } catch (err) {
          uni.showModal({
            title: '留证上传失败',
            content: (err as Error)?.message || '请检查网络后重试',
            showCancel: false,
          })
          resolve(null)
        } finally {
          uni.hideLoading()
        }
      },
      fail: () => resolve(null),
    })
  })
}

async function handleDeliver(): Promise<void> {
  if (acting.value) {
    return
  }
  const confirmed = await showConfirm({
    title: '确认送达',
    content: '请确认餐品已交付收货人。送达后订单完成，操作不可撤销。',
    confirmText: '确认送达',
    cancelText: '取消',
  })
  if (!confirmed) {
    return
  }

  acting.value = true
  try {
    const evidence = await resolveDeliverEvidence()
    if (!evidence) {
      toast('无定位时必须上传留证照片')
      return
    }

    await deliverDelivery(deliveryId.value, evidence)
    stopTrackReport()
    uni.showToast({ title: '已确认送达', icon: 'none' })
    await load(true)
    setTimeout(() => {
      uni.navigateBack({
        delta: 1,
        fail: () => {
          uni.switchTab({ url: '/pages/task/List' })
        },
      })
    }, 900)
  } catch (err) {
    // deliverDelivery 走 silent：断网、状态已变更等在这里提示，且不做本地乐观更新
    toast((err as Error)?.message || '送达失败，请重试')
  } finally {
    acting.value = false
  }
}

/* ---------------------------- 异常上报 ---------------------------- */

function openAnomaly(): void {
  anomalyType.value = 2
  anomalyRemark.value = ''
  anomalyVisible.value = true
}

function closeAnomaly(): void {
  if (anomalySubmitting.value) {
    return
  }
  anomalyVisible.value = false
}

async function submitAnomaly(): Promise<void> {
  if (anomalySubmitting.value) {
    return
  }
  anomalySubmitting.value = true
  try {
    await reportAnomaly(deliveryId.value, {
      type: anomalyType.value,
      remark: anomalyRemark.value.trim() || null,
      images: null,
    })
    anomalyVisible.value = false
    uni.showToast({ title: '已上报，平台将跟进', icon: 'none' })
  } catch (err) {
    // http 层已提示
  } finally {
    anomalySubmitting.value = false
  }
}
</script>

<style scoped>
.detail {
  min-height: 100vh;
  background-color: #f1f4f8;
}

.detail__body {
  padding: 24rpx 24rpx 0;
}

.detail__head {
  margin-bottom: 20rpx;
}

.detail__status {
  font-size: 36rpx;
  font-weight: 700;
  color: #e85d04;
}

.detail__no {
  font-size: 22rpx;
  color: #8d99aa;
}

.detail__head-meta {
  display: flex;
  margin-top: 22rpx;
  padding: 20rpx 0 4rpx;
  border-top: 1rpx solid #f2f5f9;
}

.detail__meta-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}

.detail__meta-value {
  font-size: 34rpx;
  font-weight: 700;
  color: #16233a;
}

.detail__meta-label {
  font-size: 22rpx;
  color: #7a8699;
}

.detail__promise {
  margin-top: 18rpx;
  padding: 14rpx 20rpx;
  border-radius: 12rpx;
  background-color: #eef4ff;
  color: #2c4a75;
  font-size: 26rpx;
  font-weight: 500;
}

.detail__promise.is-urgent {
  background-color: #fff1e6;
  color: #c2410c;
  font-weight: 700;
}

.detail__promise.is-overdue {
  background-color: #fee4e2;
  color: #b42318;
  font-weight: 700;
}

.detail__promise.is-done {
  background-color: #dcfae6;
  color: #067647;
  font-weight: 600;
}

.detail__card {
  margin-bottom: 20rpx;
}

.detail__card-title {
  display: flex;
  align-items: center;
  gap: 12rpx;
  font-size: 30rpx;
  font-weight: 700;
  color: #16233a;
}

.detail__card-extra {
  margin-left: auto;
  font-size: 22rpx;
  font-weight: 400;
  color: #7a8699;
}

.detail__dot {
  width: 40rpx;
  height: 40rpx;
  line-height: 40rpx;
  text-align: center;
  border-radius: 8rpx;
  font-size: 22rpx;
  font-weight: 700;
  color: #ffffff;
}

.detail__dot--pickup {
  background-color: #e85d04;
}

.detail__dot--drop {
  background-color: #16233a;
}

.detail__addr {
  display: block;
  margin-top: 16rpx;
  font-size: 30rpx;
  line-height: 1.6;
  color: #33455f;
  word-break: break-all;
}

.detail__addr-actions {
  display: flex;
  margin-top: 18rpx;
}

.detail__nav-btn {
  min-width: 240rpx;
  height: 88rpx;
  line-height: 88rpx;
  padding: 0 32rpx;
  border-radius: 12rpx;
  background: linear-gradient(135deg, #ff9a3c, #ff7a00);
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 600;
  text-align: center;
}

.detail__nav-tip {
  display: block;
  margin-top: 12rpx;
  font-size: 21rpx;
  line-height: 1.6;
  color: #8d99aa;
}

.detail__protect {
  margin-top: 16rpx;
  padding: 14rpx 18rpx;
  border-radius: 12rpx;
  background-color: #f7f9fc;
  color: #7a8699;
  font-size: 22rpx;
  line-height: 1.6;
}

.detail__empty-items {
  padding: 24rpx 0 8rpx;
  font-size: 24rpx;
  color: #8d99aa;
}

.detail__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18rpx 0;
  border-bottom: 1rpx solid #f2f5f9;
}

.detail__item:last-child {
  border-bottom: none;
}

.detail__item-main {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
  flex: 1;
  min-width: 0;
}

.detail__item-name {
  font-size: 28rpx;
  color: #33455f;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.detail__item-qty {
  font-size: 24rpx;
  color: #7a8699;
}

.detail__item-amount {
  font-size: 26rpx;
  color: #16233a;
  font-weight: 600;
}

.detail__time-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f2f5f9;
}

.detail__time-row:last-child {
  border-bottom: none;
}

.detail__time-label {
  font-size: 26rpx;
  color: #7a8699;
}

.detail__time-value {
  font-size: 26rpx;
  color: #33455f;
}

.detail__track-tip {
  padding: 16rpx 20rpx;
  border-radius: 12rpx;
  background-color: #eef4ff;
  color: #2c4a75;
  font-size: 22rpx;
  line-height: 1.6;
}

.detail__spacer {
  height: calc(150rpx + env(safe-area-inset-bottom));
}

.detail__bar-note {
  flex: 1;
  text-align: center;
  font-size: 26rpx;
  color: #7a8699;
  padding: 24rpx 0;
}

/* 异常上报浮层 */
.anomaly {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 120;
  display: flex;
  align-items: flex-end;
}

.anomaly__mask {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background-color: rgba(12, 20, 33, 0.55);
}

.anomaly__sheet {
  position: relative;
  width: 100%;
  background-color: #ffffff;
  border-radius: 28rpx 28rpx 0 0;
  padding: 32rpx 28rpx calc(24rpx + env(safe-area-inset-bottom));
}

.anomaly__title {
  font-size: 34rpx;
  font-weight: 700;
  color: #16233a;
}

.anomaly__tip {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #7a8699;
  line-height: 1.6;
}

.anomaly__types {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 22rpx;
}

.anomaly__type {
  padding: 16rpx 26rpx;
  border-radius: 12rpx;
  background-color: #f2f5f9;
  color: #44536b;
  font-size: 26rpx;
  border: 2rpx solid transparent;
}

.anomaly__type.is-active {
  background-color: #fff1e6;
  border-color: #ff9a3c;
  color: #c2410c;
  font-weight: 700;
}

.anomaly__remark {
  width: 100%;
  height: 160rpx;
  margin-top: 22rpx;
  padding: 18rpx 20rpx;
  border-radius: 14rpx;
  background-color: #f7f9fc;
  font-size: 26rpx;
  color: #16233a;
  line-height: 1.6;
}

.anomaly__placeholder {
  color: #a9b4c4;
}

.anomaly__actions {
  display: flex;
  gap: 20rpx;
  margin-top: 24rpx;
}

.anomaly__actions .rd-btn {
  flex: 1;
}
</style>
