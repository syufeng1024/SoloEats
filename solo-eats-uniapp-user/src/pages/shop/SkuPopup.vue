<template>
  <view v-if="visible" class="sku-mask" @click="close">
    <view class="sku" @click.stop>
      <view class="sku__head">
        <text class="sku__title">{{ item ? item.spuName : '选择规格' }}</text>
        <text class="sku__close" @click="close">×</text>
      </view>

      <scroll-view scroll-y class="sku__body">
        <view v-if="!item" class="se-empty">商品信息加载中…</view>

        <template v-else>
          <view v-if="item.skus.length === 0" class="se-empty">该商品暂无可售规格</view>

          <view v-if="item.skus.length > 0" class="sku__section">
            <view class="sku__section-title">规格</view>
            <view class="sku__options">
              <view
                v-for="sku in item.skus"
                :key="sku.skuId"
                class="sku__option"
                :class="{ 'is-active': sku.skuId === selectedSkuId, 'is-disabled': isSkuDisabled(sku) }"
                @click="selectSku(sku)"
              >
                <text>{{ sku.skuName }}</text>
                <text class="sku__option-price">¥{{ fenToYuan(sku.price) }}</text>
              </view>
            </view>
          </view>

          <view v-for="group in item.addonGroups" :key="group.groupId" class="sku__section">
            <view class="sku__section-title">
              {{ group.groupName }}
              <text class="sku__section-hint">{{ groupHintText(group) }}</text>
            </view>
            <view class="sku__options">
              <view
                v-for="addon in group.items"
                :key="addon.itemId"
                class="sku__option"
                :class="{
                  'is-active': isAddonSelected(group.groupId, addon.itemId),
                  'is-disabled': addon.stock === 0,
                }"
                @click="toggleAddon(group, addon)"
              >
                <text>{{ addon.itemName }}</text>
                <text class="sku__option-price">+¥{{ fenToYuan(addon.price) }}</text>
              </view>
            </view>
          </view>

          <view class="sku__qty">
            <text>数量</text>
            <view class="se-stepper">
              <view
                class="se-stepper__btn"
                :class="{ 'is-disabled': qty <= minQty }"
                @click="decreaseQty"
              >
                −
              </view>
              <text class="se-stepper__value">{{ qty }}</text>
              <view
                class="se-stepper__btn"
                :class="{ 'is-disabled': qty >= maxQty }"
                @click="increaseQty"
              >
                +
              </view>
            </view>
          </view>

          <view class="sku__note">
            加料金额暂不计入订单金额：后端下单接口只接收 skuId 与数量，实付以提交后服务端返回为准。
          </view>
        </template>
      </scroll-view>

      <view class="sku__foot">
        <view class="sku__amount">
          <text class="se-label">小计</text>
          <text class="se-price sku__amount-value">{{ fenToYuan(skuAmount) }}</text>
        </view>
        <button class="se-btn se-btn-primary sku__confirm" :disabled="!canConfirm" @click="confirm">
          {{ confirmText }}
        </button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { CartAddon, CartLine } from '@/stores/cart'
import type { MenuAddonGroupVO, MenuAddonItemVO, MenuSkuVO, SpuVO } from '@/api/types'
import { fenToYuan } from '@/utils/money'

const props = defineProps<{
  visible: boolean
  item: SpuVO | null
}>()

const emit = defineEmits<{
  (event: 'update:visible', value: boolean): void
  (event: 'confirm', line: CartLine): void
}>()

const selectedSkuId = ref<number | null>(null)
const addonSelection = reactive<Record<number, number[]>>({})
const qty = ref(1)

const selectedSku = computed<MenuSkuVO | null>(() => {
  if (!props.item) {
    return null
  }
  return props.item.skus.find((sku) => sku.skuId === selectedSkuId.value) || null
})

const selectedAddons = computed<MenuAddonItemVO[]>(() => {
  if (!props.item) {
    return []
  }
  const result: MenuAddonItemVO[] = []
  props.item.addonGroups.forEach((group) => {
    const ids = addonSelection[group.groupId] || []
    group.items.forEach((addon) => {
      if (ids.includes(addon.itemId)) {
        result.push(addon)
      }
    })
  })
  return result
})

const minQty = computed(() => Math.max(1, props.item?.minPurchaseQty || 1))

const maxQty = computed(() => {
  const limit = props.item?.limitPerOrder || 99
  const stock = selectedSku.value?.availableQty
  // availableQty 为 null 表示后端不限库存；只有明确的正数才作为上限
  return stock && stock > 0 ? Math.min(limit, stock) : limit
})

/** 只算 SKU 价 × 数量；加料不参与后端计价，避免页面金额与实付不一致 */
const skuAmount = computed(() => (selectedSku.value ? selectedSku.value.price * qty.value : 0))

const groupHint = computed(() => {
  if (!props.item) {
    return ''
  }
  for (const group of props.item.addonGroups) {
    const min = group.minSelect || 0
    const selected = (addonSelection[group.groupId] || []).length
    if (selected < min) {
      return `请选择「${group.groupName}」${min} 项`
    }
  }
  return ''
})

const canConfirm = computed(() => {
  if (!props.item || props.item.soldOut || !selectedSku.value || selectedSku.value.soldOut) {
    return false
  }
  return groupHint.value === '' && qty.value >= minQty.value
})

const confirmText = computed(() => {
  if (!props.item) {
    return '加入购物车'
  }
  if (props.item.soldOut || (selectedSku.value && selectedSku.value.soldOut)) {
    return '已售完'
  }
  if (groupHint.value) {
    return groupHint.value
  }
  return '加入购物车'
})

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      resetSelection()
    }
  },
)

watch(
  () => props.item,
  () => {
    if (props.visible) {
      resetSelection()
    }
  },
)

function resetSelection(): void {
  Object.keys(addonSelection).forEach((key) => {
    delete addonSelection[Number(key)]
  })
  qty.value = Math.max(1, props.item?.minPurchaseQty || 1)
  const firstAvailable = props.item?.skus.find((sku) => !isSkuDisabled(sku)) || null
  selectedSkuId.value = firstAvailable ? firstAvailable.skuId : null
}

function isSkuDisabled(sku: MenuSkuVO): boolean {
  return sku.soldOut || sku.availableQty === 0
}

function selectSku(sku: MenuSkuVO): void {
  if (isSkuDisabled(sku)) {
    uni.showToast({ title: '该规格已售完', icon: 'none' })
    return
  }
  selectedSkuId.value = sku.skuId
  qty.value = Math.min(Math.max(qty.value, minQty.value), maxQty.value)
}

function isAddonSelected(groupId: number, itemId: number): boolean {
  return (addonSelection[groupId] || []).includes(itemId)
}

function groupHintText(group: MenuAddonGroupVO): string {
  const min = group.minSelect || 0
  const max = group.maxSelect || 0
  if (min > 0 && max > 0) {
    return `（必选 ${min} 项，最多 ${max} 项）`
  }
  if (min > 0) {
    return `（必选 ${min} 项）`
  }
  if (max > 0) {
    return `（最多 ${max} 项）`
  }
  return '（可选）'
}

function toggleAddon(group: MenuAddonGroupVO, addon: MenuAddonItemVO): void {
  if (addon.stock === 0) {
    return
  }
  const current = [...(addonSelection[group.groupId] || [])]
  const index = current.indexOf(addon.itemId)
  if (index >= 0) {
    current.splice(index, 1)
  } else {
    const max = group.maxSelect || 0
    if (max > 0 && current.length >= max) {
      uni.showToast({ title: `「${group.groupName}」最多选 ${max} 项`, icon: 'none' })
      return
    }
    current.push(addon.itemId)
  }
  addonSelection[group.groupId] = current
}

function decreaseQty(): void {
  if (qty.value <= minQty.value) {
    return
  }
  qty.value -= 1
}

function increaseQty(): void {
  if (qty.value >= maxQty.value) {
    uni.showToast({ title: '已达可购买上限', icon: 'none' })
    return
  }
  qty.value += 1
}

function close(): void {
  emit('update:visible', false)
}

function confirm(): void {
  if (!canConfirm.value || !props.item || !selectedSku.value) {
    return
  }
  const addons: CartAddon[] = selectedAddons.value.map((addon) => ({
    itemId: addon.itemId,
    itemName: addon.itemName,
    price: addon.price,
  }))
  emit('confirm', {
    skuId: selectedSku.value.skuId,
    skuName: selectedSku.value.skuName,
    qty: qty.value,
    price: selectedSku.value.price,
    specJson: selectedSku.value.specJson || '',
    addons,
    spuId: props.item.spuId,
    spuName: props.item.spuName,
    imageUrl: selectedSku.value.imageUrl || props.item.mainImage || '',
  })
  close()
}
</script>

<style scoped>
.sku-mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  background-color: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: flex-end;
}

.sku {
  width: 100%;
  max-height: 86vh;
  background-color: #ffffff;
  border-radius: 16rpx 16rpx 0 0;
  display: flex;
  flex-direction: column;
}

.sku__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 28rpx 16rpx;
}

.sku__title {
  font-size: 32rpx;
  font-weight: 600;
}

.sku__close {
  padding: 0 12rpx;
  font-size: 44rpx;
  line-height: 1;
  color: #999999;
}

.sku__body {
  flex: 1;
  max-height: 60vh;
  padding: 0 28rpx;
}

.sku__section {
  margin-bottom: 28rpx;
}

.sku__section-title {
  font-size: 26rpx;
  font-weight: 600;
  margin-bottom: 16rpx;
}

.sku__section-hint {
  font-weight: 400;
  color: #999999;
  font-size: 22rpx;
}

.sku__options {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.sku__option {
  display: flex;
  align-items: center;
  padding: 12rpx 20rpx;
  border-radius: 8rpx;
  border: 1rpx solid #eeeeee;
  background-color: #fafafa;
  font-size: 24rpx;
  color: #444444;
}

.sku__option-price {
  margin-left: 8rpx;
  font-size: 22rpx;
  color: #8a8a8a;
}

.sku__option.is-active {
  border-color: #ff7a00;
  background-color: #fff2e6;
  color: #ff7a00;
}

.sku__option.is-active .sku__option-price {
  color: #ff7a00;
}

.sku__option.is-disabled {
  opacity: 0.4;
}

.sku__qty {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 0 24rpx;
}

.sku__note {
  padding: 16rpx;
  margin-bottom: 24rpx;
  border-radius: 8rpx;
  background-color: #fff8ef;
  color: #a26a2a;
  font-size: 20rpx;
  line-height: 1.6;
}

.sku__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 28rpx calc(20rpx + env(safe-area-inset-bottom));
  border-top: 1rpx solid #f0f0f0;
}

.sku__amount {
  display: flex;
  align-items: baseline;
}

.sku__amount-value {
  margin-left: 8rpx;
  font-size: 38rpx;
}

.sku__confirm {
  min-width: 260rpx;
}
</style>
