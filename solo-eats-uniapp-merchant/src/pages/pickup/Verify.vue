<template>
  <view class="verify mt-page">
    <!-- 成功态 -->
    <view v-if="result" class="verify__success">
      <view class="verify__success-icon">✓</view>
      <text class="verify__success-title">核销成功</text>
      <text class="verify__success-order">{{ result.orderNo }}</text>
      <text class="verify__success-status">{{ result.statusName }}</text>
      <text class="verify__success-time">{{ result.verifiedAt }} · 请把餐品交给顾客</text>

      <view class="verify__success-actions">
        <button class="mt-btn mt-btn--plain" @click="resetForm">继续核销</button>
        <button class="mt-btn mt-btn--primary" @click="backToOrders">返回订单</button>
      </view>
    </view>

    <template v-else>
      <view class="verify__head">
        <text class="verify__title">输入取餐码核销</text>
        <text class="verify__desc">
          阶段A 不接扫码插件，请向顾客索取取餐码后手动输入。核销成功后订单状态由后端推进，
          页面展示接口返回的状态名。
        </text>
      </view>

      <!-- 从订单详情进入时展示上下文，避免核销错单 -->
      <view v-if="orderId > 0" class="mt-card verify__context">
        <view class="mt-between">
          <text class="mt-muted">核销订单</text>
          <text class="verify__context-no">{{ contextOrderNo || `#${orderId}` }}</text>
        </view>
        <view class="mt-between">
          <text class="mt-muted">当前状态</text>
          <text
            class="verify__context-status"
            :class="{ 'is-warn': contextStatus !== null && contextStatus !== 31 }"
          >
            {{ contextStatusName || '加载中…' }}
          </text>
        </view>
        <view v-if="contextError" class="verify__context-error">{{ contextError }}</view>
      </view>

      <view class="mt-card verify__form">
        <text class="verify__label">取餐码</text>
        <input
          v-model="code"
          class="verify__input"
          type="text"
          maxlength="8"
          confirm-type="done"
          placeholder="请输入 4~8 位取餐码"
          placeholder-class="mt-placeholder"
          @confirm="handleVerify"
          @input="onCodeInput"
        />
        <text class="verify__hint">
          {{ orderId > 0 ? '需与订单上的取餐码一致' : '将按取餐码在待取餐订单中匹配' }}
        </text>
      </view>

      <view v-if="errorMessage" class="verify__error">
        <text>{{ errorMessage }}</text>
      </view>

      <button
        class="mt-btn mt-btn--primary verify__submit"
        :disabled="submitting || code.length < 4"
        @click="handleVerify"
      >
        {{ submitting ? '核销中…' : '确认核销' }}
      </button>

      <view class="verify__notice">
        <text class="verify__notice-title">核销规则</text>
        <text class="verify__notice-line">· 仅自取订单在待取餐阶段可核销，配送单会提示不是自取订单（以后端校验为准）。</text>
        <text class="verify__notice-line">· 重复核销会返回「订单状态不允许当前操作」，属正常提示，刷新订单即可确认。</text>
        <text class="verify__notice-line">· 核销成功后请核对订单号与商品再交付餐品。</text>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ApiError } from '@/api/http'
import { getMerchantOrder, pageMerchantOrders, verifyPickupCode } from '@/api/order'
import { useAuthStore } from '@/stores/auth'
import { useShopStore } from '@/stores/shop'
import { speakPickupVerified } from '@/utils/voice'

/**
 * 自取核销（阶段A 手动输码，不做扫码）。
 *
 * 后端核销接口是 POST /merchant/orders/{orderId}/pickup/verify，路径需要 orderId，
 * 但页面可能只拿到取餐码。两种入口：
 * 1. 从订单详情进入：带 orderId，先校验状态与取餐码再提交；
 * 2. 直接进入：在 status=31 的待用户取餐订单里按 pickupCode 匹配（分页最多 5 页 / 250 单），
 *    匹配到后提交该订单。没有新增后端接口。
 * 匹配成功后提交的 code 用订单上的原始值，避免大小写/前缀差异导致后端精确比较失败。
 */

const VERIFY_STATUS = 31
const SEARCH_PAGE_SIZE = 50
const MAX_SEARCH_PAGES = 5

const auth = useAuthStore()
const shop = useShopStore()

const orderId = ref(0)
const contextOrderNo = ref('')
const contextStatus = ref<number | null>(null)
const contextStatusName = ref('')
const contextError = ref('')

const code = ref('')
const submitting = ref(false)
const errorMessage = ref('')

const result = ref<{
  orderId: number
  orderNo: string
  statusName: string
  verifiedAt: string
} | null>(null)

onLoad((options) => {
  const id = Number(options?.orderId)
  const orderNo = options?.orderNo ? decodeURIComponent(String(options.orderNo)) : ''
  if (!auth.ensureLogin('/pages/pickup/Verify')) {
    return
  }
  if (Number.isFinite(id) && id > 0) {
    orderId.value = id
    contextOrderNo.value = orderNo
    void loadContext(id)
  }
})

async function loadContext(id: number): Promise<void> {
  contextError.value = ''
  try {
    const detail = await getMerchantOrder(id, true)
    contextOrderNo.value = detail.orderNo
    contextStatus.value = detail.status
    contextStatusName.value = detail.statusName
  } catch (error) {
    contextError.value = (error as Error)?.message || '订单信息加载失败，仍可直接核销'
  }
}

/** 全角转半角、去掉空格与分隔符、统一大写 */
function normalizeCode(raw: string): string {
  return String(raw || '')
    .replace(/[０-９Ａ-Ｚａ-ｚ]/g, (char) =>
      String.fromCharCode(char.charCodeAt(0) - 0xfee0),
    )
    .replace(/[\s\-_]/g, '')
    .toUpperCase()
}

function onCodeInput(event: { detail: { value: string } }): void {
  const normalized = normalizeCode(event.detail.value).slice(0, 8)
  if (normalized !== code.value) {
    code.value = normalized
  }
  errorMessage.value = ''
}

/** 核销时间按本机本地时区展示，不引入 UTC 偏移 */
function localNowText(): string {
  const now = new Date()
  const pad = (value: number): string => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(
    now.getHours(),
  )}:${pad(now.getMinutes())}`
}

async function handleVerify(): Promise<void> {
  if (submitting.value) {
    return
  }
  const normalized = normalizeCode(code.value)
  if (!/^[0-9A-Z]{4,8}$/.test(normalized)) {
    errorMessage.value = '取餐码应为 4~8 位数字或字母，请核对后重试'
    return
  }

  submitting.value = true
  errorMessage.value = ''
  try {
    const target = await resolveTarget(normalized)
    if (!target) {
      return
    }

    const status = await verifyPickupCode(target.orderId, target.pickupCode)
    result.value = {
      orderId: status.orderId,
      orderNo: target.orderNo,
      statusName: status.statusName,
      verifiedAt: localNowText(),
    }
    speakPickupVerified(target.orderNo)
  } catch (error) {
    errorMessage.value = resolveErrorMessage(error)
  } finally {
    submitting.value = false
  }
}

interface VerifyTarget {
  orderId: number
  orderNo: string
  pickupCode: string
}

/** 返回 null 表示已把错误写进 errorMessage，不再请求核销 */
async function resolveTarget(normalized: string): Promise<VerifyTarget | null> {
  if (orderId.value > 0) {
    const detail = await getMerchantOrder(orderId.value, true)
    contextStatus.value = detail.status
    contextStatusName.value = detail.statusName
    contextOrderNo.value = detail.orderNo
    if (detail.status !== VERIFY_STATUS) {
      errorMessage.value = `订单当前状态「${detail.statusName}」，不可核销`
      return null
    }
    if (!detail.pickupCode) {
      errorMessage.value = '该订单没有取餐码，请确认是否为自取订单'
      return null
    }
    if (normalizeCode(detail.pickupCode) !== normalized) {
      errorMessage.value = '取餐码不正确，请向顾客核对后重试'
      return null
    }
    return { orderId: detail.orderId, orderNo: detail.orderNo, pickupCode: detail.pickupCode }
  }

  const matched = await findOrderByCode(normalized)
  if (!matched) {
    errorMessage.value = '未找到该取餐码对应的待取餐订单，请确认取餐码或改用订单详情进入核销'
    return null
  }
  return {
    orderId: matched.orderId,
    orderNo: matched.orderNo,
    pickupCode: matched.pickupCode || normalized,
  }
}

async function findOrderByCode(normalized: string): Promise<{ orderId: number; orderNo: string; pickupCode: string | null } | null> {
  for (let pageNum = 1; pageNum <= MAX_SEARCH_PAGES; pageNum += 1) {
    const page = await pageMerchantOrders({
      status: VERIFY_STATUS,
      shopId: shop.currentShopId,
      pageNum,
      pageSize: SEARCH_PAGE_SIZE,
      silent: true,
    })
    const hit = (page.list || []).find(
      (order) => order.pickupCode && normalizeCode(order.pickupCode) === normalized,
    )
    if (hit) {
      return { orderId: hit.orderId, orderNo: hit.orderNo, pickupCode: hit.pickupCode }
    }
    if (pageNum * SEARCH_PAGE_SIZE >= (page.total || 0)) {
      break
    }
  }
  return null
}

function resolveErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 10001) {
      return '取餐码不正确，或该订单不是自取订单'
    }
    if (error.code === 20001) {
      return '该订单当前状态不可核销（可能已被核销），请刷新订单查看最新状态'
    }
    if (error.code === 10004) {
      return '无权核销该订单，请确认门店归属'
    }
    return error.message || '核销失败，请重试'
  }
  return (error as Error)?.message || '核销失败，请检查网络后重试'
}

function resetForm(): void {
  result.value = null
  code.value = ''
  errorMessage.value = ''
  if (orderId.value > 0) {
    void loadContext(orderId.value)
  }
}

function backToOrders(): void {
  uni.navigateBack({
    delta: 1,
    fail: () => {
      uni.switchTab({ url: '/pages/order/List' })
    },
  })
}
</script>

<style scoped>
.verify {
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
}

.verify__head {
  padding: 4rpx 4rpx 16rpx;
}

.verify__title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: #1b2432;
}

.verify__desc {
  display: block;
  margin-top: 6rpx;
  font-size: 21rpx;
  color: #7a8699;
  line-height: 1.6;
}

.verify__context {
  margin-bottom: 14rpx;
}

.verify__context .mt-between {
  padding: 5rpx 0;
  font-size: 23rpx;
}

.verify__context-no {
  color: #1b2432;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.verify__context-status {
  color: #067647;
  font-weight: 600;
}

.verify__context-status.is-warn {
  color: #b42318;
}

.verify__context-error {
  margin-top: 8rpx;
  padding: 8rpx 12rpx;
  border-radius: 6rpx;
  background-color: #fff6ea;
  color: #a2520a;
  font-size: 20rpx;
}

.verify__form {
  padding: 20rpx;
}

.verify__label {
  display: block;
  font-size: 23rpx;
  color: #33455f;
  font-weight: 600;
}

.verify__input {
  width: 100%;
  height: 108rpx;
  margin-top: 10rpx;
  padding: 0 18rpx;
  border-radius: 10rpx;
  background-color: #f7f9fc;
  border: 2rpx solid #e4e9f0;
  font-size: 40rpx;
  font-weight: 700;
  letter-spacing: 8rpx;
  text-align: center;
  color: #1b2432;
  font-variant-numeric: tabular-nums;
}

.verify__hint {
  display: block;
  margin-top: 8rpx;
  text-align: center;
  font-size: 20rpx;
  color: #8d99aa;
}

.verify__error {
  margin-top: 14rpx;
  padding: 14rpx 16rpx;
  border-radius: 8rpx;
  background-color: #fee4e2;
  color: #b42318;
  font-size: 22rpx;
  line-height: 1.6;
}

.verify__submit {
  margin-top: 20rpx;
  height: 88rpx;
  line-height: 88rpx;
}

.verify__notice {
  margin-top: 26rpx;
  padding: 16rpx;
  border-radius: 10rpx;
  background-color: #eef4ff;
}

.verify__notice-title {
  display: block;
  font-size: 22rpx;
  font-weight: 600;
  color: #2c4a75;
  margin-bottom: 6rpx;
}

.verify__notice-line {
  display: block;
  font-size: 20rpx;
  color: #3f5d88;
  line-height: 1.7;
}

.verify__success {
  margin-top: 40rpx;
  padding: 40rpx 28rpx;
  border-radius: 16rpx;
  background-color: #ffffff;
  text-align: center;
}

.verify__success-icon {
  width: 96rpx;
  height: 96rpx;
  line-height: 96rpx;
  margin: 0 auto;
  border-radius: 50%;
  background-color: #dcfae6;
  color: #067647;
  font-size: 48rpx;
  font-weight: 700;
}

.verify__success-title {
  display: block;
  margin-top: 16rpx;
  font-size: 32rpx;
  font-weight: 700;
  color: #067647;
}

.verify__success-order {
  display: block;
  margin-top: 14rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: #1b2432;
  font-variant-numeric: tabular-nums;
}

.verify__success-status {
  display: inline-block;
  margin-top: 8rpx;
  padding: 2rpx 14rpx;
  border-radius: 6rpx;
  background-color: #dcfae6;
  color: #067647;
  font-size: 21rpx;
}

.verify__success-time {
  display: block;
  margin-top: 10rpx;
  font-size: 21rpx;
  color: #7a8699;
}

.verify__success-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 30rpx;
}

.verify__success-actions .mt-btn {
  flex: 1;
}
</style>
