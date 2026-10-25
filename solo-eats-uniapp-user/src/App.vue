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
</style>
