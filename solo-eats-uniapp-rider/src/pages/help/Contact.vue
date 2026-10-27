<template>
  <view class="contact rd-page">
    <view class="contact__hero">
      <text class="contact__hero-title">骑手客服</text>
      <text class="contact__hero-sub">订单异常、提现审核、账号问题都可以找我们</text>
      <text class="contact__hero-note">服务时间以平台公告为准</text>
    </view>

    <view class="rd-card contact__ways">
      <view class="contact__way" @click="contactOnline">
        <view class="contact__way-main">
          <text class="contact__way-name">在线客服</text>
          <text class="contact__way-desc">尝试创建 IM 会话，失败时使用电话</text>
        </view>
        <text class="contact__way-action">{{ connecting ? '连接中…' : '进入 ›' }}</text>
      </view>

      <view class="rd-divider" />

      <view class="contact__way" @click="callService">
        <view class="contact__way-main">
          <text class="contact__way-name">客服电话</text>
          <text class="contact__way-desc">{{ SERVICE_PHONE }}（占位）</text>
        </view>
        <text class="contact__way-action">拨打 ›</text>
      </view>

      <view class="rd-divider" />

      <view class="contact__way" @click="goHelp">
        <view class="contact__way-main">
          <text class="contact__way-name">帮助中心</text>
          <text class="contact__way-desc">先查常见问题，多数问题可自助解决</text>
        </view>
        <text class="contact__way-action">查看 ›</text>
      </view>
    </view>

    <view v-if="imError" class="contact__fallback">
      <text class="contact__fallback-title">在线客服暂不可用</text>
      <text class="contact__fallback-line">{{ imError }}</text>
      <text class="contact__fallback-line">
        请拨打客服电话 {{ SERVICE_PHONE }}（占位），或前往帮助中心查看常见问题。
      </text>
    </view>

    <view v-if="imNote" class="contact__note">{{ imNote }}</view>

    <view class="contact__tips">
      <text class="contact__tips-title">联系前请准备</text>
      <text class="contact__tips-line">· 骑手编号或注册手机号后四位</text>
      <text class="contact__tips-line">· 涉及配送单时提供配送单号</text>
      <text class="contact__tips-line">· 涉及提现时提供提现单号</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { createImSession } from '@/api/rider'

/**
 * 联系客服。
 *
 * POST /im/sessions 对骑手主体只接受 peerType=1（对端必须是消费者），
 * 骑手发起 peerType=4 的客服会话会被服务端以 10001 拒绝；骑手端也还没有 IM 聊天页。
 * 因此这里按产品要求保留入口尝试，失败即展示客服电话占位，不伪造会话成功的状态。
 */

const SERVICE_PHONE = '400-000-0000'
const CS_PEER_TYPE = 4
/** 客服对端 ID 占位：后端暂无客服坐席配置表，该 ID 仅用于发起探测请求。 */
const CS_PEER_ID = 1

const connecting = ref(false)
const imError = ref('')
const imNote = ref('')

async function contactOnline(): Promise<void> {
  if (connecting.value) {
    return
  }
  connecting.value = true
  imError.value = ''
  imNote.value = ''
  try {
    const session = await createImSession({
      peerType: CS_PEER_TYPE,
      peerId: CS_PEER_ID,
    })
    imNote.value = `会话已创建（${session.sessionNo}），骑手端聊天页尚未提供，请先使用电话或帮助中心。`
  } catch (err) {
    imError.value = '骑手端在线客服入口尚未开通'
  } finally {
    connecting.value = false
  }
}

function callService(): void {
  // 号码为占位，不直接拨号，避免用户拨打空号
  uni.showToast({ title: '客服电话待开通，请先查看帮助中心', icon: 'none' })
}

function goHelp(): void {
  uni.navigateTo({ url: '/pages/help/Help' })
}
</script>

<style scoped>
.contact {
  padding-bottom: 60rpx;
}

.contact__hero {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  padding: 36rpx 32rpx;
  border-radius: 24rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #2c4467, #16233a);
}

.contact__hero-title {
  font-size: 38rpx;
  font-weight: 700;
}

.contact__hero-sub {
  font-size: 24rpx;
  opacity: 0.85;
}

.contact__hero-note {
  font-size: 21rpx;
  opacity: 0.65;
}

.contact__ways {
  margin-top: 20rpx;
  padding: 4rpx 24rpx;
}

.contact__way {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  min-height: 112rpx;
}

.contact__way-main {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.contact__way-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #16233a;
}

.contact__way-desc {
  font-size: 21rpx;
  color: #8d99aa;
}

.contact__way-action {
  font-size: 24rpx;
  color: #e85d04;
  font-weight: 600;
}

.contact__fallback {
  margin-top: 20rpx;
  padding: 24rpx;
  border-radius: 16rpx;
  background-color: #fff1f0;
}

.contact__fallback-title {
  display: block;
  font-size: 26rpx;
  font-weight: 700;
  color: #b42318;
}

.contact__fallback-line {
  display: block;
  margin-top: 8rpx;
  font-size: 23rpx;
  color: #7a2a22;
  line-height: 1.7;
}

.contact__note {
  margin-top: 20rpx;
  padding: 18rpx 22rpx;
  border-radius: 12rpx;
  background-color: #f6f8fb;
  font-size: 23rpx;
  color: #7a8699;
  line-height: 1.6;
}

.contact__tips {
  margin-top: 28rpx;
  padding: 0 8rpx;
}

.contact__tips-title {
  display: block;
  margin-bottom: 10rpx;
  font-size: 24rpx;
  font-weight: 600;
  color: #33455f;
}

.contact__tips-line {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #8d99aa;
  line-height: 1.7;
}
</style>
