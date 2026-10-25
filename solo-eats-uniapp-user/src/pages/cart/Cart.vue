<template>
  <view class="se-page cart">
    <view v-if="loadingShop" class="se-loading">正在加载购物车…</view>

    <template v-else>
      <view v-if="lines.length === 0" class="se-empty">
        <text class="se-empty__title">购物车还是空的</text>
        <text>去门店挑点想吃的吧</text>
        <view class="se-error__retry cart__go" @click="goHome">去逛逛</view>
      </view>

      <template v-else>
        <view class="se-card cart__head">
          <view class="se-between">
            <text class="cart__shop">{{ shop ? shop.shopName : `门店 ${shopId}` }}</text>
            <text class="cart__clear" @click="clearCart">清空</text>
          </view>
          <text class="se-muted">共 {{ totalCount }} 件商品</text>
        </view>

        <view class="se-card cart__list">
          <view v-for="line in lines" :key="lineKey(line)" class="cart__line">
            <view class="cart__line-img">
              <image v-if="line.imageUrl" class="cart__image" :src="line.imageUrl" mode="aspectFill" />
              <text v-else class="cart__letter">{{ line.skuName.slice(0, 1) }}</text>
            </view>

            <view class="cart__line-body">
              <text class="cart__name">{{ line.skuName }}</text>
              <text v-if="cartSpecText(line)" class="cart__spec">{{ cartSpecText(line) }}</text>
              <text v-if="line.addons.length > 0" class="cart__addons">
                加料：{{ line.addons.map((addon) => addon.itemName).join('、') }}
              </text>

              <view class="cart__line-foot">
                <text class="se-price cart__price">{{ fenToYuan(line.price) }}</text>
                <view class="se-stepper">
                  <view class="se-stepper__btn" @click="decrease(line)">−</view>
                  <text class="se-stepper__value">{{ line.qty }}</text>
                  <view class="se-stepper__btn" @click="increase(line)">+</view>
                </view>
              </view>
            </view>

            <text class="cart__remove" @click="removeLine(line)">删除</text>
          </view>
        </view>

        <view class="se-card cart__summary">
          <view class="se-between cart__summary-row">
            <text class="se-label">商品小计</text>
            <text>¥{{ fenToYuan(totalAmount) }}</text>
          </view>
          <view v-if="packagingFee > 0" class="se-between cart__summary-row">
            <text class="se-label">打包费</text>
            <text>¥{{ fenToYuan(packagingFee) }}</text>
          </view>
          <view class="se-divider" />
          <view class="se-between cart__summary-row">
            <text class="se-strong">合计（不含配送费）</text>
            <text class="se-price cart__total">{{ fenToYuan(totalAmount + packagingFee) }}</text>
          </view>
          <text v-if="hasAddons" class="cart__note">
            本单含加料，后端下单接口只按 SKU 计价，加料金额不计入实付。
          </text>
        </view>

        <view class="se-fixed-bottom cart__bar">
          <view class="cart__bar-amount">
            <text class="se-label">合计</text>
            <text class="se-price cart__bar-total">{{ fenToYuan(totalAmount + packagingFee) }}</text>
          </view>
          <button class="se-btn se-btn-primary cart__checkout" @click="goCheckout">去结算</button>
        </view>
      </template>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchShopDetail } from '@/api/shop'
import type { ShopDetailVO } from '@/api/types'
import { fenToYuan } from '@/utils/money'
import { cartLineKey, cartSpecText, useCartStore, type CartLine } from '@/stores/cart'

const cart = useCartStore()

const shopId = ref(0)
const shop = ref<ShopDetailVO | null>(null)
const loadingShop = ref(true)

const lines = computed<CartLine[]>(() => cart.lines(shopId.value))
const totalCount = computed(() => cart.totalCount(shopId.value))
const totalAmount = computed(() => cart.totalAmount(shopId.value))
const packagingFee = computed(() => shop.value?.packagingFee || 0)
const hasAddons = computed(() => cart.hasAddons(shopId.value))

onLoad(async (options) => {
  const id = Number(options?.shopId)
  if (Number.isFinite(id) && id > 0) {
    shopId.value = id
  }
  if (!shopId.value) {
    loadingShop.value = false
    return
  }
  // 先在 onLoad 里同步载入本地购物车，computed 只做只读聚合
  cart.load(shopId.value)
  try {
    shop.value = await fetchShopDetail(shopId.value)
  } catch (err) {
    // 门店信息失败不影响购物车增删，只是少显示店名与打包费
  } finally {
    loadingShop.value = false
  }
})

function lineKey(line: CartLine): string {
  return cartLineKey(line)
}

function increase(line: CartLine): void {
  cart.inc(shopId.value, cartLineKey(line))
}

function decrease(line: CartLine): void {
  cart.dec(shopId.value, cartLineKey(line))
}

function removeLine(line: CartLine): void {
  uni.showModal({
    title: '移出购物车',
    content: `确认删除「${line.skuName}」？`,
    success: (result) => {
      if (result.confirm) {
        cart.remove(shopId.value, cartLineKey(line))
      }
    },
  })
}

function clearCart(): void {
  uni.showModal({
    title: '清空购物车',
    content: '清空后需重新选择商品，确认继续？',
    success: (result) => {
      if (result.confirm) {
        cart.clear(shopId.value)
      }
    },
  })
}

function goCheckout(): void {
  if (lines.value.length === 0) {
    uni.showToast({ title: '购物车是空的', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/order/Confirm?shopId=${shopId.value}` })
}

function goHome(): void {
  uni.switchTab({ url: '/pages/index/Index' })
}
</script>

<style scoped>
.cart {
  padding-bottom: 200rpx;
}

.cart__go {
  margin-top: 24rpx;
}

.cart__head {
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.cart__shop {
  font-size: 30rpx;
  font-weight: 600;
}

.cart__clear {
  font-size: 24rpx;
  color: #999999;
}

.cart__list {
  padding: 0 24rpx;
}

.cart__line {
  display: flex;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f5f5f5;
}

.cart__line:last-child {
  border-bottom: none;
}

.cart__line-img {
  width: 120rpx;
  height: 120rpx;
  border-radius: 8rpx;
  overflow: hidden;
  background-color: #fff2e6;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.cart__image {
  width: 120rpx;
  height: 120rpx;
}

.cart__letter {
  color: #ff7a00;
  font-size: 40rpx;
}

.cart__line-body {
  flex: 1;
  min-width: 0;
  margin-left: 16rpx;
}

.cart__name {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
}

.cart__spec {
  display: block;
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #8a8a8a;
}

.cart__addons {
  display: block;
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #a26a2a;
}

.cart__line-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12rpx;
}

.cart__price {
  font-size: 28rpx;
}

.cart__remove {
  align-self: flex-start;
  padding-left: 16rpx;
  font-size: 22rpx;
  color: #b0b0b0;
}

.cart__summary {
  margin-top: 16rpx;
  padding: 24rpx;
}

.cart__summary-row {
  padding: 10rpx 0;
  font-size: 26rpx;
}

.cart__total {
  font-size: 32rpx;
}

.cart__note {
  display: block;
  margin-top: 12rpx;
  padding: 12rpx 16rpx;
  border-radius: 8rpx;
  background-color: #fff8ef;
  font-size: 20rpx;
  color: #a26a2a;
}

.cart__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.cart__bar-amount {
  display: flex;
  align-items: baseline;
}

.cart__bar-total {
  margin-left: 8rpx;
  font-size: 38rpx;
}

.cart__checkout {
  min-width: 240rpx;
}
</style>
