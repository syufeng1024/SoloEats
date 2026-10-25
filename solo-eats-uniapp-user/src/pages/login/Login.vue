<template>
  <view class="se-page login">
    <view class="login__brand">
      <view class="login__logo">数</view>
      <text class="login__name">数创星光外卖</text>
      <text class="login__slogan">附近好店，送到家门口</text>
    </view>

    <view class="se-card login__form">
      <view class="login__field">
        <text class="login__label">手机号</text>
        <input
          v-model="phone"
          class="login__input"
          type="number"
          maxlength="11"
          placeholder="请输入 11 位手机号"
          placeholder-class="login__placeholder"
        />
      </view>
      <view class="se-divider" />
      <view class="login__field">
        <text class="login__label">验证码</text>
        <input
          v-model="code"
          class="login__input"
          type="number"
          maxlength="6"
          placeholder="6 位数字验证码"
          placeholder-class="login__placeholder"
        />
        <text
          class="login__code"
          :class="{ 'is-disabled': counting || sending }"
          @click="handleSendCode"
        >
          {{ counting ? `${seconds}s 后重发` : sending ? '发送中…' : '获取验证码' }}
        </text>
      </view>
    </view>

    <button class="se-btn se-btn-primary login__submit" :disabled="submitting" @click="handleLogin">
      {{ submitting ? '登录中…' : '登录' }}
    </button>

    <text class="login__agreement">登录即表示同意《用户协议》与《隐私政策》</text>
    <text class="login__skip" @click="goHome">先逛逛</text>
  </view>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { sendSmsCode } from '@/api/user'

const auth = useAuthStore()

const phone = ref('')
const code = ref('')
const sending = ref(false)
const submitting = ref(false)
const seconds = ref(0)
const counting = computed(() => seconds.value > 0)

let timer: ReturnType<typeof setInterval> | null = null
let redirect = ''

onLoad((options) => {
  const raw = options?.redirect
  redirect = raw ? decodeURIComponent(String(raw)) : ''
})

onUnmounted(() => {
  stopCountdown()
})

function stopCountdown(): void {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function startCountdown(): void {
  stopCountdown()
  seconds.value = 60
  timer = setInterval(() => {
    seconds.value -= 1
    if (seconds.value <= 0) {
      stopCountdown()
    }
  }, 1000)
}

function toast(message: string): void {
  uni.showToast({ title: message, icon: 'none' })
}

function isPhoneValid(): boolean {
  return /^1[3-9]\d{9}$/.test(phone.value)
}

async function handleSendCode(): Promise<void> {
  if (counting.value || sending.value) {
    return
  }
  if (!isPhoneValid()) {
    toast('请输入正确的 11 位手机号')
    return
  }
  sending.value = true
  try {
    await sendSmsCode(phone.value, 1)
    toast('验证码已发送')
    startCountdown()
  } catch (error) {
    // http 层已统一 toast，这里不重复提示
  } finally {
    sending.value = false
  }
}

async function handleLogin(): Promise<void> {
  if (submitting.value) {
    return
  }
  if (!isPhoneValid()) {
    toast('请输入正确的 11 位手机号')
    return
  }
  if (!/^\d{6}$/.test(code.value)) {
    toast('请输入 6 位数字验证码')
    return
  }
  submitting.value = true
  try {
    const result = await auth.login(phone.value, code.value)
    uni.showToast({ title: result.isNewUser ? '注册成功' : '登录成功', icon: 'none' })
    navigateAfterLogin()
  } catch (error) {
    // http 层已统一提示
  } finally {
    submitting.value = false
  }
}

const TAB_PAGES = ['/pages/index/Index', '/pages/order/List']

function navigateAfterLogin(): void {
  const target = redirect
  redirect = ''
  if (target && !target.startsWith('/pages/login')) {
    // tabBar 页面只能用 switchTab，普通页面用 reLaunch 清掉登录页
    if (TAB_PAGES.includes(target.split('?')[0])) {
      uni.switchTab({ url: target.split('?')[0] })
    } else {
      uni.reLaunch({ url: target })
    }
    return
  }
  uni.switchTab({ url: '/pages/index/Index' })
}

function goHome(): void {
  uni.switchTab({ url: '/pages/index/Index' })
}
</script>

<style scoped>
.login {
  padding: 0 40rpx 60rpx;
  background: linear-gradient(180deg, #fff3e6 0%, #f5f5f7 42%);
}

.login__brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 96rpx 0 64rpx;
}

.login__logo {
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

.login__name {
  margin-top: 24rpx;
  font-size: 40rpx;
  font-weight: 600;
  letter-spacing: 2rpx;
}

.login__slogan {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #8a8a8a;
}

.login__form {
  padding: 8rpx 28rpx;
}

.login__field {
  display: flex;
  align-items: center;
  height: 104rpx;
}

.login__label {
  width: 116rpx;
  font-size: 26rpx;
  color: #444444;
}

.login__input {
  flex: 1;
  height: 104rpx;
  font-size: 28rpx;
}

.login__placeholder {
  color: #b8b8b8;
  font-size: 26rpx;
}

.login__code {
  padding-left: 20rpx;
  border-left: 1rpx solid #eeeeee;
  color: #ff7a00;
  font-size: 26rpx;
}

.login__code.is-disabled {
  color: #b8b8b8;
}

.login__submit {
  margin-top: 48rpx;
}

.login__agreement {
  display: block;
  margin-top: 32rpx;
  text-align: center;
  font-size: 22rpx;
  color: #a0a0a0;
}

.login__skip {
  display: block;
  margin-top: 24rpx;
  text-align: center;
  font-size: 26rpx;
  color: #ff7a00;
}
</style>
