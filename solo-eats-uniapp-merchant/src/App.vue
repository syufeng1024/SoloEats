<script>
import { useAuthStore } from '@/stores/auth'
import { useShopStore } from '@/stores/shop'

export default {
  onLaunch() {
    // http 层收到 10003 时清 storage 并广播；这里同步清空内存态，
    // 避免换账号后仍显示上一商家的门店与营业状态。
    uni.$on('auth:expired', () => {
      useShopStore().reset()
      useAuthStore().clearLocal()
    })
  },
  onShow() {},
  onHide() {},
}
</script>

<style>
/* ===== 商家端主题：后台工作台，主色 #FF7A00，信息密度高于用户端 ===== */
page,
body {
  background-color: #f2f4f7;
  color: #1b2432;
  font-size: 26rpx;
  line-height: 1.45;
  font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue', 'PingFang SC', 'Microsoft YaHei',
    sans-serif;
}

view,
text,
input,
textarea,
button,
scroll-view,
picker,
image,
switch,
canvas {
  box-sizing: border-box;
}

/* 页面容器：紧凑内边距 */
.mt-page {
  min-height: 100vh;
  padding: 20rpx 20rpx 40rpx;
  /*
   * H5 下 tabBar 是独立于页面内容的 fixed 节点（50px + 安全区），不占文档流，
   * 页面最后一段内容会被它压住。--window-bottom 在 H5 tab 页等于 tabBar 高度
   * （已含 env(safe-area-inset-bottom)，不要再叠一次），非 tab 页与小程序端为 0，
   * 因此非 tab 页/小程序端视觉与改动前完全一致。上/左右内边距不变。
   */
  padding-bottom: calc(40rpx + var(--window-bottom, 0px));
}

.mt-card {
  background-color: #ffffff;
  border-radius: 12rpx;
  padding: 20rpx;
  box-shadow: 0 2rpx 8rpx rgba(20, 35, 58, 0.05);
}

.mt-section {
  margin-top: 20rpx;
}

.mt-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4rpx 4rpx 14rpx;
}

.mt-section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #1b2432;
}

.mt-section-extra {
  font-size: 22rpx;
  color: #7a8699;
}

.mt-row {
  display: flex;
  align-items: center;
}

.mt-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.mt-muted {
  color: #7a8699;
  font-size: 22rpx;
}

.mt-strong {
  font-weight: 600;
}

.mt-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #1b2432;
}

.mt-price {
  color: #e85d04;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.mt-price::before {
  content: '¥';
  font-size: 0.78em;
  margin-right: 2rpx;
  font-weight: 600;
}

.mt-num {
  font-variant-numeric: tabular-nums;
}

.mt-tag {
  display: inline-block;
  padding: 2rpx 10rpx;
  border-radius: 6rpx;
  font-size: 20rpx;
  line-height: 1.6;
  color: #a2520a;
  background-color: #fff1e6;
}

.mt-tag--ok {
  color: #067647;
  background-color: #dcfae6;
}

.mt-tag--warn {
  color: #b42318;
  background-color: #fee4e2;
}

.mt-tag--info {
  color: #2c4a75;
  background-color: #eef4ff;
}

.mt-tag--off {
  color: #55637a;
  background-color: #eef2f7;
}

.mt-divider {
  height: 1rpx;
  background-color: #eef1f5;
}

.mt-btn {
  height: 76rpx;
  line-height: 76rpx;
  padding: 0 26rpx;
  border-radius: 10rpx;
  text-align: center;
  font-size: 28rpx;
  font-weight: 600;
  border: none;
}

.mt-btn::after {
  border: none;
}

.mt-btn--primary {
  background: linear-gradient(135deg, #ff9a3c, #ff7a00);
  color: #ffffff;
}

.mt-btn--plain {
  background-color: #eef2f7;
  color: #33455f;
}

.mt-btn--ghost {
  background-color: #ffffff;
  color: #e85d04;
  border: 2rpx solid #ffb27a;
}

.mt-btn--danger {
  background-color: #fff1f0;
  color: #d93a2b;
  border: 2rpx solid #ffc9c4;
}

.mt-btn--small {
  height: 56rpx;
  line-height: 56rpx;
  padding: 0 20rpx;
  font-size: 24rpx;
  border-radius: 8rpx;
}

.mt-btn[disabled],
.mt-btn.is-disabled {
  opacity: 0.5;
}

/*
 * H5 的 tabBar 是 DOM 节点（z-index 998，高 50px），固定底栏若贴 bottom:0
 * 会被它盖住导致按钮点不到。--window-bottom 在 H5 tab 页为 tabBar 高度，
 * 非 tab 页与 App/小程序为 0，因此用它做偏移可跨端保持「在 tabBar 之上」。
 */
.mt-fixed-bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: var(--window-bottom, 0px);
  z-index: 30;
  display: flex;
  gap: 16rpx;
  background-color: #ffffff;
  padding: 14rpx 20rpx calc(14rpx + env(safe-area-inset-bottom));
  box-shadow: 0 -2rpx 12rpx rgba(20, 35, 58, 0.08);
}

.mt-fixed-bottom .mt-btn {
  flex: 1;
}

.mt-loading {
  padding: 80rpx 0;
  text-align: center;
  color: #7a8699;
  font-size: 24rpx;
}

.mt-empty {
  padding: 80rpx 32rpx;
  text-align: center;
  color: #7a8699;
  font-size: 24rpx;
}

.mt-empty__title {
  display: block;
  margin-bottom: 10rpx;
  font-size: 28rpx;
  color: #33455f;
  font-weight: 600;
}

.mt-empty__action {
  display: inline-block;
  margin-top: 24rpx;
  padding: 14rpx 36rpx;
  border-radius: 10rpx;
  background: linear-gradient(135deg, #ff9a3c, #ff7a00);
  color: #ffffff;
  font-size: 26rpx;
  font-weight: 600;
}

.mt-error {
  padding: 64rpx 32rpx;
  text-align: center;
  color: #7a8699;
  font-size: 24rpx;
}

.mt-error__msg {
  display: block;
  color: #b42318;
  margin-bottom: 18rpx;
  line-height: 1.6;
}

.mt-error__retry {
  display: inline-block;
  padding: 12rpx 34rpx;
  border: 2rpx solid #ff7a00;
  border-radius: 10rpx;
  color: #e85d04;
  font-size: 24rpx;
  font-weight: 600;
}

/* 表单 */
.mt-field {
  display: flex;
  align-items: center;
  min-height: 88rpx;
  padding: 0 2rpx;
}

.mt-field__label {
  width: 176rpx;
  font-size: 26rpx;
  color: #33455f;
}

.mt-field__value {
  flex: 1;
  text-align: right;
  font-size: 26rpx;
  color: #1b2432;
}

.mt-input {
  flex: 1;
  height: 76rpx;
  padding: 0 16rpx;
  border-radius: 8rpx;
  background-color: #f7f9fc;
  font-size: 26rpx;
  color: #1b2432;
}

.mt-placeholder {
  color: #a9b4c4;
  font-size: 24rpx;
}

.mt-switch {
  transform: scale(0.8);
  transform-origin: right center;
}

/* 浮层 */
.mt-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 120;
  background-color: rgba(12, 20, 33, 0.55);
}

.mt-sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: var(--window-bottom, 0px);
  z-index: 121;
  background-color: #ffffff;
  border-radius: 20rpx 20rpx 0 0;
  padding: 24rpx 20rpx calc(20rpx + env(safe-area-inset-bottom));
  max-height: 82vh;
}

.mt-sheet__title {
  font-size: 30rpx;
  font-weight: 600;
  color: #1b2432;
}

.mt-sheet__tip {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #7a8699;
  line-height: 1.6;
}

.mt-sheet__actions {
  display: flex;
  gap: 16rpx;
  margin-top: 20rpx;
}

.mt-sheet__actions .mt-btn {
  flex: 1;
}

/*
 * ===== H5 + PC 宽屏：内容收成「手机壳」宽度并居中 =====
 * 问题：H5 下页面内容没有宽度上限，1265px 屏上卡片/输入框会被拉满整屏，不适合演示。
 * 做法：条件编译 + 媒体查询双重限定，小程序端样式完全不变。
 * 注意：uni-h5 的 tabBar（<uni-tabbar class="uni-tabbar-bottom"><div class="uni-tabbar">）、
 *      原生导航栏、页面内 fixed 底栏/底部弹层都是独立于页面内容的 fixed 节点，
 *      必须和内容一起限宽居中，否则会出现「内容窄、导航条撑满」的错位。
 * 壳宽：改 --se-shell-width 一处即可（三端保持同值、同实现风格）。
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

  /* 壳内保持原页面底色（uni-h5 默认把 uni-page-body 置为透明） */
  uni-page[data-type] uni-page-body {
    min-height: 100%;
    background-color: #f2f4f7;
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

  /* 页面内 fixed 的底部操作条与底部弹层同样跟随壳宽 */
  .mt-fixed-bottom,
  .mt-sheet {
    left: 0;
    right: 0;
    max-width: var(--se-shell-width);
    margin-left: auto;
    margin-right: auto;
  }
}
/* #endif */
</style>
