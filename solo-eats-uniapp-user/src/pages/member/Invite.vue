<template>
  <view class="invite">
    <view class="invite__hero">
      <text class="invite__hero-title">邀请好友，一起点好饭</text>
      <text class="invite__hero-desc">好友绑定你的邀请码并完成首单后，你可获得 50 积分与 50 成长值。</text>

      <view class="invite__code-box">
        <text class="invite__code-label">我的邀请码</text>
        <text class="invite__code">{{ code || (loading ? '加载中…' : '--') }}</text>
        <view
          class="invite__copy"
          :class="{ 'is-disabled': !code }"
          @click="copyCode"
        >
          复制邀请码
        </view>
      </view>

      <view v-if="error" class="invite__error">
        <text>{{ error }}</text>
        <text class="invite__error-retry" @click="loadCode(false)">重新加载</text>
      </view>
    </view>

    <view class="se-card invite__rules">
      <text class="invite__section-title">邀请规则</text>
      <view v-for="(rule, index) in RULES" :key="rule" class="invite__rule">
        <text class="invite__rule-index">{{ index + 1 }}</text>
        <text class="invite__rule-text">{{ rule }}</text>
      </view>
    </view>

    <view class="se-card invite__bind">
      <text class="invite__section-title">填写好友的邀请码</text>
      <text class="invite__bind-desc">
        绑定后不可修改，也不能绑定自己的邀请码；每个账号只能绑定一次。
      </text>

      <view class="invite__bind-row">
        <input
          v-model="bindCode"
          class="invite__input"
          type="text"
          maxlength="16"
          placeholder="请输入 16 位以内的邀请码"
          placeholder-class="invite__placeholder"
        />
        <view
          class="invite__bind-btn"
          :class="{ 'is-disabled': binding }"
          @click="handleBind"
        >
          {{ binding ? '绑定中…' : '绑定' }}
        </view>
      </view>
    </view>

    <view class="se-card invite__record">
      <view class="se-between">
        <text class="invite__section-title">邀请记录</text>
        <text class="se-muted">暂未开放</text>
      </view>
      <text class="invite__record-desc">
        当前后端未提供邀请人数与奖励进度查询接口，暂不展示具体数量；绑定结果以「绑定成功」提示为准。
      </text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { bindInviteCode, fetchInviteCode } from '@/api/member'
import { useAuthStore } from '@/stores/auth'

/**
 * 邀请好友。
 *
 * 邀请码由后端按 userId 做 Base62 编码，大小写敏感，页面只做 trim，不转大写。
 * 后端没有「已邀请人数」查询接口，因此不展示臆造的数量，改为明确标注暂未开放。
 */

const RULES = [
  '好友注册后，在「邀请好友」页填写你的邀请码完成绑定。',
  '好友完成首单后，你获得 50 积分与 50 成长值，奖励自动到账。',
  '每个账号只能绑定一次邀请码，绑定后不可修改，且不能绑定自己的邀请码。',
  '同一邀请人 24 小时内最多成功绑定 10 位好友，超出后请次日再试。',
  '邀请码由数字与大小写字母组成，输入时请保持大小写一致。',
]

const auth = useAuthStore()

const code = ref('')
const loading = ref(false)
const error = ref('')
const bindCode = ref('')
const binding = ref(false)

onShow(() => {
  if (!auth.ensureLogin('/pages/member/Invite')) {
    return
  }
  if (!code.value) {
    loadCode(false)
  }
})

async function loadCode(silent: boolean): Promise<void> {
  if (loading.value) {
    return
  }
  loading.value = true
  if (!silent) {
    error.value = ''
  }
  try {
    code.value = await fetchInviteCode(silent)
    error.value = ''
  } catch (err) {
    error.value = (err as Error)?.message || '邀请码加载失败'
    if (!silent) {
      uni.showToast({ title: error.value, icon: 'none' })
    }
  } finally {
    loading.value = false
  }
}

function copyCode(): void {
  if (!code.value) {
    return
  }
  uni.setClipboardData({
    data: code.value,
    success: () => {
      uni.showToast({ title: '邀请码已复制', icon: 'none' })
    },
  })
}

async function handleBind(): Promise<void> {
  if (!auth.ensureLogin('/pages/member/Invite') || binding.value) {
    return
  }
  const value = bindCode.value.trim()
  if (!value) {
    uni.showToast({ title: '请输入邀请码', icon: 'none' })
    return
  }
  if (value.length > 16) {
    uni.showToast({ title: '邀请码不能超过 16 个字符', icon: 'none' })
    return
  }
  binding.value = true
  try {
    await bindInviteCode(value)
    bindCode.value = ''
    uni.showToast({ title: '邀请码绑定成功', icon: 'none' })
  } catch {
    // 已绑定、绑定自己、码非法等业务错误由 http 层统一 toast
  } finally {
    binding.value = false
  }
}
</script>

<style scoped>
.invite {
  min-height: 100vh;
  padding: 24rpx;
  background: linear-gradient(180deg, #fff3e6 0%, #f5f5f7 360rpx);
}

.invite__hero {
  padding: 32rpx;
  border-radius: 12rpx;
  background: linear-gradient(120deg, #ff8a1f 0%, #ff5c00 100%);
  color: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(255, 92, 0, 0.22);
}

.invite__hero-title {
  display: block;
  font-size: 38rpx;
  font-weight: 600;
}

.invite__hero-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.6;
  opacity: 0.92;
}

.invite__code-box {
  margin-top: 32rpx;
  padding: 24rpx;
  border-radius: 10rpx;
  background-color: rgba(255, 255, 255, 0.16);
  text-align: center;
}

.invite__code-label {
  display: block;
  font-size: 22rpx;
  opacity: 0.9;
}

.invite__code {
  display: block;
  margin-top: 8rpx;
  font-size: 52rpx;
  font-weight: 600;
  letter-spacing: 6rpx;
  word-break: break-all;
}

.invite__copy {
  display: inline-block;
  margin-top: 20rpx;
  padding: 14rpx 48rpx;
  border-radius: 36rpx;
  background-color: #ffffff;
  color: #ff5c00;
  font-size: 26rpx;
  font-weight: 600;
}

.invite__copy.is-disabled {
  opacity: 0.5;
}

.invite__error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  margin-top: 20rpx;
  padding: 12rpx 16rpx;
  border-radius: 8rpx;
  background-color: rgba(255, 255, 255, 0.18);
  font-size: 22rpx;
}

.invite__error-retry {
  text-decoration: underline;
}

.invite__rules,
.invite__bind,
.invite__record {
  margin-top: 20rpx;
  padding: 24rpx;
}

.invite__section-title {
  font-size: 30rpx;
  font-weight: 600;
}

.invite__rule {
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
  margin-top: 18rpx;
}

.invite__rule-index {
  width: 34rpx;
  height: 34rpx;
  line-height: 34rpx;
  flex-shrink: 0;
  text-align: center;
  border-radius: 50%;
  background-color: #fff2e6;
  color: #ff7a00;
  font-size: 20rpx;
}

.invite__rule-text {
  flex: 1;
  color: #555555;
  font-size: 24rpx;
  line-height: 1.6;
}

.invite__bind-desc {
  display: block;
  margin-top: 10rpx;
  color: #a0a0a0;
  font-size: 22rpx;
  line-height: 1.6;
}

.invite__bind-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 20rpx;
}

.invite__input {
  flex: 1;
  height: 80rpx;
  padding: 0 20rpx;
  border-radius: 8rpx;
  background-color: #f7f7f8;
  color: #1f1f1f;
  font-size: 26rpx;
}

.invite__placeholder {
  color: #b0b0b0;
}

.invite__bind-btn {
  padding: 0 40rpx;
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 8rpx;
  background: linear-gradient(135deg, #ffa24c, #ff7a00);
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 500;
  flex-shrink: 0;
}

.invite__bind-btn.is-disabled {
  opacity: 0.5;
}

.invite__record-desc {
  display: block;
  margin-top: 12rpx;
  color: #a0a0a0;
  font-size: 22rpx;
  line-height: 1.6;
}
</style>
