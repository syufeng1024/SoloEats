<template>
  <view class="home mt-page">
    <!-- 门店头：名称 / 状态 / 切店 / 设置 -->
    <view class="home__shop">
      <view class="home__shop-main" @click="openShopPicker">
        <text class="home__shop-name">{{ shop.currentShop?.shopName || '门店加载中' }}</text>
        <view class="home__shop-meta">
          <text
            class="mt-tag"
            :class="shop.status === 1 ? 'mt-tag--ok' : shop.status === 3 ? 'mt-tag--warn' : 'mt-tag--off'"
          >
            {{ shop.statusText || '状态未知' }}
          </text>
          <text v-if="shop.shops.length > 1" class="home__switch-shop">切换门店 ›</text>
        </view>
      </view>
      <view class="home__shop-actions">
        <text class="home__action" @click="goSetting">门店设置</text>
        <text class="home__action" @click="goVerify">核销</text>
      </view>
    </view>

    <!-- 营业状态开关 -->
    <view class="home__status">
      <view class="home__status-text">
        <text class="home__status-title">{{ shop.isOpen ? '营业中，用户可下单' : '当前不可下单' }}</text>
        <text class="home__status-desc">
          {{
            shop.status === 3
              ? '门店已被平台停用，需联系运营恢复'
              : shop.isOpen
                ? '仍需在营业时段内，用户端才会展示可下单'
                : '开启后用户端恢复点单；关闭需二次确认'
          }}
        </text>
      </view>
      <switch
        :key="statusSwitchKey"
        :checked="shop.isOpen"
        :disabled="togglingStatus || shop.status === 3"
        color="#FF7A00"
        @change="onToggleStatus"
      />
    </view>

    <view v-if="scopeHint" class="home__scope-hint">{{ scopeHint }}</view>

    <!-- 经营工具入口（非 tab 页，只能从工作台进入） -->
    <view class="home__entries">
      <view class="home__entry" @click="goFinance">
        <text class="home__entry-icon">¥</text>
        <text class="home__entry-label">财务结算</text>
      </view>
      <view class="home__entry" @click="goReview">
        <text class="home__entry-icon">★</text>
        <text class="home__entry-label">评价管理</text>
      </view>
      <view class="home__entry" @click="goMine">
        <text class="home__entry-icon">我</text>
        <text class="home__entry-label">我的</text>
      </view>
    </view>

    <!-- 数据卡：全部取 dashboard 接口，不在前端求和 -->
    <view class="home__stats">
      <view class="home__stat" @click="goOrderTab('pending')">
        <text class="home__stat-value warn">{{ statText(dashboard?.pendingAcceptCount) }}</text>
        <text class="home__stat-label">待接单</text>
      </view>
      <view class="home__stat" @click="goOrderTab('preparing')">
        <text class="home__stat-value">{{ statText(dashboard?.preparingCount) }}</text>
        <text class="home__stat-label">备餐中</text>
      </view>
      <view class="home__stat" @click="goOrderTab('pickup')">
        <text class="home__stat-value warn">{{ statText(dashboard?.waitPickupCount) }}</text>
        <text class="home__stat-label">待取货</text>
      </view>
      <view class="home__stat">
        <text class="home__stat-value">{{ statText(dashboard?.todayOrderCount) }}</text>
        <text class="home__stat-label">今日订单</text>
      </view>
      <view class="home__stat">
        <text class="home__stat-value ok">{{ statText(dashboard?.completedTodayCount) }}</text>
        <text class="home__stat-label">今日完成</text>
      </view>
    </view>

    <view v-if="dashboardError" class="home__inline-error" @click="loadDashboard(true)">
      {{ dashboardError }} · 点击重试
    </view>

    <!-- 待接单 -->
    <view class="home__section">
      <view class="mt-section-head">
        <text class="mt-section-title">待接单</text>
        <text class="mt-section-extra" @click="goOrderTab('pending')">
          {{ pendingAccept.length > 0 ? `查看全部 ${dashboard?.pendingAcceptCount ?? ''} 单 ›` : '刷新' }}
        </text>
      </view>

      <view v-if="loading && pendingAccept.length === 0" class="mt-loading">正在加载订单…</view>
      <view v-else-if="pendingError && pendingAccept.length === 0" class="mt-error">
        <text class="mt-error__msg">{{ pendingError }}</text>
        <view class="mt-error__retry" @click="loadPending(true)">重新加载</view>
      </view>
      <view v-else-if="pendingAccept.length === 0" class="home__empty">暂无待接单订单</view>

      <view
        v-for="order in pendingAccept"
        :key="order.orderId"
        class="home__order"
        :class="{ 'is-overtime': isAcceptOvertime(order) }"
        @click="openOrder(order)"
      >
        <view class="mt-between home__order-head">
          <view class="home__order-tags">
            <text class="mt-tag" :class="order.fulfillmentType === 2 ? 'mt-tag--info' : ''">
              {{ fulfillmentText(order.fulfillmentType) }}
            </text>
            <text class="home__order-no">{{ order.orderNo }}</text>
          </view>
          <text class="home__order-time">{{ formatShortDateTime(order.createdAt) }}</text>
        </view>

        <view class="mt-between home__order-body">
          <text class="home__order-items">{{ itemSummary(order) }}</text>
          <text class="mt-price home__order-amount">{{ fenToYuan(order.payAmount) }}</text>
        </view>

        <view v-if="isAcceptOvertime(order)" class="home__overtime">
          已等待 {{ waitedMinutes(order) }} 分钟，请尽快接单或拒单（后端超时规则不会自动取消）
        </view>

        <view class="home__order-foot">
          <text class="home__order-status">{{ order.statusName }}</text>
          <view class="home__order-actions" @click.stop>
            <button class="mt-btn mt-btn--danger mt-btn--small" @click="quickReject(order)">拒单</button>
            <button class="mt-btn mt-btn--primary mt-btn--small" @click="quickAccept(order)">接单</button>
          </view>
        </view>
      </view>
    </view>

    <!-- 待取货 -->
    <view class="home__section">
      <view class="mt-section-head">
        <text class="mt-section-title">待取货（自取）</text>
        <text class="mt-section-extra" @click="goOrderTab('pickup')">查看全部 ›</text>
      </view>

      <view v-if="loading && waitPickup.length === 0" class="mt-loading">正在加载订单…</view>
      <view v-else-if="waitPickup.length === 0" class="home__empty">暂无待取货订单</view>

      <view
        v-for="order in waitPickup"
        :key="order.orderId"
        class="home__order home__order--pickup"
        @click="openOrder(order)"
      >
        <view class="mt-between home__order-head">
          <view class="home__order-tags">
            <text class="mt-tag mt-tag--info">自取</text>
            <text class="home__order-no">{{ order.orderNo }}</text>
          </view>
          <text class="home__order-time">{{ formatShortDateTime(order.createdAt) }}</text>
        </view>
        <view class="mt-between home__order-body">
          <text class="home__order-items">{{ itemSummary(order) }}</text>
          <text class="home__order-amount mt-strong">{{ order.pickupCode ? `取餐码 ${order.pickupCode}` : '取餐码待生成' }}</text>
        </view>
        <view class="home__order-foot">
          <text class="home__order-status">{{ order.statusName }}</text>
          <text class="home__order-link">去核销 ›</text>
        </view>
      </view>
    </view>

    <view class="home__footer">
      工作台每 30 秒自动刷新 · 下拉可立即刷新{{ voiceOn ? ' · 新单语音播报已开启' : '' }}
    </view>

    <!-- 门店切换 -->
    <view v-if="shopPickerVisible" class="mt-mask" @click="shopPickerVisible = false" />
    <view v-if="shopPickerVisible" class="mt-sheet">
      <text class="mt-sheet__title">切换门店</text>
      <view class="mt-sheet__tip">
        切换后订单与商品查询按新门店过滤；收写接口仍以登录令牌绑定门店为准。
      </view>
      <scroll-view scroll-y class="home__shop-list">
        <view
          v-for="item in shop.shops"
          :key="item.id"
          class="home__shop-item"
          :class="{ 'is-active': item.id === shop.currentShopId }"
          @click="selectShop(item.id)"
        >
          <view>
            <text class="home__shop-item-name">{{ item.shopName }}</text>
            <text class="home__shop-item-addr">{{ item.address || '地址待维护' }}</text>
          </view>
          <text class="mt-tag" :class="item.status === 1 ? 'mt-tag--ok' : 'mt-tag--off'">
            {{ item.statusText || '状态未知' }}
          </text>
        </view>
      </scroll-view>
      <view class="mt-sheet__actions">
        <button class="mt-btn mt-btn--plain" @click="shopPickerVisible = false">关闭</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onHide, onPullDownRefresh, onShow, onUnload } from '@dcloudio/uni-app'
import { ApiError } from '@/api/http'
import { fetchDashboard } from '@/api/report'
import { acceptOrder, pageMerchantOrders } from '@/api/order'
import { updateShopStatus } from '@/api/shop'
import type { MerchantDashboardVO, MerchantOrderVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useShopStore } from '@/stores/shop'
import { speakNewOrder, isVoiceEnabled } from '@/utils/voice'
import { fenToYuan } from '@/utils/money'
import { formatShortDateTime, parseServerDateTime } from '@/utils/date'

/**
 * 工作台。
 * - 5 个计数全部来自 GET /merchant/report/dashboard，前端不做金额聚合
 *   （dashboard 目前不返回营业额，T11 未落地前不展示、不推测）
 * - 待接单超出 merchant-accept-timeout-minutes（默认 10 分钟）标红提示，
 *   前端不自动拒单，超时规则由后端处理
 * - 30 秒轮询 + 下拉刷新；新出现的待接单订单做一次语音播报
 */

const REFRESH_INTERVAL_MS = 30_000
const ACCEPT_TIMEOUT_MINUTES = 10
const HOME_PATH = '/pages/home/Home'

const auth = useAuthStore()
const shop = useShopStore()

const dashboard = ref<MerchantDashboardVO | null>(null)
const dashboardError = ref('')
const pendingAccept = ref<MerchantOrderVO[]>([])
const waitPickup = ref<MerchantOrderVO[]>([])
const pendingError = ref('')
const loading = ref(false)
const togglingStatus = ref(false)
const statusSwitchKey = ref(0)
const shopPickerVisible = ref(false)
const now = ref(Date.now())

let timer: ReturnType<typeof setInterval> | null = null
let refreshing = false
let bootstrapped = false
/** 已播报过的待接单订单，避免同一单反复播报 */
const announcedIds = new Set<number>()
let pendingFirstLoad = true

const voiceOn = computed(() => isVoiceEnabled())

/**
 * dashboard 不带 shopId 参数，后端固定用令牌内的 shopId 统计。
 * 多门店切换到其它门店时这里明确提示，避免把 A 店数据当成 B 店。
 */
const scopeHint = computed(() => {
  if (!auth.shopId || shop.currentShopId === null || shop.currentShopId === auth.shopId) {
    return ''
  }
  return '当前账号令牌绑定的是另一家门店：上方统计仍按令牌门店计算，订单/商品列表按所选门店查询。'
})

onShow(() => {
  if (!auth.ensureLogin(HOME_PATH)) {
    return
  }
  void bootstrap()
})

onHide(() => stopTimer())

onUnload(() => stopTimer())

onPullDownRefresh(() => {
  void refreshAll()
})

function stopTimer(): void {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function startTimer(): void {
  stopTimer()
  timer = setInterval(() => {
    now.value = Date.now()
    void refreshAll(true)
  }, REFRESH_INTERVAL_MS)
}

async function bootstrap(): Promise<void> {
  if (!bootstrapped) {
    bootstrapped = true
    try {
      if (shop.shops.length === 0) {
        const list = await shop.loadShops(true)
        if (shop.currentShopId === null && list.length > 0) {
          shop.selectShop(list[0].id)
        }
      }
    } catch (error) {
      dashboardError.value = (error as Error)?.message || '门店信息加载失败'
    }
  }
  now.value = Date.now()
  await refreshAll(false)
  startTimer()
}

/** silent=true 用于 30 秒轮询：失败不弹 toast，只更新区块内的错误文案 */
async function refreshAll(silent = false): Promise<void> {
  if (refreshing) {
    return
  }
  refreshing = true
  if (!silent) {
    loading.value = true
  }
  try {
    await Promise.all([loadDashboard(silent), loadPending(silent)])
  } finally {
    loading.value = false
    refreshing = false
    uni.stopPullDownRefresh()
  }
}

async function loadDashboard(silent = false): Promise<void> {
  try {
    dashboard.value = await fetchDashboard(silent)
    dashboardError.value = ''
  } catch (error) {
    dashboardError.value = (error as Error)?.message || '工作台数据加载失败'
    if (!silent) {
      uni.showToast({ title: dashboardError.value, icon: 'none' })
    }
  }
}

async function loadPending(silent = false): Promise<void> {
  const shopId = shop.currentShopId
  try {
    const [acceptPage, pickupPage] = await Promise.all([
      pageMerchantOrders({ status: 10, shopId, pageNum: 1, pageSize: 10, silent }),
      pageMerchantOrders({ status: 31, shopId, pageNum: 1, pageSize: 10, silent }),
    ])
    pendingAccept.value = acceptPage.list || []
    waitPickup.value = pickupPage.list || []
    pendingError.value = ''
    announceNewOrders(pendingAccept.value)
  } catch (error) {
    pendingError.value = (error as Error)?.message || '待处理订单加载失败'
    if (!silent) {
      uni.showToast({ title: pendingError.value, icon: 'none' })
    }
  }
}

/** 首次加载只建基线；之后出现新订单号才播报 */
function announceNewOrders(list: MerchantOrderVO[]): void {
  if (pendingFirstLoad) {
    list.forEach((order) => announcedIds.add(order.orderId))
    pendingFirstLoad = false
    return
  }
  const fresh = list.find((order) => !announcedIds.has(order.orderId))
  list.forEach((order) => announcedIds.add(order.orderId))
  if (fresh) {
    speakNewOrder(fresh.orderNo)
  }
}

function statText(value: number | null | undefined): string {
  return typeof value === 'number' ? String(value) : '--'
}

function itemSummary(order: MerchantOrderVO): string {
  const items = order.items || []
  const count = items.reduce((total, item) => total + (item.quantity || 0), 0)
  if (count === 0) {
    return '暂无商品明细'
  }
  return `${items[0]?.skuName || '商品'} 等 ${count} 件`
}

/** 取餐方式文案：MerchantOrderView 不下发名称，按枚举码转 */
function fulfillmentText(type: number): string {
  return type === 2 ? '自取' : '配送'
}

function waitedMinutes(order: MerchantOrderVO): number {
  const created = parseServerDateTime(order.createdAt)
  if (!created) {
    return 0
  }
  return Math.max(0, Math.floor((now.value - created.getTime()) / 60_000))
}

function isAcceptOvertime(order: MerchantOrderVO): boolean {
  return order.status === 10 && waitedMinutes(order) >= ACCEPT_TIMEOUT_MINUTES
}

function openOrder(order: MerchantOrderVO): void {
  uni.navigateTo({ url: `/pages/order/Detail?id=${order.orderId}` })
}

function goSetting(): void {
  uni.navigateTo({ url: '/pages/shop/ShopSetting' })
}

function goVerify(): void {
  uni.navigateTo({ url: '/pages/pickup/Verify' })
}

function goFinance(): void {
  uni.navigateTo({ url: '/pages/finance/Finance' })
}

function goReview(): void {
  uni.navigateTo({ url: '/pages/review/Review' })
}

function goMine(): void {
  uni.navigateTo({ url: '/pages/mine/Mine' })
}

/** 订单 Tab 通过本地存储传参（switchTab 不支持 query） */
function goOrderTab(tabKey: string): void {
  uni.setStorageSync('merchant:order-tab', tabKey)
  uni.switchTab({ url: '/pages/order/List' })
}

function openShopPicker(): void {
  if (shop.shops.length > 1) {
    shopPickerVisible.value = true
  }
}

function selectShop(shopId: number): void {
  shop.selectShop(shopId)
  shopPickerVisible.value = false
  pendingFirstLoad = true
  announcedIds.clear()
  void refreshAll()
}

async function onToggleStatus(event: { detail: { value: boolean } }): Promise<void> {
  const next = Boolean(event.detail.value)
  if (togglingStatus.value) {
    statusSwitchKey.value += 1
    return
  }
  if (!shop.currentShop) {
    uni.showToast({ title: '门店信息未加载，请下拉刷新', icon: 'none' })
    statusSwitchKey.value += 1
    return
  }
  if (next === shop.isOpen) {
    return
  }

  if (!next) {
    const confirmed = await showConfirm({
      title: '确认停止接单',
      content: '关闭后用户端该门店立即不可下单（已下单不受影响）。确认切换到休息状态？',
      confirmText: '确认休息',
      cancelText: '继续营业',
    })
    if (!confirmed) {
      statusSwitchKey.value += 1
      return
    }
  }

  togglingStatus.value = true
  try {
    await updateShopStatus(shop.currentShop.id, next ? 1 : 0)
    shop.applyStatus(next ? 1 : 0, next ? '营业' : '休息')
    uni.showToast({ title: next ? '已开始营业' : '已切换为休息', icon: 'none' })
  } catch (error) {
    // 失败时开关必须回弹到真实状态
    statusSwitchKey.value += 1
  } finally {
    togglingStatus.value = false
  }
}

function quickAccept(order: MerchantOrderVO): void {
  uni.showModal({
    title: '确认接单',
    content: `订单 ${order.orderNo}，接单后请尽快备餐。`,
    confirmText: '接单',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      try {
        await acceptOrder(order.orderId)
        uni.showToast({ title: '已接单，开始备餐', icon: 'none' })
        await refreshAll(true)
      } catch (error) {
        handleActionError(error)
      }
    },
  })
}

/** 拒单必须填原因并二次确认，统一在详情页完成，这里只带参数跳转 */
function quickReject(order: MerchantOrderVO): void {
  uni.navigateTo({ url: `/pages/order/Detail?id=${order.orderId}&action=reject` })
}

function handleActionError(error: unknown): void {
  if (error instanceof ApiError) {
    if (error.code === 20001) {
      uni.showToast({ title: '订单状态已变更，正在刷新', icon: 'none' })
      void refreshAll(true)
      return
    }
    if (error.code === 10004) {
      uni.showToast({ title: '无权操作该门店订单', icon: 'none' })
      return
    }
  }
  uni.showToast({ title: (error as Error)?.message || '操作失败，请重试', icon: 'none' })
}

function showConfirm(options: {
  title: string
  content: string
  confirmText?: string
  cancelText?: string
}): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title: options.title,
      content: options.content,
      confirmText: options.confirmText,
      cancelText: options.cancelText,
      success: (result) => resolve(Boolean(result.confirm)),
      fail: () => resolve(false),
    })
  })
}
</script>

<style scoped>
.home {
  /* 底部原值（安全区）保留作兜底 */
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
  /*
   * 本页是 tab 页：再叠加 fixed tabBar 高度。--window-bottom 在 H5 已含安全区，
   * 故与 env() 取较大者（max）而不是相加，避免刘海机双算；
   * 上/左右内边距由 .mt-page 提供，此处不动。
   */
  padding-bottom: calc(40rpx + max(var(--window-bottom, 0px), env(safe-area-inset-bottom)));
}

.home__shop {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 20rpx;
  border-radius: 12rpx;
  background: linear-gradient(135deg, #1f3a4d, #16233a);
  color: #ffffff;
}

.home__shop-main {
  flex: 1;
  min-width: 0;
}

.home__shop-name {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
}

.home__shop-meta {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 8rpx;
}

.home__switch-shop {
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.8);
  text-decoration: underline;
}

.home__shop-actions {
  display: flex;
  gap: 12rpx;
  flex: none;
}

.home__action {
  padding: 6rpx 14rpx;
  border-radius: 8rpx;
  background-color: rgba(255, 255, 255, 0.14);
  font-size: 21rpx;
  color: #ffffff;
}

.home__status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14rpx;
  padding: 16rpx 20rpx;
  border-radius: 12rpx;
  background-color: #ffffff;
}

.home__status-text {
  flex: 1;
  min-width: 0;
}

.home__status-title {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: #1b2432;
}

.home__status-desc {
  display: block;
  margin-top: 4rpx;
  font-size: 20rpx;
  color: #7a8699;
  line-height: 1.5;
}

.home__scope-hint {
  margin-top: 12rpx;
  padding: 12rpx 16rpx;
  border-radius: 8rpx;
  background-color: #fff6ea;
  color: #a2520a;
  font-size: 20rpx;
  line-height: 1.6;
}

.home__stats {
  display: flex;
  margin-top: 14rpx;
  padding: 18rpx 6rpx;
  border-radius: 12rpx;
  background-color: #ffffff;
}

.home__stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
}

.home__stat-value {
  font-size: 34rpx;
  font-weight: 700;
  color: #1b2432;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.home__stat-value.warn {
  color: #e85d04;
}

.home__stat-value.ok {
  color: #067647;
}

.home__stat-label {
  font-size: 20rpx;
  color: #7a8699;
}

.home__inline-error {
  margin-top: 10rpx;
  padding: 10rpx 14rpx;
  border-radius: 8rpx;
  background-color: #fee4e2;
  color: #b42318;
  font-size: 21rpx;
}

.home__section {
  margin-top: 22rpx;
}

.home__empty {
  padding: 30rpx 20rpx;
  border-radius: 12rpx;
  background-color: #ffffff;
  text-align: center;
  font-size: 23rpx;
  color: #8d99aa;
}

.home__order {
  margin-bottom: 12rpx;
  padding: 18rpx 20rpx;
  border-radius: 12rpx;
  background-color: #ffffff;
  border-left: 6rpx solid #ffb27a;
}

.home__order.is-overtime {
  border-left-color: #d93a2b;
  background-color: #fffafa;
}

.home__order--pickup {
  border-left-color: #2c4a75;
}

.home__order-head {
  gap: 12rpx;
}

.home__order-tags {
  display: flex;
  align-items: center;
  gap: 10rpx;
  min-width: 0;
}

.home__order-no {
  font-size: 21rpx;
  color: #8d99aa;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.home__order-time {
  flex: none;
  font-size: 20rpx;
  color: #8d99aa;
}

.home__order-body {
  margin-top: 10rpx;
  gap: 16rpx;
}

.home__order-items {
  flex: 1;
  min-width: 0;
  font-size: 23rpx;
  color: #33455f;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.home__order-amount {
  flex: none;
  font-size: 26rpx;
}

.home__overtime {
  margin-top: 8rpx;
  padding: 8rpx 12rpx;
  border-radius: 6rpx;
  background-color: #fee4e2;
  color: #b42318;
  font-size: 20rpx;
  line-height: 1.5;
}

.home__order-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12rpx;
  padding-top: 10rpx;
  border-top: 1rpx solid #f2f5f9;
}

.home__order-status {
  font-size: 21rpx;
  color: #e85d04;
  font-weight: 600;
}

.home__order-link {
  font-size: 21rpx;
  color: #2c4a75;
  font-weight: 600;
}

.home__order-actions {
  display: flex;
  gap: 12rpx;
}

.home__footer {
  margin-top: 24rpx;
  text-align: center;
  font-size: 20rpx;
  color: #a9b4c4;
}

.home__shop-list {
  max-height: 48vh;
  margin-top: 14rpx;
}

.home__shop-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14rpx;
  padding: 18rpx 16rpx;
  margin-bottom: 10rpx;
  border-radius: 10rpx;
  background-color: #f7f9fc;
  border: 2rpx solid transparent;
}

.home__shop-item.is-active {
  background-color: #fff6ea;
  border-color: #ff9a3c;
}

.home__shop-item-name {
  display: block;
  font-size: 25rpx;
  font-weight: 600;
  color: #1b2432;
}

.home__shop-item-addr {
  display: block;
  margin-top: 2rpx;
  font-size: 20rpx;
  color: #7a8699;
}

.home__entries {
  display: flex;
  gap: 12rpx;
  margin-top: 14rpx;
}

.home__entry {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  padding: 14rpx 0;
  border-radius: 10rpx;
  background-color: #ffffff;
  box-shadow: 0 2rpx 8rpx rgba(20, 35, 58, 0.05);
}

.home__entry-icon {
  width: 38rpx;
  height: 38rpx;
  line-height: 38rpx;
  text-align: center;
  border-radius: 8rpx;
  background-color: #fff1e6;
  color: #e85d04;
  font-size: 22rpx;
  font-weight: 700;
}

.home__entry-label {
  font-size: 23rpx;
  color: #33455f;
  font-weight: 600;
}
</style>
