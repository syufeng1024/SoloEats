<template>
  <view class="se-page pay">
    <view class="se-card pay__card">
      <template v-if="phase === 'success'">
        <view class="pay__mark pay__mark--success">✓</view>
        <text class="pay__title">支付成功</text>
        <text class="se-price pay__amount">{{ fenToYuan(amount) }}</text>
        <text class="se-muted pay__desc">门店已收到订单，可在订单详情查看进度</text>
      </template>

      <template v-else-if="phase === 'failed'">
        <view class="pay__mark pay__mark--fail">!</view>
        <text class="pay__title">支付未完成</text>
        <text class="pay__error">{{ errorText }}</text>
        <text class="se-muted pay__desc">未扣款，可重试或稍后在订单列表继续支付</text>
      </template>

      <template v-else>
        <view class="pay__mark pay__mark--wait">{{ phase === 'paying' ? '¥' : '…' }}</view>
        <text class="pay__title">{{ phase === 'paying' ? '正在发起支付' : timedOut ? '暂未收到支付结果' : '支付结果确认中' }}</text>
        <text class="se-price pay__amount">{{ fenToYuan(amount) }}</text>
        <text class="se-muted pay__desc">
          {{
            phase === 'paying'
              ? '正在向后端创建支付单，请稍候'
              : timedOut
                ? '超过 60 秒仍未确认，可点击下方按钮重新查询'
                : `每 2 秒自动查询一次${countdownText}`
          }}
        </text>
      </template>

      <view class="pay__actions">
        <template v-if="phase === 'success'">
          <button class="se-btn se-btn-primary pay__btn" @click="goDetail">查看订单</button>
          <button v-if="pickupCode" class="se-btn se-btn-ghost pay__btn" @click="goPickupCode">
            查看取餐码 {{ pickupCode }}
          </button>
          <button class="se-btn se-btn-plain pay__btn" @click="goHome">返回首页</button>
        </template>

        <template v-else-if="phase === 'failed'">
          <button class="se-btn se-btn-primary pay__btn" :disabled="retrying" @click="startPay">
            {{ retrying ? '发起中…' : '重新支付' }}
          </button>
          <button class="se-btn se-btn-plain pay__btn" @click="goDetail">查看订单</button>
        </template>

        <template v-else>
          <button class="se-btn se-btn-primary pay__btn" :disabled="refreshing" @click="refreshStatus">
            {{ refreshing ? '查询中…' : '我已完成支付，刷新状态' }}
          </button>
          <button class="se-btn se-btn-plain pay__btn" @click="goDetail">查看订单</button>
        </template>
      </view>

      <view v-if="phase === 'success' && pickupCode" class="pay__pickup">
        自取订单：到店出示 6 位取餐码 {{ pickupCode }}
      </view>
    </view>

    <view v-if="isDev" class="pay__tip">
      本地联调提示：/payments/pay 只创建支付单（PAYING），支付成功需要后端收到
      /payments/callback 回调。联调脚本见 docs/支付与超时联调验收.md。
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { ORDER_STATUS, fetchOrderDetail } from '@/api/order'
import { DEFAULT_PAY_CHANNEL, payOrder } from '@/api/pay'
import { fenToYuan } from '@/utils/money'
import { clearIdempotencyKey, issueIdempotencyKey } from '@/utils/request'

type Phase = 'paying' | 'waiting' | 'success' | 'failed'

const orderId = ref(0)
const amount = ref(0)
const pickupCode = ref('')
const phase = ref<Phase>('paying')
const errorText = ref('')
const timedOut = ref(false)
const refreshing = ref(false)
const retrying = ref(false)
// 联调提示只在 dev 显示，生产构建（npm run build:h5）自动去掉
const isDev = process.env.NODE_ENV !== 'production'

const POLL_INTERVAL_MS = 2000
const MAX_ATTEMPTS = 30

let pollTimer: ReturnType<typeof setInterval> | null = null
let attempts = 0
let polling = false
let stopped = false

const remainingSeconds = computed(() => Math.max(0, (MAX_ATTEMPTS - attempts) * 2))
const countdownText = computed(() =>
  timedOut.value ? '' : `，剩余约 ${remainingSeconds.value} 秒`,
)

function payScope(): string {
  return `pay:${orderId.value}:${DEFAULT_PAY_CHANNEL}`
}

onLoad((options) => {
  const id = Number(options?.orderId)
  if (!Number.isFinite(id) || id <= 0) {
    phase.value = 'failed'
    errorText.value = '缺少订单参数'
    return
  }
  orderId.value = id
  amount.value = Number(options?.payAmount) || 0
  pickupCode.value = options?.pickupCode ? decodeURIComponent(String(options.pickupCode)) : ''
  startPay()
})

onUnload(() => {
  stopped = true
  stopPolling()
})

async function startPay(): Promise<void> {
  if (retrying.value) {
    return
  }
  retrying.value = true
  stopPolling()
  phase.value = 'paying'
  timedOut.value = false
  errorText.value = ''
  const key = issueIdempotencyKey(payScope())

  try {
    const payment = await payOrder(orderId.value, DEFAULT_PAY_CHANNEL, key)
    amount.value = payment.amount || amount.value
    if (payment.status === 20) {
      // 已存在成功支付单：直接按成功处理
      clearIdempotencyKey(payScope())
      succeed()
      return
    }
    phase.value = 'waiting'
    startPolling()
  } catch (error) {
    phase.value = 'failed'
    errorText.value = (error as Error)?.message || '发起支付失败'
  } finally {
    retrying.value = false
  }
}

function startPolling(): void {
  stopPolling()
  attempts = 0
  pollTimer = setInterval(() => {
    void pollOnce()
  }, POLL_INTERVAL_MS)
}

function stopPolling(): void {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function pollOnce(): Promise<void> {
  if (polling || stopped || phase.value !== 'waiting') {
    return
  }
  polling = true
  attempts += 1
  try {
    const detail = await fetchOrderDetail(orderId.value, true)
    amount.value = detail.payAmount || amount.value
    pickupCode.value = detail.pickupCode || pickupCode.value

    if (detail.status === ORDER_STATUS.CANCELLED) {
      phase.value = 'failed'
      errorText.value = '订单已取消'
      stopPolling()
      return
    }
    if (detail.status !== ORDER_STATUS.WAIT_PAY) {
      succeed()
      return
    }
    if (attempts >= MAX_ATTEMPTS) {
      timedOut.value = true
      stopPolling()
    }
  } catch (error) {
    // 轮询失败不打断用户，下一轮继续
    if (attempts >= MAX_ATTEMPTS) {
      timedOut.value = true
      stopPolling()
    }
  } finally {
    polling = false
  }
}

function succeed(): void {
  stopPolling()
  phase.value = 'success'
  clearIdempotencyKey(payScope())
}

async function refreshStatus(): Promise<void> {
  if (refreshing.value) {
    return
  }
  refreshing.value = true
  timedOut.value = false
  try {
    const detail = await fetchOrderDetail(orderId.value)
    amount.value = detail.payAmount || amount.value
    pickupCode.value = detail.pickupCode || pickupCode.value
    if (detail.status === ORDER_STATUS.CANCELLED) {
      phase.value = 'failed'
      errorText.value = '订单已取消'
      return
    }
    if (detail.status !== ORDER_STATUS.WAIT_PAY) {
      succeed()
      return
    }
    phase.value = 'waiting'
    startPolling()
  } catch (error) {
    // http 层已提示
  } finally {
    refreshing.value = false
  }
}

function goDetail(): void {
  uni.redirectTo({ url: `/pages/order/Detail?id=${orderId.value}` })
}

function goPickupCode(): void {
  uni.navigateTo({ url: `/pages/order/PickupCode?orderId=${orderId.value}` })
}

function goHome(): void {
  uni.switchTab({ url: '/pages/index/Index' })
}
</script>

<style scoped>
.pay__card {
  padding: 48rpx 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.pay__mark {
  width: 120rpx;
  height: 120rpx;
  line-height: 120rpx;
  text-align: center;
  border-radius: 50%;
  font-size: 60rpx;
  color: #ffffff;
  margin-bottom: 24rpx;
}

.pay__mark--success {
  background: linear-gradient(135deg, #52c41a, #389e0d);
}

.pay__mark--fail {
  background: #ff4d4f;
}

.pay__mark--wait {
  background: linear-gradient(135deg, #ffa24c, #ff7a00);
}

.pay__title {
  font-size: 34rpx;
  font-weight: 600;
}

.pay__amount {
  margin-top: 12rpx;
  font-size: 56rpx;
}

.pay__desc {
  margin-top: 12rpx;
  text-align: center;
}

.pay__error {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #d4380d;
  text-align: center;
}

.pay__actions {
  width: 100%;
  margin-top: 40rpx;
}

.pay__btn {
  width: 100%;
  margin-bottom: 16rpx;
}

.pay__pickup {
  width: 100%;
  margin-top: 16rpx;
  padding: 16rpx;
  border-radius: 8rpx;
  background-color: #fff8ef;
  color: #a26a2a;
  font-size: 22rpx;
  text-align: center;
}

.pay__tip {
  margin-top: 24rpx;
  padding: 0 8rpx;
  font-size: 20rpx;
  line-height: 1.7;
  color: #a0a0a0;
}
</style>
