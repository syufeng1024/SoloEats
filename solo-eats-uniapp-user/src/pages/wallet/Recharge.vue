<template>
  <view class="recharge">
    <view class="recharge__balance se-card">
      <text class="recharge__balance-label">当前余额（元）</text>
      <text class="recharge__balance-value">
        {{ wallet ? fenToYuan(wallet.balance) : '--' }}
      </text>
      <text v-if="balanceError" class="recharge__balance-error" @click="loadWallet">
        {{ balanceError }}，点击重试
      </text>
    </view>

    <view class="se-card recharge__form">
      <text class="recharge__title">充值金额</text>
      <view class="recharge__input-row">
        <text class="recharge__currency">¥</text>
        <input
          v-model="amountText"
          class="recharge__input"
          type="digit"
          maxlength="10"
          placeholder="0.00"
          placeholder-class="recharge__placeholder"
          @input="clearSuccess"
        />
      </view>
      <text class="recharge__hint">单次可充 0.01 ~ 1000.00 元，本期为模拟充值，不产生真实扣款</text>

      <view class="recharge__quick">
        <view
          v-for="option in QUICK_AMOUNTS"
          :key="option.fen"
          class="recharge__quick-item"
          :class="{ 'is-active': amountFen === option.fen }"
          @click="chooseQuick(option.fen)"
        >
          {{ option.label }}
        </view>
      </view>
    </view>

    <view v-if="successMessage" class="recharge__success">
      <text>{{ successMessage }}</text>
    </view>

    <button
      class="se-btn se-btn-primary recharge__submit"
      :disabled="submitting"
      @click="submit"
    >
      {{ submitting ? '充值中…' : submitLabel }}
    </button>

    <text class="recharge__footnote">
      充值记录可在「我的钱包 → 收支明细」核对；同一幂等键重复提交不会重复入账。
    </text>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchWallet, rechargeWallet } from '@/api/user'
import type { WalletVO } from '@/api/types'
import { fenToYuan, yuanToFen } from '@/utils/money'
import { clearIdempotencyKey, issueIdempotencyKey } from '@/utils/request'
import { useAuthStore } from '@/stores/auth'

/**
 * 钱包充值页。
 *
 * 金额口径：输入框是「元」，提交前用 yuanToFen 逐位转成「分」整数，
 * 后端限制 1 ~ 100000 分（0.01 ~ 1000.00 元），越界返回 80007。
 *
 * 幂等：键的 scope 里带上用户与金额，失败重试复用同一个键；
 * 改了金额会拿到新键，避免后端按 requestId 判重后「返回成功但没入账」。
 * 成功后清除，下一次充值生成新键。
 */

const QUICK_AMOUNTS = [
  { label: '¥10', fen: 1000 },
  { label: '¥50', fen: 5000 },
  { label: '¥100', fen: 10000 },
  { label: '¥200', fen: 20000 },
] as const

const MIN_FEN = 1
const MAX_FEN = 100_000

const auth = useAuthStore()

const wallet = ref<WalletVO | null>(null)
const balanceError = ref('')
const amountText = ref('')
const submitting = ref(false)
const successMessage = ref('')

const amountFen = computed(() => {
  if (!/^\d+(\.\d{1,2})?$/.test(amountText.value.trim())) {
    return 0
  }
  try {
    return yuanToFen(amountText.value.trim())
  } catch {
    return 0
  }
})

const submitLabel = computed(() =>
  amountFen.value > 0 ? `确认充值 ¥${fenToYuan(amountFen.value)}` : '确认充值',
)

onLoad(() => {
  if (!auth.ensureLogin('/pages/wallet/Recharge')) {
    return
  }
  loadWallet()
})

async function loadWallet(): Promise<void> {
  balanceError.value = ''
  try {
    wallet.value = await fetchWallet(true)
  } catch (err) {
    balanceError.value = (err as Error)?.message || '余额加载失败'
  }
}

function chooseQuick(fen: number): void {
  amountText.value = fenToYuan(fen)
  clearSuccess()
}

function clearSuccess(): void {
  successMessage.value = ''
}

function toast(message: string): void {
  uni.showToast({ title: message, icon: 'none', duration: 2200 })
}

function validate(): number | null {
  const raw = amountText.value.trim()
  if (!raw) {
    toast('请输入充值金额')
    return null
  }
  if (!/^\d+(\.\d{1,2})?$/.test(raw)) {
    toast('金额最多两位小数')
    return null
  }
  let fen = 0
  try {
    fen = yuanToFen(raw)
  } catch {
    toast('金额格式不正确')
    return null
  }
  if (fen < MIN_FEN) {
    toast('单次充值不能低于 0.01 元')
    return null
  }
  if (fen > MAX_FEN) {
    toast('单次充值不能超过 1000.00 元')
    return null
  }
  return fen
}

async function submit(): Promise<void> {
  if (submitting.value) {
    return
  }
  const fen = validate()
  if (fen === null) {
    return
  }
  if (!auth.ensureLogin('/pages/wallet/Recharge')) {
    return
  }

  const scope = `wallet-recharge:${auth.userId ?? 0}:${fen}`
  const idempotencyKey = issueIdempotencyKey(scope)

  submitting.value = true
  successMessage.value = ''
  try {
    wallet.value = await rechargeWallet(fen, idempotencyKey)
    clearIdempotencyKey(scope)
    amountText.value = ''
    successMessage.value = `充值成功，当前余额 ¥${fenToYuan(wallet.value?.balance)}`
    toast('充值成功')
  } catch {
    // 失败保留幂等键，用户重试同一金额时复用；http 层已统一 toast
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.recharge {
  min-height: 100vh;
  padding: 24rpx;
  background-color: #f5f5f7;
}

.recharge__balance {
  padding: 32rpx;
  background: linear-gradient(120deg, #ff8a1f 0%, #ff5c00 100%);
  color: #ffffff;
}

.recharge__balance-label {
  font-size: 24rpx;
  opacity: 0.9;
}

.recharge__balance-value {
  display: block;
  margin-top: 10rpx;
  font-size: 60rpx;
  font-weight: 600;
  letter-spacing: 2rpx;
}

.recharge__balance-error {
  display: inline-block;
  margin-top: 12rpx;
  font-size: 22rpx;
  opacity: 0.92;
  text-decoration: underline;
}

.recharge__form {
  margin-top: 20rpx;
  padding: 28rpx;
}

.recharge__title {
  font-size: 28rpx;
  font-weight: 600;
  color: #1f1f1f;
}

.recharge__input-row {
  display: flex;
  align-items: center;
  margin-top: 18rpx;
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid #f0f0f0;
}

.recharge__currency {
  font-size: 44rpx;
  font-weight: 600;
  color: #1f1f1f;
  margin-right: 12rpx;
}

.recharge__input {
  flex: 1;
  height: 76rpx;
  font-size: 52rpx;
  font-weight: 600;
  color: #1f1f1f;
}

.recharge__placeholder {
  color: #d0d0d0;
  font-size: 44rpx;
}

.recharge__hint {
  display: block;
  margin-top: 16rpx;
  font-size: 22rpx;
  line-height: 1.7;
  color: #8a8a8a;
}

.recharge__quick {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 24rpx;
}

.recharge__quick-item {
  flex: 1;
  min-width: 140rpx;
  padding: 18rpx 0;
  text-align: center;
  border: 1rpx solid #eeeeee;
  border-radius: 8rpx;
  font-size: 28rpx;
  color: #444444;
}

.recharge__quick-item.is-active {
  border-color: #ff7a00;
  background-color: #fff2e6;
  color: #ff7a00;
  font-weight: 600;
}

.recharge__success {
  margin-top: 20rpx;
  padding: 18rpx 20rpx;
  border-radius: 8rpx;
  background-color: #f0f9eb;
  color: #3f8f1f;
  font-size: 24rpx;
}

.recharge__submit {
  margin-top: 40rpx;
}

.recharge__footnote {
  display: block;
  margin-top: 24rpx;
  font-size: 22rpx;
  line-height: 1.7;
  color: #a0a0a0;
  text-align: center;
}
</style>
