<template>
  <view class="rdetail rd-page">
    <view class="rdetail__head">
      <text class="rdetail__title">评分是怎么算的</text>
      <text class="rdetail__sub">
        骑手端当前只拿到汇总评分与准时率，各项权重与逐单明细后端尚未提供。
      </text>
    </view>

    <view class="rd-card rdetail__card">
      <view v-for="(item, index) in FACTORS" :key="item.key" class="rdetail__factor">
        <view class="rdetail__factor-head">
          <text class="rdetail__factor-index">{{ index + 1 }}</text>
          <text class="rdetail__factor-name">{{ item.name }}</text>
          <text class="rdetail__factor-source">{{ item.source }}</text>
        </view>
        <text class="rdetail__factor-body">{{ item.body }}</text>
        <view v-if="index < FACTORS.length - 1" class="rd-divider rdetail__divider" />
      </view>
    </view>

    <view class="rd-card rdetail__note">
      <text class="rdetail__note-title">明细接口待后端提供</text>
      <text class="rdetail__note-line">
        当前可用的只有 GET /api/v1/rider/stats，返回 rating、onTimeRate、totalOrders、certStatus。
      </text>
      <text class="rdetail__note-line">
        逐单评价、超时次数、完成率分母等明细暂无接口，因此本页不展示具体权重比例，也不推算评分。
      </text>
    </view>

    <view class="rdetail__buttons">
      <button class="rd-btn rd-btn--plain" @click="goBack">返回我的评分</button>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 评分说明。
 * 这里只做静态口径说明：权重数值和逐单明细都没有后端接口，
 * 任何具体百分比都会变成编造，故不展示。
 */

const FACTORS = [
  {
    key: 'onTime',
    name: '准时率',
    source: 't_rider.on_time_rate',
    body: '按承诺送达时间内完成的配送单占比统计，接口返回百分比数值（0~100）。我的评分页直接展示该值，不做换算。',
  },
  {
    key: 'complete',
    name: '订单完成率',
    source: '平台统计任务',
    body: '反映接单后成功送达的比例，取消、改派与异常关闭的订单如何计入分母由平台统计任务定义；骑手端暂未拿到该指标。',
  },
  {
    key: 'review',
    name: '用户评价',
    source: '评价数据',
    body: '用户对配送服务的评分与文字评价会参与综合评分；骑手端暂无逐单评价查询接口，无法在页面罗列。',
  },
]

function goBack(): void {
  uni.navigateBack({
    fail: () => {
      uni.redirectTo({ url: '/pages/rating/Rating' })
    },
  })
}
</script>

<style scoped>
.rdetail {
  padding-bottom: 60rpx;
}

.rdetail__head {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  padding: 32rpx;
  border-radius: 24rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #2c4467, #16233a);
}

.rdetail__title {
  font-size: 36rpx;
  font-weight: 700;
}

.rdetail__sub {
  font-size: 23rpx;
  opacity: 0.82;
  line-height: 1.6;
}

.rdetail__card {
  margin-top: 20rpx;
  padding: 8rpx 24rpx;
}

.rdetail__factor {
  padding: 22rpx 0;
}

.rdetail__factor-head {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.rdetail__factor-index {
  width: 40rpx;
  height: 40rpx;
  line-height: 40rpx;
  border-radius: 50%;
  background-color: #fff1e6;
  color: #e85d04;
  font-size: 22rpx;
  font-weight: 700;
  text-align: center;
}

.rdetail__factor-name {
  flex: 1;
  font-size: 28rpx;
  font-weight: 600;
  color: #16233a;
}

.rdetail__factor-source {
  font-size: 20rpx;
  color: #a9b4c4;
}

.rdetail__factor-body {
  display: block;
  margin-top: 10rpx;
  margin-left: 54rpx;
  font-size: 23rpx;
  color: #7a8699;
  line-height: 1.7;
}

.rdetail__divider {
  margin-top: 20rpx;
}

.rdetail__note {
  margin-top: 20rpx;
  padding: 24rpx;
  background-color: #fffaf3;
}

.rdetail__note-title {
  display: block;
  font-size: 26rpx;
  font-weight: 700;
  color: #a2520a;
}

.rdetail__note-line {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #8a6a45;
  line-height: 1.7;
}

.rdetail__buttons {
  margin-top: 28rpx;
}
</style>
