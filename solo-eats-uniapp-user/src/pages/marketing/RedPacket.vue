<template>
  <view class="packet">
    <view class="packet__banner">
      <text class="packet__banner-title">拼手气红包</text>
      <text class="packet__banner-desc">
        列表金额为红包池总额，领取金额由系统随机分配；每个红包每人限领一次。
      </text>
    </view>

    <view v-if="loading && packets.length === 0" class="se-loading">正在加载红包…</view>

    <view v-else-if="error && packets.length === 0" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="load(false)">重新加载</view>
    </view>

    <view v-else-if="packets.length === 0" class="se-empty">
      <text class="se-empty__title">暂无可领取的红包</text>
      <text>新红包开放后会在这里展示</text>
    </view>

    <template v-else>
      <view v-if="error" class="packet__error">
        <text>{{ error }}</text>
        <text class="packet__error-retry" @click="load(false)">重试</text>
      </view>

      <view v-for="item in packets" :key="item.id" class="se-card packet__card">
        <view class="packet__main">
          <view class="packet__amount-row">
            <text class="packet__currency">¥</text>
            <text class="packet__amount">{{ fenToYuan(item.totalAmount) }}</text>
            <text class="packet__amount-label">总额</text>
          </view>
          <text class="packet__qty">剩余 {{ item.remainQty }} / {{ item.totalQty }} 份</text>
          <text class="packet__validity">
            {{ formatDateTime(item.startAt) }} ~ {{ formatDateTime(item.endAt) }}
          </text>
        </view>

        <view class="packet__side">
          <text class="packet__status" :class="`is-${stateOf(item).tone}`">
            {{ stateOf(item).label }}
          </text>
          <view
            class="packet__action"
            :class="{
              'is-disabled': !stateOf(item).canGrab,
              'is-done': item.grabbed,
            }"
            @click="handleGrab(item)"
          >
            {{ actionLabel(item) }}
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { fetchRedPackets, grabRedPacket } from '@/api/marketing'
import type { RedPacketVO } from '@/api/marketing'
import { fenToYuan } from '@/utils/money'
import { formatDateTime } from '@/utils/date'
import { useAuthStore } from '@/stores/auth'

/**
 * 红包列表。
 *
 * RedPacketView 没有「单人可得金额」字段：totalAmount 是整个红包池总额，
 * 页面按「总额 + 剩余份数」展示，领取后的实际金额取 grab 的返回值。
 * grabbed=true 由后端按当前用户判断；已领取、已抢完、未开始、已结束均禁用按钮。
 */

interface PacketState {
  label: string
  tone: string
  canGrab: boolean
}

const auth = useAuthStore()

const packets = ref<RedPacketVO[]>([])
const loading = ref(false)
const error = ref('')
const grabbing = ref<Record<number, boolean>>({})
const nowText = ref(localNow())

let sequence = 0

onShow(() => {
  if (!auth.ensureLogin('/pages/marketing/RedPacket')) {
    return
  }
  load(false)
})

onPullDownRefresh(() => {
  if (loading.value) {
    uni.stopPullDownRefresh()
    return
  }
  load(false)
})

async function load(silent: boolean): Promise<void> {
  const current = ++sequence
  loading.value = true
  if (!silent) {
    error.value = ''
  }
  try {
    const list = await fetchRedPackets(silent)
    if (current !== sequence) {
      return
    }
    packets.value = list
    nowText.value = localNow()
    error.value = ''
  } catch (err) {
    if (current === sequence) {
      // 保留已有红包列表，只提示失败
      error.value = (err as Error)?.message || '红包加载失败'
      if (!silent) {
        uni.showToast({ title: error.value, icon: 'none' })
      }
    }
  } finally {
    if (current === sequence) {
      loading.value = false
    }
    uni.stopPullDownRefresh()
  }
}

async function handleGrab(item: RedPacketVO): Promise<void> {
  if (!auth.ensureLogin('/pages/marketing/RedPacket') || grabbing.value[item.id]) {
    return
  }
  const state = stateOf(item)
  if (!state.canGrab) {
    uni.showToast({ title: state.label, icon: 'none' })
    return
  }
  grabbing.value = { ...grabbing.value, [item.id]: true }
  try {
    const amount = await grabRedPacket(item.id)
    item.grabbed = true
    item.remainQty = Math.max(0, item.remainQty - 1)
    uni.showModal({
      title: '领取成功',
      content: `获得 ¥${fenToYuan(amount)} 红包，已发放至你的红包账户。`,
      showCancel: false,
      confirmText: '知道了',
    })
    // 静默刷新剩余份数与领取状态，失败也不影响已展示的结果
    load(true)
  } catch {
    // 已抢完、不在领取时间、重复领取等错误由 http 层统一 toast
  } finally {
    const next = { ...grabbing.value }
    delete next[item.id]
    grabbing.value = next
  }
}

function stateOf(item: RedPacketVO): PacketState {
  if (item.grabbed) {
    return { label: '已领取', tone: 'done', canGrab: false }
  }
  if (item.remainQty <= 0) {
    return { label: '已抢完', tone: 'done', canGrab: false }
  }
  if (item.startAt && normalize(item.startAt) > nowText.value) {
    return { label: '未开始', tone: 'soon', canGrab: false }
  }
  if (item.endAt && normalize(item.endAt) <= nowText.value) {
    return { label: '已结束', tone: 'done', canGrab: false }
  }
  return { label: '可领取', tone: 'active', canGrab: true }
}

function actionLabel(item: RedPacketVO): string {
  if (grabbing.value[item.id]) {
    return '领取中…'
  }
  if (item.grabbed) {
    return '已领取'
  }
  return stateOf(item).canGrab ? '领取红包' : stateOf(item).label
}

/** LocalDateTime 字符串统一成 yyyy-MM-ddTHH:mm:ss，便于和本地时间做字典序比较。 */
function normalize(value: string): string {
  return value.replace(' ', 'T')
}

function localNow(): string {
  const date = new Date()
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  )
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}
</script>

<style scoped>
.packet {
  min-height: 100vh;
  padding: 24rpx;
  background-color: #f5f5f7;
}

.packet__banner {
  padding: 28rpx 32rpx;
  border-radius: 12rpx;
  background: linear-gradient(120deg, #ff9a2e 0%, #e63a3a 100%);
  color: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(230, 58, 58, 0.22);
}

.packet__banner-title {
  display: block;
  font-size: 36rpx;
  font-weight: 600;
}

.packet__banner-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  line-height: 1.6;
  opacity: 0.92;
}

.packet__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
  padding: 14rpx 20rpx;
  border-radius: 8rpx;
  background-color: #fff8ef;
  color: #a26a2a;
  font-size: 22rpx;
}

.packet__error-retry {
  color: #ff7a00;
  margin-left: 16rpx;
}

.packet__card {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-top: 16rpx;
  padding: 26rpx 24rpx;
}

.packet__main {
  flex: 1;
  min-width: 0;
}

.packet__amount-row {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
}

.packet__currency {
  color: #ff4d00;
  font-size: 28rpx;
  font-weight: 600;
}

.packet__amount {
  color: #ff4d00;
  font-size: 52rpx;
  font-weight: 600;
  line-height: 1.2;
}

.packet__amount-label {
  margin-left: 6rpx;
  color: #a0a0a0;
  font-size: 20rpx;
}

.packet__qty {
  display: block;
  margin-top: 6rpx;
  color: #555555;
  font-size: 23rpx;
}

.packet__validity {
  display: block;
  margin-top: 4rpx;
  color: #a0a0a0;
  font-size: 20rpx;
}

.packet__side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10rpx;
  flex-shrink: 0;
}

.packet__status {
  padding: 2rpx 12rpx;
  border-radius: 6rpx;
  background-color: #f7f7f8;
  color: #8a8a8a;
  font-size: 20rpx;
}

.packet__status.is-active {
  background-color: #fff2e6;
  color: #ff7a00;
}

.packet__status.is-soon {
  background-color: #fff8ef;
  color: #ff9a2e;
}

.packet__action {
  min-width: 140rpx;
  height: 64rpx;
  line-height: 64rpx;
  padding: 0 20rpx;
  border-radius: 32rpx;
  text-align: center;
  background: linear-gradient(135deg, #ff7a4c, #e63a3a);
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 500;
}

.packet__action.is-disabled {
  background: #e8e8ec;
  color: #a0a0a0;
}

.packet__action.is-done {
  background-color: #f2f2f4;
  color: #8a8a8a;
}
</style>
