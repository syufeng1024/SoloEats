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

    <view class="orders__body">
      <view v-if="loading && orders.length === 0" class="se-loading">正在加载订单…</view>

      <view v-else-if="error && orders.length === 0" class="se-error">
        <text>{{ error }}</text>
        <view class="se-error__retry" @click="reload">重新加载</view>
      </view>

      <view v-else-if="orders.length === 0" class="se-empty">
        <text class="se-empty__title">该分类下暂无订单</text>
        <text>去附近好店点一份吧</text>
        <view class="se-error__retry orders__go" @click="goHome">去点餐</view>
      </view>

      <view v-else>
        <view
          v-for="order in orders"
          :key="order.orderId"
          class="se-card orders__card"
          @click="openDetail(order)"
        >
          <view class="se-between orders__card-head">
            <text class="orders__shop">{{ shopName(order.shopId) }}</text>
            <text class="orders__status" :class="statusClass(order.status)">{{ order.statusName }}</text>
          </view>

          <view class="orders__meta">
            <text class="se-tag">{{ order.fulfillmentTypeName }}</text>
            <text class="se-muted">订单号 {{ order.orderNo }}</text>
          </view>

          <view class="orders__amount-row">
            <text class="se-muted">共 {{ order.itemCount }} 件</text>
            <text class="se-price orders__amount">{{ fenToYuan(order.payAmount) }}</text>
          </view>

          <view class="orders__foot">
            <text class="se-muted">{{ formatDateTime(order.createdAt) }}</text>
            <view class="orders__actions" @click.stop>
              <view
                v-if="canCancel(order.status)"
                class="orders__action orders__action--plain"
                @click="handleCancel(order)"
              >
                取消订单
              </view>
              <view
                v-if="order.status === ORDER_STATUS.WAIT_PAY"
                class="orders__action orders__action--primary"
                @click="goPay(order)"
              >
                去支付
              </view>
              <view v-else class="orders__action" @click="openDetail(order)">查看详情</view>
            </view>
          </view>
        </view>

        <view v-if="truncated" class="orders__hint">
          仅扫描最近 {{ MAX_FILTER_PAGES * 50 }} 条订单做本地筛选，更早的订单请用后端状态筛选接口
        </view>
        <view v-if="hasMore" class="orders__more" @click="loadMore">
          {{ loading ? '加载中…' : '加载更多' }}
        </view>
        <view v-else-if="orders.length > 0" class="orders__end">没有更多订单了</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { ORDER_STATUS, cancelOrder, fetchOrders } from '@/api/order'
import { fetchShopDetail } from '@/api/shop'
import type { OrderListItemVO } from '@/api/types'
import { fenToYuan } from '@/utils/money'
import { formatDateTime } from '@/utils/date'
import { clearIdempotencyKey, issueIdempotencyKey } from '@/utils/request'
import { useAuthStore } from '@/stores/auth'

/**
 * 订单 Tab 与后端状态映射（规格书 5.2.6）：
 * - 待付款 0
 * - 待接单 10
 * - 进行中 20 备餐中 / 30 待骑手取货 / 31 待用户取餐 / 40 配送中
 * - 已完成 60 已完成 / 50 已送达 / 51 已取餐
 * - 已取消 70
 *
 * 后端 GET /orders 只接受单个 status 精确过滤，不接受 status 列表。
 * 取舍：
 * - 单状态 Tab（待付款/待接单/已取消）走服务端分页，翻页准确；
 * - 多状态 Tab（进行中/已完成）逐页拉取无状态列表后在本地过滤，最多扫 10 页（500 单）。
 *   这样不新增后端契约、不改状态机，代价是用户订单超过 500 条时更早的订单不会出现在这两个 Tab，
 *   页面底部会明确提示。全部 Tab 仍走服务端分页。
 */

interface TabConfig {
  key: string
  label: string
  statuses: number[] | null
}

const TABS: TabConfig[] = [
  { key: 'all', label: '全部', statuses: null },
  { key: 'waitPay', label: '待付款', statuses: [ORDER_STATUS.WAIT_PAY] },
  { key: 'waitAccept', label: '待接单', statuses: [ORDER_STATUS.WAIT_ACCEPT] },
  {
    key: 'processing',
    label: '进行中',
    statuses: [
      ORDER_STATUS.PREPARING,
      ORDER_STATUS.WAIT_RIDER_PICKUP,
      ORDER_STATUS.WAIT_USER_PICKUP,
      ORDER_STATUS.DELIVERING,
    ],
  },
  {
    key: 'done',
    label: '已完成',
    statuses: [ORDER_STATUS.COMPLETED, ORDER_STATUS.DELIVERED, ORDER_STATUS.PICKED_UP],
  },
  { key: 'cancelled', label: '已取消', statuses: [ORDER_STATUS.CANCELLED] },
]

const PAGE_SIZE = 10
const MAX_FILTER_PAGES = 10
const FILTER_PAGE_SIZE = 50
const CANCEL_REASONS = ['不想要了', '点错了', '商品信息有误', '其他原因']

const auth = useAuthStore()

const activeKey = ref('all')
const orders = ref<OrderListItemVO[]>([])
const pageNum = ref(0)
const total = ref(0)
const hasMore = ref(false)
const truncated = ref(false)
const loading = ref(false)
const error = ref('')
const shopNames = reactive<Record<number, string>>({})

const currentTab = computed<TabConfig>(
  () => TABS.find((tab) => tab.key === activeKey.value) || TABS[0],
)

onShow(() => {
  if (!auth.ensureLogin('/pages/order/List')) {
    return
  }
  loadTab(true)
})

onPullDownRefresh(() => {
  if (loading.value) {
    uni.stopPullDownRefresh()
    return
  }
  loadTab(true)
})

function changeTab(key: string): void {
  if (activeKey.value === key) {
    return
  }
  activeKey.value = key
  orders.value = []
  total.value = 0
  hasMore.value = false
  truncated.value = false
  loadTab(true)
}

async function loadTab(reset: boolean): Promise<void> {
  if (loading.value) {
    return
  }
  const tab = currentTab.value
  loading.value = true
  error.value = ''
  try {
    if (tab.statuses && tab.statuses.length > 1) {
      await loadMultiStatus(tab)
    } else {
      const status = tab.statuses ? tab.statuses[0] : undefined
      if (reset) {
        pageNum.value = 0
      }
      const page = await fetchOrders({
        status,
        pageNum: pageNum.value + 1,
        pageSize: PAGE_SIZE,
      })
      orders.value = reset ? page.list : orders.value.concat(page.list)
      pageNum.value = page.pageNum
      total.value = page.total
      hasMore.value = orders.value.length < page.total
    }
    void resolveShopNames()
  } catch (err) {
    error.value = (err as Error)?.message || '订单加载失败'
    if (reset) {
      orders.value = []
      total.value = 0
      hasMore.value = false
    }
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

async function loadMultiStatus(tab: TabConfig): Promise<void> {
  const statuses = tab.statuses || []
  const matched: OrderListItemVO[] = []
  let page = 1

  while (page <= MAX_FILTER_PAGES) {
    const result = await fetchOrders({ pageNum: page, pageSize: FILTER_PAGE_SIZE })
    matched.push(...result.list.filter((order) => statuses.includes(order.status)))
    const reachedEnd = result.list.length < FILTER_PAGE_SIZE || page * FILTER_PAGE_SIZE >= result.total
    if (reachedEnd) {
      truncated.value = false
      break
    }
    page += 1
  }

  if (page > MAX_FILTER_PAGES) {
    truncated.value = true
  }
  orders.value = matched
  total.value = matched.length
  hasMore.value = false
}

function loadMore(): void {
  if (!hasMore.value || loading.value) {
    return
  }
  loadTab(false)
}

function reload(): void {
  loadTab(true)
}

async function resolveShopNames(): Promise<void> {
  const pending = Array.from(new Set(orders.value.map((order) => order.shopId)))
    .filter((shopId) => shopId && !shopNames[shopId])
    .slice(0, 20)
  if (pending.length === 0) {
    return
  }
  const results = await Promise.allSettled(pending.map((shopId) => fetchShopDetail(shopId)))
  results.forEach((result, index) => {
    if (result.status === 'fulfilled') {
      shopNames[pending[index]] = result.value.shopName
    }
  })
}

function shopName(shopId: number): string {
  return shopNames[shopId] || `门店 ${shopId}`
}

function statusClass(status: number): string {
  if (status === ORDER_STATUS.CANCELLED) {
    return 'is-muted'
  }
  if (status === ORDER_STATUS.COMPLETED || status === ORDER_STATUS.PICKED_UP) {
    return 'is-done'
  }
  return 'is-active'
}

function canCancel(status: number): boolean {
  return status === ORDER_STATUS.WAIT_PAY || status === ORDER_STATUS.WAIT_ACCEPT
}

function openDetail(order: OrderListItemVO): void {
  uni.navigateTo({ url: `/pages/order/Detail?id=${order.orderId}` })
}

function goPay(order: OrderListItemVO): void {
  uni.navigateTo({
    url: `/pages/order/PayResult?orderId=${order.orderId}&payAmount=${order.payAmount}&fulfillmentType=${order.fulfillmentType}`,
  })
}

function handleCancel(order: OrderListItemVO): void {
  uni.showActionSheet({
    itemList: CANCEL_REASONS,
    success: async (result) => {
      const reason = CANCEL_REASONS[result.tapIndex]
      if (!reason) {
        return
      }
      const scope = `cancel:${order.orderId}`
      const key = issueIdempotencyKey(scope)
      try {
        await cancelOrder(order.orderId, reason, key)
        clearIdempotencyKey(scope)
        uni.showToast({ title: '订单已取消', icon: 'none' })
        loadTab(true)
      } catch (err) {
        // http 层已提示；保留幂等键以便重试
      }
    },
  })
}

function goHome(): void {
  uni.switchTab({ url: '/pages/index/Index' })
}
</script>

<style scoped>
.orders {
  min-height: 100vh;
  background-color: #f5f5f7;
}

.orders__tabs {
  position: sticky;
  top: 0;
  z-index: 10;
  white-space: nowrap;
  background-color: #ffffff;
  box-shadow: 0 2rpx 8rpx rgba(20, 20, 20, 0.04);
}

.orders__tab {
  display: inline-block;
  padding: 24rpx 0;
  margin: 0 24rpx;
  font-size: 28rpx;
  color: #555555;
  position: relative;
}

.orders__tab.is-active {
  color: #ff7a00;
  font-weight: 600;
}

.orders__tab.is-active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 8rpx;
  width: 40rpx;
  height: 6rpx;
  margin-left: -20rpx;
  border-radius: 3rpx;
  background-color: #ff7a00;
}

.orders__body {
  padding: 20rpx 24rpx 40rpx;
}

.orders__go {
  margin-top: 24rpx;
}

.orders__card {
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.orders__card-head {
  margin-bottom: 10rpx;
}

.orders__shop {
  font-size: 30rpx;
  font-weight: 600;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  max-width: 60%;
}

.orders__status {
  font-size: 24rpx;
}

.orders__status.is-active {
  color: #ff7a00;
}

.orders__status.is-done {
  color: #52c41a;
}

.orders__status.is-muted {
  color: #999999;
}

.orders__meta {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 12rpx;
}

.orders__amount-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 8rpx 0;
}

.orders__amount {
  font-size: 32rpx;
}

.orders__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f5f5f5;
}

.orders__actions {
  display: flex;
  gap: 16rpx;
}

.orders__action {
  padding: 10rpx 24rpx;
  border-radius: 8rpx;
  border: 1rpx solid #dddddd;
  font-size: 24rpx;
  color: #444444;
  background-color: #ffffff;
}

.orders__action--primary {
  border-color: #ff7a00;
  color: #ffffff;
  background: linear-gradient(135deg, #ffa24c, #ff7a00);
}

.orders__action--plain {
  border-color: #eeeeee;
  color: #999999;
}

.orders__more,
.orders__end {
  padding: 24rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #8a8a8a;
}

.orders__more {
  color: #ff7a00;
}

.orders__hint {
  padding: 12rpx 16rpx;
  border-radius: 8rpx;
  background-color: #fff8ef;
  color: #a26a2a;
  font-size: 20rpx;
  line-height: 1.6;
}
</style>
