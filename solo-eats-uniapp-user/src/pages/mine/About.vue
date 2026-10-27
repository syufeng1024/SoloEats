<template>
  <view class="about">
    <view class="about__brand">
      <view class="about__logo">数</view>
      <text class="about__name">数创星光外卖</text>
      <text class="about__slogan">附近好店，送到家门口</text>
      <text class="about__version">v{{ APP_VERSION }}</text>
    </view>

    <view class="se-card about__meta">
      <view class="about__row">
        <text class="about__label">应用名称</text>
        <text class="about__value">{{ APP_NAME }}</text>
      </view>
      <view class="se-divider" />
      <view class="about__row">
        <text class="about__label">版本</text>
        <text class="about__value">v{{ APP_VERSION }}</text>
      </view>
      <view class="se-divider" />
      <view class="about__row" @click="callService">
        <text class="about__label">客服电话</text>
        <text class="about__value about__value--link">{{ SERVICE_PHONE }}（占位）</text>
      </view>
    </view>

    <view class="se-card about__terms">
      <text class="about__title">服务条款</text>
      <text class="about__note">以下为平台服务摘要，正式条款以平台发布的完整文本为准。</text>
      <text v-for="item in TERMS" :key="item.title" class="about__paragraph">
        {{ item.title }}
        {{ item.body }}
      </text>
    </view>

    <view class="about__footer">
      <text>数创星光外卖 · 本地生活点餐平台</text>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 关于页。版本号与 src/manifest.json 的 versionName 保持一致；
 * H5 下 uni.getSystemInfoSync().appVersion 不可靠，这里用固定文案，改版本时两处同步。
 */
const APP_NAME = '数创星光外卖'
const APP_VERSION = '1.0.0'
const SERVICE_PHONE = '400-000-0000'

const TERMS = [
  {
    title: '一、服务内容',
    body: '平台提供附近门店浏览、商品下单、配送到家与到店自取、订单售后与客服工单等服务。具体可用范围以门店营业状态与配送围栏为准。',
  },
  {
    title: '二、订单履约',
    body: '订单提交后按状态机流转：待付款、待接单、备餐中、配送中、已完成或已取消。已支付订单的取消与退款需满足对应状态的限制条件。',
  },
  {
    title: '三、费用',
    body: '商品金额、包装费、配送费与优惠减免以下单页展示为准，金额以分为最小单位计算，展示时换算为元并保留两位小数。',
  },
  {
    title: '四、个人信息',
    body: '手机号、收货地址等敏感信息加密存储，接口返回时脱敏；日志不记录完整手机号。你可以在设置中清除本地缓存。',
  },
  {
    title: '五、联系我们',
    body: '遇到订单、退款、配送或账号问题时，可通过「意见反馈」或「我的工单」提交，平台客服会在工作时间内处理。',
  },
]

function callService(): void {
  // 号码为占位，不直接拨号，避免用户拨打空号
  uni.showToast({ title: '客服电话待开通，请通过意见反馈联系我们', icon: 'none' })
}
</script>

<style scoped>
.about {
  min-height: 100vh;
  padding: 24rpx;
  background: linear-gradient(180deg, #fff3e6 0%, #f5f5f7 360rpx);
}

.about__brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56rpx 0 40rpx;
}

.about__logo {
  width: 128rpx;
  height: 128rpx;
  line-height: 128rpx;
  text-align: center;
  border-radius: 32rpx;
  background: linear-gradient(135deg, #ffa24c, #ff7a00);
  color: #ffffff;
  font-size: 60rpx;
  font-weight: 600;
  box-shadow: 0 8rpx 20rpx rgba(255, 122, 0, 0.28);
}

.about__name {
  margin-top: 24rpx;
  font-size: 38rpx;
  font-weight: 600;
  letter-spacing: 2rpx;
}

.about__slogan {
  margin-top: 8rpx;
  color: #8a8a8a;
  font-size: 24rpx;
}

.about__version {
  margin-top: 12rpx;
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
  background-color: #fff2e6;
  color: #ff7a00;
  font-size: 22rpx;
}

.about__meta {
  padding: 0 28rpx;
}

.about__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 92rpx;
}

.about__label {
  color: #444444;
  font-size: 28rpx;
}

.about__value {
  color: #8a8a8a;
  font-size: 26rpx;
  text-align: right;
}

.about__value--link {
  color: #ff7a00;
}

.about__terms {
  margin-top: 20rpx;
  padding: 28rpx;
}

.about__title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
}

.about__note {
  display: block;
  margin-top: 10rpx;
  color: #a0a0a0;
  font-size: 22rpx;
  line-height: 1.6;
}

.about__paragraph {
  display: block;
  margin-top: 22rpx;
  color: #444444;
  font-size: 26rpx;
  line-height: 1.75;
}

.about__footer {
  padding: 40rpx 0;
  text-align: center;
  color: #b0b0b0;
  font-size: 20rpx;
}
</style>
