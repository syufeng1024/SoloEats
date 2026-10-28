<script>
import { useAuthStore } from '@/stores/auth'

export default {
  onLaunch() {
    // http 层拿到 10003 时清理 storage 并广播；这里同步清空内存登录态，
    // 否则 reLaunch 到登录页后其他页面仍会显示旧昵称。
    uni.$on('auth:expired', () => {
      useAuthStore().clearLocal()
    })
  },
  onShow() {},
  onHide() {},
}
</script>

<style>
/* ===== 全局主题：品牌橙金 #FF7A00，小圆角 8rpx，白底轻阴影 ===== */
/* H5 下 uni-app 没有 page 标签，body 一并声明，避免页面底色露白 */
page,
body {
  background-color: #f5f5f7;
  color: #1f1f1f;
  font-size: 26rpx;
  line-height: 1.5;
  font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue', 'PingFang SC', 'Microsoft YaHei',
    sans-serif;
}

view,
text,
input,
textarea,
button,
scroll-view {
  box-sizing: border-box;
}

.se-page {
  min-height: 100vh;
  padding: 24rpx;
  /*
   * H5 下 tabBar 是独立于页面内容的 fixed 节点（50px + 安全区），不占文档流，
   * 页面最后一段内容会被它压住。--window-bottom 在 H5 tab 页等于 tabBar 高度，
   * 非 tab 页与小程序端为 0，用它做底部留白，跨端一行搞定且不改上/左右内边距。
   */
  padding-bottom: calc(24rpx + var(--window-bottom, 0px));
}

.se-card {
  background-color: #ffffff;
  border-radius: 8rpx;
  box-shadow: 0 2rpx 8rpx rgba(20, 20, 20, 0.04);
}

.se-row {
  display: flex;
  align-items: center;
}

.se-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.se-price {
  color: #ff4d00;
  font-weight: 600;
}

.se-price::before {
  content: '¥';
  font-size: 0.78em;
  margin-right: 2rpx;
  font-weight: 500;
}

.se-muted {
  color: #8a8a8a;
  font-size: 24rpx;
}

.se-label {
  color: #8a8a8a;
  font-size: 24rpx;
}

.se-strong {
  font-weight: 600;
}

.se-tag {
  display: inline-block;
  padding: 2rpx 10rpx;
  margin-right: 8rpx;
  border-radius: 4rpx;
  font-size: 20rpx;
  color: #ff7a00;
  background-color: #fff2e6;
}

.se-divider {
  height: 1rpx;
  background-color: #f0f0f0;
}

.se-btn {
  height: 80rpx;
  line-height: 80rpx;
  padding: 0 32rpx;
  border-radius: 8rpx;
  text-align: center;
  font-size: 28rpx;
  font-weight: 500;
  border: none;
}

.se-btn::after {
  border: none;
}

.se-btn-primary {
  background: linear-gradient(135deg, #ffa24c, #ff7a00);
  color: #ffffff;
}

.se-btn-ghost {
  background-color: #ffffff;
  color: #ff7a00;
  border: 1rpx solid #ff7a00;
}

.se-btn-plain {
  background-color: #f2f2f4;
  color: #444444;
}

.se-btn[disabled],
.se-btn.is-disabled {
  opacity: 0.45;
}

.se-loading {
  padding: 80rpx 0;
  text-align: center;
  color: #8a8a8a;
  font-size: 24rpx;
}

.se-empty {
  padding: 120rpx 40rpx;
  text-align: center;
  color: #8a8a8a;
  font-size: 26rpx;
}

.se-empty__title {
  display: block;
  margin-bottom: 12rpx;
  font-size: 30rpx;
  color: #444444;
}

.se-error {
  padding: 80rpx 40rpx;
  text-align: center;
  color: #8a8a8a;
  font-size: 26rpx;
}

.se-error__retry {
  display: inline-block;
  margin-top: 20rpx;
  padding: 10rpx 32rpx;
  border: 1rpx solid #ff7a00;
  border-radius: 8rpx;
  color: #ff7a00;
  font-size: 24rpx;
}

.se-stepper {
  display: flex;
  align-items: center;
}

.se-stepper__btn {
  width: 48rpx;
  height: 48rpx;
  line-height: 44rpx;
  text-align: center;
  border-radius: 50%;
  font-size: 32rpx;
  color: #ff7a00;
  border: 1rpx solid #ffcfa6;
  background-color: #ffffff;
}

.se-stepper__btn.is-disabled {
  color: #cccccc;
  border-color: #eeeeee;
}

.se-stepper__value {
  min-width: 56rpx;
  text-align: center;
  font-size: 26rpx;
}

.se-fixed-bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  background-color: #ffffff;
  box-shadow: 0 -2rpx 10rpx rgba(20, 20, 20, 0.05);
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
}

/*
 * ===== H5 + PC 宽屏：内容收成「手机壳」宽度并居中 =====
 * 问题：H5 下页面内容没有宽度上限，1265px 屏上卡片/输入框会被拉满整屏，不适合演示。
 * 做法：条件编译 + 媒体查询双重限定，小程序端样式完全不变。
 * 注意：uni-h5 的 tabBar（<uni-tabbar class="uni-tabbar-bottom"><div class="uni-tabbar">）、
 *      原生导航栏、页面内 fixed 底栏都是独立于页面内容的 fixed 节点，
 *      必须和内容一起限宽居中，否则会出现「内容窄、导航条撑满」的错位。
 * 壳宽：改 --se-shell-width 一处即可。默认 420px —— H5 在窗口 >960px 时会把 rpx 基准
 *      降到 375px（html font-size 固定 16px），壳宽取 375~430 才与内容同一比例；
 *      取 750px 会让内容只剩半密度、字很小，看上去仍是「被拉伸」。
 */
/* #ifdef H5 */
html {
  --se-shell-width: 420px;
}

@media (min-width: 1024px) {
  /* 壳外区域：中性底色铺满 */
  body {
    background-color: #e8ebef;
  }

  /* 页面内容列：限宽居中 */
  uni-page-wrapper {
    width: 100%;
    max-width: var(--se-shell-width);
    margin-left: auto;
    margin-right: auto;
  }

  /* 壳内保持原页面底色。uni-h5 默认把 uni-page-body 置为透明，这里显式补回；
     min-height 让短页面也铺满一屏，长页面随内容一起长高，避免出现「半截壳」。 */
  uni-page[data-type] uni-page-body {
    min-height: 100%;
    background-color: #f5f5f7;
    box-shadow:
      0 0 0 1px rgba(17, 24, 39, 0.05),
      0 12px 32px rgba(17, 24, 39, 0.08);
  }

  /* 原生导航栏（H5 下为 fixed）跟随壳宽 */
  .uni-page-head {
    left: 0;
    right: 0;
    max-width: var(--se-shell-width);
    margin-left: auto;
    margin-right: auto;
  }

  /* 底部 tabBar：与内容同宽同位置 */
  uni-tabbar,
  .uni-tabbar-bottom,
  .uni-tabbar-bottom .uni-tabbar {
    left: 0;
    right: 0;
    width: 100%;
    max-width: var(--se-shell-width);
    margin-left: auto;
    margin-right: auto;
  }

  /* 页面内 fixed 的底部操作条同样跟随壳宽 */
  .se-fixed-bottom {
    left: 0;
    right: 0;
    max-width: var(--se-shell-width);
    margin-left: auto;
    margin-right: auto;
  }
}
/* #endif */
</style>
