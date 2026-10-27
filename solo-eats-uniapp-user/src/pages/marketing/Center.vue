<template>
  <view class="center">
    <view class="center__banner">
      <text class="center__banner-title">活动中心</text>
      <text class="center__banner-desc">满减、秒杀与红包活动集中在这里，具体可用范围以结算页为准。</text>
    </view>

    <view class="center__cards">
      <view class="se-card center__card" @click="goPromotion">
        <view class="center__icon center__icon--promotion">减</view>
        <view class="center__card-main">
          <text class="center__card-title">满减活动</text>
          <text class="center__card-desc">平台与商家的满减、折扣档位</text>
        </view>
        <text class="center__arrow">›</text>
      </view>

      <view class="se-card center__card" @click="goSeckill">
        <view class="center__icon center__icon--seckill">秒</view>
        <view class="center__card-main">
          <text class="center__card-title">限时秒杀</text>
          <text class="center__card-desc">分场次开抢，库存售完即止</text>
        </view>
        <text class="center__arrow">›</text>
      </view>

      <view class="se-card center__card" @click="goRedPacket">
        <view class="center__icon center__icon--packet">包</view>
        <view class="center__card-main">
          <text class="center__card-title">红包</text>
          <text class="center__card-desc">拼手气红包，每人限领一次</text>
        </view>
        <text class="center__arrow">›</text>
      </view>

      <view class="se-card center__card is-disabled" @click="goCoupon">
        <view class="center__icon center__icon--coupon">券</view>
        <view class="center__card-main">
          <view class="center__card-title-row">
            <text class="center__card-title">优惠券</text>
            <text class="center__badge">待开放</text>
          </view>
          <text class="center__card-desc">后端暂未提供 C 端优惠券查询接口</text>
        </view>
        <text class="center__arrow">›</text>
      </view>
    </view>

    <view class="center__note">
      优惠券入口目前只提供字段与状态说明，不展示用户券数据，避免展示不存在的接口结果。
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 活动中心。
 *
 * 四个入口里，优惠券的 C 端查询接口（/c/coupons）尚不存在，
 * 因此该卡片视觉置灰并带「待开放」徽标，点击只进入说明页，不请求后端。
 */

function goPromotion(): void {
  uni.navigateTo({ url: '/pages/marketing/Promotion' })
}

function goSeckill(): void {
  uni.navigateTo({ url: '/pages/marketing/Seckill' })
}

function goRedPacket(): void {
  uni.navigateTo({ url: '/pages/marketing/RedPacket' })
}

function goCoupon(): void {
  uni.navigateTo({ url: '/pages/marketing/Coupon' })
}
</script>

<style scoped>
.center {
  min-height: 100vh;
  padding: 24rpx;
  background: linear-gradient(180deg, #fff3e6 0%, #f5f5f7 340rpx);
}

.center__banner {
  padding: 32rpx;
  border-radius: 12rpx;
  background: linear-gradient(120deg, #ff8a1f 0%, #ff5c00 100%);
  color: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(255, 92, 0, 0.22);
}

.center__banner-title {
  display: block;
  font-size: 40rpx;
  font-weight: 600;
}

.center__banner-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.6;
  opacity: 0.92;
}

.center__cards {
  margin-top: 20rpx;
}

.center__card {
  display: flex;
  align-items: center;
  padding: 26rpx 24rpx;
  margin-bottom: 16rpx;
}

.center__card.is-disabled .center__card-title {
  color: #8a8a8a;
}

.center__card.is-disabled .center__card-desc {
  color: #a8a8a8;
}

.center__icon {
  width: 88rpx;
  height: 88rpx;
  line-height: 88rpx;
  flex-shrink: 0;
  text-align: center;
  border-radius: 22rpx;
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 600;
}

.center__icon--promotion {
  background: linear-gradient(135deg, #ff9a4c, #ff6a00);
}

.center__icon--seckill {
  background: linear-gradient(135deg, #ff6b6b, #e63a3a);
}

.center__icon--packet {
  background: linear-gradient(135deg, #ffb84c, #f08a00);
}

.center__icon--coupon {
  background: linear-gradient(135deg, #c8c8cc, #a0a0a5);
}

.center__card-main {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.center__card-title-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.center__card-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #1f1f1f;
}

.center__card-desc {
  color: #8a8a8a;
  font-size: 22rpx;
}

.center__badge {
  padding: 2rpx 10rpx;
  border-radius: 6rpx;
  background-color: #f0f0f0;
  color: #8a8a8a;
  font-size: 18rpx;
}

.center__arrow {
  margin-left: 12rpx;
  color: #c0c0c0;
  font-size: 40rpx;
}

.center__note {
  padding: 20rpx 8rpx;
  color: #a0a0a0;
  font-size: 22rpx;
  line-height: 1.6;
}
</style>
