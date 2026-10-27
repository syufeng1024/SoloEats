<template>
  <view class="finance mt-page">
    <!-- 汇总：由接口 summary 下发，非前端累加；口径随当前状态筛选变化 -->
    <view class="finance__summary mt-card">
      <view class="mt-between">
        <text class="mt-section-title">结算汇总</text>
        <text class="mt-muted">{{ activeLabel }} · 全部账单</text>
      </view>
      <view v-if="loading && !summary" class="mt-loading">正在汇总…</view>
      <template v-else>
        <view class="finance__summary-grid">
          <view class="finance__summary-item">
            <text class="finance__summary-label">营业额</text>
            <text class="mt-price finance__summary-value">{{ fenToYuan(summary?.grossAmount) }}</text>
          </view>
          <view class="finance__summary-item">
            <text class="finance__summary-label">平台抽佣</text>
            <text class="mt-price finance__summary-value">{{ fenToYuan(summary?.platformCommission) }}</text>
          </view>
          <view class="finance__summary-item">
            <text class="finance__summary-label">应结金额</text>
            <text class="mt-price finance__summary-value">{{ fenToYuan(summary?.merchantAmount) }}</text>
          </view>
        </view>
        <text class="finance__summary-note">
          共 {{ summary?.billCount ?? 0 }} 张账单；金额来自接口 summary 字段，非前端求和。
        </text>
      </template>
    </view>

    <!-- 状态筛选 -->
    <scroll-view scroll-x class="finance__tabs" :show-scrollbar="false">
      <view
        v-for="tab in STATUS_TABS"
        :key="String(tab.value)"
        class="finance__tab"
        :class="{ 'is-active': tab.value === activeStatus }"
        @click="changeStatus(tab.value)"
      >
        {{ tab.label }}
      </view>
    </scroll-view>

    <view class="finance__toolbar">
      <text class="finance__count">{{ bills.length }} 张账单</text>
      <text class="finance__refresh" @click="load">刷新</text>
    </view>

    <view v-if="loading && bills.length === 0" class="mt-loading">正在加载结算单…</view>

    <view v-else-if="error && bills.length === 0" class="mt-error">
      <text class="mt-error__msg">{{ error }}</text>
      <view class="mt-error__retry" @click="load">重新加载</view>
    </view>

    <view v-else-if="bills.length === 0" class="mt-empty finance__empty">
      <text class="mt-empty__title">暂无结算单</text>
      <text>该筛选条件下没有账单记录</text>
    </view>

    <template v-else>
      <view
        v-for="bill in pageBills"
        :key="bill.billId"
        class="finance__row"
        @click="openDetail(bill)"
      >
        <view class="mt-between">
          <text class="finance__no">{{ bill.billNo }}</text>
          <text class="mt-tag" :class="statusClass(bill.status)">{{ statusText(bill.status) }}</text>
        </view>
        <view class="mt-between finance__row-meta">
          <text class="mt-muted">账单日 {{ bill.billDate || '--' }}</text>
          <text class="finance__row-payable">
            应结 <text class="mt-price">{{ fenToYuan(bill.merchantAmount) }}</text>
          </text>
        </view>
        <view class="finance__row-sub">
          <text>营业额 ¥{{ fenToYuan(bill.grossAmount) }}</text>
          <text>抽佣 ¥{{ fenToYuan(bill.platformCommission) }}</text>
          <text v-if="bill.refundAmount" class="finance__row-refund">
            退款冲抵 ¥{{ fenToYuan(bill.refundAmount) }}
          </text>
        </view>
      </view>

      <view class="finance__pager">
        <text class="finance__pager-note">接口一次返回全部账单，本页按 {{ PAGE_SIZE }} 条前端分页</text>
        <view class="finance__pager-actions">
          <button
            class="mt-btn mt-btn--plain mt-btn--small"
            :disabled="pageNum <= 1"
            @click="prevPage"
          >
            上一页
          </button>
          <text class="finance__pager-index">{{ pageNum }} / {{ totalPages }}</text>
          <button
            class="mt-btn mt-btn--plain mt-btn--small"
            :disabled="pageNum >= totalPages"
            @click="nextPage"
          >
            下一页
          </button>
        </view>
      </view>
    </template>

    <!-- 账单详情 -->
    <view v-if="detailVisible" class="mt-mask" @click="closeDetail" />
    <view v-if="detailVisible" class="mt-sheet">
      <view class="mt-between">
        <text class="mt-sheet__title">{{ detail?.bill.billNo || '结算详情' }}</text>
        <text class="finance__close" @click="closeDetail">关闭</text>
      </view>

      <scroll-view scroll-y class="finance__detail-body">
        <view v-if="detailLoading" class="mt-loading">正在加载详情…</view>
        <view v-else-if="detailError" class="mt-error">
          <text class="mt-error__msg">{{ detailError }}</text>
          <view class="mt-error__retry" @click="reloadDetail">重新加载</view>
        </view>
        <template v-else-if="detail">
          <view class="finance__detail-grid">
            <view class="finance__detail-item">
              <text class="finance__detail-label">营业额</text>
              <text class="mt-price finance__detail-value">{{ fenToYuan(detail.bill.grossAmount) }}</text>
            </view>
            <view class="finance__detail-item">
              <text class="finance__detail-label">平台抽佣</text>
              <text class="mt-price finance__detail-value">{{ fenToYuan(detail.bill.platformCommission) }}</text>
            </view>
            <view class="finance__detail-item">
              <text class="finance__detail-label">应结金额</text>
              <text class="mt-price finance__detail-value">{{ fenToYuan(detail.bill.merchantAmount) }}</text>
            </view>
          </view>

          <view class="finance__detail-meta">
            <view class="mt-between">
              <text class="mt-muted">账单日</text>
              <text>{{ detail.bill.billDate || '--' }}</text>
            </view>
            <view class="mt-between">
              <text class="mt-muted">状态</text>
              <text>{{ statusText(detail.bill.status) }}</text>
            </view>
            <view class="mt-between">
              <text class="mt-muted">佣金基数</text>
              <text>¥{{ fenToYuan(detail.bill.commissionBase) }}</text>
            </view>
            <view class="mt-between">
              <text class="mt-muted">商家承担优惠</text>
              <text>¥{{ fenToYuan(detail.bill.merchantDiscount) }}</text>
            </view>
            <view class="mt-between">
              <text class="mt-muted">平台承担优惠</text>
              <text>¥{{ fenToYuan(detail.bill.platformDiscount) }}</text>
            </view>
            <view class="mt-between">
              <text class="mt-muted">退款冲抵</text>
              <text>¥{{ fenToYuan(detail.bill.refundAmount) }}</text>
            </view>
            <view class="mt-between">
              <text class="mt-muted">履约服务费</text>
              <text>¥{{ fenToYuan(detail.bill.performanceFee) }}</text>
            </view>
          </view>

          <view class="finance__detail-orders">
            <view class="mt-between finance__detail-orders-head">
              <text class="mt-section-title">订单明细</text>
              <text class="mt-muted">{{ detail.orders.length }} 单</text>
            </view>
            <view v-if="detail.orders.length === 0" class="mt-muted">该账单没有订单明细</view>
            <view v-for="order in detail.orders" :key="order.orderId" class="finance__order">
              <view class="mt-between">
                <text class="finance__order-no">{{ order.orderNo }}</text>
                <text class="finance__order-amount">应结 ¥{{ fenToYuan(order.merchantAmount) }}</text>
              </view>
              <view class="finance__order-sub">
                <text>实付 ¥{{ fenToYuan(order.payAmount) }}</text>
                <text>佣金 ¥{{ fenToYuan(order.commissionAmount) }}</text>
              </view>
            </view>
          </view>
        </template>
      </scroll-view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { getSettlementDetail, listSettlements } from '@/api/finance'
import type {
  SettlementBillVO,
  SettlementDetailVO,
  SettlementSummaryVO,
} from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { fenToYuan } from '@/utils/money'

/**
 * 财务结算。
 *
 * 接口口径（SettlementServiceImpl#listMerchantBills）：
 * - 需要 merchantId，后端与令牌比对；前端取 auth.merchantId；
 * - 一次返回当前 status 下的全部账单 + summary，没有 pageNum/pageSize，
 *   因此分页在本页按 PAGE_SIZE 切片，summary 仍代表「当前筛选的全部账单」；
 * - status：0已生成 / 10待审核 / 20已审核 / 30已打款 / 40已驳回，
 *   接口未下发 statusName，中文映射维护在本页。
 */

interface StatusTab {
  value: number | null
  label: string
}

const PAGE_SIZE = 10
const FINANCE_PATH = '/pages/finance/Finance'

const STATUS_TABS: StatusTab[] = [
  { value: null, label: '全部' },
  { value: 0, label: '已生成' },
  { value: 10, label: '待审核' },
  { value: 20, label: '已审核' },
  { value: 30, label: '已打款' },
  { value: 40, label: '已驳回' },
]

const STATUS_TEXT: Record<number, string> = {
  0: '已生成',
  10: '待审核',
  20: '已审核',
  30: '已打款',
  40: '已驳回',
}

const auth = useAuthStore()

const bills = ref<SettlementBillVO[]>([])
const summary = ref<SettlementSummaryVO | null>(null)
const activeStatus = ref<number | null>(null)
const pageNum = ref(1)
const loading = ref(false)
const error = ref('')

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref<SettlementDetailVO | null>(null)
let detailBillId: number | null = null

let requestToken = 0

const activeLabel = computed(
  () => STATUS_TABS.find((tab) => tab.value === activeStatus.value)?.label || '全部',
)

const totalPages = computed(() => Math.max(1, Math.ceil(bills.value.length / PAGE_SIZE)))

const pageBills = computed(() => {
  const start = (pageNum.value - 1) * PAGE_SIZE
  return bills.value.slice(start, start + PAGE_SIZE)
})

onShow(() => {
  if (!auth.ensureLogin(FINANCE_PATH)) {
    return
  }
  void load()
})

onPullDownRefresh(() => {
  void load()
})

function toast(message: string): void {
  uni.showToast({ title: message, icon: 'none', duration: 2400 })
}

function statusText(status: number | null): string {
  if (status === null || status === undefined) {
    return '状态未知'
  }
  return STATUS_TEXT[status] ?? `状态 ${status}`
}

function statusClass(status: number | null): string {
  if (status === 30) {
    return 'mt-tag--ok'
  }
  if (status === 40) {
    return 'mt-tag--warn'
  }
  if (status === 0 || status === 10) {
    return ''
  }
  return 'mt-tag--info'
}

async function load(): Promise<void> {
  const merchantId = auth.merchantId
  if (!merchantId) {
    error.value = '登录令牌缺少 merchantId，请退出后重新登录'
    bills.value = []
    summary.value = null
    uni.stopPullDownRefresh()
    return
  }

  const token = ++requestToken
  loading.value = true
  error.value = ''
  try {
    const result = await listSettlements(merchantId, activeStatus.value, true)
    if (token !== requestToken) {
      return
    }
    bills.value = Array.isArray(result.bills) ? result.bills : []
    summary.value = result.summary ?? null
    pageNum.value = 1
  } catch (err) {
    if (token !== requestToken) {
      return
    }
    error.value = (err as Error)?.message || '结算单加载失败'
    bills.value = []
    summary.value = null
  } finally {
    if (token === requestToken) {
      loading.value = false
    }
    uni.stopPullDownRefresh()
  }
}

function changeStatus(status: number | null): void {
  if (activeStatus.value === status) {
    return
  }
  activeStatus.value = status
  pageNum.value = 1
  void load()
}

function prevPage(): void {
  if (pageNum.value > 1) {
    pageNum.value -= 1
  }
}

function nextPage(): void {
  if (pageNum.value < totalPages.value) {
    pageNum.value += 1
  }
}

function openDetail(bill: SettlementBillVO): void {
  if (detailVisible.value) {
    return
  }
  detailBillId = bill.billId
  detail.value = null
  detailError.value = ''
  detailVisible.value = true
  void loadDetail()
}

async function loadDetail(): Promise<void> {
  const merchantId = auth.merchantId
  if (!detailBillId || !merchantId) {
    detailError.value = '登录态缺少商家信息，请重新登录'
    return
  }
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await getSettlementDetail(detailBillId, merchantId, true)
  } catch (err) {
    detailError.value = (err as Error)?.message || '结算详情加载失败'
  } finally {
    detailLoading.value = false
  }
}

function reloadDetail(): void {
  void loadDetail()
}

function closeDetail(): void {
  detailVisible.value = false
  detailBillId = null
  detail.value = null
  detailError.value = ''
}
</script>

<style scoped>
.finance {
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
}

.finance__summary {
  background: linear-gradient(135deg, #1f3a4d, #16233a);
  color: #ffffff;
}

.finance__summary .mt-section-title {
  color: #ffffff;
}

.finance__summary .mt-muted,
.finance__summary-note {
  color: rgba(255, 255, 255, 0.68);
}

.finance__summary-grid {
  display: flex;
  gap: 12rpx;
  margin-top: 14rpx;
}

.finance__summary-item {
  flex: 1;
  min-width: 0;
  padding: 12rpx 10rpx;
  border-radius: 10rpx;
  background-color: rgba(255, 255, 255, 0.08);
}

.finance__summary-label {
  display: block;
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.72);
}

.finance__summary-value {
  display: block;
  margin-top: 4rpx;
  font-size: 30rpx;
  color: #ffb27a;
}

.finance__summary-note {
  display: block;
  margin-top: 12rpx;
  font-size: 20rpx;
  line-height: 1.6;
}

.finance__tabs {
  margin-top: 14rpx;
  white-space: nowrap;
  border-radius: 10rpx;
  background-color: #ffffff;
}

.finance__tab {
  display: inline-block;
  padding: 18rpx 22rpx;
  font-size: 25rpx;
  color: #55637a;
  position: relative;
}

.finance__tab.is-active {
  color: #e85d04;
  font-weight: 700;
}

.finance__tab.is-active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 6rpx;
  width: 32rpx;
  height: 5rpx;
  margin-left: -16rpx;
  border-radius: 3rpx;
  background-color: #ff7a00;
}

.finance__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 4rpx;
}

.finance__count {
  font-size: 21rpx;
  color: #7a8699;
}

.finance__refresh {
  font-size: 21rpx;
  color: #e85d04;
  font-weight: 600;
}

.finance__empty {
  background-color: #ffffff;
  border-radius: 12rpx;
}

.finance__row {
  padding: 16rpx 18rpx;
  margin-bottom: 10rpx;
  border-radius: 10rpx;
  background-color: #ffffff;
}

.finance__no {
  font-size: 23rpx;
  font-weight: 600;
  color: #1b2432;
  font-variant-numeric: tabular-nums;
}

.finance__row-meta {
  margin-top: 8rpx;
}

.finance__row-payable {
  font-size: 22rpx;
  color: #55637a;
}

.finance__row-sub {
  display: flex;
  flex-wrap: wrap;
  gap: 18rpx;
  margin-top: 8rpx;
  font-size: 20rpx;
  color: #8d99aa;
}

.finance__row-refund {
  color: #b42318;
}

.finance__pager {
  margin-top: 8rpx;
  padding: 10rpx 4rpx 20rpx;
}

.finance__pager-note {
  display: block;
  text-align: center;
  font-size: 19rpx;
  color: #a9b4c4;
}

.finance__pager-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20rpx;
  margin-top: 10rpx;
}

.finance__pager-index {
  font-size: 22rpx;
  color: #55637a;
  font-variant-numeric: tabular-nums;
}

.finance__close {
  font-size: 23rpx;
  color: #7a8699;
}

.finance__detail-body {
  max-height: 62vh;
  margin-top: 14rpx;
}

.finance__detail-grid {
  display: flex;
  gap: 10rpx;
}

.finance__detail-item {
  flex: 1;
  min-width: 0;
  padding: 12rpx 10rpx;
  border-radius: 10rpx;
  background-color: #f7f9fc;
}

.finance__detail-label {
  display: block;
  font-size: 20rpx;
  color: #7a8699;
}

.finance__detail-value {
  display: block;
  margin-top: 4rpx;
  font-size: 28rpx;
}

.finance__detail-meta {
  margin-top: 14rpx;
  padding: 4rpx 12rpx;
  border-radius: 10rpx;
  background-color: #ffffff;
  border: 1rpx solid #eef1f5;
  font-size: 23rpx;
}

.finance__detail-meta .mt-between {
  padding: 10rpx 0;
  border-bottom: 1rpx solid #f4f6f9;
}

.finance__detail-meta .mt-between:last-child {
  border-bottom: none;
}

.finance__detail-orders {
  margin-top: 16rpx;
}

.finance__detail-orders-head {
  margin-bottom: 6rpx;
}

.finance__order {
  padding: 12rpx 0;
  border-bottom: 1rpx solid #f4f6f9;
}

.finance__order-no {
  font-size: 22rpx;
  color: #33455f;
  font-variant-numeric: tabular-nums;
}

.finance__order-amount {
  font-size: 22rpx;
  color: #e85d04;
  font-weight: 600;
}

.finance__order-sub {
  display: flex;
  gap: 18rpx;
  margin-top: 4rpx;
  font-size: 19rpx;
  color: #8d99aa;
}
</style>
