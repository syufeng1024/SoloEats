<template>
  <view class="splash">
    <view class="splash__brand">
      <view class="splash__logo">数</view>
      <text class="splash__name">数创星光外卖</text>
      <text class="splash__slogan">附近好店，送到家门口</text>
    </view>

    <view class="splash__skip" @click="enter">跳过 {{ seconds }}s</view>

    <view class="splash__footer">
      <text class="splash__footer-text">数创星光 · 本地生活点餐平台</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

/**
 * 启动页：pages.json 的第一个页面，冷启动时展示 3 秒后进入首页。
 *
 * 「只显示一次」的口径：同一次启动内 H5 可能因热更新/页面重挂载再次触发 onLoad，
 * 这里用本地存储记录展示时间，10 秒内重复进入直接跳首页；超过 10 秒视为新的冷启动。
 * 不使用永久标记位——否则清除浏览器缓存前用户再也看不到开屏。
 */

const SPLASH_STORAGE_KEY = 'splash:shownAt'
const HOME_PAGE = '/pages/index/Index'
const HOLD_SECONDS = 3
const REPEAT_WINDOW_MS = 10_000

const seconds = ref(HOLD_SECONDS)

let timer: ReturnType<typeof setInterval> | null = null
let entered = false

function stopTimer(): void {
  if (timer !== null) {
    clearInterval(timer)
    timer = null
  }
}

/** 只允许跳转一次：倒计时结束与手动「跳过」同时触发时不重复 switchTab */
function enter(): void {
  if (entered) {
    return
  }
  entered = true
  stopTimer()
  uni.switchTab({ url: HOME_PAGE })
}

function startCountdown(): void {
  stopTimer()
  timer = setInterval(() => {
    seconds.value -= 1
    if (seconds.value <= 0) {
      enter()
    }
  }, 1000)
}

onLoad(() => {
  const lastShownAt = Number(uni.getStorageSync(SPLASH_STORAGE_KEY))
  if (Number.isFinite(lastShownAt) && lastShownAt > 0 && Date.now() - lastShownAt < REPEAT_WINDOW_MS) {
    enter()
    return
  }
  uni.setStorageSync(SPLASH_STORAGE_KEY, Date.now())
  startCountdown()
})

onUnmounted(() => {
  stopTimer()
})
</script>

<style scoped>
.splash {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #ff8a1f 0%, #ff5c00 58%, #e94e00 100%);
  color: #ffffff;
}

.splash__brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: -80rpx;
}

.splash__logo {
  width: 168rpx;
  height: 168rpx;
  line-height: 168rpx;
  text-align: center;
  border-radius: 40rpx;
  background-color: #ffffff;
  color: #ff7a00;
  font-size: 88rpx;
  font-weight: 600;
  box-shadow: 0 12rpx 32rpx rgba(120, 40, 0, 0.28);
}

.splash__name {
  margin-top: 36rpx;
  font-size: 48rpx;
  font-weight: 600;
  letter-spacing: 4rpx;
}

.splash__slogan {
  margin-top: 14rpx;
  font-size: 26rpx;
  opacity: 0.88;
  letter-spacing: 1rpx;
}

.splash__skip {
  position: absolute;
  top: calc(32rpx + env(safe-area-inset-top));
  right: 28rpx;
  padding: 10rpx 24rpx;
  border-radius: 28rpx;
  background-color: rgba(0, 0, 0, 0.18);
  font-size: 24rpx;
  color: #ffffff;
}

.splash__footer {
  position: absolute;
  bottom: calc(56rpx + env(safe-area-inset-bottom));
  width: 100%;
  text-align: center;
}

.splash__footer-text {
  font-size: 22rpx;
  opacity: 0.72;
}
</style>
