<template>
  <view class="tasks">
    <scroll-view scroll-x class="tasks__tabs" :show-scrollbar="false">
      <view
        v-for="tab in TABS"
        :key="tab.key"
        class="tasks__tab"
        :class="{ 'is-active': tab.key === activeKey }"
        @click="changeTab(tab.key)"
      >
        {{ tab.label }}
      </view>
    </scroll-view>

    <view class="tasks__body">
      <view v-if="loading && tasks.length === 0" class="rd-loading">正在加载任务…</view>

      <view v-else-if="error && tasks.length === 0" class="rd-error">
        <text class="rd-error__msg">{{ error }}</text>
        <view class="rd-error__retry" @click="reload">重新加载</view>
      </view>

      <view v-else-if="tasks.length === 0" class="rd-empty tasks__empty">
        <text class="rd-empty__title">{{ emptyTitle }}</text>
        <text>{{ emptyDesc }}</text>
        <view v-if="activeKey === 'all'" class="rd-empty__action" @click="goHome">去听单</view>
      </view>

      <view v-else>
        <view
          v-for="task in tasks"
          :key="task.deliveryId"
          class="rd-card tasks__card"
          @click="openDetail(task)"
        >
          <view class="rd-between tasks__head">
            <text class="tasks__status">{{ task.statusName }}</text>
            <text class="tasks__no">{{ task.deliveryNo }}</text>
          </view>

          <view class="tasks__route">
            <view class="tasks__route-line">
              <text class="tasks__dot tasks__dot--pickup">取</text>
              <text class="tasks__route-text">{{ task.pickupAddress || '取货地址待更新' }}</text>
            </view>
            <view class="tasks__route-line">
              <text class="tasks__dot tasks__dot--drop">送</text>
              <text class="tasks__route-text">{{ task.receiverAddress || '收货地址待更新' }}</text>
            </view>
          </view>

          <view class="tasks__meta">
            <text class="tasks__meta-item">{{ formatDistance(task.distanceM) }}</text>
            <text class="tasks__meta-item">共 {{ task.itemCount }} 件</text>
            <text class="tasks__meta-item rd-price">{{ fenToYuan(task.deliveryFee) }}</text>
          </view>

          <view v-if="promiseText(task)" class="tasks__promise" :class="promiseClass(task)">
            {{ promiseText(task) }}
          </view>

          <view class="tasks__foot">
            <text class="tasks__time">{{ timeText(task) }}</text>
            <text class="tasks__go">查看详情 ›</text>
          </view>
        </view>

        <view v-if="hasMore" class="tasks__more" @click="loadMore">
          {{ loading ? '加载中…' : '加载更多' }}
        </view>
        <view v-else class="tasks__end">没有更多任务了</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onHide, onPullDownRefresh, onReachBottom, onShow, onUnload } from '@dcloudio/uni-app'
import { pageMyDeliveries } from '@/api/order'
import type { RiderTaskVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { fenToYuan } from '@/utils/money'
import { formatDateTime, formatTime, parseServerDateTime } from '@/utils/date'

/**
 * 任务 Tab 与配送单状态：
 * - 待取货 20（已接单，尚未取货）
 * - 配送中 40（已取货，映射 DeliveryOrderStatus.DELIVERING）
 * - 已完成 50（已送达）
 * 状态中文一律使用接口的 statusName，不在这里维护映射。
 */

interface TabConfig {
  key: string
  label: string
  status?: number
}

const TABS: TabConfig[] = [
  { key: 'pickup', label: '待取货', status: 20 },
  { key: 'delivering', label: '配送中', status: 40 },
  { key: 'done', label: '已完成', status: 50 },
  { key: 'all', label: '全部' },
]

const PAGE_SIZE = 10

const auth = useAuthStore()

const activeKey = ref('pickup')
const tasks = ref<RiderTaskVO[]>([])
const pageNum = ref(0)
const hasMore = ref(false)
const loading = ref(false)
const error = ref('')
const now = ref(Date.now())

let clock: ReturnType<typeof setInterval> | null = null

const currentTab = computed<TabConfig>(
  () => TABS.find((tab) => tab.key === activeKey.value) || TABS[0],
)

const emptyTitle = computed(() => {
  if (activeKey.value === 'pickup') {
    return '暂无待取货任务'
  }
  if (activeKey.value === 'delivering') {
    return '暂无配送中任务'
  }
  if (activeKey.value === 'done') {
    return '暂无已完成任务'
  }
  return '暂无任务'
})

const emptyDesc = computed(() =>
  activeKey.value === 'all'
    ? '上线听单后，系统派单与抢单成功的任务会出现在这里'
    : '有新的配送任务时会自动出现在这个分类',
)

onShow(() => {
  if (!auth.ensureLogin('/pages/task/List')) {
    return
  }
  startClock()
  void load(true)
})

onHide(() => stopClock())

onUnload(() => stopClock())

onPullDownRefresh(() => {
  void load(true)
})

onReachBottom(() => {
  loadMore()
})

function startClock(): void {
  stopClock()
  now.value = Date.now()
  // 承诺送达剩余时间只需要分钟级精度，30 秒刷新一次足够
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

function changeTab(key: string): void {
  if (activeKey.value === key) {
    return
  }
  activeKey.value = key
  tasks.value = []
  pageNum.value = 0
  hasMore.value = false
  void load(true)
}

async function load(reset: boolean): Promise<void> {
  if (loading.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const page = await pageMyDeliveries({
      status: currentTab.value.status,
      pageNum: reset ? 1 : pageNum.value + 1,
      pageSize: PAGE_SIZE,
    })
    const list = Array.isArray(page.list) ? page.list : []
    tasks.value = reset ? list : tasks.value.concat(list)
    pageNum.value = page.pageNum || (reset ? 1 : pageNum.value + 1)
    hasMore.value = tasks.value.length < (page.total || 0)
  } catch (err) {
    error.value = (err as Error)?.message || '任务加载失败'
    if (reset) {
      tasks.value = []
      hasMore.value = false
    }
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

function loadMore(): void {
  if (!hasMore.value || loading.value) {
    return
  }
  void load(false)
}

function reload(): void {
  void load(true)
}

function openDetail(task: RiderTaskVO): void {
  uni.navigateTo({ url: `/pages/task/Detail?id=${task.deliveryId}` })
}

function goHome(): void {
  uni.switchTab({ url: '/pages/home/Home' })
}

function formatDistance(distanceM: number | null | undefined): string {
  if (distanceM === null || distanceM === undefined) {
    return '距离未知'
  }
  const meters = Number(distanceM)
  if (!Number.isFinite(meters)) {
    return '距离未知'
  }
  return meters < 1000 ? `${Math.round(meters)} m` : `${(meters / 1000).toFixed(1)} km`
}

interface PromiseInfo {
  text: string
  urgent: boolean
  overdue: boolean
}

function resolvePromise(task: RiderTaskVO): PromiseInfo | null {
  const target = parseServerDateTime(task.promiseDeliverAt)
  if (!target) {
    return null
  }
  const diffMinutes = Math.round((target.getTime() - now.value) / 60_000)
  if (task.status === 50) {
    return { text: `已于 ${formatDateTime(task.deliveredAt)} 送达`, urgent: false, overdue: false }
  }
  if (diffMinutes < 0) {
    return {
      text: `已超时 ${Math.abs(diffMinutes)} 分钟，请优先配送`,
      urgent: true,
      overdue: true,
    }
  }
  return {
    text: `承诺 ${formatTime(task.promiseDeliverAt)} 前送达（剩余 ${diffMinutes} 分钟）`,
    urgent: diffMinutes <= 5,
    overdue: false,
  }
}

function promiseText(task: RiderTaskVO): string {
  return resolvePromise(task)?.text || ''
}

function promiseClass(task: RiderTaskVO): string {
  const info = resolvePromise(task)
  if (!info) {
    return ''
  }
  if (info.overdue) {
    return 'is-overdue'
  }
  return info.urgent ? 'is-urgent' : ''
}

function timeText(task: RiderTaskVO): string {
  if (task.status === 50) {
    return `送达 ${formatDateTime(task.deliveredAt)}`
  }
  if (task.pickedUpAt) {
    return `取货 ${formatDateTime(task.pickedUpAt)}`
  }
  if (task.acceptedAt) {
    return `接单 ${formatDateTime(task.acceptedAt)}`
  }
  return `任务 ${task.deliveryNo}`
}
</script>

<style scoped>
.tasks {
  min-height: 100vh;
  background-color: #f1f4f8;
}

.tasks__tabs {
  position: sticky;
  top: 0;
  z-index: 10;
  white-space: nowrap;
  background-color: #ffffff;
  box-shadow: 0 2rpx 10rpx rgba(20, 35, 58, 0.06);
}

.tasks__tab {
  display: inline-block;
  padding: 26rpx 0;
  margin: 0 30rpx;
  font-size: 30rpx;
  color: #55637a;
  position: relative;
}

.tasks__tab.is-active {
  color: #e85d04;
  font-weight: 700;
}

.tasks__tab.is-active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 8rpx;
  width: 44rpx;
  height: 6rpx;
  margin-left: -22rpx;
  border-radius: 3rpx;
  background-color: #ff7a00;
}

.tasks__body {
  padding: 20rpx 24rpx 48rpx;
}

.tasks__empty {
  background-color: #ffffff;
  border-radius: 20rpx;
  margin-top: 8rpx;
}

.tasks__card {
  margin-bottom: 20rpx;
  padding: 24rpx;
}

.tasks__head {
  padding-bottom: 14rpx;
}

.tasks__status {
  font-size: 30rpx;
  font-weight: 700;
  color: #e85d04;
}

.tasks__no {
  font-size: 22rpx;
  color: #8d99aa;
}

.tasks__route {
  padding: 16rpx 20rpx;
  border-radius: 14rpx;
  background-color: #f7f9fc;
}

.tasks__route-line {
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
  padding: 6rpx 0;
}

.tasks__dot {
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

.tasks__dot--pickup {
  background-color: #e85d04;
}

.tasks__dot--drop {
  background-color: #16233a;
}

.tasks__route-text {
  flex: 1;
  font-size: 26rpx;
  color: #33455f;
  line-height: 1.5;
  word-break: break-all;
}

.tasks__meta {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-top: 16rpx;
  font-size: 24rpx;
  color: #55637a;
}

.tasks__meta-item {
  font-size: 24rpx;
}

.tasks__promise {
  margin-top: 14rpx;
  padding: 12rpx 18rpx;
  border-radius: 10rpx;
  background-color: #eef4ff;
  color: #2c4a75;
  font-size: 24rpx;
  font-weight: 500;
}

.tasks__promise.is-urgent {
  background-color: #fff1e6;
  color: #c2410c;
  font-weight: 700;
}

.tasks__promise.is-overdue {
  background-color: #fee4e2;
  color: #b42318;
  font-weight: 700;
}

.tasks__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 18rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f2f5f9;
}

.tasks__time {
  font-size: 22rpx;
  color: #8d99aa;
}

.tasks__go {
  font-size: 24rpx;
  color: #e85d04;
  font-weight: 600;
}

.tasks__more,
.tasks__end {
  padding: 26rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #8d99aa;
}

.tasks__more {
  color: #e85d04;
  font-weight: 600;
}
</style>
