<template>
  <view class="login">
    <view class="login__hero">
      <view class="login__logo">骑</view>
      <text class="login__brand">数创星光外卖 · 骑手端</text>
      <text class="login__slogan">认证 → 听单 → 取货 → 送达 → 收入</text>
    </view>

    <view class="rd-card login__form">
      <view class="login__field">
        <text class="login__label">账号</text>
        <input
          v-model="username"
          class="login__input"
          type="text"
          maxlength="32"
          placeholder="请输入骑手账号"
          placeholder-class="login__placeholder"
        />
      </view>
      <view class="rd-divider" />
      <view class="login__field">
        <text class="login__label">密码</text>
        <input
          v-model="password"
          class="login__input"
          type="text"
          :password="!showPassword"
          maxlength="32"
          placeholder="请输入登录密码"
          placeholder-class="login__placeholder"
          @confirm="handleLogin"
        />
        <text class="login__eye" @click="showPassword = !showPassword">
          {{ showPassword ? '隐藏' : '显示' }}
        </text>
      </view>
    </view>

    <view class="login__demo" @click="fillDemoAccount">
      测试账号 rider001 / Rider@123，点此填入
    </view>

    <button
      class="rd-btn rd-btn--primary login__submit"
      :disabled="submitting"
      @click="handleLogin"
    >
      {{ submitting ? '登录中…' : '登 录' }}
    </button>

    <view class="login__tip">
      仅限配送人员使用。登录即表示同意平台配送服务规范与隐私政策。
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useRiderStore } from '@/stores/rider'
import type { LoginVO } from '@/api/types'

const auth = useAuthStore()
const rider = useRiderStore()

const username = ref('')
const password = ref('')
const showPassword = ref(false)
const submitting = ref(false)

/** 已在跳转中，避免 onShow 与点击登录各跳一次 */
let navigating = false
let redirect = ''

const TAB_PAGES = ['/pages/home/Home', '/pages/task/List', '/pages/mine/Mine']

onLoad((options) => {
  const raw = options?.redirect
  redirect = raw ? decodeURIComponent(String(raw)) : ''
})

onShow(() => {
  // 登录页是首屏：已登录直接放行，未登录停留
  if (auth.isLogin && !navigating && !submitting.value) {
    void routeAfterLogin(auth.nickname)
  }
})

function toast(message: string): void {
  uni.showToast({ title: message, icon: 'none', duration: 2200 })
}

function fillDemoAccount(): void {
  username.value = 'rider001'
  password.value = 'Rider@123'
  showPassword.value = true
}

function isRiderAccount(result: LoginVO): boolean {
  const roles = result.roles || []
  // 角色码是细粒度族码（RIDER 或 RIDER_xxx），按族匹配，不做精确相等
  return roles.some((role) => role === 'RIDER' || role.startsWith('RIDER_'))
}

async function handleLogin(): Promise<void> {
  if (submitting.value || navigating) {
    return
  }
  if (!username.value.trim()) {
    toast('请输入骑手账号')
    return
  }
  if (!password.value) {
    toast('请输入登录密码')
    return
  }

  submitting.value = true
  try {
    const result = await auth.login(username.value, password.value)
    if (!isRiderAccount(result)) {
      auth.logout()
      toast('该账号不是骑手账号，请联系平台开通')
      return
    }
    await routeAfterLogin(auth.nickname)
  } catch (error) {
    // http 层已统一提示
  } finally {
    submitting.value = false
  }
}

/**
 * 认证门槛前置：certStatus≠2 一律进认证状态页，不让骑手看到听单开关。
 * 认证查询失败不阻断登录，首页会再次校准并给出错误态。
 */
async function routeAfterLogin(nickname: string): Promise<void> {
  if (navigating) {
    return
  }
  navigating = true
  if (nickname) {
    uni.setStorageSync('nickname', nickname)
  }
  try {
    try {
      await rider.loadCertification()
    } catch (error) {
      // 本地残留的旧 token 或非骑手账号：清掉登录态，停在登录页
      if (error instanceof ApiError && (error.code === 10004 || error.code === 10003)) {
        auth.logout()
        rider.reset()
        toast('请使用骑手账号登录')
        return
      }
      // 网络类错误放行到首页，由首页展示可重试的错误态
    }
    if (rider.certStatus !== 2) {
      uni.reLaunch({ url: '/pages/cert/CertStatus' })
      return
    }
    navigateAfterLogin()
  } finally {
    navigating = false
  }
}

function navigateAfterLogin(): void {
  const target = redirect
  redirect = ''
  if (target && !target.startsWith('/pages/login')) {
    const path = target.split('?')[0]
    if (TAB_PAGES.includes(path)) {
      uni.switchTab({ url: path })
    } else {
      uni.reLaunch({ url: target })
    }
    return
  }
  uni.switchTab({ url: '/pages/home/Home' })
}
</script>

<style scoped>
.login {
  min-height: 100vh;
  padding: 0 40rpx 60rpx;
  background: linear-gradient(180deg, #eaf1fb 0%, #f1f4f8 46%);
}

.login__hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 104rpx 0 56rpx;
}

.login__logo {
  width: 136rpx;
  height: 136rpx;
  line-height: 136rpx;
  text-align: center;
  border-radius: 36rpx;
  background: linear-gradient(135deg, #ff9a3c, #ff7a00);
  color: #ffffff;
  font-size: 64rpx;
  font-weight: 700;
  box-shadow: 0 12rpx 28rpx rgba(255, 106, 0, 0.28);
}

.login__brand {
  margin-top: 26rpx;
  font-size: 38rpx;
  font-weight: 700;
  letter-spacing: 1rpx;
  color: #16233a;
}

.login__slogan {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #7a8699;
}

.login__form {
  padding: 8rpx 28rpx;
}

.login__field {
  display: flex;
  align-items: center;
  height: 112rpx;
}

.login__label {
  width: 104rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #33455f;
}

.login__input {
  flex: 1;
  height: 112rpx;
  font-size: 30rpx;
  color: #16233a;
}

.login__placeholder {
  color: #a9b4c4;
  font-size: 28rpx;
}

.login__eye {
  padding-left: 20rpx;
  border-left: 1rpx solid #edf1f6;
  color: #e85d04;
  font-size: 26rpx;
}

.login__demo {
  margin: 24rpx 8rpx 0;
  padding: 16rpx 20rpx;
  border-radius: 12rpx;
  background-color: #fff6ea;
  color: #a2520a;
  font-size: 24rpx;
  line-height: 1.5;
}

.login__submit {
  margin-top: 40rpx;
}

.login__tip {
  margin-top: 32rpx;
  text-align: center;
  font-size: 22rpx;
  color: #8d99aa;
  line-height: 1.7;
}
</style>
