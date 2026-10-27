<template>
  <view class="feedback">
    <view class="se-card feedback__card">
      <view class="se-between feedback__head">
        <text class="feedback__title">意见与建议</text>
        <text class="feedback__counter" :class="{ 'is-limit': content.length >= MAX_CONTENT }">
          {{ content.length }}/{{ MAX_CONTENT }}
        </text>
      </view>

      <textarea
        v-model="content"
        class="feedback__textarea"
        :maxlength="MAX_CONTENT"
        placeholder="请描述你遇到的问题或建议，例如门店、商品、配送、退款等"
        placeholder-class="feedback__placeholder"
      />

      <view class="feedback__field">
        <text class="feedback__label">联系方式</text>
        <input
          v-model="contact"
          class="feedback__input"
          type="text"
          maxlength="64"
          placeholder="手机号或邮箱（选填，便于回复）"
          placeholder-class="feedback__placeholder"
        />
      </view>
    </view>

    <view class="feedback__tips">
      <text>提交后平台客服会尽快处理；涉及具体订单时，建议同时提交「我的工单」并关联订单号。</text>
    </view>

    <button
      class="se-btn se-btn-primary feedback__submit"
      :disabled="submitting"
      @click="handleSubmit"
    >
      {{ submitting ? '提交中…' : '提交反馈' }}
    </button>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { submitFeedback } from '@/api/mine'
import { useAuthStore } from '@/stores/auth'

/**
 * 意见反馈。
 * POST /c/feedbacks 的校验：content 非空且 ≤1000 字，contact ≤64 字，images ≤9 张。
 * 本页不上传图片，images 固定传空数组。
 */

const MAX_CONTENT = 1000
const MAX_CONTACT = 64

const auth = useAuthStore()

const content = ref('')
const contact = ref('')
const submitting = ref(false)

onLoad(() => {
  auth.ensureLogin('/pages/mine/Feedback')
})

async function handleSubmit(): Promise<void> {
  if (submitting.value) {
    return
  }
  const text = content.value.trim()
  const contactText = contact.value.trim()
  if (!text) {
    uni.showToast({ title: '请填写反馈内容', icon: 'none' })
    return
  }
  if (text.length > MAX_CONTENT) {
    uni.showToast({ title: `反馈内容最多 ${MAX_CONTENT} 字`, icon: 'none' })
    return
  }
  if (contactText.length > MAX_CONTACT) {
    uni.showToast({ title: `联系方式最多 ${MAX_CONTACT} 字`, icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await submitFeedback({ content: text, contact: contactText || null })
    uni.showToast({ title: '反馈已提交，感谢你的建议', icon: 'none' })
    // 停在提示上 600ms，让用户看清结果再返回
    setTimeout(() => {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        uni.navigateBack()
      } else {
        uni.switchTab({ url: '/pages/mine/Mine' })
      }
    }, 600)
  } catch (err) {
    // http 层统一 toast；保留已填内容，方便重试
    submitting.value = false
  }
}
</script>

<style scoped>
.feedback {
  min-height: 100vh;
  padding: 24rpx;
  background-color: #f5f5f7;
}

.feedback__card {
  padding: 24rpx;
}

.feedback__head {
  margin-bottom: 12rpx;
}

.feedback__title {
  font-size: 30rpx;
  font-weight: 600;
}

.feedback__counter {
  color: #a0a0a0;
  font-size: 22rpx;
}

.feedback__counter.is-limit {
  color: #ff4d4f;
}

.feedback__textarea {
  width: 100%;
  height: 320rpx;
  padding: 16rpx;
  border-radius: 8rpx;
  background-color: #f7f7f8;
  color: #1f1f1f;
  font-size: 26rpx;
  line-height: 1.7;
}

.feedback__placeholder {
  color: #b0b0b0;
  font-size: 24rpx;
}

.feedback__field {
  display: flex;
  align-items: center;
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid #f5f5f5;
}

.feedback__label {
  width: 140rpx;
  flex-shrink: 0;
  color: #444444;
  font-size: 26rpx;
}

.feedback__input {
  flex: 1;
  height: 60rpx;
  font-size: 26rpx;
  color: #1f1f1f;
}

.feedback__tips {
  padding: 20rpx 12rpx;
  color: #a0a0a0;
  font-size: 22rpx;
  line-height: 1.7;
}

.feedback__submit {
  margin-top: 12rpx;
}
</style>
