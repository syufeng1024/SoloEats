<template>
  <view class="se-page confirm">
    <view v-if="loading" class="se-loading">正在加载订单信息…</view>

    <view v-else-if="error" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="goHome">返回首页</view>
    </view>

    <template v-else>
      <view class="se-card confirm__fulfillment">
        <view
          class="confirm__fulfillment-item"
          :class="{ 'is-active': fulfillmentType === 1 }"
          @click="switchFulfillment(1)"
        >
          外卖配送
        </view>
        <view
          class="confirm__fulfillment-item"
          :class="{
            'is-active': fulfillmentType === 2,
            'is-disabled': shop && shop.pickupEnabled !== 1,
          }"
          @click="switchFulfillment(2)"
        >
          到店自取
          <text v-if="shop && shop.pickupEnabled !== 1" class="confirm__fulfillment-hint">未开通</text>
        </view>
      </view>

      <view v-if="fulfillmentType === 1" class="se-card confirm__block">
        <view class="confirm__address" @click="chooseAddress">
          <view class="confirm__address-main">
            <view v-if="selectedAddress" class="se-between">
              <text class="confirm__address-name">{{ selectedAddress.receiverName }}</text>
              <text class="se-muted">{{ selectedAddress.receiverPhone }}</text>
            </view>
            <text v-else class="confirm__address-name">请选择收货地址</text>
            <text v-if="selectedAddress" class="confirm__address-detail">
              {{ fullAddress }}
            </text>
            <text v-else class="se-muted">地址管理在阶段B开放，可选择已有地址</text>
          </view>
          <text class="confirm__arrow">›</text>
        </view>

        <view class="se-divider" />

        <view class="confirm__field">
          <text class="confirm__field-label">收货人</text>
          <input v-model="receiverName" class="confirm__input" placeholder="请填写收货人姓名" />
        </view>
        <view class="confirm__field">
          <text class="confirm__field-label">手机号</text>
          <input
            v-model="receiverPhone"
            class="confirm__input"
            type="number"
            maxlength="11"
            placeholder="请填写完整手机号"
          />
        </view>
        <text class="confirm__hint">地址簿接口只返回脱敏手机号，配送需重新填写完整号码</text>
      </view>

      <view v-else class="se-card confirm__block">
        <view class="confirm__shop">
          <text class="confirm__shop-name">{{ shop ? shop.shopName : '' }}</text>
          <text class="confirm__address-detail">{{ shop ? shop.address : '' }}</text>
          <text v-if="shop && shop.contactPhone" class="se-muted">门店电话 {{ shop.contactPhone }}</text>
        </view>
        <view class="se-divider" />
        <view class="confirm__field">
          <text class="confirm__field-label">自取人手机号</text>
          <input
            v-model="pickupPhone"
            class="confirm__input"
            type="number"
            maxlength="11"
            placeholder="取餐时门店核对用"
          />
        </view>
      </view>

      <view class="se-card confirm__block">
        <view class="confirm__block-title">商品明细</view>
        <view v-for="line in lines" :key="cartLineKey(line)" class="confirm__line">
          <view class="confirm__line-body">
            <text class="confirm__line-name">{{ line.skuName }}</text>
            <text v-if="cartSpecText(line)" class="confirm__line-spec">{{ cartSpecText(line) }}</text>
            <text v-if="line.addons.length > 0" class="confirm__line-addons">
              加料：{{ line.addons.map((addon) => addon.itemName).join('、') }}
            </text>
          </view>
          <text class="se-muted confirm__line-qty">×{{ line.qty }}</text>
          <text class="confirm__line-amount">¥{{ fenToYuan(line.price * line.qty) }}</text>
        </view>
        <text v-if="cartHasAddons" class="confirm__hint">
          含加料商品：后端下单接口只按 SKU 计价，加料金额不进入实付
        </text>
      </view>

      <view class="se-card confirm__block">
        <view class="confirm__block-title">备注</view>
        <input v-model="remark" class="confirm__input" maxlength="100" placeholder="口味、餐具等要求（选填）" />
      </view>

      <view class="se-card confirm__block">
        <view class="se-between confirm__fee-row">
          <text class="se-label">商品小计</text>
          <text>¥{{ fenToYuan(goodsAmount) }}</text>
        </view>
        <view class="se-between confirm__fee-row">
          <text class="se-label">打包费</text>
          <text>¥{{ fenToYuan(packagingFee) }}</text>
        </view>
        <view class="se-between confirm__fee-row">
          <text class="se-label">{{ fulfillmentType === 2 ? '配送费（自取免费）' : '配送费' }}</text>
          <text v-if="checking" class="se-muted">试算中…</text>
          <text v-else>¥{{ fenToYuan(deliveryFee) }}</text>
        </view>
        <view class="se-divider" />
        <view class="se-between confirm__fee-row">
          <text class="se-strong">合计</text>
          <text class="se-price confirm__total">{{ fenToYuan(totalAmount) }}</text>
        </view>

        <view v-if="checkError" class="confirm__warning">{{ checkError }}</view>
        <view v-else-if="belowMin" class="confirm__warning">
          还差 ¥{{ fenToYuan(minOrderAmount - goodsAmount) }} 起送
        </view>
        <view v-else-if="deliveryCheck && deliveryCheck.expectedDeliveryAt && fulfillmentType === 1" class="se-muted confirm__eta">
          预计送达 {{ formatDateTime(deliveryCheck.expectedDeliveryAt) }}
        </view>
      </view>

      <view class="se-fixed-bottom confirm__bar">
        <view class="confirm__bar-amount">
          <text class="se-label">实付</text>
          <text class="se-price confirm__bar-total">{{ fenToYuan(totalAmount) }}</text>
        </view>
        <button
          class="se-btn se-btn-primary confirm__submit"
          :disabled="submitting || checking"
          @click="submit"
        >
          {{ submitting ? '提交中…' : checking ? '费用试算中…' : '提交订单' }}
        </button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { checkDelivery, estimateFreight, fetchShopDetail } from '@/api/shop'
import { fetchAddresses } from '@/api/user'
import { createOrder } from '@/api/order'
import type { AddressVO, DeliveryCheckVO, FreightEstimateVO, ShopDetailVO } from '@/api/types'
import { fenToYuan } from '@/utils/money'
import { formatDateTime } from '@/utils/date'
import { clearIdempotencyKey, issueIdempotencyKey } from '@/utils/request'
import { useAuthStore } from '@/stores/auth'
import { cartLineKey, cartSpecText, useCartStore, type CartLine } from '@/stores/cart'

const auth = useAuthStore()
const cart = useCartStore()

const shopId = ref(0)
const shop = ref<ShopDetailVO | null>(null)
const fulfillmentType = ref<1 | 2>(1)
const addresses = ref<AddressVO[]>([])
const selectedAddressId = ref<number | null>(null)
/** 地址簿里新增后直接选中的地址，可能不在 bootstrap 时拉到的列表里 */
const selectedAddressSnapshot = ref<AddressVO | null>(null)
const receiverName = ref('')
const receiverPhone = ref('')
const pickupPhone = ref('')
const remark = ref('')

const deliveryCheck = ref<DeliveryCheckVO | null>(null)
const freightEstimate = ref<FreightEstimateVO | null>(null)
const checking = ref(false)
const checkError = ref('')

const loading = ref(true)
const error = ref('')
const submitting = ref(false)

let feeRequestId = 0

const lines = computed<CartLine[]>(() => cart.lines(shopId.value))
const goodsAmount = computed(() => cart.totalAmount(shopId.value))
const cartHasAddons = computed(() => cart.hasAddons(shopId.value))
const packagingFee = computed(() => shop.value?.packagingFee || 0)
const deliveryFee = computed(() => {
  if (fulfillmentType.value === 2) {
    return 0
  }
  return freightEstimate.value?.deliveryFee ?? shop.value?.deliveryFee ?? 0
})
const totalAmount = computed(() => goodsAmount.value + packagingFee.value + deliveryFee.value)
const minOrderAmount = computed(
  () => deliveryCheck.value?.minOrderAmount ?? shop.value?.minOrderAmount ?? 0,
)
const belowMin = computed(() => goodsAmount.value < minOrderAmount.value)
const canPickup = computed(() => shop.value?.pickupEnabled === 1)

const selectedAddress = computed<AddressVO | null>(() => {
  const fromList = addresses.value.find((address) => address.id === selectedAddressId.value)
  if (fromList) {
    return fromList
  }
  const snapshot = selectedAddressSnapshot.value
  return snapshot && snapshot.id === selectedAddressId.value ? snapshot : null
})

const fullAddress = computed(() => {
  const address = selectedAddress.value
  if (!address) {
    return ''
  }
  const parts = [address.province, address.city, address.district, address.address, address.houseNumber]
  return parts.filter((part) => Boolean(part)).join('')
})

onLoad((options) => {
  const id = Number(options?.shopId)
  if (!Number.isFinite(id) || id <= 0) {
    loading.value = false
    error.value = '缺少门店参数'
    return
  }
  shopId.value = id

  if (!auth.ensureLogin(`/pages/order/Confirm?shopId=${id}`)) {
    return
  }
  // 地址列表页选中后通过全局事件回传，避免依赖页面栈层级
  uni.$on('address:selected', onAddressSelected)
  cart.load(id)
  if (cart.lines(id).length === 0) {
    loading.value = false
    error.value = '购物车为空，请先选购商品'
    return
  }
  bootstrap()
})

onUnload(() => {
  uni.$off('address:selected', onAddressSelected)
})

async function bootstrap(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    shop.value = await fetchShopDetail(shopId.value)
    if (shop.value.pickupEnabled !== 1) {
      fulfillmentType.value = 1
    }
    await loadAddresses()
    prefillContact()
    await refreshFees()
  } catch (err) {
    error.value = (err as Error)?.message || '订单信息加载失败'
  } finally {
    loading.value = false
  }
}

async function loadAddresses(): Promise<void> {
  try {
    addresses.value = await fetchAddresses()
  } catch (err) {
    addresses.value = []
  }
}

function prefillContact(): void {
  const lastId = Number(uni.getStorageSync('address:last'))
  const target =
    addresses.value.find((address) => address.id === lastId) ||
    addresses.value.find((address) => address.isDefault) ||
    addresses.value[0] ||
    null
  if (target) {
    selectedAddressId.value = target.id
    receiverName.value = target.receiverName
  }
  // 地址簿只返回脱敏手机号，完整号码只能由用户填写，这里复用上一次填写的值
  const savedPhone = uni.getStorageSync('order:receiverPhone')
  receiverPhone.value = typeof savedPhone === 'string' ? savedPhone : ''
  const savedPickupPhone = uni.getStorageSync('pickup:phone')
  pickupPhone.value = typeof savedPickupPhone === 'string' ? savedPickupPhone : ''
}

function chooseAddress(): void {
  // 进入地址簿的 select 模式；选中后由 address:selected 事件回传
  uni.navigateTo({
    url: `/pages/address/List?mode=select&selectedId=${selectedAddressId.value || ''}`,
  })
}

function onAddressSelected(address: AddressVO): void {
  if (!address || !address.id) {
    return
  }
  const index = addresses.value.findIndex((item) => item.id === address.id)
  if (index >= 0) {
    addresses.value[index] = address
  } else {
    addresses.value.push(address)
  }
  selectedAddressSnapshot.value = address
  selectedAddressId.value = address.id
  receiverName.value = address.receiverName
  refreshFees()
}

function switchFulfillment(type: 1 | 2): void {
  if (fulfillmentType.value === type) {
    return
  }
  if (type === 2 && !canPickup.value) {
    uni.showToast({ title: '该门店暂未开通到店自取', icon: 'none' })
    return
  }
  fulfillmentType.value = type
  // 规格要求：切换履约方式后必须重新做配送范围校验与运费试算
  refreshFees()
}

function resolveCoordinates(type: 1 | 2): { lng: number; lat: number } {
  const stored = uni.getStorageSync('location') as { lng?: number; lat?: number } | ''
  const fallbackLng = stored && Number(stored.lng) ? Number(stored.lng) : 116.397428
  const fallbackLat = stored && Number(stored.lat) ? Number(stored.lat) : 39.90923

  if (type === 2) {
    return {
      lng: Number(shop.value?.lng) || fallbackLng,
      lat: Number(shop.value?.lat) || fallbackLat,
    }
  }
  return {
    lng: Number(selectedAddress.value?.lng) || fallbackLng,
    lat: Number(selectedAddress.value?.lat) || fallbackLat,
  }
}

async function refreshFees(): Promise<void> {
  if (!shop.value || goodsAmount.value <= 0) {
    return
  }
  const requestId = ++feeRequestId
  const lngLat = resolveCoordinates(fulfillmentType.value)
  checking.value = true
  checkError.value = ''

  try {
    const [check, freight] = await Promise.all([
      checkDelivery(shopId.value, {
        lng: lngLat.lng,
        lat: lngLat.lat,
        goodsAmount: goodsAmount.value,
        fulfillmentType: fulfillmentType.value,
      }),
      estimateFreight({
        shopId: shopId.value,
        lng: lngLat.lng,
        lat: lngLat.lat,
        goodsAmount: goodsAmount.value,
        fulfillmentType: fulfillmentType.value,
      }),
    ])
    // 快速来回切换时丢弃过期响应，避免费用与履约方式错配
    if (requestId !== feeRequestId) {
      return
    }
    deliveryCheck.value = check
    freightEstimate.value = freight
    if (fulfillmentType.value === 1 && !check.inRange) {
      checkError.value = check.reason || '当前地址超出配送范围'
    }
  } catch (err) {
    if (requestId !== feeRequestId) {
      return
    }
    deliveryCheck.value = null
    freightEstimate.value = null
    checkError.value = (err as Error)?.message || '配送范围与运费试算失败，请重试'
  } finally {
    if (requestId === feeRequestId) {
      checking.value = false
    }
  }
}

function toast(message: string): void {
  uni.showToast({ title: message, icon: 'none' })
}

function isPhoneValid(phone: string): boolean {
  return /^1[3-9]\d{9}$/.test(phone)
}

async function submit(): Promise<void> {
  if (submitting.value || checking.value) {
    return
  }
  if (!shop.value || !auth.userId) {
    toast('登录状态无效，请重新登录')
    return
  }
  if (lines.value.length === 0) {
    toast('购物车为空')
    return
  }
  if (belowMin.value) {
    toast(`还差 ¥${fenToYuan(minOrderAmount.value - goodsAmount.value)} 起送`)
    return
  }
  if (checkError.value) {
    toast(checkError.value)
    return
  }

  if (fulfillmentType.value === 1) {
    if (!selectedAddress.value) {
      toast('请选择收货地址')
      return
    }
    if (!receiverName.value.trim()) {
      toast('请填写收货人')
      return
    }
    if (!isPhoneValid(receiverPhone.value)) {
      toast('请填写完整的收货手机号')
      return
    }
  } else {
    if (!canPickup.value) {
      toast('该门店未开通到店自取')
      return
    }
    if (!isPhoneValid(pickupPhone.value)) {
      toast('请填写完整的自取人手机号')
      return
    }
  }

  submitting.value = true
  const signature = lines.value
    .map((line) => `${line.skuId}x${line.qty}`)
    .sort()
    .join('_')
  const scope = `order:${shopId.value}:${fulfillmentType.value}:${signature}`
  // 失败重试复用同一个幂等键；购物车或履约方式变化会生成不同 scope，避免误判重复
  const idempotencyKey = issueIdempotencyKey(scope)
  const isDelivery = fulfillmentType.value === 1

  try {
    const order = await createOrder(
      {
        userId: auth.userId,
        merchantId: shop.value.merchantId,
        shopId: shopId.value,
        fulfillmentType: fulfillmentType.value,
        packagingFee: packagingFee.value,
        deliveryFee: deliveryFee.value,
        receiverName: isDelivery ? receiverName.value.trim() : null,
        receiverPhone: isDelivery ? receiverPhone.value : null,
        receiverAddress: isDelivery ? fullAddress.value : null,
        pickupPhone: isDelivery ? null : pickupPhone.value,
        remark: remark.value.trim() || null,
        items: lines.value.map((line) => ({ skuId: line.skuId, quantity: line.qty })),
      },
      idempotencyKey,
    )

    clearIdempotencyKey(scope)
    if (isDelivery) {
      uni.setStorageSync('order:receiverPhone', receiverPhone.value)
      if (selectedAddressId.value) {
        uni.setStorageSync('address:last', selectedAddressId.value)
      }
    } else {
      uni.setStorageSync('pickup:phone', pickupPhone.value)
    }
    cart.clear(shopId.value)

    const query = [
      `orderId=${order.orderId}`,
      `payAmount=${order.payAmount}`,
      `fulfillmentType=${order.fulfillmentType}`,
    ]
    if (order.pickupCode) {
      query.push(`pickupCode=${encodeURIComponent(order.pickupCode)}`)
    }
    uni.redirectTo({ url: `/pages/order/PayResult?${query.join('&')}` })
  } catch (err) {
    // http 层已统一提示；保留幂等键，用户重试时会复用
  } finally {
    submitting.value = false
  }
}

function goHome(): void {
  uni.switchTab({ url: '/pages/index/Index' })
}
</script>

<style scoped>
.confirm {
  padding-bottom: 200rpx;
}

.confirm__fulfillment {
  display: flex;
  padding: 8rpx;
  margin-bottom: 16rpx;
}

.confirm__fulfillment-item {
  flex: 1;
  height: 72rpx;
  line-height: 72rpx;
  text-align: center;
  border-radius: 8rpx;
  font-size: 26rpx;
  color: #555555;
}

.confirm__fulfillment-item.is-active {
  background-color: #fff2e6;
  color: #ff7a00;
  font-weight: 600;
}

.confirm__fulfillment-item.is-disabled {
  color: #b8b8b8;
}

.confirm__fulfillment-hint {
  margin-left: 6rpx;
  font-size: 20rpx;
}

.confirm__block {
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.confirm__block-title {
  font-size: 28rpx;
  font-weight: 600;
  margin-bottom: 16rpx;
}

.confirm__address {
  display: flex;
  align-items: center;
  padding-bottom: 16rpx;
}

.confirm__address-main {
  flex: 1;
  min-width: 0;
}

.confirm__address-name {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
}

.confirm__address-detail {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #666666;
  line-height: 1.5;
}

.confirm__arrow {
  padding-left: 16rpx;
  color: #c0c0c0;
  font-size: 40rpx;
}

.confirm__field {
  display: flex;
  align-items: center;
  min-height: 80rpx;
  border-bottom: 1rpx solid #f5f5f5;
}

.confirm__field-label {
  width: 190rpx;
  font-size: 26rpx;
  color: #444444;
}

.confirm__input {
  flex: 1;
  height: 80rpx;
  font-size: 26rpx;
}

.confirm__hint {
  display: block;
  margin-top: 12rpx;
  font-size: 20rpx;
  color: #a26a2a;
  line-height: 1.5;
}

.confirm__shop {
  padding-bottom: 16rpx;
}

.confirm__shop-name {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  margin-bottom: 6rpx;
}

.confirm__line {
  display: flex;
  align-items: flex-start;
  padding: 12rpx 0;
}

.confirm__line-body {
  flex: 1;
  min-width: 0;
}

.confirm__line-name {
  display: block;
  font-size: 26rpx;
}

.confirm__line-spec {
  display: block;
  font-size: 22rpx;
  color: #999999;
}

.confirm__line-addons {
  display: block;
  font-size: 20rpx;
  color: #a26a2a;
}

.confirm__line-qty {
  width: 80rpx;
  text-align: center;
  font-size: 24rpx;
}

.confirm__line-amount {
  min-width: 120rpx;
  text-align: right;
  font-size: 26rpx;
}

.confirm__fee-row {
  padding: 8rpx 0;
  font-size: 26rpx;
}

.confirm__total {
  font-size: 34rpx;
}

.confirm__warning {
  margin-top: 12rpx;
  padding: 12rpx 16rpx;
  border-radius: 8rpx;
  background-color: #fff1f0;
  color: #d4380d;
  font-size: 22rpx;
}

.confirm__eta {
  display: block;
  margin-top: 10rpx;
}

.confirm__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.confirm__bar-amount {
  display: flex;
  align-items: baseline;
}

.confirm__bar-total {
  margin-left: 8rpx;
  font-size: 38rpx;
}

.confirm__submit {
  min-width: 240rpx;
}
</style>
