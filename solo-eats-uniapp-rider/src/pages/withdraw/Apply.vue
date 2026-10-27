<template>
  <view class="apply rd-page">
    <view class="apply__balance">
      <text class="apply__balance-label">可提现余额（元）</text>
      <text class="apply__balance-value">{{ balanceText }}</text>
      <text v-if="balanceError" class="apply__balance-error" @click="loadBalance">
        {{ balanceError }} · 点击重试
      </text>
      <text v-else class="apply__balance-hint">
        由后端按已结算收入扣减未驳回提现计算，前端不做汇总
      </text>
    </view>

    <view class="rd-card apply__card">
      <text class="apply__label">提现金额</text>
      <view class="apply__amount">
        <text class="apply__currency">¥</text>
        <input
          v-model="amount"
          class="apply__input"
          type="digit"
          placeholder="0.00"
          placeholder-class="apply__placeholder"
          maxlength="12"
          @input="onAmountInput"
        />
        <text class="apply__all" @click="fillAll">全部提现</text>
      </view>
      <text class="apply__hint">
        单笔上限 1,000,000 元；金额按分提交，页面仅做元与分的换算。
      </text>

      <view class="rd-divider apply__divider" />

      <text class="apply__label">到账渠道</text>
      <view class="apply__channels">
        <view
          v-for="item in CHANNELS"
          :key="item.value"
          class="apply__channel"
          :class="{ 'is-active': channel === item.value }"
          @click="channel = item.value"
        >
          <view class="apply__channel-main">
            <text class="apply__channel-name">{{ item.label }}</text>
            <text class="apply__channel-desc">{{ item.desc }}</text>
          </view>
          <text class="apply__channel-check">{{ channel === item.value ? '✓' : '' }}</text>
        </view>
      </view>

      <view class="apply__note">
        <text>
          手续费：后端未返回手续费字段，本页不做费用估算；实际到账金额以平台审核与打款结果为准。
        </text>
      </view>
    </view>

    <view v-if="error" class="apply__error">{{ error }}</view>

    <view class="rd-fixed-bottom apply__footer">
      <button
        class="rd-btn rd-btn--primary"
        :disabled="submitting || balanceFen === null"
        @click="submit"
      >
        {{ submitting ? '提交中…' : '申请提现' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { applyWithdraw, fetchWithdrawBalance } from '@/api/rider'
import { useAuthStore } from '@/stores/auth'
import { fenToYuan, yuanToFen } from '@/utils/money'

/**
 * 提现申请。
 * 金额在前端用 yuanToFen 逐位解析，避免 Number(yuan) * 100 的浮点漂移；
 * 余额只展示后端 availableBalance 的结果，页面不自行汇总收入。
 * 提交前二次确认；后端 @Idempotent 以方法参数哈希防 10 秒内重复提交。
 */

const CHANNELS = [
  { value: 1, label: '支付宝', desc: '提现至绑定的支付宝账号' },
  { value: 2, label: '银行卡', desc: '提现至绑定的银行卡' },
] as const

const auth = useAuthStore()

const amount = ref('')
const channel = ref<number>(1)
const balanceFen = ref<number | null>(null)
const balanceError = ref('')
const error = ref('')
const submitting = ref(false)

const balanceText = computed(() =>
  balanceFen.value === null ? '--' : fenToYuan(balanceFen.value),
)

onShow(() => {
  if (!auth.ensureLogin('/pages/withdraw/Apply')) {
    return
  }
  void loadBalance()
})

async function loadBalance(): Promise<void> {
  balanceError.value = ''
  try {
    const data = await fetchWithdrawBalance()
    balanceFen.value = Number(data?.availableBalance ?? 0)
  } catch (err) {
    balanceFen.value = null
    balanceError.value = (err as Error)?.message || '余额加载失败'
  }
}

/** 只保留数字与一个小数点，小数最多两位；非法输入直接丢弃而不是写入 NaN。 */
function onAmountInput(event: { detail: { value: string } }): void {
  let value = String(event?.detail?.value ?? '').replace(/[^\d.]/g, '')
  const dot = value.indexOf('.')
  if (dot >= 0) {
    value = `${value.slice(0, dot + 1)}${value
      .slice(dot + 1)
      .replace(/\./g, '')
      .slice(0, 2)}`
  }
  amount.value = value
}

function fillAll(): void {
  if (balanceFen.value === null) {
    return
  }
  amount.value = fenToYuan(balanceFen.value)
}

function submit(): void {
  if (submitting.value) {
    return
  }
  error.value = ''

  let fen: number
  try {
    fen = yuanToFen(amount.value)
  } catch (err) {
    error.value = (err as Error)?.message || '金额格式不正确'
    return
  }
  if (fen <= 0) {
    error.value = '提现金额必须大于 0'
    return
  }
  if (balanceFen.value === null) {
    error.value = '可提现余额尚未加载，请稍后重试'
    return
  }
  if (fen > balanceFen.value) {
    error.value = `超出可提现余额 ¥${fenToYuan(balanceFen.value)}`
    return
  }

  const channelLabel = CHANNELS.find((item) => item.value === channel.value)?.label || '默认渠道'
  uni.showModal({
    title: '确认提现',
    content: `提现 ¥${fenToYuan(fen)} 至${channelLabel}，提交后需平台审核，确定继续吗？`,
    confirmText: '确认提现',
    confirmColor: '#FF7A00',
    success: (result) => {
      if (result.confirm) {
        void doSubmit(fen)
      }
    },
  })
}

async function doSubmit(fen: number): Promise<void> {
  submitting.value = true
  try {
    await applyWithdraw({ amount: fen, channel: channel.value })
    uni.showToast({ title: '提现申请已提交', icon: 'success' })
    // 回列表重新拉取：新建提现单的 createdAt 由数据库默认值生成，
    // 服务层插入后回显的视图里该字段为空，直接跳详情会出现「提交时间 --」。
    setTimeout(() => {
      uni.redirectTo({ url: '/pages/withdraw/List' })
    }, 600)
  } catch (err) {
    error.value = (err as Error)?.message || '提现申请失败，请稍后重试'
    void loadBalance()
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.apply {
  padding-bottom: 180rpx;
}

.apply__balance {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  padding: 32rpx;
  border-radius: 24rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #2c4467, #16233a);
}

.apply__balance-label {
  font-size: 24rpx;
  opacity: 0.85;
}

.apply__balance-value {
  font-size: 64rpx;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.apply__balance-hint {
  font-size: 21rpx;
  opacity: 0.7;
}

.apply__balance-error {
  font-size: 22rpx;
  color: #ffd2ad;
  text-decoration: underline;
}

.apply__card {
  margin-top: 20rpx;
  padding: 28rpx;
}

.apply__label {
  font-size: 26rpx;
  font-weight: 600;
  color: #33455f;
}

.apply__amount {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 14rpx;
  padding-bottom: 14rpx;
  border-bottom: 2rpx solid #edf1f6;
}

.apply__currency {
  font-size: 42rpx;
  font-weight: 700;
  color: #16233a;
}

.apply__input {
  flex: 1;
  height: 72rpx;
  font-size: 52rpx;
  font-weight: 700;
  color: #16233a;
}

.apply__placeholder {
  color: #c3ccd9;
  font-size: 44rpx;
}

.apply__all {
  font-size: 24rpx;
  font-weight: 600;
  color: #e85d04;
}

.apply__hint {
  display: block;
  margin-top: 14rpx;
  font-size: 21rpx;
  color: #8d99aa;
  line-height: 1.6;
}

.apply__divider {
  margin: 28rpx 0;
}

.apply__channels {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-top: 16rpx;
}

.apply__channel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 20rpx 24rpx;
  border: 2rpx solid #e6ecf4;
  border-radius: 16rpx;
}

.apply__channel.is-active {
  border-color: #ff9a3c;
  background-color: #fff7ef;
}

.apply__channel-main {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.apply__channel-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #16233a;
}

.apply__channel-desc {
  font-size: 21rpx;
  color: #8d99aa;
}

.apply__channel-check {
  width: 36rpx;
  font-size: 30rpx;
  font-weight: 700;
  color: #ff7a00;
  text-align: center;
}

.apply__note {
  margin-top: 24rpx;
  padding: 16rpx 20rpx;
  border-radius: 12rpx;
  background-color: #f6f8fb;
  font-size: 21rpx;
  color: #7a8699;
  line-height: 1.6;
}

.apply__error {
  margin-top: 20rpx;
  padding: 16rpx 20rpx;
  border-radius: 12rpx;
  background-color: #fff1f0;
  color: #b42318;
  font-size: 24rpx;
}

.apply__footer {
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
}
</style>
