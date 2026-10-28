<script>
import { useAuthStore } from '@/stores/auth'
import { useRiderStore } from '@/stores/rider'
import { resetTrackReporter } from '@/utils/location'

export default {
  onLaunch() {
    // http 层收到 10003 时清 storage 并广播；这里同步清空内存态与轨迹队列，
    // 避免换账号后仍显示上一名骑手的在线状态、继续补传旧配送单的轨迹。
    uni.$on('auth:expired', () => {
      resetTrackReporter()
      useRiderStore().reset()
      useAuthStore().clearLocal()
    })
  },
  onShow() {},
  onHide() {},
}
</script>

<style>
/* ===== 骑手端主题：工具型，深墨蓝文字 + 品牌橙 #FF7A00，大按钮、强对比 ===== */
page,
body {
  background-color: #f1f4f8;
  color: #16233a;
  font-size: 28rpx;
  line-height: 1.5;
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
switch {
  box-sizing: border-box;
}

.rd-page {
  min-height: 100vh;
  padding: 24rpx 24rpx 48rpx;
  /*
   * H5 下 tabBar 是独立于页面内容的 fixed 节点（50px + 安全区），不占文档流，
   * 页面最后一段内容会被它压住。--window-bottom 在 H5 tab 页等于 tabBar 高度
   * （已含 env(safe-area-inset-bottom)，不要再叠一次），非 tab 页与小程序端为 0，
   * 因此非 tab 页/小程序端视觉与改动前完全一致。上/左右内边距不变。
   */
  padding-bottom: calc(48rpx + var(--window-bottom, 0px));
}

.rd-card {
  background-color: #ffffff;
  border-radius: 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(20, 35, 58, 0.06);
  padding: 24rpx;
}

.rd-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.rd-row {
  display: flex;
  align-items: center;
}

.rd-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #16233a;
}

.rd-subtitle {
  font-size: 26rpx;
  font-weight: 600;
  color: #33455f;
}

.rd-muted {
  color: #7a8699;
  font-size: 24rpx;
}

.rd-strong {
  font-weight: 600;
}

.rd-price {
  color: #e85d04;
  font-weight: 700;
}

.rd-price::before {
  content: '¥';
  font-size: 0.76em;
  margin-right: 2rpx;
  font-weight: 600;
}

.rd-tag {
  display: inline-block;
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
  color: #c2410c;
  background-color: #fff1e6;
}

.rd-tag--warn {
  color: #b42318;
  background-color: #fee4e2;
}

.rd-tag--ok {
  color: #067647;
  background-color: #dcfae6;
}

.rd-divider {
  height: 1rpx;
  background-color: #edf1f6;
}

/* 骑行场景：关键按钮 ≥96rpx（约 48px），底部固定、单手可达 */
.rd-btn {
  height: 96rpx;
  line-height: 96rpx;
  padding: 0 32rpx;
  border-radius: 16rpx;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  border: none;
}

.rd-btn::after {
  border: none;
}

.rd-btn--primary {
  background: linear-gradient(135deg, #ff9a3c, #ff7a00);
  color: #ffffff;
}

.rd-btn--plain {
  background-color: #eef2f7;
  color: #33455f;
}

.rd-btn--ghost {
  background-color: #ffffff;
  color: #e85d04;
  border: 2rpx solid #ffb27a;
}

.rd-btn--danger {
  background-color: #fff1f0;
  color: #d93a2b;
  border: 2rpx solid #ffc9c4;
}

.rd-btn[disabled],
.rd-btn.is-disabled {
  opacity: 0.5;
}

.rd-fixed-bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 30;
  display: flex;
  gap: 20rpx;
  background-color: #ffffff;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  box-shadow: 0 -4rpx 16rpx rgba(20, 35, 58, 0.08);
}

.rd-fixed-bottom .rd-btn {
  flex: 1;
}

.rd-loading {
  padding: 96rpx 0;
  text-align: center;
  color: #7a8699;
  font-size: 26rpx;
}

.rd-empty {
  padding: 96rpx 40rpx;
  text-align: center;
  color: #7a8699;
  font-size: 26rpx;
}

.rd-empty__title {
  display: block;
  margin-bottom: 12rpx;
  font-size: 30rpx;
  color: #33455f;
  font-weight: 600;
}

.rd-empty__action {
  display: inline-block;
  margin-top: 28rpx;
  padding: 16rpx 44rpx;
  border-radius: 12rpx;
  background: linear-gradient(135deg, #ff9a3c, #ff7a00);
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 600;
}

.rd-error {
  padding: 80rpx 40rpx;
  text-align: center;
  color: #7a8699;
  font-size: 26rpx;
}

.rd-error__msg {
  display: block;
  color: #b42318;
  margin-bottom: 20rpx;
  line-height: 1.6;
}

.rd-error__retry {
  display: inline-block;
  padding: 14rpx 40rpx;
  border: 2rpx solid #ff7a00;
  border-radius: 12rpx;
  color: #e85d04;
  font-size: 26rpx;
  font-weight: 600;
}

/*
 * ===== H5 + PC 宽屏：内容收成「手机壳」宽度并居中 =====
 * 问题：H5 下页面内容没有宽度上限，1265px 屏上卡片/输入框会被拉满整屏，不适合演示。
 * 做法：条件编译 + 媒体查询双重限定，小程序端样式完全不变。
 * 注意：uni-h5 的 tabBar（<uni-tabbar class="uni-tabbar-bottom"><div class="uni-tabbar">）、
 *      原生导航栏、页面内 fixed 底栏都是独立于页面内容的 fixed 节点，
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
    background-color: #f1f4f8;
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
  .rd-fixed-bottom {
    left: 0;
    right: 0;
    max-width: var(--se-shell-width);
    margin-left: auto;
    margin-right: auto;
  }
}
/* #endif */
</style>
