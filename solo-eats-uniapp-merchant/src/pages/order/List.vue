<template>
  <view class="orders">
    <scroll-view scroll-x class="orders__tabs" :show-scrollbar="false">
      <view
        v-for="tab in TABS"
        :key="tab.key"
        class="orders__tab"
        :class="{ 'is-active': tab.key === activeKey }"
        @click="changeTab(tab.key)"
      >
        {{ tab.label }}
      </view>
    </scroll-view>

    <view class="orders__toolbar">
      <text class="orders__count">共 {{ total }} 单</text>
      <text class="orders__refresh" @click="load(true)">刷新</text>
    </view>

    <view class="orders__body">
      <view v-if="loading && orders.length === 0" class="mt-loading">正在加载订单…</view>

      <view v-else-if="error && orders.length === 0" class="mt-error">
        <text class="mt-error__msg">{{ error }}</text>
        <view class="mt-error__retry" @click="load(true)">重新加载</view>
      </view>

      <view v-else-if="orders.length === 0" class="mt-empty orders__empty">
        <text class="mt-empty__title">{{ emptyTitle }}</text>
        <text>{{ emptyDesc }}</text>
      </view>

      <view v-else>
        <view
          v-for="order in orders"
          :key="order.orderId"
          class="orders__row"
          @click="openDetail(order)"
        >
          <view class="orders__row-main">
            <view class="orders__row-line">
              <text class="orders__no">{{ order.orderNo }}</text>
              <text class="orders__status" :class="statusClass(order.status)">{{ order.statusName }}</text>
            </view>
            <view class="orders__row-line orders__row-line--meta">
              <text class="orders__meta">{{ formatShortDateTime(order.createdAt) }}</text>
              <text class="orders__meta">{{ fulfillmentText(order.fulfillmentType) }}</text>
              <text class="orders__meta">{{ itemCount(order) }} 件</text>
              <text
                v-if="order.status === 31 && order.pickupCode"
                class="orders__code"
              >取餐码 {{ order.pickupCode }}</text>
            </view>
          </view>
          <view class="orders__row-side">
            <text class="mt-price orders__amount">{{ fenToYuan(order.payAmount) }}</text>
            <text class="orders__arrow">›</text>
          </view>
        </view>

        <view v-if="hasMore" class="orders__more" @click="loadMore">
          {{ loading ? '加载中…' : '加载更多' }}
        </view>
        <view v-else class="orders__end">没有更多订单了</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { pageMerchantOrders } from '@/api/order'
import type { MerchantOrderVO, PageResult } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useShopStore } from '@/stores/shop'
import { fenToYuan } from '@/utils/money'
import { formatShortDateTime } from '@/utils/date'

/**
 * 订单 Tab。
 *
 * 多状态合并的取舍：
 * 后端 GET /merchant/orders 只接受单个 status，没有 status in (...) 的能力。
 * 「待取货」需要同时展示 30（待骑手取货）与 31（待用户取餐），因此这里对两个状态
 * 各发一次请求再按 createdAt 倒序合并：
 * - 优点：不新增后端接口、不丢数据、每一页都能正确分页（同一 pageNum 分别拉取后合并）；
 * - 代价：该 Tab 每次翻页 2 次请求，total 为两状态之和，某状态下无更多数据时
 *   仍会多一次空请求（最多到 hasMore 判 false 为止）。
 * 单状态 Tab 仍只发 1 次请求，不受影响。
 * 状态中文一律用接口返回的 statusName，Tab 文案只是导航标签。
 */

interface TabConfig {
  key: string
  label: string
  statuses: number[]
}

const TABS: TabConfig[] = [
  { key: 'pending', label: '待接单', statuses: [10] },
  { key: 'preparing', label: '备餐中', statuses: [20] },
  { key: 'pickup', label: '待取货', statuses: [30, 31] },
  { key: 'done', label: '已完成', statuses: [60] },
  { key: 'cancelled', label: '已取消', statuses: [70] },
]

const PAGE_SIZE = 15
const TAB_STORAGE_KEY = 'merchant:order-tab'
const LIST_PATH = '/pages/order/List'

const auth = useAuthStore()
const shop = useShopStore()

const activeKey = ref('pending')
const orders = ref<MerchantOrderVO[]>([])
const pageNum = ref(0)
const total = ref(0)
const loading = ref(false)
const error = ref('')

/** 防止切换 Tab 后旧请求覆盖新列表 */
let requestToken = 0

const currentTab = computed<TabConfig>(
  () => TABS.find((tab) => tab.key === activeKey.value) || TABS[0],
)

const hasMore = computed(() => orders.value.length < total.value)

const emptyTitle = computed(() => {
  switch (activeKey.value) {
    case 'pending':
      return '暂无待接单订单'
    case 'preparing':
      return '暂无备餐中订单'
    case 'pickup':
      return '暂无待取货订单'
    case 'done':
      return '暂无已完成订单'
    default:
      return '暂无已取消订单'
  }
})

const emptyDesc = computed(() =>
  activeKey.value === 'pending' ? '新订单会在这里出现，工作台同时会语音提醒' : '该状态下暂无订单',
)

onShow(() => {
  if (!auth.ensureLogin(LIST_PATH)) {
    return
  }
  consumeTabRequest()
  void load(true)
})

onPullDownRefresh(() => {
  void load(true)
})

onReachBottom(() => {
  loadMore()
})

/** 工作台的「查看全部 / 待取货」通过 storage 传参，switchTab 不支持 query */
function consumeTabRequest(): void {
  const requested = uni.getStorageSync(TAB_STORAGE_KEY)
  uni.removeStorageSync(TAB_STORAGE_KEY)
  if (typeof requested === 'string' && TABS.some((tab) => tab.key === requested)) {
    activeKey.value = requested
  }
}

function changeTab(key: string): void {
  if (activeKey.value === key) {
    return
  }
  activeKey.value = key
  orders.value = []
  pageNum.value = 0
  total.value = 0
  error.value = ''
  void load(true)
}

async function load(reset: boolean): Promise<void> {
  if (!reset && loading.value) {
    return
  }
  const token = ++requestToken
  loading.value = true
  error.value = ''
  const nextPage = reset ? 1 : pageNum.value + 1
  const statuses = currentTab.value.statuses

  try {
    const pages: PageResult<MerchantOrderVO>[] = await Promise.all(
      statuses.map((status) =>
        pageMerchantOrders({
          status,
          shopId: shop.currentShopId,
          pageNum: nextPage,
          pageSize: PAGE_SIZE,
          silent: true,
        }),
      ),
    )
    // 切 Tab / 重新加载后旧响应直接丢弃
    if (token !== requestToken) {
      return
    }

    const merged = new Map<number, MerchantOrderVO>()
    if (!reset) {
      orders.value.forEach((order) => merged.set(order.orderId, order))
    }
    let totalCount = 0
    pages.forEach((page) => {
      totalCount += Number(page.total) || 0
      ;(page.list || []).forEach((order) => merged.set(order.orderId, order))
    })

    orders.value = Array.from(merged.values()).sort(byCreatedDesc)
    total.value = totalCount
    pageNum.value = nextPage
  } catch (err) {
    if (token !== requestToken) {
      return
    }
    error.value = (err as Error)?.message || '订单加载失败'
    if (reset) {
      orders.value = []
      total.value = 0
    }
  } finally {
    if (token === requestToken) {
      loading.value = false
    }
    uni.stopPullDownRefresh()
  }
}

function loadMore(): void {
  if (!hasMore.value || loading.value) {
    return
  }
  void load(false)
}

function byCreatedDesc(left: MerchantOrderVO, right: MerchantOrderVO): number {
  const leftTime = left.createdAt || ''
  const rightTime = right.createdAt || ''
  if (leftTime !== rightTime) {
    return rightTime.localeCompare(leftTime)
  }
  return right.orderId - left.orderId
}

function statusClass(status: number): string {
  if (status === 10) {
    return 'is-warn'
  }
  if (status === 20) {
    return 'is-prepare'
  }
  if (status === 30 || status === 31) {
    return 'is-pickup'
  }
  if (status === 60) {
    return 'is-done'
  }
  return 'is-muted'
}

/** 取餐方式只有枚举码，没有名称字段 */
function fulfillmentText(type: number): string {
  return type === 2 ? '自取' : '配送'
}

function itemCount(order: MerchantOrderVO): number {
  return (order.items || []).reduce((sum, item) => sum + (item.quantity || 0), 0)
}

function openDetail(order: MerchantOrderVO): void {
  uni.navigateTo({ url: `/pages/order/Detail?id=${order.orderId}` })
}
</script>

<style scoped>
.orders {
  min-height: 100vh;
  background-color: #f2f4f7;
}

.orders__tabs {
  position: sticky;
  top: 0;
  z-index: 10;
  white-space: nowrap;
  background-color: #ffffff;
  box-shadow: 0 2rpx 8rpx rgba(20, 35, 58, 0.05);
}

.orders__tab {
  display: inline-block;
  padding: 22rpx 0;
  margin: 0 24rpx;
  font-size: 27rpx;
  color: #55637a;
  position: relative;
}

.orders__tab.is-active {
  color: #e85d04;
  font-weight: 700;
}

.orders__tab.is-active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 6rpx;
  width: 36rpx;
  height: 5rpx;
  margin-left: -18rpx;
  border-radius: 3rpx;
  background-color: #ff7a00;
}

.orders__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 20rpx;
}

.orders__count {
  font-size: 21rpx;
  color: #7a8699;
}

.orders__refresh {
  font-size: 21rpx;
  color: #e85d04;
  font-weight: 600;
}

.orders__body {
  padding: 0 20rpx 40rpx;
}

.orders__empty {
  background-color: #ffffff;
  border-radius: 12rpx;
  margin-top: 6rpx;
}

/* 表格式紧凑行 */
.orders__row {
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 16rpx 18rpx;
  margin-bottom: 10rpx;
  border-radius: 10rpx;
  background-color: #ffffff;
}

.orders__row-main {
  flex: 1;
  min-width: 0;
}

.orders__row-line {
  display: flex;
  align-items: center;
  gap: 10rpx;
  min-width: 0;
}

.orders__row-line--meta {
  margin-top: 6rpx;
  gap: 14rpx;
  flex-wrap: wrap;
}

.orders__no {
  flex: none;
  font-size: 23rpx;
  color: #1b2432;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.orders__status {
  flex: none;
  padding: 1rpx 8rpx;
  border-radius: 5rpx;
  font-size: 19rpx;
  line-height: 1.6;
  background-color: #eef2f7;
  color: #55637a;
}

.orders__status.is-warn {
  background-color: #fee4e2;
  color: #b42318;
}

.orders__status.is-prepare {
  background-color: #fff1e6;
  color: #a2520a;
}

.orders__status.is-pickup {
  background-color: #eef4ff;
  color: #2c4a75;
}

.orders__status.is-done {
  background-color: #dcfae6;
  color: #067647;
}

.orders__status.is-muted {
  background-color: #eef2f7;
  color: #7a8699;
}

.orders__meta {
  font-size: 20rpx;
  color: #8d99aa;
}

.orders__code {
  font-size: 20rpx;
  color: #2c4a75;
  font-weight: 600;
}

.orders__row-side {
  display: flex;
  align-items: center;
  gap: 8rpx;
  flex: none;
}

.orders__amount {
  font-size: 27rpx;
}

.orders__arrow {
  font-size: 30rpx;
  color: #c3ccd8;
}

.orders__more,
.orders__end {
  padding: 24rpx 0;
  text-align: center;
  font-size: 22rpx;
  color: #8d99aa;
}

.orders__more {
  color: #e85d04;
  font-weight: 600;
}
</style>
