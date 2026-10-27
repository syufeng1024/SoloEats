<template>
  <view class="shift rd-page">
    <view class="shift__hero">
      <text class="shift__hero-tag">功能说明</text>
      <text class="shift__hero-title">排班功能待后端提供</text>
      <text class="shift__hero-sub">
        当前骑手端为自由接单制：上线即可听单，没有固定班次，也不需要提前排班。
      </text>
    </view>

    <view class="rd-card shift__block">
      <text class="rd-subtitle">为什么没有排班表</text>
      <text class="shift__line">
        后端尚未提供班次、排班或签到接口，也没有对应的数据表。前端不生成模拟班次，避免与平台实际规则冲突。
      </text>
      <text class="shift__line">
        骑手的工作时段以上下线记录为准：每次上线开始计入在线时长，下线即停止听单。
      </text>
    </view>

    <view class="rd-card shift__block">
      <text class="rd-subtitle">你可以在这里查看实际在线记录</text>
      <view class="shift__link" @click="goCalendar">
        <view class="shift__link-main">
          <text class="shift__link-name">考勤日历</text>
          <text class="shift__link-desc">按月查看每日在线时长与上线次数</text>
        </view>
        <text class="shift__arrow">›</text>
      </view>
      <view class="rd-divider" />
      <view class="shift__link" @click="goAttendance">
        <view class="shift__link-main">
          <text class="shift__link-name">考勤明细</text>
          <text class="shift__link-desc">按日期区间筛选逐日记录</text>
        </view>
        <text class="shift__arrow">›</text>
      </view>
    </view>

    <view class="shift__note">
      在线时长按首末上线时间估算，仅用于个人查看，不作为结算依据。
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 排班页：后端无排班接口、无排班表，故不伪造班次数据。
 * 页面只解释自由接单规则，并引导到已有考勤数据。
 */

function goCalendar(): void {
  uni.navigateTo({ url: '/pages/schedule/Calendar' })
}

function goAttendance(): void {
  uni.navigateTo({ url: '/pages/schedule/Attendance' })
}
</script>

<style scoped>
.shift {
  padding-bottom: 60rpx;
}

.shift__hero {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding: 36rpx 32rpx;
  border-radius: 24rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #2c4467, #16233a);
}

.shift__hero-tag {
  align-self: flex-start;
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  background-color: rgba(255, 255, 255, 0.18);
  font-size: 20rpx;
}

.shift__hero-title {
  font-size: 38rpx;
  font-weight: 700;
}

.shift__hero-sub {
  font-size: 24rpx;
  opacity: 0.85;
  line-height: 1.7;
}

.shift__block {
  margin-top: 20rpx;
  padding: 24rpx;
}

.shift__line {
  display: block;
  margin-top: 12rpx;
  font-size: 23rpx;
  color: #7a8699;
  line-height: 1.8;
}

.shift__link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  min-height: 108rpx;
}

.shift__link-main {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.shift__link-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #16233a;
}

.shift__link-desc {
  font-size: 21rpx;
  color: #8d99aa;
}

.shift__arrow {
  font-size: 34rpx;
  color: #c3ccd9;
}

.shift__note {
  margin-top: 24rpx;
  text-align: center;
  font-size: 21rpx;
  color: #a9b4c4;
  line-height: 1.7;
}
</style>
