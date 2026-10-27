<template>
  <view class="asl">
    <view class="asl__notice">
      售后记录接口后端尚未提供，本页不展示任何虚构的退款进度
    </view>

    <view class="se-card asl__empty">
      <view class="asl__icon">售</view>
      <text class="asl__empty-title">暂无售后记录可展示</text>
      <text class="asl__empty-desc">
        当前后端只有「申请退款」写接口，没有售后单列表与详情查询接口，
        因此无法列出历史售后记录。
      </text>
    </view>

    <view class="se-card asl__block">
      <text class="asl__block-title">现在可以这样查看</text>
      <view class="asl__step">
        <text class="asl__step-index">1</text>
        <view class="asl__step-body">
          <text class="asl__step-title">进入订单详情</text>
          <text class="asl__step-text">
            待接单、备餐中的订单可点击「申请退款」，流程见下方退款规则。
          </text>
        </view>
      </view>
      <view class="asl__step">
        <text class="asl__step-index">2</text>
        <view class="asl__step-body">
          <text class="asl__step-title">查看订单的退款字段</text>
          <text class="asl__step-text">
            订单详情接口返回 refundStatus 与 refundedAmount，
            可判断该订单是否已退款、退了多少金额。
          </text>
        </view>
      </view>
      <view class="asl__step">
        <text class="asl__step-index">3</text>
        <view class="asl__step-body">
          <text class="asl__step-title">记录退款单号</text>
          <text class="asl__step-text">
            退款申请成功页会展示 refundNo 与渠道退款号，请先截图保存。
          </text>
        </view>
      </view>
    </view>

    <button class="se-btn se-btn-primary asl__btn" @click="goOrders">去订单列表</button>
    <view class="asl__link" @click="goPolicy">查看退款规则</view>
  </view>
</template>

<script setup lang="ts">
/**
 * 售后记录。
 *
 * 后端现状：只有 POST /api/v1/orders/{orderId}/refund，
 * 没有售后单列表接口（也没有分页查询）。本页不做本地拼装，
 * 因为订单列表 UserOrderView 不含 refundStatus，无法从列表推断哪些订单有售后。
 */

function goOrders(): void {
  uni.switchTab({ url: '/pages/order/List' })
}

function goPolicy(): void {
  uni.navigateTo({ url: '/pages/afterSale/Policy' })
}
</script>

<style scoped>
.asl {
  min-height: 100vh;
  padding: 20rpx 24rpx 60rpx;
  background-color: #f5f5f7;
}

.asl__notice {
  margin-bottom: 16rpx;
  padding: 14rpx 18rpx;
  border-radius: 8rpx;
  background-color: #fff8ef;
  color: #a26a2a;
  font-size: 22rpx;
  line-height: 1.6;
}

.asl__empty {
  padding: 56rpx 32rpx 48rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 16rpx;
}

.asl__icon {
  width: 112rpx;
  height: 112rpx;
  line-height: 112rpx;
  text-align: center;
  border-radius: 50%;
  background-color: #f2f2f4;
  color: #b8b8b8;
  font-size: 44rpx;
  margin-bottom: 20rpx;
}

.asl__empty-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #444444;
  margin-bottom: 12rpx;
}

.asl__empty-desc {
  font-size: 24rpx;
  line-height: 1.8;
  color: #8a8a8a;
  text-align: center;
}

.asl__block {
  padding: 24rpx;
  margin-bottom: 24rpx;
}

.asl__block-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #1f1f1f;
  margin-bottom: 12rpx;
}

.asl__step {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  padding: 14rpx 0;
  border-bottom: 1rpx solid #f5f5f5;
}

.asl__step:last-child {
  border-bottom: none;
}

.asl__step-index {
  width: 40rpx;
  height: 40rpx;
  line-height: 40rpx;
  text-align: center;
  border-radius: 50%;
  background-color: #fff2e6;
  color: #ff7a00;
  font-size: 22rpx;
  flex-shrink: 0;
}

.asl__step-body {
  flex: 1;
  min-width: 0;
}

.asl__step-title {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: #333333;
  margin-bottom: 4rpx;
}

.asl__step-text {
  font-size: 23rpx;
  line-height: 1.7;
  color: #666666;
}

.asl__btn {
  width: 100%;
}

.asl__link {
  padding: 24rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #ff7a00;
}
</style>
