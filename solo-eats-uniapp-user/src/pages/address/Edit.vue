<template>
  <view class="edit">
    <view v-if="loading" class="se-loading">正在加载地址…</view>

    <view v-else-if="error" class="se-error">
      <text>{{ error }}</text>
      <view class="se-error__retry" @click="loadExisting">重新加载</view>
    </view>

    <template v-else>
      <view class="se-card edit__block">
        <view class="edit__row">
          <text class="edit__label">收货人</text>
          <input
            v-model="form.receiverName"
            class="edit__input"
            placeholder="请输入收货人姓名"
            placeholder-class="edit__placeholder"
            :maxlength="64"
          />
        </view>

        <view class="edit__row">
          <text class="edit__label">手机号</text>
          <input
            v-model="form.receiverPhone"
            class="edit__input"
            type="number"
            placeholder="请输入 11 位手机号"
            placeholder-class="edit__placeholder"
            :maxlength="11"
          />
        </view>
        <view v-if="maskedPhone" class="edit__tip">
          当前手机号 {{ maskedPhone }} 为脱敏值，保存时需重新输入完整号码
        </view>

        <view class="edit__row">
          <text class="edit__label">省市区</text>
          <picker
            mode="multiSelector"
            class="edit__picker"
            :range="regionRange"
            :value="regionIndexes"
            @columnchange="onColumnChange"
            @change="onRegionChange"
          >
            <view class="edit__picker-value" :class="{ 'is-placeholder': !regionSelected }">
              {{ regionDisplay }}
            </view>
          </picker>
        </view>
        <view class="edit__tip">
          区域表为常用省市精简版，未列出的区县请写在详细地址里
        </view>

        <view class="edit__row edit__row--top">
          <text class="edit__label">详细地址</text>
          <textarea
            v-model="form.address"
            class="edit__textarea"
            placeholder="街道、小区、楼栋，例如：星光路 18 号 3 栋"
            placeholder-class="edit__placeholder"
            :maxlength="255"
          />
        </view>

        <view class="edit__row">
          <text class="edit__label">门牌号</text>
          <input
            v-model="form.houseNumber"
            class="edit__input"
            placeholder="如 3 单元 502 室（选填）"
            placeholder-class="edit__placeholder"
            :maxlength="64"
          />
        </view>
      </view>

      <view class="se-card edit__block">
        <view class="edit__block-title">地址标签</view>
        <view class="edit__tags">
          <view
            v-for="tag in TAGS"
            :key="tag"
            class="edit__tag"
            :class="{ 'is-active': form.tag === tag }"
            @click="toggleTag(tag)"
          >
            {{ tag }}
          </view>
        </view>
      </view>

      <view class="se-card edit__block se-between">
        <view>
          <text class="edit__block-title">设为默认地址</text>
          <view class="edit__tip edit__tip--inline">下单时优先选中该地址</view>
        </view>
        <switch :checked="form.isDefault" color="#FF7A00" @change="onDefaultChange" />
      </view>

      <view class="edit__help" @click="goRegionHelp">
        地址填写遇到问题？查看地址填写帮助
      </view>

      <button class="se-btn se-btn-primary edit__submit" :disabled="saving" @click="submit">
        {{ saving ? '保存中…' : '保存地址' }}
      </button>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createAddress, fetchAddresses, updateAddress } from '@/api/user'
import type { AddressVO } from '@/api/types'
import {
  REGION_PROVINCES,
  findRegionIndexes,
  matchesRegion,
} from '@/utils/regions'
import { useAuthStore } from '@/stores/auth'

/**
 * 新增 / 编辑收货地址。
 *
 * 手机号：AddressView 只返回掩码（如 138****8000），无法回填 input，
 * 编辑时要求用户重新输入完整号码；后端 SaveAddressRequest 的
 * @Pattern(^1[3-9]\d{9}$) 是最终校验。
 *
 * 省市区：后端只做长度校验，不校验行政编码，因此使用内置精简表 + 手填详细地址。
 */

const TAGS = ['家', '公司', '学校']
const PHONE_PATTERN = /^1[3-9]\d{9}$/

const auth = useAuthStore()

const id = ref(0)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const maskedPhone = ref('')
const existing = ref<AddressVO | null>(null)

const province = ref('')
const city = ref('')
const district = ref('')
const regionIndexes = ref<[number, number, number]>([0, 0, 0])

const form = reactive({
  receiverName: '',
  receiverPhone: '',
  address: '',
  houseNumber: '',
  tag: '',
  isDefault: false,
})

const regionRange = computed<string[][]>(() => {
  const provinceItem = REGION_PROVINCES[regionIndexes.value[0]] || REGION_PROVINCES[0]
  const cityItem = provinceItem.cities[regionIndexes.value[1]] || provinceItem.cities[0]
  return [
    REGION_PROVINCES.map((item) => item.name),
    provinceItem.cities.map((item) => item.name),
    cityItem.districts,
  ]
})

const regionSelected = computed(() => Boolean(province.value && city.value && district.value))

const regionDisplay = computed(() => {
  if (!regionSelected.value) {
    return '请选择省 / 市 / 区'
  }
  return [province.value, city.value, district.value].join(' ')
})

onLoad((options) => {
  const parsed = Number(options?.id)
  if (Number.isFinite(parsed) && parsed > 0) {
    id.value = parsed
    uni.setNavigationBarTitle({ title: '编辑收货地址' })
  } else {
    uni.setNavigationBarTitle({ title: '新增收货地址' })
  }
  if (!auth.ensureLogin('/pages/address/List')) {
    return
  }
  if (id.value > 0) {
    loadExisting()
  }
})

async function loadExisting(): Promise<void> {
  if (id.value <= 0 || loading.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const list = await fetchAddresses()
    const target = list.find((item) => item.id === id.value)
    if (!target) {
      error.value = '地址不存在或已被删除'
      return
    }
    applyExisting(target)
  } catch (err) {
    error.value = (err as Error)?.message || '地址加载失败'
  } finally {
    loading.value = false
  }
}

function applyExisting(target: AddressVO): void {
  existing.value = target
  form.receiverName = target.receiverName || ''
  // 后端只返回掩码，不能直接放进 type=number 的 input
  maskedPhone.value = target.receiverPhone || ''
  form.receiverPhone = ''
  form.address = target.address || ''
  form.houseNumber = target.houseNumber || ''
  form.tag = target.tag || ''
  form.isDefault = target.isDefault
  province.value = target.province || ''
  city.value = target.city || ''
  district.value = target.district || ''
  if (matchesRegion(target.province, target.city, target.district)) {
    regionIndexes.value = findRegionIndexes(target.province, target.city, target.district)
  }
}

function onColumnChange(event: { detail: { column: number; value: number } }): void {
  const next: [number, number, number] = [
    regionIndexes.value[0],
    regionIndexes.value[1],
    regionIndexes.value[2],
  ]
  next[event.detail.column] = event.detail.value
  if (event.detail.column === 0) {
    next[1] = 0
    next[2] = 0
  } else if (event.detail.column === 1) {
    next[2] = 0
  }
  regionIndexes.value = next
}

function onRegionChange(event: { detail: { value: number[] } }): void {
  const [provinceIndex, cityIndex, districtIndex] = event.detail.value
  regionIndexes.value = [provinceIndex, cityIndex, districtIndex]
  const provinceItem = REGION_PROVINCES[provinceIndex] || REGION_PROVINCES[0]
  const cityItem = provinceItem.cities[cityIndex] || provinceItem.cities[0]
  province.value = provinceItem.name
  city.value = cityItem.name
  district.value = cityItem.districts[districtIndex] || cityItem.districts[0]
}

function toggleTag(tag: string): void {
  form.tag = form.tag === tag ? '' : tag
}

function onDefaultChange(event: { detail: { value: boolean } }): void {
  form.isDefault = event.detail.value
}

/** 去掉首尾空白与换行，避免把复制来的地址原样入库。 */
function normalize(value: string): string {
  return String(value || '').replace(/[\r\n\t]/g, ' ').trim()
}

async function submit(): Promise<void> {
  if (saving.value) {
    return
  }
  const receiverName = normalize(form.receiverName)
  const receiverPhone = normalize(form.receiverPhone)
  const detailAddress = normalize(form.address)
  const houseNumber = normalize(form.houseNumber)

  if (!receiverName) {
    uni.showToast({ title: '请填写收货人', icon: 'none' })
    return
  }
  if (!PHONE_PATTERN.test(receiverPhone)) {
    uni.showToast({ title: '请输入 11 位手机号', icon: 'none' })
    return
  }
  if (!detailAddress) {
    uni.showToast({ title: '请填写详细地址', icon: 'none' })
    return
  }

  const payload = {
    receiverName,
    receiverPhone,
    province: province.value || null,
    city: city.value || null,
    district: district.value || null,
    address: detailAddress,
    houseNumber: houseNumber || null,
    lng: existing.value?.lng ?? null,
    lat: existing.value?.lat ?? null,
    tag: form.tag || null,
    isDefault: form.isDefault,
  }

  saving.value = true
  try {
    const saved =
      id.value > 0 ? await updateAddress(id.value, payload) : await createAddress(payload)
    // 选中本次新增/编辑的地址，返回列表后下单页也能通过 address:last 复用
    if (saved && saved.id) {
      uni.setStorageSync('address:last', saved.id)
    }
    uni.showToast({ title: '地址已保存', icon: 'none' })
    setTimeout(() => {
      uni.navigateBack()
    }, 400)
  } catch (err) {
    // http 层已 toast，保留表单内容供用户修改
  } finally {
    saving.value = false
  }
}

function goRegionHelp(): void {
  uni.navigateTo({ url: '/pages/address/Region' })
}
</script>

<style scoped>
.edit {
  min-height: 100vh;
  padding: 20rpx 24rpx 60rpx;
  background-color: #f5f5f7;
}

.edit__block {
  padding: 8rpx 24rpx;
  margin-bottom: 16rpx;
}

.edit__block-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #1f1f1f;
}

.edit__row {
  display: flex;
  align-items: center;
  min-height: 96rpx;
  border-bottom: 1rpx solid #f5f5f5;
}

.edit__row--top {
  align-items: flex-start;
  padding: 24rpx 0;
}

.edit__block > .edit__row:last-child {
  border-bottom: none;
}

.edit__label {
  width: 150rpx;
  flex-shrink: 0;
  font-size: 27rpx;
  color: #444444;
}

.edit__input {
  flex: 1;
  min-width: 0;
  font-size: 27rpx;
  color: #1f1f1f;
  text-align: right;
}

.edit__textarea {
  flex: 1;
  width: auto;
  height: 150rpx;
  font-size: 27rpx;
  line-height: 1.6;
  color: #1f1f1f;
}

.edit__placeholder {
  color: #b8b8b8;
}

.edit__picker {
  flex: 1;
  min-width: 0;
}

.edit__picker-value {
  font-size: 27rpx;
  color: #1f1f1f;
  text-align: right;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.edit__picker-value.is-placeholder {
  color: #b8b8b8;
}

.edit__tip {
  font-size: 22rpx;
  line-height: 1.6;
  color: #a26a2a;
  padding: 8rpx 0 12rpx;
}

.edit__tip--inline {
  padding: 4rpx 0 0;
  color: #8a8a8a;
}

.edit__tags {
  display: flex;
  gap: 20rpx;
  padding: 20rpx 0 24rpx;
}

.edit__tag {
  padding: 10rpx 32rpx;
  border-radius: 8rpx;
  border: 1rpx solid #eeeeee;
  background-color: #fafafa;
  font-size: 24rpx;
  color: #555555;
}

.edit__tag.is-active {
  border-color: #ff7a00;
  background-color: #fff2e6;
  color: #ff7a00;
}

.edit__help {
  padding: 20rpx 8rpx;
  font-size: 24rpx;
  color: #ff7a00;
  text-align: center;
}

.edit__submit {
  width: 100%;
  margin-top: 8rpx;
}
</style>
