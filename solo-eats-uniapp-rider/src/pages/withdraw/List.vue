<template>
  <view class="list rd-page">
    <view class="list__filters">
      <view
        v-for="item in FILTERS"
        :key="item.label"
        class="list__filter"
        :class="{ 'is-active': statusFilter === item.value }"
        @click="changeFilter(item.value)"
      >
        {{ item.label }}
      </view>
    </view>

    <view v-if="loading && records.length === 0" class="rd-loading">正在加载提现记录…</view>

    <view v-else-if="error && records.length === 0" class="rd-error">
      <text class="rd-error__msg">{{ error }}</text>
      <view class="rd-error__retry" @click="load(true)">重新加载</view>
    </view>

    <view v-else-if="records.length === 0" class="rd-empty">
      <text class="rd-empty__title">暂无提现记录</text>
      <text>提交提现申请后，审核进度会显示在这里</text>
      <view class="rd-empty__action" @click="goApply">去提现</view>
    </view>

    <view v-else>
      <view v-if="error" class="list__error">
        <text>{{ error }}</text>
        <text class="list__error-retry" @click="load(true)">重试</text>
      </view>

      <view
        v-for="item in records"
        :key="item.id"
        class="rd-card list__row"
        @click="openDetail(item)"
      >
        <view class="list__row-head rd-between">
          <text class="list__amount rd-price">{{ fenToYuan(item.amount) }}</text>
          <text class="list__status" :class="statusClass(item.status)">
            {{ statusLabel(item.status) }}
          </text>
        </view>
        <view class="list__row-meta">
          <text>单号 {{ item.payoutNo }}</text>
          <text>{{ channelLabel(item.channel) }}</text>
        </view>
        <view class="list__row-foot rd-between">
          <text class="list__time">{{ formatDateTime(item.createdAt) }}</text>
          <text class="list__arrow">查看详情 ›</text>
        </view>
      </view>

      <view v-if="hasMore" class="list__more" @click="load(false)">
        {{ loading ? '加载中…' : '加载更多' }}
      </view>
      <view v-else class="list__end">没有更多记录了</view>
    </view>

    <view class="rd-fixed-bottom list__footer">
      <button class="rd-btn rd-btn--ghost" @click="goApply">申请提现</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { fetchWithdrawals } from '@/api/rider'
import type { WithdrawOrderVO } from '@/api/types'
import { WITHDRAW_STATUS } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/date'
import { fenToYuan } from '@/utils/money'

/**
 * 提现记录。
 * status 为后端 PayoutStatus：0待审核 / 10审核通过 / 20打款中 / 30已到账 / 40已驳回。
 * 详情接口后端尚未提供，列表项整条快照通过 URL 传给详情页，避免按 id 再翻页查找。
 */

const PAGE_SIZE = 20

const FILTERS: Array<{ label: string; value: number | undefined }> = [
  { label: '全部', value: undefined },
  { label: '待审核', value: WITHDRAW_STATUS.PENDING },
  { label: '审核通过', value: WITHDRAW_STATUS.APPROVED },
  { label: '打款中', value: WITHDRAW_STATUS.PAYING },
  { label: '已到账', value: WITHDRAW_STATUS.ARRIVED },
  { label: '已驳回', value: WITHDRAW_STATUS.REJECTED },
]

const auth = useAuthStore()

const records = ref<WithdrawOrderVO[]>([])
const statusFilter = ref<number | undefined>(undefined)
const pageNum = ref(0)
const total = ref(0)
const hasMore = ref(false)
const loading = ref(false)
const error = ref('')

onShow(() => {
  if (!auth.ensureLogin('/pages/withdraw/List')) {
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

function changeFilter(value: number | undefined): void {
  if (statusFilter.value === value) {
    return
  }
  statusFilter.value = value
  void load(true)
}

async function load(reset: boolean): Promise<void> {
  if (loading.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const page = await fetchWithdrawals({
      status: statusFilter.value,
      pageNum: reset ? 1 : pageNum.value + 1,
      pageSize: PAGE_SIZE,
    })
    const list = Array.isArray(page.list) ? page.list : []
    records.value = reset ? list : records.value.concat(list)
    pageNum.value = page.pageNum || (reset ? 1 : pageNum.value + 1)
    total.value = Number(page.total || 0)
    hasMore.value = records.value.length < total.value
  } catch (err) {
    error.value = (err as Error)?.message || '提现记录加载失败'
    if (reset) {
      records.value = []
      total.value = 0
      hasMore.value = false
    }
  } finally {
    loading.value = false
  }
}

function statusLabel(status: number): string {
  switch (Number(status)) {
    case WITHDRAW_STATUS.PENDING:
      return '待审核'
    case WITHDRAW_STATUS.APPROVED:
      return '审核通过'
    case WITHDRAW_STATUS.PAYING:
      return '打款中'
    case WITHDRAW_STATUS.ARRIVED:
      return '已到账'
    case WITHDRAW_STATUS.REJECTED:
      return '已驳回'
    default:
      return `状态 ${status}`
  }
}

function statusClass(status: number): string {
  switch (Number(status)) {
    case WITHDRAW_STATUS.ARRIVED:
      return 'is-ok'
    case WITHDRAW_STATUS.REJECTED:
      return 'is-reject'
    case WITHDRAW_STATUS.PENDING:
    case WITHDRAW_STATUS.APPROVED:
    case WITHDRAW_STATUS.PAYING:
      return 'is-pending'
    default:
      return ''
  }
}

function channelLabel(channel: number): string {
  if (Number(channel) === 1) {
    return '支付宝'
  }
  if (Number(channel) === 2) {
    return '银行卡'
  }
  return '未指定渠道'
}

function openDetail(item: WithdrawOrderVO): void {
  uni.navigateTo({
    url: `/pages/withdraw/Detail?data=${encodeURIComponent(JSON.stringify(item))}`,
  })
}

function goApply(): void {
  uni.navigateTo({ url: '/pages/withdraw/Apply' })
}
</script>

<style scoped>
.list {
  padding-bottom: 180rpx;
}

.list__filters {
  display: flex;
  gap: 12rpx;
  padding-bottom: 20rpx;
  overflow-x: auto;
  white-space: nowrap;
}

.list__filter {
  flex: none;
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  background-color: #ffffff;
  font-size: 24rpx;
  color: #7a8699;
}

.list__filter.is-active {
  background-color: #16233a;
  color: #ffffff;
  font-weight: 600;
}

.list__row {
  margin-bottom: 18rpx;
  padding: 24rpx;
}

.list__amount {
  font-size: 38rpx;
}

.list__status {
  padding: 6rpx 16rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
}

.list__status.is-ok {
  color: #067647;
  background-color: #dcfae6;
}

.list__status.is-pending {
  color: #a2520a;
  background-color: #fff6ea;
}

.list__status.is-reject {
  color: #b42318;
  background-color: #fee4e2;
}

.list__row-meta {
  display: flex;
  justify-content: space-between;
  gap: 20rpx;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #8d99aa;
}

.list__row-foot {
  margin-top: 16rpx;
  padding-top: 14rpx;
  border-top: 1rpx solid #edf1f6;
}

.list__time {
  font-size: 22rpx;
  color: #a9b4c4;
}

.list__arrow {
  font-size: 22rpx;
  color: #e85d04;
}

.list__error {
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

.list__error-retry {
  font-weight: 700;
  text-decoration: underline;
}

.list__more,
.list__end {
  padding: 24rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #8d99aa;
}

.list__more {
  color: #e85d04;
  font-weight: 600;
}

.list__footer {
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
}
</style>
