<template>
  <view class="addr">
    <view v-if="selectMode" class="addr__hint">
      点击地址即可选中并返回上一页
    </view>

    <view v-if="loading && list.length === 0" class="se-loading">正在加载收货地址…</view>

    <view v-else-if="error && list.length === 0" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="load">重新加载</view>
    </view>

    <view v-else-if="list.length === 0" class="se-empty">
      <text class="se-empty__title">还没有收货地址</text>
      <text>添加地址后下单可直接选用</text>
      <view class="se-error__retry addr__empty-btn" @click="goCreate">新增地址</view>
    </view>

    <template v-else>
      <view v-if="error" class="addr__error-bar">
        <text>{{ error }}</text>
        <text class="addr__error-retry" @click="load">重试</text>
      </view>

      <view
        v-for="item in list"
        :key="item.id"
        class="se-card addr__card"
        @click="onCardTap(item)"
      >
        <view class="addr__body">
          <view class="addr__head">
            <text class="addr__name">{{ item.receiverName }}</text>
            <text class="addr__phone">{{ item.receiverPhone }}</text>
            <text v-if="item.isDefault" class="addr__badge">默认</text>
            <text v-if="item.tag" class="addr__tag">{{ item.tag }}</text>
          </view>
          <text class="addr__text">{{ fullAddress(item) }}</text>

          <view class="addr__actions" @click.stop>
            <view
              v-if="!item.isDefault"
              class="addr__action"
              :class="{ 'is-busy': busyId === item.id }"
              @click="handleSetDefault(item)"
            >
              {{ busyId === item.id ? '处理中…' : '设为默认' }}
            </view>
            <view class="addr__action" @click="goEdit(item)">编辑</view>
            <view class="addr__action addr__action--danger" @click="handleDelete(item)">删除</view>
          </view>
        </view>

        <view
          v-if="selectMode"
          class="addr__radio"
          :class="{ 'is-active': item.id === selectedId }"
        >
          <view v-if="item.id === selectedId" class="addr__radio-dot" />
        </view>
      </view>

      <view class="addr__end">共 {{ list.length }} 个收货地址</view>
    </template>

    <view class="se-fixed-bottom addr__bar">
      <button class="se-btn se-btn-primary addr__add" @click="goCreate">新增收货地址</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import {
  deleteAddress,
  fetchAddresses,
  setDefaultAddress,
} from '@/api/user'
import type { AddressVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'

/**
 * 收货地址列表。
 *
 * 两种模式：
 * - 默认（管理）：点击卡片进入编辑；
 * - mode=select：点击卡片后写 address:last 并 emit('address:selected')，随后返回上一页。
 *
 * 后端 AddressView#receiverPhone 只返回掩码，编辑页必须要求用户重新输入完整号码。
 */

const auth = useAuthStore()

const selectMode = ref(false)
const list = ref<AddressVO[]>([])
const selectedId = ref(0)
const loading = ref(false)
const error = ref('')
const busyId = ref(0)

onLoad((options) => {
  selectMode.value = options?.mode === 'select'
  selectedId.value = Number(options?.selectedId) || 0
  if (!auth.ensureLogin('/pages/address/List')) {
    return
  }
  load()
})

onShow(() => {
  // 从编辑页返回后刷新；首次 onLoad 已经加载过，避免重复请求
  if (list.value.length > 0 || !loading.value) {
    load()
  }
})

onPullDownRefresh(() => {
  load()
})

async function load(): Promise<void> {
  if (loading.value) {
    uni.stopPullDownRefresh()
    return
  }
  loading.value = true
  error.value = ''
  try {
    list.value = await fetchAddresses()
  } catch (err) {
    // 保留已有数据，只提示本次刷新失败
    error.value = (err as Error)?.message || '收货地址加载失败'
    uni.showToast({ title: error.value, icon: 'none' })
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

function fullAddress(item: AddressVO): string {
  const parts = [item.province, item.city, item.district, item.address, item.houseNumber]
  return parts.filter((part) => Boolean(part)).join(' ')
}

function onCardTap(item: AddressVO): void {
  if (!selectMode.value) {
    goEdit(item)
    return
  }
  selectedId.value = item.id
  uni.setStorageSync('address:last', item.id)
  uni.$emit('address:selected', item)
  uni.navigateBack()
}

function goCreate(): void {
  uni.navigateTo({ url: '/pages/address/Edit' })
}

function goEdit(item: AddressVO): void {
  uni.navigateTo({ url: `/pages/address/Edit?id=${item.id}` })
}

async function handleSetDefault(item: AddressVO): Promise<void> {
  if (busyId.value > 0) {
    return
  }
  busyId.value = item.id
  try {
    await setDefaultAddress(item.id)
    list.value = list.value.map((address) => ({
      ...address,
      isDefault: address.id === item.id,
    }))
    uni.showToast({ title: '已设为默认地址', icon: 'none' })
  } catch (err) {
    // http 层已 toast，保留原列表
  } finally {
    busyId.value = 0
  }
}

function handleDelete(item: AddressVO): void {
  uni.showModal({
    title: '删除地址',
    content: `确认删除「${item.receiverName} ${item.receiverPhone}」这个收货地址？`,
    confirmText: '删除',
    confirmColor: '#e5484d',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      try {
        await deleteAddress(item.id)
        list.value = list.value.filter((address) => address.id !== item.id)
        uni.showToast({ title: '地址已删除', icon: 'none' })
        // 删除默认地址后后端会自动提升最近一条为默认，重新拉取才能反映
        await load()
      } catch (err) {
        // http 层已 toast
      }
    },
  })
}
</script>

<style scoped>
.addr {
  min-height: 100vh;
  padding: 20rpx 24rpx calc(180rpx + env(safe-area-inset-bottom));
  background-color: #f5f5f7;
}

.addr__hint {
  margin-bottom: 16rpx;
  padding: 16rpx 20rpx;
  border-radius: 8rpx;
  background-color: #fff8ef;
  color: #a26a2a;
  font-size: 24rpx;
}

.addr__error-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
  padding: 14rpx 16rpx;
  border-radius: 8rpx;
  background-color: #fff8ef;
  color: #a26a2a;
  font-size: 22rpx;
}

.addr__error-retry {
  color: #ff7a00;
  margin-left: 16rpx;
}

.addr__empty-btn {
  margin-top: 24rpx;
}

.addr__card {
  display: flex;
  align-items: flex-start;
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.addr__radio {
  width: 40rpx;
  height: 40rpx;
  margin: 4rpx 0 0 20rpx;
  border-radius: 50%;
  border: 2rpx solid #d9d9d9;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.addr__radio.is-active {
  border-color: #ff7a00;
}

.addr__radio-dot {
  width: 22rpx;
  height: 22rpx;
  border-radius: 50%;
  background-color: #ff7a00;
}

.addr__body {
  flex: 1;
  min-width: 0;
}

.addr__head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-bottom: 10rpx;
}

.addr__name {
  font-size: 30rpx;
  font-weight: 600;
  color: #1f1f1f;
}

.addr__phone {
  font-size: 26rpx;
  color: #555555;
}

.addr__badge {
  padding: 2rpx 10rpx;
  border-radius: 4rpx;
  font-size: 20rpx;
  color: #ffffff;
  background-color: #ff7a00;
}

.addr__tag {
  padding: 2rpx 10rpx;
  border-radius: 4rpx;
  font-size: 20rpx;
  color: #555555;
  background-color: #f2f2f4;
}

.addr__text {
  display: block;
  font-size: 26rpx;
  line-height: 1.6;
  color: #444444;
  word-break: break-all;
}

.addr__actions {
  display: flex;
  align-items: center;
  gap: 28rpx;
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f5f5f5;
}

.addr__action {
  font-size: 24rpx;
  color: #666666;
}

.addr__action.is-busy {
  color: #b8b8b8;
}

.addr__action--danger {
  color: #e5484d;
  margin-left: auto;
}

.addr__end {
  padding: 24rpx 0;
  text-align: center;
  font-size: 22rpx;
  color: #a0a0a0;
}

.addr__bar {
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
}

.addr__add {
  width: 100%;
}
</style>
