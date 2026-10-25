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
</style>
