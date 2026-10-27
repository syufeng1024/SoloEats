<template>
  <view class="shop">
    <view v-if="loading" class="se-loading">正在加载门店…</view>

    <view v-else-if="error" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="load">重新加载</view>
    </view>

    <template v-else-if="shop">
      <view class="shop__hero">
        <view class="shop__hero-top">
          <view class="shop__logo">
            <image v-if="shop.logoUrl" class="shop__logo-img" :src="shop.logoUrl" mode="aspectFill" />
            <text v-else class="shop__logo-letter">{{ shop.shopName.slice(0, 1) }}</text>
          </view>
          <view class="shop__hero-copy">
            <text class="shop__name">{{ shop.shopName }}</text>
            <view class="shop__meta">
              <text class="shop__rating">{{ shop.rating ? Number(shop.rating).toFixed(1) : '--' }} 分</text>
              <text class="se-muted">月售 {{ shop.monthSales || 0 }}</text>
              <text class="se-muted">约 {{ shop.prepMinutes || 30 }} 分钟</text>
            </view>
            <view class="shop__meta">
              <text class="se-muted">起送 ¥{{ fenToYuan(shop.minOrderAmount) }}</text>
              <text class="se-muted">配送 ¥{{ fenToYuan(shop.deliveryFee) }}</text>
              <text v-if="shop.packagingFee" class="se-muted">打包 ¥{{ fenToYuan(shop.packagingFee) }}</text>
            </view>
          </view>
        </view>
        <text v-if="shop.address" class="shop__address">{{ shop.address }}</text>
        <text v-if="shop.announcement" class="shop__notice">{{ shop.announcement }}</text>
        <view class="shop__tags">
          <text v-if="shop.pickupEnabled === 1" class="se-tag">到店自取</text>
          <text class="se-tag">{{ shop.statusText || '营业中' }}</text>
        </view>
        <view class="shop__links">
          <text class="shop__link" @click="goShopInfo">店铺信息</text>
          <text class="shop__link-sep">|</text>
          <text class="shop__link" @click="goShopReviews">店铺评价</text>
        </view>
      </view>

      <view class="shop__body">
        <scroll-view class="shop__cats" scroll-y>
          <view
            v-for="(category, index) in menu"
            :key="category.categoryId"
            class="shop__cat"
            :class="{ 'is-active': index === activeCategory }"
            @click="selectCategory(index)"
          >
            {{ category.categoryName }}
          </view>
          <view v-if="menu.length === 0" class="shop__cat-empty">暂无分类</view>
        </scroll-view>

        <scroll-view
          class="shop__goods"
          scroll-y
          :scroll-into-view="scrollInto"
          :scroll-with-animation="true"
        >
          <view v-if="menu.length === 0" class="se-empty">
            <text class="se-empty__title">门店暂未上架商品</text>
            <text>换一家店看看</text>
          </view>

          <view
            v-for="(category, index) in menu"
            :id="`cat-block-${index}`"
            :key="category.categoryId"
            class="shop__block"
          >
            <view class="shop__block-title">{{ category.categoryName }}</view>

            <view
              v-for="item in category.items"
              :key="item.spuId"
              class="shop__item"
              :class="{ 'is-soldout': item.soldOut }"
            >
              <view class="shop__item-img">
                <image v-if="item.mainImage" class="shop__item-image" :src="item.mainImage" mode="aspectFill" />
                <text v-else class="shop__item-letter">{{ item.spuName.slice(0, 1) }}</text>
              </view>

              <view class="shop__item-body">
                <text class="shop__item-name">{{ item.spuName }}</text>
                <text v-if="item.subtitle" class="shop__item-sub">{{ item.subtitle }}</text>
                <view class="shop__item-tags">
                  <text v-for="tag in item.tags || []" :key="tag" class="se-tag">{{ tag }}</text>
                  <text class="se-muted">月售 {{ item.monthSales || 0 }}</text>
                </view>
                <view class="shop__item-foot">
                  <view>
                    <text class="se-price shop__item-price">{{ fenToYuan(item.minPrice) }}</text>
                    <text v-if="item.minPurchaseQty && item.minPurchaseQty > 1" class="se-muted">
                      · {{ item.minPurchaseQty }} 份起购
                    </text>
                  </view>
                  <view
                    class="shop__add"
                    :class="{ 'is-disabled': item.soldOut || (item.skus && item.skus.length === 0) }"
                    @click="openSku(item)"
                  >
                    {{ item.soldOut ? '已售完' : '+' }}
                  </view>
                </view>
              </view>
            </view>
          </view>

          <view v-if="menu.length > 0" class="shop__end">— 已经到底了 —</view>
        </scroll-view>
      </view>

      <view class="se-fixed-bottom shop__bar">
        <view class="shop__cart" @click="goCart">
          <text class="shop__cart-icon">🛒</text>
          <text v-if="cartCount > 0" class="shop__cart-badge">{{ cartCount > 99 ? '99+' : cartCount }}</text>
          <text class="shop__cart-amount">¥{{ fenToYuan(cartAmount) }}</text>
        </view>
        <button
          class="se-btn se-btn-primary shop__checkout"
          :disabled="!canCheckout"
          @click="goCheckout"
        >
          {{ checkoutText }}
        </button>
      </view>

      <SkuPopup v-model:visible="popupVisible" :item="activeItem" @confirm="onSkuConfirm" />
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import SkuPopup from '@/pages/shop/SkuPopup.vue'
import { fetchShopDetail, fetchShopMenu } from '@/api/shop'
import type { ShopDetailVO, ShopMenuVO, SpuVO } from '@/api/types'
import { fenToYuan } from '@/utils/money'
import { useCartStore, type CartLine } from '@/stores/cart'

const cart = useCartStore()

const shopId = ref(0)
const shop = ref<ShopDetailVO | null>(null)
const menu = ref<ShopMenuVO[]>([])
const loading = ref(true)
const error = ref('')
const activeCategory = ref(0)
const scrollInto = ref('')
const popupVisible = ref(false)
const activeItem = ref<SpuVO | null>(null)

const cartCount = computed(() => cart.totalCount(shopId.value))
const cartAmount = computed(() => cart.totalAmount(shopId.value))
const minOrder = computed(() => shop.value?.minOrderAmount || 0)
const canCheckout = computed(() => cartCount.value > 0 && cartAmount.value >= minOrder.value)
const checkoutText = computed(() => {
  if (cartCount.value === 0) {
    return '请先选购'
  }
  if (cartAmount.value < minOrder.value) {
    return `差 ¥${fenToYuan(minOrder.value - cartAmount.value)} 起送`
  }
  return '去结算'
})

onLoad((options) => {
  const id = Number(options?.id)
  if (!Number.isFinite(id) || id <= 0) {
    loading.value = false
    error.value = '缺少门店参数'
    return
  }
  shopId.value = id
  cart.load(id)
  load()
})

async function load(): Promise<void> {
  if (!shopId.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const [detail, categories] = await Promise.all([
      fetchShopDetail(shopId.value),
      fetchShopMenu(shopId.value),
    ])
    shop.value = detail
    // 空分类不渲染，避免左侧出现点不开的目录项
    menu.value = categories.filter((category) => (category.items?.length || 0) > 0)
    activeCategory.value = 0
  } catch (err) {
    error.value = (err as Error)?.message || '门店加载失败'
  } finally {
    loading.value = false
  }
}

/**
 * 分类 → 右侧滚动。反向联动（滚动时高亮左侧）需要量取每个分类块的
 * 真实偏移，H5 与小程序实现差异大，本期只做「点击分类滚动到位」。
 */
function selectCategory(index: number): void {
  activeCategory.value = index
  scrollInto.value = ''
  // 下一帧再赋值，保证同一个分类连续点击也能触发滚动
  setTimeout(() => {
    scrollInto.value = `cat-block-${index}`
  }, 0)
}

function openSku(item: SpuVO): void {
  if (item.soldOut) {
    uni.showToast({ title: '该商品已售完', icon: 'none' })
    return
  }
  if (!item.skus || item.skus.length === 0) {
    uni.showToast({ title: '该商品暂无可售规格', icon: 'none' })
    return
  }
  activeItem.value = item
  popupVisible.value = true
}

function onSkuConfirm(line: CartLine): void {
  cart.add(shopId.value, line)
  uni.showToast({ title: '已加入购物车', icon: 'none' })
}

function goShopInfo(): void {
  uni.navigateTo({ url: `/pages/shop/ShopInfo?id=${shopId.value}` })
}

function goShopReviews(): void {
  const name = shop.value?.shopName || ''
  uni.navigateTo({
    url: `/pages/shop/ShopReview?shopId=${shopId.value}&shopName=${encodeURIComponent(name)}`,
  })
}

function goCart(): void {
  if (cartCount.value === 0) {
    uni.showToast({ title: '购物车还是空的', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/cart/Cart?shopId=${shopId.value}` })
}

function goCheckout(): void {
  if (cartCount.value === 0) {
    uni.showToast({ title: '请先选购商品', icon: 'none' })
    return
  }
  if (!canCheckout.value) {
    uni.showToast({ title: `还差 ¥${fenToYuan(minOrder.value - cartAmount.value)} 起送`, icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/cart/Cart?shopId=${shopId.value}` })
}
</script>

<style scoped>
.shop {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #f5f5f7;
}

.shop__hero {
  margin: 20rpx 20rpx 0;
  padding: 24rpx;
  border-radius: 8rpx;
  background: linear-gradient(160deg, #fff6ec, #ffffff 70%);
  box-shadow: 0 2rpx 8rpx rgba(20, 20, 20, 0.04);
}

.shop__hero-top {
  display: flex;
}

.shop__logo {
  width: 112rpx;
  height: 112rpx;
  border-radius: 8rpx;
  overflow: hidden;
  background-color: #fff2e6;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.shop__logo-img {
  width: 112rpx;
  height: 112rpx;
}

.shop__logo-letter {
  color: #ff7a00;
  font-size: 44rpx;
  font-weight: 600;
}

.shop__hero-copy {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
}

.shop__name {
  display: block;
  font-size: 34rpx;
  font-weight: 600;
}

.shop__meta {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 4rpx;
}

.shop__rating {
  color: #ff7a00;
  font-size: 24rpx;
  font-weight: 600;
}

.shop__address {
  display: block;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #8a8a8a;
}

.shop__notice {
  display: block;
  margin-top: 8rpx;
  padding: 12rpx 16rpx;
  border-radius: 8rpx;
  background-color: #fff8ef;
  font-size: 22rpx;
  color: #a26a2a;
}

.shop__tags {
  margin-top: 12rpx;
}

.shop__links {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 10rpx;
}

.shop__link {
  font-size: 22rpx;
  color: #ff7a00;
}

.shop__link-sep {
  font-size: 20rpx;
  color: #d0d0d0;
}

.shop__body {
  flex: 1;
  display: flex;
  min-height: 0;
  margin-top: 16rpx;
  padding-bottom: 140rpx;
}

.shop__cats {
  width: 176rpx;
  height: 100%;
  background-color: #f0f0f2;
}

.shop__cat {
  padding: 26rpx 16rpx;
  font-size: 24rpx;
  color: #555555;
  text-align: center;
}

.shop__cat.is-active {
  background-color: #ffffff;
  color: #ff7a00;
  font-weight: 600;
  border-left: 6rpx solid #ff7a00;
}

.shop__cat-empty {
  padding: 40rpx 0;
  text-align: center;
  color: #999999;
  font-size: 22rpx;
}

.shop__goods {
  flex: 1;
  height: 100%;
  background-color: #ffffff;
}

.shop__block-title {
  padding: 20rpx 20rpx 12rpx;
  font-size: 24rpx;
  color: #8a8a8a;
}

.shop__item {
  display: flex;
  padding: 20rpx;
  border-bottom: 1rpx solid #f6f6f6;
}

.shop__item.is-soldout {
  opacity: 0.5;
}

.shop__item-img {
  width: 140rpx;
  height: 140rpx;
  border-radius: 8rpx;
  overflow: hidden;
  background-color: #fff2e6;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.shop__item-image {
  width: 140rpx;
  height: 140rpx;
}

.shop__item-letter {
  color: #ff7a00;
  font-size: 44rpx;
}

.shop__item-body {
  flex: 1;
  min-width: 0;
  margin-left: 16rpx;
  display: flex;
  flex-direction: column;
}

.shop__item-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #1f1f1f;
}

.shop__item-sub {
  margin-top: 2rpx;
  font-size: 22rpx;
  color: #8a8a8a;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.shop__item-tags {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-top: 6rpx;
  flex-wrap: wrap;
}

.shop__item-foot {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 8rpx;
}

.shop__item-price {
  font-size: 30rpx;
}

.shop__add {
  width: 52rpx;
  height: 52rpx;
  line-height: 50rpx;
  text-align: center;
  border-radius: 50%;
  background: linear-gradient(135deg, #ffa24c, #ff7a00);
  color: #ffffff;
  font-size: 34rpx;
}

.shop__add.is-disabled {
  background: #e0e0e0;
  font-size: 20rpx;
  line-height: 50rpx;
}

.shop__end {
  padding: 32rpx 0 48rpx;
  text-align: center;
  font-size: 20rpx;
  color: #c0c0c0;
}

.shop__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.shop__cart {
  display: flex;
  align-items: center;
  position: relative;
}

.shop__cart-icon {
  font-size: 40rpx;
}

.shop__cart-badge {
  position: absolute;
  top: -8rpx;
  left: 28rpx;
  min-width: 30rpx;
  height: 30rpx;
  line-height: 30rpx;
  padding: 0 6rpx;
  border-radius: 15rpx;
  background-color: #ff4d00;
  color: #ffffff;
  font-size: 18rpx;
  text-align: center;
}

.shop__cart-amount {
  margin-left: 16rpx;
  font-size: 32rpx;
  font-weight: 600;
  color: #ff4d00;
}

.shop__checkout {
  min-width: 240rpx;
}
</style>
