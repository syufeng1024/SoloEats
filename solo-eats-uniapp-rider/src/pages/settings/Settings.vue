<template>
  <view class="settings rd-page">
    <view class="settings__section-head">
      <text class="rd-subtitle">通知设置</text>
      <text class="settings__section-note">由服务端按骑手账号保存</text>
    </view>

    <view class="rd-card settings__card">
      <view v-if="notifyLoading" class="settings__hint">正在加载通知设置…</view>

      <view v-else-if="notifyError" class="settings__error">
        <text>{{ notifyError }}</text>
        <text class="settings__error-retry" @click="loadNotifySettings">重试</text>
      </view>

      <view v-else-if="notifySettings.length === 0" class="settings__hint">
        暂无可配置的通知类型
      </view>

      <view v-else>
        <view v-for="(item, index) in notifySettings" :key="item.notifyType">
          <view v-if="index > 0" class="rd-divider" />
          <view class="settings__item">
            <view class="settings__item-main">
              <text class="settings__item-label">{{ item.notifyTypeName }}</text>
              <text class="settings__item-desc">{{ notifyDesc(item.notifyType) }}</text>
            </view>
            <switch
              :checked="item.enabled"
              color="#FF7A00"
              @change="onNotifyChange(item, $event)"
            />
          </view>
        </view>
      </view>
    </view>

    <view class="settings__section-head settings__section-head--gap">
      <text class="rd-subtitle">播放与提醒</text>
      <text class="settings__section-note">仅保存在本机</text>
    </view>

    <view class="rd-card settings__card">
      <view class="settings__item">
        <view class="settings__item-main">
          <text class="settings__item-label">语音播报</text>
          <text class="settings__item-desc">新派单时播报订单信息，H5 依赖浏览器语音能力</text>
        </view>
        <switch :checked="voiceEnabled" color="#FF7A00" @change="onVoiceChange" />
      </view>
    </view>

    <view class="settings__section-head settings__section-head--gap">
      <text class="rd-subtitle">通用</text>
    </view>

    <view class="rd-card settings__card">
      <view class="settings__item settings__item--tap" @click="clearCache">
        <view class="settings__item-main">
          <text class="settings__item-label">清除缓存</text>
          <text class="settings__item-desc">清除本地临时数据，登录状态与待上传轨迹不受影响</text>
        </view>
        <text class="settings__arrow">›</text>
      </view>

      <view class="rd-divider" />

      <view class="settings__item">
        <text class="settings__item-label">版本号</text>
        <text class="settings__item-value">v{{ APP_VERSION }}</text>
      </view>
    </view>

    <button class="rd-btn rd-btn--danger settings__logout" @click="handleLogout">退出登录</button>

    <view class="settings__foot">数创星光外卖 · 骑手端 v{{ APP_VERSION }}</view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchNotifySettings, updateNotifySetting } from '@/api/rider'
import type { NotifySettingVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useRiderStore } from '@/stores/rider'
import { resetTrackReporter, stopTrackReport } from '@/utils/location'
import { isVoiceEnabled, setVoiceEnabled } from '@/utils/voice'

/**
 * 设置页。
 * 通知开关走 GET/PUT /notify/settings：NotifySettingController 只要求登录，
 * CurrentReceiver 把骑手解析为 receiverType=3、receiverId=t_rider.id，
 * NotifySettingServiceImpl 的 requireReceiver 允许 1~3，因此骑手可以读写。
 * 后端不存在的开关（如语音播报）只落本地，并在区块标题标注「仅保存在本机」。
 */

const APP_VERSION = '0.1.0'
/** 清缓存时保留：登录态、语音偏好、未上传的轨迹队列 */
const PRESERVED_KEYS = ['token', 'tokenExpireAt', 'userId', 'nickname', 'rider:voiceEnabled', 'rider:track:queue']

const auth = useAuthStore()
const rider = useRiderStore()

const notifySettings = ref<NotifySettingVO[]>([])
const notifyLoading = ref(false)
const notifyError = ref('')
const voiceEnabled = ref(isVoiceEnabled())

onShow(() => {
  if (!auth.ensureLogin('/pages/settings/Settings')) {
    return
  }
  voiceEnabled.value = isVoiceEnabled()
  void loadNotifySettings()
})

function notifyDesc(notifyType: number): string {
  switch (Number(notifyType)) {
    case 1:
      return '新派单、订单状态变化的站内提醒'
    case 2:
      return '优惠活动与平台营销消息'
    case 3:
      return '账号审核、系统维护与公告'
    case 4:
      return '用户消息与客服回复'
    default:
      return ''
  }
}

async function loadNotifySettings(): Promise<void> {
  if (notifyLoading.value) {
    return
  }
  notifyLoading.value = true
  notifyError.value = ''
  try {
    const data = await fetchNotifySettings()
    notifySettings.value = Array.isArray(data) ? data.filter((item) => !!item) : []
  } catch (err) {
    // 失败保留已有开关状态，只提示错误，不伪造本地开启值
    notifyError.value = (err as Error)?.message || '通知设置加载失败'
  } finally {
    notifyLoading.value = false
  }
}

async function onNotifyChange(
  item: NotifySettingVO,
  event: { detail: { value: boolean } },
): Promise<void> {
  const next = Boolean(event?.detail?.value)
  const previous = item.enabled
  item.enabled = next
  try {
    const updated = await updateNotifySetting(item.notifyType, next)
    item.enabled = Boolean(updated?.enabled ?? next)
  } catch (err) {
    item.enabled = previous
    uni.showToast({ title: (err as Error)?.message || '通知设置保存失败', icon: 'none' })
  }
}

function onVoiceChange(event: { detail: { value: boolean } }): void {
  const enabled = Boolean(event?.detail?.value)
  voiceEnabled.value = enabled
  setVoiceEnabled(enabled)
  uni.showToast({ title: enabled ? '语音播报已开启' : '语音播报已关闭', icon: 'none' })
}

function clearCache(): void {
  uni.showModal({
    title: '清除缓存',
    content: '将清除本地临时数据（如最近定位），登录状态与待上传轨迹不受影响。',
    confirmText: '清除',
    confirmColor: '#D93A2B',
    success: (result) => {
      if (!result.confirm) {
        return
      }
      let removed = 0
      try {
        const info = uni.getStorageInfoSync()
        const keys = Array.isArray(info?.keys) ? info.keys : []
        keys.forEach((key) => {
          if (!PRESERVED_KEYS.includes(key)) {
            uni.removeStorageSync(key)
            removed += 1
          }
        })
      } catch (error) {
        uni.showToast({ title: '缓存清除失败，请稍后重试', icon: 'none' })
        return
      }
      uni.showToast({
        title: removed > 0 ? `已清除 ${removed} 项缓存` : '暂无可清除的缓存',
        icon: 'none',
      })
    },
  })
}

function handleLogout(): void {
  uni.showModal({
    title: '退出登录',
    content: '退出后将停止听单与轨迹上报，确定退出吗？',
    confirmText: '退出',
    confirmColor: '#D93A2B',
    success: (result) => {
      if (!result.confirm) {
        return
      }
      stopTrackReport()
      resetTrackReporter()
      rider.reset()
      auth.logout()
      uni.reLaunch({ url: '/pages/login/Login' })
    },
  })
}
</script>

<style scoped>
.settings {
  padding-bottom: 60rpx;
}

.settings__section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 8rpx 4rpx 14rpx;
}

.settings__section-head--gap {
  margin-top: 32rpx;
}

.settings__section-note {
  font-size: 21rpx;
  color: #a9b4c4;
}

.settings__card {
  padding: 4rpx 24rpx;
}

.settings__hint {
  padding: 28rpx 0;
  font-size: 23rpx;
  color: #8d99aa;
  text-align: center;
}

.settings__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 20rpx 0;
  font-size: 23rpx;
  color: #b42318;
}

.settings__error-retry {
  flex: none;
  font-weight: 700;
  text-decoration: underline;
}

.settings__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  min-height: 112rpx;
  padding: 12rpx 0;
}

.settings__item--tap:active {
  opacity: 0.7;
}

.settings__item-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.settings__item-label {
  font-size: 28rpx;
  font-weight: 500;
  color: #16233a;
}

.settings__item-desc {
  font-size: 21rpx;
  color: #8d99aa;
  line-height: 1.6;
}

.settings__item-value {
  flex: none;
  font-size: 24rpx;
  color: #7a8699;
}

.settings__arrow {
  flex: none;
  font-size: 34rpx;
  color: #c3ccd9;
}

.settings__logout {
  margin-top: 40rpx;
}

.settings__foot {
  margin-top: 32rpx;
  text-align: center;
  font-size: 21rpx;
  color: #a9b4c4;
}
</style>
