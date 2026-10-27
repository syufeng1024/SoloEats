<template>
  <view class="settings">
    <view class="settings__notice">
      开关保存到后端 <text class="settings__code">GET/PUT /api/v1/notify/settings</text>；
      没有记录的通知类型默认开启，关闭后仅影响站内推送，不影响站内信留存。
    </view>

    <view v-if="loading" class="se-loading">正在加载通知设置…</view>

    <view v-else-if="error" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="load">重新加载</view>
    </view>

    <view v-else-if="list.length === 0" class="se-empty">
      <text class="se-empty__title">暂无可配置的通知类型</text>
      <text>后端未返回任何 notifyType</text>
    </view>

    <view v-else class="se-card settings__card">
      <view v-for="item in list" :key="item.notifyType" class="settings__row">
        <view class="settings__copy">
          <text class="settings__name">{{ typeName(item) }}</text>
          <text class="settings__desc">{{ typeDesc(item.notifyType) }}</text>
        </view>
        <switch
          :checked="item.enabled"
          :disabled="Boolean(pending[item.notifyType])"
          color="#ff7a00"
          @change="onToggle(item, $event)"
        />
      </view>
    </view>

    <view class="settings__hint">
      <text>修改后立即生效。若推送被关闭，对应的站内信仍可在消息列表查看。</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchNotifySettings, updateNotifySetting } from '@/api/message'
import type { NotifySettingVO } from '@/api/message'

interface SwitchEvent {
  detail: { value: boolean }
}

const TYPE_LABELS: Record<number, string> = {
  1: '订单通知',
  2: '活动通知',
  3: '系统通知',
  4: '会话通知',
}

const TYPE_DESCS: Record<number, string> = {
  1: '接单、出餐、配送与退款等订单状态变化',
  2: '优惠券、满减、秒杀等活动消息',
  3: '平台公告与账号相关通知',
  4: '商家、骑手或客服的新消息提醒',
}

const list = ref<NotifySettingVO[]>([])
const loading = ref(false)
const error = ref('')
const pending = reactive<Record<number, boolean>>({})

onShow(() => {
  load()
})

async function load(): Promise<void> {
  if (loading.value) {
    return
  }
  loading.value = true
  error.value = ''
  let loadingShown = false
  if (list.value.length === 0) {
    uni.showLoading({ title: '正在加载', mask: true })
    loadingShown = true
  }
  try {
    const result = await fetchNotifySettings()
    list.value = (Array.isArray(result) ? result : []).map((item) => ({
      notifyType: Number(item.notifyType),
      notifyTypeName: item.notifyTypeName,
      enabled: item.enabled === true,
    }))
  } catch (err) {
    // 保留已有开关状态，失败只展示错误
    error.value = (err as Error)?.message || '通知设置加载失败'
  } finally {
    loading.value = false
    if (loadingShown) {
      uni.hideLoading()
    }
  }
}

function typeName(item: NotifySettingVO): string {
  return item.notifyTypeName || TYPE_LABELS[item.notifyType] || `通知 ${item.notifyType}`
}

function typeDesc(notifyType: number): string {
  return TYPE_DESCS[notifyType] || '该类型的推送提醒'
}

async function onToggle(item: NotifySettingVO, event: SwitchEvent): Promise<void> {
  const enabled = event?.detail?.value === true
  if (pending[item.notifyType]) {
    return
  }
  const previous = item.enabled
  item.enabled = enabled
  pending[item.notifyType] = true
  try {
    const updated = await updateNotifySetting(item.notifyType, enabled)
    item.enabled = updated?.enabled === true
    uni.showToast({ title: enabled ? '已开启' : '已关闭', icon: 'none' })
  } catch (err) {
    // 失败回滚开关，避免界面状态与后端不一致
    item.enabled = previous
    uni.showToast({ title: '保存失败，请重试', icon: 'none' })
  } finally {
    pending[item.notifyType] = false
  }
}
</script>

<style scoped>
.settings {
  min-height: 100vh;
  padding: 24rpx;
  background-color: #f5f5f7;
}

.settings__notice {
  padding: 20rpx 24rpx;
  margin-bottom: 20rpx;
  border-radius: 8rpx;
  background-color: #fff8ef;
  color: #a26a2a;
  font-size: 22rpx;
  line-height: 1.7;
}

.settings__code {
  font-family: monospace;
  font-size: 21rpx;
}

.settings__card {
  padding: 0 24rpx;
}

.settings__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  padding: 26rpx 0;
  border-bottom: 1rpx solid #f5f5f5;
}

.settings__row:last-child {
  border-bottom: none;
}

.settings__copy {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.settings__name {
  font-size: 30rpx;
  color: #1f1f1f;
}

.settings__desc {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #8a8a8a;
  line-height: 1.5;
}

.settings__hint {
  padding: 20rpx 8rpx;
  color: #a0a0a0;
  font-size: 22rpx;
  line-height: 1.6;
}
</style>
