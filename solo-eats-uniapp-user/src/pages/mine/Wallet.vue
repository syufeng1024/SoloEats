<template>
  <view class="wallet">
    <view class="wallet__card">
      <text class="wallet__label">账户余额（元）</text>
      <text class="wallet__balance">{{ wallet ? fenToYuan(wallet.balance) : '--' }}</text>
      <view class="wallet__sub">
        <text class="wallet__sub-item">冻结 ¥{{ wallet ? fenToYuan(wallet.frozenAmount) : '--' }}</text>
        <text class="wallet__sub-item">累计充值 ¥{{ wallet ? fenToYuan(wallet.totalRecharge) : '--' }}</text>
        <text class="wallet__sub-item">累计消费 ¥{{ wallet ? fenToYuan(wallet.totalConsume) : '--' }}</text>
      </view>
    </view>

    <view class="wallet__section-head">
      <text class="wallet__section-title">收支明细</text>
      <text v-if="logs.length > 0" class="wallet__refresh" @click="loadLogs(true)">
        刷新
      </text>
    </view>

    <view v-if="logsLoading && logs.length === 0" class="se-loading">正在加载钱包流水…</view>

    <view v-else-if="error && logs.length === 0" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="refreshAll">重新加载</view>
    </view>

    <view v-else-if="logs.length === 0" class="se-empty">
      <text class="se-empty__title">暂无钱包流水</text>
      <text>充值、消费、退款记录会展示在这里</text>
    </view>

    <view v-else class="se-card wallet__logs">
      <view v-if="error" class="wallet__error">
        <text>{{ error }}</text>
        <text class="wallet__error-retry" @click="loadLogs(true)">重试</text>
      </view>

      <view v-for="log in logs" :key="log.id" class="wallet__log">
        <view class="wallet__log-main">
          <text class="wallet__log-title">{{ bizLabel(log.bizType) }}</text>
          <text v-if="log.remark" class="wallet__log-remark">{{ log.remark }}</text>
          <text class="wallet__log-time">{{ formatDateTime(log.createdAt) }}</text>
        </view>
        <view class="wallet__log-side">
          <text class="wallet__log-amount" :class="log.direction === 1 ? 'is-in' : 'is-out'">
            {{ log.direction === 1 ? '+' : '-' }}{{ fenToYuan(log.amount) }}
          </text>
          <text class="wallet__log-balance">余额 {{ fenToYuan(log.afterBalance) }}</text>
        </view>
      </view>
    </view>

    <view v-if="hasMore" class="wallet__more" @click="loadLogs(false)">
      {{ logsLoading ? '加载中…' : '加载更多' }}
    </view>
    <view v-else-if="logs.length > 0" class="wallet__end">没有更多流水了</view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { fetchWallet } from '@/api/user'
import { fetchWalletLogs } from '@/api/mine'
import type { WalletLogVO } from '@/api/mine'
import type { WalletVO } from '@/api/types'
import { fenToYuan } from '@/utils/money'
import { formatDateTime } from '@/utils/date'
import { useAuthStore } from '@/stores/auth'

/**
 * 我的钱包。
 *
 * 金额一律按「分」传输与计算，展示时走 fenToYuan，禁止 Number(yuan) * 100。
 * 流水 direction：1 收入 / 2 支出，amount 恒为正数；颜色按产品要求收入绿、支出红。
 */

const PAGE_SIZE = 20

const BIZ_LABELS: Record<string, string> = {
  RECHARGE: '余额充值',
  CONSUME: '订单消费',
  REFUND: '退款回退',
  ADJUST: '后台调账',
  WITHDRAW: '提现',
  FREEZE: '冻结',
  UNFREEZE: '解冻',
}

const auth = useAuthStore()

const wallet = ref<WalletVO | null>(null)
const logs = ref<WalletLogVO[]>([])
const pageNum = ref(0)
const hasMore = ref(false)
const logsLoading = ref(false)
const walletLoading = ref(false)
const error = ref('')

let logSequence = 0

onShow(() => {
  if (!auth.ensureLogin('/pages/mine/Wallet')) {
    return
  }
  refreshAll()
})

onPullDownRefresh(() => {
  if (logsLoading.value || walletLoading.value) {
    uni.stopPullDownRefresh()
    return
  }
  refreshAll()
})

async function refreshAll(): Promise<void> {
  error.value = ''
  await Promise.all([loadWallet(), loadLogs(true)])
  uni.stopPullDownRefresh()
}

async function loadWallet(): Promise<void> {
  if (walletLoading.value) {
    return
  }
  walletLoading.value = true
  try {
    wallet.value = await fetchWallet(true)
  } catch (err) {
    // 保留上一次余额，错误在流水区域统一展示
    error.value = (err as Error)?.message || '钱包余额加载失败'
  } finally {
    walletLoading.value = false
  }
}

async function loadLogs(reset: boolean): Promise<void> {
  if (logsLoading.value) {
    if (reset) {
      uni.stopPullDownRefresh()
    }
    return
  }
  const sequence = ++logSequence
  logsLoading.value = true
  error.value = ''
  try {
    const page = await fetchWalletLogs(reset ? 1 : pageNum.value + 1, PAGE_SIZE, true)
    if (sequence !== logSequence) {
      return
    }
    logs.value = reset ? page.list : logs.value.concat(page.list)
    pageNum.value = page.pageNum
    hasMore.value = logs.value.length < page.total
  } catch (err) {
    if (sequence === logSequence) {
      error.value = (err as Error)?.message || '钱包流水加载失败'
      uni.showToast({ title: error.value, icon: 'none' })
    }
  } finally {
    if (sequence === logSequence) {
      logsLoading.value = false
      uni.stopPullDownRefresh()
    }
  }
}

function bizLabel(bizType: string): string {
  return BIZ_LABELS[bizType] || bizType || '账户变动'
}
</script>

<style scoped>
.wallet {
  min-height: 100vh;
  padding: 24rpx;
  background-color: #f5f5f7;
}

.wallet__card {
  padding: 32rpx;
  border-radius: 12rpx;
  background: linear-gradient(120deg, #ff8a1f 0%, #ff5c00 100%);
  color: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(255, 92, 0, 0.22);
}

.wallet__label {
  font-size: 24rpx;
  opacity: 0.9;
}

.wallet__balance {
  display: block;
  margin-top: 8rpx;
  font-size: 64rpx;
  font-weight: 600;
  letter-spacing: 2rpx;
}

.wallet__sub {
  display: flex;
  flex-wrap: wrap;
  gap: 24rpx;
  margin-top: 20rpx;
  font-size: 22rpx;
  opacity: 0.92;
}

.wallet__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx 8rpx 16rpx;
}

.wallet__section-title {
  font-size: 30rpx;
  font-weight: 600;
}

.wallet__refresh {
  color: #ff7a00;
  font-size: 24rpx;
}

.wallet__logs {
  padding: 0 24rpx;
}

.wallet__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
  padding: 14rpx 16rpx;
  border-radius: 8rpx;
  background-color: #fff8ef;
  color: #a26a2a;
  font-size: 22rpx;
}

.wallet__error-retry {
  color: #ff7a00;
  margin-left: 16rpx;
}

.wallet__log {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f5f5f5;
}

.wallet__log:last-child {
  border-bottom: none;
}

.wallet__log-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.wallet__log-title {
  font-size: 27rpx;
  color: #1f1f1f;
  font-weight: 500;
}

.wallet__log-remark {
  font-size: 22rpx;
  color: #6f6f6f;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.wallet__log-time {
  font-size: 22rpx;
  color: #a0a0a0;
}

.wallet__log-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4rpx;
  margin-left: 20rpx;
  flex-shrink: 0;
}

.wallet__log-amount {
  font-size: 30rpx;
  font-weight: 600;
}

.wallet__log-amount.is-in {
  color: #52c41a;
}

.wallet__log-amount.is-out {
  color: #ff4d4f;
}

.wallet__log-balance {
  font-size: 20rpx;
  color: #a0a0a0;
}

.wallet__more,
.wallet__end {
  padding: 28rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #8a8a8a;
}

.wallet__more {
  color: #ff7a00;
}
</style>
