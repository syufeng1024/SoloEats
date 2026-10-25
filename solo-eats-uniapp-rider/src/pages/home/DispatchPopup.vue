<template>
  <view class="dispatch">
    <view class="dispatch__mask" />
    <view class="dispatch__card">
      <view class="dispatch__head">
        <text class="dispatch__badge">系统派单</text>
        <text class="dispatch__countdown">{{ left }}s</text>
      </view>

      <view class="dispatch__progress">
        <view class="dispatch__progress-bar" :style="{ width: `${percent}%` }" />
      </view>

      <view class="dispatch__body">
        <view class="dispatch__row">
          <text class="dispatch__key dispatch__key--pickup">取</text>
          <text class="dispatch__value">{{ task.pickupAddress || '取货地址待更新' }}</text>
        </view>
        <view class="dispatch__row">
          <text class="dispatch__key dispatch__key--drop">送</text>
          <text class="dispatch__value">{{ task.receiverAddress || '收货地址待更新' }}</text>
        </view>
        <view class="dispatch__fee">
          <text class="dispatch__fee-label">本单配送费</text>
          <text class="dispatch__fee-value rd-price">{{ fenToYuan(task.deliveryFee) }}</text>
        </view>
        <view class="dispatch__protect">收货人姓名与电话由平台保护，按地址送达即可</view>
      </view>

      <view class="dispatch__actions">
        <button
          class="rd-btn rd-btn--plain dispatch__btn"
          :disabled="acting"
          @click="handleReject"
        >
          拒 绝
        </button>
        <button
          class="rd-btn rd-btn--primary dispatch__btn"
          :disabled="acting"
          @click="handleAccept"
        >
          {{ acting ? '处理中…' : '接 单' }}
        </button>
      </view>

      <view class="dispatch__foot">倒计时结束将自动拒绝，配送单会继续派给其他骑手</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { acceptDispatch, rejectDispatch } from '@/api/rider'
import type { PendingDispatchVO } from '@/api/types'
import { fenToYuan } from '@/utils/money'

/**
 * 派单强提醒。
 *
 * 倒计时口径：不使用服务端绝对 expireAt 与本地 now 比较（两端时钟可能有偏差），
 * 而是在收到弹窗的瞬间用服务端下发的 remainingSeconds 与本地 Date.now() 建立
 * 相对截止时间，之后只测本地流逝，时钟偏差不会影响倒计时长度。
 */

const props = defineProps<{ task: PendingDispatchVO }>()

const emit = defineEmits<{
  (event: 'accepted', deliveryId: number): void
  (event: 'rejected', deliveryId: number, reason: string): void
  (event: 'failed'): void
}>()

const REJECT_REASONS = ['距离太远', '路线不顺路', '已有其他订单', '临时有事']

const serverSeconds = Math.max(0, Math.floor(Number(props.task.remainingSeconds) || 0))
const totalSeconds = Math.max(serverSeconds, 1)
const deadline = Date.now() + serverSeconds * 1000

const left = ref(serverSeconds)
const percent = computed(() => Math.max(0, Math.min(100, (left.value / totalSeconds) * 100)))

const acting = ref(false)
let timer: ReturnType<typeof setInterval> | null = null
let finished = false
/** 打开拒单原因选择器期间暂停自动倒计时，避免选原因时被自动拒单 */
let choosingReason = false

onMounted(() => {
  if (serverSeconds <= 0) {
    void handleTimeout()
    return
  }
  timer = setInterval(tick, 500)
})

onUnmounted(() => {
  stopTimer()
})

function stopTimer(): void {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function tick(): void {
  const remainingMs = deadline - Date.now()
  left.value = Math.max(0, Math.ceil(remainingMs / 1000))
  if (remainingMs <= 0) {
    // 不在这里停表：若骑手正开着拒单原因选择器，handleTimeout 会跳过，
    // 等选择器关闭后的下一拍再自动拒单，否则弹窗会停在 0s 不动。
    void handleTimeout()
  }
}

async function handleTimeout(): Promise<void> {
  if (finished || acting.value || choosingReason) {
    return
  }
  stopTimer()
  finished = true
  acting.value = true
  try {
    await rejectDispatch(props.task.deliveryId, '超时未响应')
    emit('rejected', props.task.deliveryId, '超时未响应')
  } catch (error) {
    // 拒单失败（如已被改派）也关闭弹窗，由服务端最终状态兜底
    emit('rejected', props.task.deliveryId, '超时未响应')
  } finally {
    acting.value = false
  }
}

async function handleAccept(): Promise<void> {
  if (finished || acting.value) {
    return
  }
  finished = true
  acting.value = true
  stopTimer()
  try {
    await acceptDispatch(props.task.deliveryId)
    emit('accepted', props.task.deliveryId)
  } catch (error) {
    // 派单超时/已被他人接走：http 层已提示，关闭弹窗等待下一次派单
    emit('failed')
  } finally {
    acting.value = false
  }
}

function handleReject(): void {
  if (finished || acting.value) {
    return
  }
  choosingReason = true
  uni.showActionSheet({
    itemList: REJECT_REASONS,
    success: (result) => {
      const reason = REJECT_REASONS[result.tapIndex]
      if (reason) {
        void submitReject(reason)
      } else {
        choosingReason = false
      }
    },
    fail: () => {
      choosingReason = false
    },
  })
}

async function submitReject(reason: string): Promise<void> {
  if (finished) {
    choosingReason = false
    return
  }
  finished = true
  acting.value = true
  stopTimer()
  try {
    await rejectDispatch(props.task.deliveryId, reason)
    emit('rejected', props.task.deliveryId, reason)
  } catch (error) {
    // http 层提示；关闭后若单仍在，下一次轮询会重新弹出
    emit('failed')
  } finally {
    choosingReason = false
    acting.value = false
  }
}
</script>

<style scoped>
.dispatch {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32rpx;
}

.dispatch__mask {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background-color: rgba(12, 20, 33, 0.62);
}

.dispatch__card {
  position: relative;
  width: 100%;
  max-width: 660rpx;
  border-radius: 28rpx;
  background-color: #ffffff;
  overflow: hidden;
  box-shadow: 0 24rpx 64rpx rgba(12, 20, 33, 0.32);
}

.dispatch__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx 12rpx;
}

.dispatch__badge {
  padding: 6rpx 18rpx;
  border-radius: 10rpx;
  background-color: #fff1e6;
  color: #c2410c;
  font-size: 26rpx;
  font-weight: 700;
  letter-spacing: 2rpx;
}

.dispatch__countdown {
  font-size: 44rpx;
  font-weight: 700;
  color: #e85d04;
  font-variant-numeric: tabular-nums;
}

.dispatch__progress {
  height: 8rpx;
  margin: 0 32rpx;
  border-radius: 4rpx;
  background-color: #ffe6d1;
  overflow: hidden;
}

.dispatch__progress-bar {
  height: 100%;
  background: linear-gradient(90deg, #ffb26b, #ff7a00);
  transition: width 0.4s linear;
}

.dispatch__body {
  padding: 24rpx 32rpx 8rpx;
}

.dispatch__row {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  padding: 14rpx 0;
}

.dispatch__key {
  flex: none;
  width: 40rpx;
  height: 40rpx;
  line-height: 40rpx;
  text-align: center;
  border-radius: 8rpx;
  font-size: 22rpx;
  font-weight: 700;
  color: #ffffff;
  margin-top: 4rpx;
}

.dispatch__key--pickup {
  background-color: #e85d04;
}

.dispatch__key--drop {
  background-color: #16233a;
}

.dispatch__value {
  flex: 1;
  font-size: 30rpx;
  line-height: 1.5;
  color: #16233a;
  font-weight: 500;
  word-break: break-all;
}

.dispatch__fee {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 16rpx;
  padding: 18rpx 22rpx;
  border-radius: 14rpx;
  background-color: #fff8ef;
}

.dispatch__fee-label {
  font-size: 26rpx;
  color: #7a8699;
}

.dispatch__fee-value {
  font-size: 40rpx;
}

.dispatch__protect {
  margin-top: 16rpx;
  font-size: 22rpx;
  color: #8d99aa;
  line-height: 1.6;
}

.dispatch__actions {
  display: flex;
  gap: 20rpx;
  padding: 24rpx 32rpx 12rpx;
}

.dispatch__btn {
  flex: 1;
}

.dispatch__foot {
  padding: 4rpx 32rpx 28rpx;
  text-align: center;
  font-size: 21rpx;
  color: #a9b4c4;
}
</style>
