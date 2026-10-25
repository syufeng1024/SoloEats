<template>
  <view class="home rd-page">
    <view v-if="certError" class="rd-error">
      <text class="rd-error__msg">{{ certError }}</text>
      <view class="rd-error__retry" @click="bootstrap">重新加载</view>
      <view class="home__relogin" @click="relogin">重新登录</view>
    </view>

    <template v-else>
      <!-- 听单开关 -->
      <view class="home__top" :class="rider.online ? 'is-online' : 'is-offline'">
        <view class="home__identity">
          <view class="home__avatar">{{ avatarText }}</view>
          <view class="home__identity-text">
            <text class="home__name">{{ rider.realName || auth.nickname || '骑手' }}</text>
            <text class="home__no">{{ rider.riderNo ? `骑手编号 ${rider.riderNo}` : '资料加载中' }}</text>
          </view>
        </view>

        <view class="home__switch-block">
          <text class="home__switch-label">{{ onlineLabel }}</text>
          <switch
            :key="switchKey"
            :checked="rider.online"
            :disabled="toggling"
            color="#FF7A00"
            @change="onToggleOnline"
          />
        </view>
      </view>

      <view class="home__hint" :class="rider.online ? 'is-on' : 'is-off'">
        {{ rider.online ? '听单中：系统派单会强提醒并语音播报，抢单大厅实时更新' : '已下线：不会收到派单，也不会上报轨迹' }}
      </view>

      <!-- 今日概览 -->
      <view class="rd-card home__stats">
        <view class="home__stat">
          <text class="home__stat-value">{{ incomeLoaded ? rider.todayOrderCount : '--' }}</text>
          <text class="home__stat-label">今日单量</text>
        </view>
        <view class="home__stat-divider" />
        <view class="home__stat">
          <text class="home__stat-value">{{ incomeLoaded ? `¥${fenToYuan(rider.todayAmount)}` : '--' }}</text>
          <text class="home__stat-label">今日收入</text>
        </view>
        <view class="home__stat-divider" />
        <view class="home__stat">
          <text class="home__stat-value">{{ incomeLoaded ? onTimeRateText : '--' }}</text>
          <text class="home__stat-label">准时率</text>
        </view>
      </view>

      <view v-if="activeDispatch" class="home__dispatch-tip">
        <text class="home__dispatch-title">有 1 单系统派单待确认</text>
        <text class="home__dispatch-desc">请在弹窗倒计时内选择接单或拒绝</text>
      </view>

      <!-- 抢单大厅 -->
      <view class="home__section">
        <view class="rd-between home__section-head">
          <text class="home__section-title">抢单大厅</text>
          <text class="home__section-extra">
            {{ rider.online ? `按距离排序 · ${hallList.length} 单` : '上线后可查看' }}
          </text>
        </view>

        <view v-if="!rider.online" class="rd-empty home__empty">
          <text class="rd-empty__title">当前已下线</text>
          <text>打开听单开关后，系统派单与抢单大厅会实时推送给你</text>
          <view class="rd-empty__action" @click="handleTurnOnline">上线听单</view>
        </view>

        <view v-else-if="hallLoading && hallList.length === 0" class="rd-loading">正在获取附近订单…</view>

        <view v-else-if="hallError" class="rd-error">
          <text class="rd-error__msg">{{ hallError }}</text>
          <view class="rd-error__retry" @click="loadHall()">重新加载</view>
        </view>

        <view v-else-if="hallList.length === 0" class="rd-empty home__empty">
          <text class="rd-empty__title">附近暂无可抢订单</text>
          <text>保持在线，有新单会在 5 秒内出现在这里</text>
          <view class="rd-empty__action" @click="loadHall()">刷新看看</view>
        </view>

        <view v-else>
          <view
            v-for="order in hallList"
            :key="order.deliveryId"
            class="rd-card home__order"
          >
            <view class="rd-between home__order-head">
              <view class="home__order-tags">
                <text class="rd-tag">{{ formatDistance(order.distanceM) }}</text>
                <text class="home__order-no">{{ order.deliveryNo }}</text>
              </view>
              <text class="rd-price home__order-fee">{{ fenToYuan(order.deliveryFee) }}</text>
            </view>

            <view class="home__route">
              <view class="home__route-line">
                <text class="home__dot home__dot--pickup">取</text>
                <text class="home__route-text">{{ order.pickupAddress || '取货地址待更新' }}</text>
              </view>
              <view class="home__route-line">
                <text class="home__dot home__dot--drop">送</text>
                <text class="home__route-text">{{ order.receiverAddress || '收货地址待更新' }}</text>
              </view>
            </view>

            <view class="home__order-foot">
              <text class="home__order-time">{{ formatShortDateTime(order.createdAt) }} 发布</text>
              <button
                class="rd-btn home__grab-btn"
                :disabled="grabbingId === order.deliveryId"
                @click="handleGrab(order)"
              >
                {{ grabbingId === order.deliveryId ? '抢单中…' : '抢 单' }}
              </button>
            </view>
          </view>
        </view>
      </view>

      <view class="home__footer">收货人姓名与电话由平台保护，按地址送达即可</view>
    </template>

    <DispatchPopup
      v-if="activeDispatch"
      :task="activeDispatch"
      @accepted="onDispatchAccepted"
      @rejected="onDispatchRejected"
      @failed="onDispatchFailed"
    />
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onHide, onPullDownRefresh, onShow, onUnload } from '@dcloudio/uni-app'
import { ApiError } from '@/api/http'
import {
  fetchHall,
  fetchIncomeOverview,
  fetchPendingDispatches,
  grabDelivery,
  setOnlineState,
} from '@/api/rider'
import type { HallOrderVO, PendingDispatchVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useRiderStore } from '@/stores/rider'
import { getCurrentLocation, stopTrackReport } from '@/utils/location'
import { speak } from '@/utils/voice'
import { fenToYuan } from '@/utils/money'
import { formatShortDateTime } from '@/utils/date'
import DispatchPopup from './DispatchPopup.vue'

const auth = useAuthStore()
const rider = useRiderStore()

const booting = ref(false)
const certError = ref('')
const incomeLoaded = ref(false)
const hallList = ref<HallOrderVO[]>([])
const hallLoading = ref(false)
const hallError = ref('')
const activeDispatch = ref<PendingDispatchVO | null>(null)
const grabbingId = ref(0)
const toggling = ref(false)
const switchKey = ref(0)

/** 本轮页面生命周期内已处理过的派单，避免拒绝后又弹一次 */
const handledIds = new Set<number>()
const announcedIds = new Set<number>()

let timer: ReturnType<typeof setInterval> | null = null
let tickCount = 0
let certRedirecting = false

const avatarText = computed(() => {
  const name = rider.realName || auth.nickname || '骑'
  return name.slice(0, 1)
})

const onlineLabel = computed(() => {
  if (toggling.value) {
    return '切换中…'
  }
  return rider.online ? '听单中' : '已下线'
})

const onTimeRateText = computed(() => `${(Number(rider.onTimeRate) || 0).toFixed(1)}%`)

onShow(() => {
  void bootstrap()
})

onHide(() => {
  stopTimers()
})

onUnload(() => {
  stopTimers()
})

onPullDownRefresh(() => {
  void handlePullDown()
})

async function bootstrap(): Promise<void> {
  if (booting.value) {
    return
  }
  if (!auth.ensureLogin('/pages/home/Home')) {
    return
  }
  booting.value = true
  certError.value = ''
  try {
    try {
      await rider.loadCertification()
    } catch (error) {
      if (!rider.certLoaded) {
        certError.value = (error as Error)?.message || '认证状态查询失败'
        return
      }
      // 已加载过认证信息时保留上次结果，网络抖动不整页报错
      uni.showToast({ title: '认证状态刷新失败，请稍后重试', icon: 'none' })
    }

    // 接单门槛前置：未通过认证不允许停留在听单页
    if (rider.certStatus !== 2) {
      redirectToCert()
      return
    }

    const online = await calibrateOnline()
    void refreshIncome()
    if (online) {
      startTimers()
      await pollPending()
    }
  } finally {
    booting.value = false
  }
}

function relogin(): void {
  auth.logout()
  rider.reset()
  uni.reLaunch({ url: '/pages/login/Login' })
}

function redirectToCert(): void {
  if (certRedirecting) {
    return
  }
  certRedirecting = true
  uni.showToast({ title: '请先完成实名认证', icon: 'none' })
  setTimeout(() => {
    uni.reLaunch({ url: '/pages/cert/CertStatus' })
  }, 600)
}

/**
 * 用抢单大厅接口校准在线状态：
 * 成功 = 服务端认为可接单（在线）；50007 = 未上线；50008 = 在线但已到接单上限；
 * 其它错误不改本地镜像，只把错误展示在大厅区域，避免误判成离线。
 */
async function calibrateOnline(): Promise<boolean> {
  const location = rider.lastLocation
  try {
    const list = await fetchHall({
      lng: location?.lng,
      lat: location?.lat,
      sortBy: 'distance',
      silent: true,
    })
    rider.setOnline(true)
    hallList.value = list
    hallError.value = ''
    return true
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.code === 50007) {
        rider.setOnline(false)
        hallList.value = []
        return false
      }
      if (error.code === 50008) {
        rider.setOnline(true)
        hallError.value = '进行中订单已达上限，完成后再抢单'
        return true
      }
      if (error.code === 50009) {
        hallError.value = '健康证已过期，暂不能接单，请联系运营更新'
        return rider.online
      }
      if (error.code === 50006) {
        hallError.value = '骑手未通过实名认证'
        return false
      }
    }
    hallError.value = (error as Error)?.message || '抢单大厅加载失败'
    return rider.online
  }
}

async function loadHall(silent = false): Promise<void> {
  if (!rider.online) {
    hallList.value = []
    return
  }
  if (!silent) {
    hallLoading.value = true
  }
  try {
    const location = rider.lastLocation
    hallList.value = await fetchHall({
      lng: location?.lng,
      lat: location?.lat,
      sortBy: 'distance',
      silent,
    })
    hallError.value = ''
  } catch (error) {
    if (error instanceof ApiError && error.code === 50007) {
      // 服务端已判定离线（如被后台强制下线），同步开关
      rider.setOnline(false)
      hallList.value = []
      stopTimers()
      switchKey.value += 1
    } else {
      hallError.value = (error as Error)?.message || '抢单大厅加载失败'
    }
  } finally {
    hallLoading.value = false
  }
}

async function refreshIncome(): Promise<void> {
  try {
    const overview = await fetchIncomeOverview()
    rider.applyIncomeOverview(overview)
    incomeLoaded.value = true
  } catch (error) {
    // 收入概览失败不影响接单，保持 '--'
  }
}

async function pollPending(): Promise<void> {
  if (!rider.online || activeDispatch.value) {
    return
  }
  try {
    const list = await fetchPendingDispatches(true)
    if (!Array.isArray(list) || list.length === 0) {
      return
    }
    const fresh = list.filter((item) => !handledIds.has(item.deliveryId))
    if (fresh.length === 0) {
      return
    }
    const unseen = fresh.filter((item) => !announcedIds.has(item.deliveryId))
    unseen.forEach((item) => announcedIds.add(item.deliveryId))
    if (unseen.length > 0) {
      speak('您有新的配送订单，请及时处理')
    }
    activeDispatch.value = fresh[0]
  } catch (error) {
    // 轮询失败静默，下一轮重试
  }
}

function startTimers(): void {
  stopTimers()
  tickCount = 0
  timer = setInterval(() => {
    void onTick()
  }, 5_000)
}

function stopTimers(): void {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

async function onTick(): Promise<void> {
  if (!rider.online) {
    return
  }
  await pollPending()
  tickCount += 1
  // 每 30 秒刷新一次大厅，避免高频请求；派单提醒仍按 5 秒轮询
  if (tickCount % 6 === 0) {
    await loadHall(true)
  }
}

async function onToggleOnline(event: { detail: { value: boolean } }): Promise<void> {
  const next = Boolean(event.detail.value)
  if (toggling.value || next === rider.online) {
    if (next !== rider.online) {
      switchKey.value += 1
    }
    return
  }
  toggling.value = true
  let succeeded = false
  try {
    succeeded = next ? await goOnline() : await goOffline()
  } finally {
    toggling.value = false
    if (!succeeded) {
      // 开关是受控组件，失败时重建以回到 store 中的真实状态
      switchKey.value += 1
    }
  }
}

/** 空态里的「上线听单」按钮，与开关走同一套流程 */
async function handleTurnOnline(): Promise<void> {
  if (toggling.value || rider.online) {
    return
  }
  toggling.value = true
  let succeeded = false
  try {
    succeeded = await goOnline()
  } finally {
    toggling.value = false
    if (!succeeded) {
      switchKey.value += 1
    }
  }
}

async function goOnline(): Promise<boolean> {
  if (rider.certStatus !== 2) {
    uni.showToast({ title: '请先完成实名认证', icon: 'none' })
    return false
  }

  // 上线前必须先取到定位：后端要求 lng/lat，且大厅按距离排序依赖它
  let location
  try {
    location = await getCurrentLocation()
  } catch (error) {
    uni.showModal({
      title: '定位不可用',
      content: `${(error as Error)?.message || '无法获取当前位置'}。听单需要定位权限，请在系统或浏览器设置中允许后重试。`,
      showCancel: false,
    })
    return false
  }
  rider.rememberLocation(location)

  try {
    await setOnlineState({ online: true, lng: location.lng, lat: location.lat })
  } catch (error) {
    // http 层已提示（未认证、健康证过期、账号停用等）
    return false
  }

  rider.setOnline(true)
  uni.showToast({ title: '已上线，开始听单', icon: 'none' })
  startTimers()
  void refreshIncome()
  await loadHall()
  await pollPending()
  return true
}

async function goOffline(): Promise<boolean> {
  try {
    await setOnlineState({ online: false, reason: '骑手主动下线' })
  } catch (error) {
    // 下线失败保持在线，避免前端显示已下线但服务端仍在派单
    return false
  }

  rider.setOnline(false)
  // 听单关闭后不再上报轨迹（T14 约束），同时停掉派单轮询
  stopTrackReport()
  stopTimers()
  activeDispatch.value = null
  hallList.value = []
  hallError.value = ''
  uni.showToast({ title: '已下线，不再接收派单', icon: 'none' })
  return true
}

function handleGrab(order: HallOrderVO): void {
  if (grabbingId.value) {
    return
  }
  uni.showModal({
    title: '确认抢单',
    content: `配送费 ¥${fenToYuan(order.deliveryFee)}，距你 ${formatDistance(order.distanceM)}。抢单成功后请尽快到店取货。`,
    confirmText: '抢单',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      grabbingId.value = order.deliveryId
      try {
        await grabDelivery(order.deliveryId)
        uni.showToast({ title: '抢单成功', icon: 'none' })
        hallList.value = hallList.value.filter((item) => item.deliveryId !== order.deliveryId)
        uni.navigateTo({ url: `/pages/task/Detail?id=${order.deliveryId}` })
      } catch (error) {
        // 已被抢走 / 到接单上限等：http 层提示，移除该条避免连续点击
        hallList.value = hallList.value.filter((item) => item.deliveryId !== order.deliveryId)
        void loadHall(true)
      } finally {
        grabbingId.value = 0
      }
    },
  })
}

function onDispatchAccepted(deliveryId: number): void {
  handledIds.add(deliveryId)
  activeDispatch.value = null
  void loadHall(true)
  uni.navigateTo({ url: `/pages/task/Detail?id=${deliveryId}` })
}

function onDispatchRejected(deliveryId: number, reason: string): void {
  handledIds.add(deliveryId)
  activeDispatch.value = null
  uni.showToast({
    title: reason === '超时未响应' ? '超时未接单，已自动拒单' : '已拒单',
    icon: 'none',
  })
  void loadHall(true)
}

function onDispatchFailed(): void {
  // 接单/拒单接口失败：关闭弹窗，若配送单仍有效会在下一轮轮询重新弹出
  activeDispatch.value = null
}

async function handlePullDown(): Promise<void> {
  try {
    if (!auth.isLogin) {
      return
    }
    if (!rider.certLoaded) {
      await bootstrap()
      return
    }
    if (rider.certStatus !== 2) {
      return
    }
    await calibrateOnline()
    await refreshIncome()
    if (rider.online) {
      await loadHall()
      await pollPending()
    }
  } finally {
    uni.stopPullDownRefresh()
  }
}

function formatDistance(distanceM: number | null | undefined): string {
  if (distanceM === null || distanceM === undefined) {
    return '距离未知'
  }
  const meters = Number(distanceM)
  if (!Number.isFinite(meters)) {
    return '距离未知'
  }
  if (meters < 1000) {
    return `${Math.round(meters)} m`
  }
  return `${(meters / 1000).toFixed(1)} km`
}
</script>

<style scoped>
.home {
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
}

.home__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx;
  border-radius: 24rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #2c4467, #16233a);
}

.home__top.is-online {
  background: linear-gradient(135deg, #1f7a5a, #0b4f39);
}

.home__identity {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.home__avatar {
  width: 88rpx;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  border-radius: 24rpx;
  background-color: rgba(255, 255, 255, 0.18);
  font-size: 40rpx;
  font-weight: 700;
}

.home__identity-text {
  display: flex;
  flex-direction: column;
}

.home__name {
  font-size: 32rpx;
  font-weight: 700;
}

.home__no {
  margin-top: 6rpx;
  font-size: 22rpx;
  opacity: 0.82;
}

.home__switch-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}

.home__switch-label {
  font-size: 22rpx;
  font-weight: 600;
  opacity: 0.9;
}

.home__hint {
  margin-top: 16rpx;
  padding: 14rpx 22rpx;
  border-radius: 12rpx;
  font-size: 22rpx;
  line-height: 1.6;
}

.home__hint.is-on {
  background-color: #dcfae6;
  color: #067647;
}

.home__hint.is-off {
  background-color: #eef2f7;
  color: #55637a;
}

.home__stats {
  display: flex;
  align-items: center;
  margin-top: 20rpx;
  padding: 28rpx 12rpx;
}

.home__stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.home__stat-value {
  font-size: 36rpx;
  font-weight: 700;
  color: #16233a;
  font-variant-numeric: tabular-nums;
}

.home__stat-label {
  font-size: 22rpx;
  color: #7a8699;
}

.home__stat-divider {
  width: 1rpx;
  height: 56rpx;
  background-color: #edf1f6;
}

.home__dispatch-tip {
  margin-top: 20rpx;
  padding: 20rpx 24rpx;
  border-radius: 16rpx;
  background-color: #fff1e6;
  border: 2rpx solid #ffd0a8;
}

.home__dispatch-title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #c2410c;
}

.home__dispatch-desc {
  display: block;
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #a2520a;
}

.home__section {
  margin-top: 32rpx;
}

.home__section-head {
  padding: 0 4rpx 16rpx;
}

.home__section-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #16233a;
}

.home__section-extra {
  font-size: 22rpx;
  color: #7a8699;
}

.home__empty {
  background-color: #ffffff;
  border-radius: 20rpx;
  padding: 72rpx 40rpx;
}

.home__order {
  margin-bottom: 20rpx;
  padding: 24rpx;
}

.home__order-head {
  align-items: flex-start;
}

.home__order-tags {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.home__order-no {
  font-size: 22rpx;
  color: #8d99aa;
}

.home__order-fee {
  font-size: 40rpx;
}

.home__route {
  margin-top: 18rpx;
  padding: 18rpx 20rpx;
  border-radius: 14rpx;
  background-color: #f7f9fc;
}

.home__route-line {
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
  padding: 6rpx 0;
}

.home__dot {
  flex: none;
  width: 36rpx;
  height: 36rpx;
  line-height: 36rpx;
  text-align: center;
  border-radius: 8rpx;
  font-size: 20rpx;
  font-weight: 700;
  color: #ffffff;
  margin-top: 4rpx;
}

.home__dot--pickup {
  background-color: #e85d04;
}

.home__dot--drop {
  background-color: #16233a;
}

.home__route-text {
  flex: 1;
  font-size: 26rpx;
  color: #33455f;
  line-height: 1.5;
  word-break: break-all;
}

.home__order-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 18rpx;
}

.home__order-time {
  font-size: 22rpx;
  color: #8d99aa;
}

.home__grab-btn {
  width: 200rpx;
  height: 80rpx;
  line-height: 80rpx;
  font-size: 28rpx;
  border-radius: 14rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #ff9a3c, #ff7a00);
}

.home__footer {
  margin-top: 32rpx;
  text-align: center;
  font-size: 21rpx;
  color: #a9b4c4;
}

.home__relogin {
  display: inline-block;
  margin-top: 28rpx;
  color: #55637a;
  font-size: 24rpx;
  text-decoration: underline;
}
</style>
