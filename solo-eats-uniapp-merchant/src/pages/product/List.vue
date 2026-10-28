<template>
  <view class="product">
    <view class="product__toolbar">
      <text class="product__count">共 {{ total }} 个商品</text>
      <view class="product__toolbar-actions">
        <text class="product__tool" @click="load(true)">刷新</text>
        <text class="product__tool" :class="{ 'is-active': selectMode }" @click="toggleSelectMode">
          {{ selectMode ? '退出批量' : '批量估清' }}
        </text>
      </view>
    </view>

    <view class="product__body">
      <view v-if="loading && spus.length === 0" class="mt-loading">正在加载商品…</view>

      <view v-else-if="error && spus.length === 0" class="mt-error">
        <text class="mt-error__msg">{{ error }}</text>
        <view class="mt-error__retry" @click="load(true)">重新加载</view>
      </view>

      <view v-else-if="spus.length === 0" class="mt-empty product__empty">
        <text class="mt-empty__title">暂无已上架商品</text>
        <text>商品由商家中心 Web 端上架到门店后，会出现在这里</text>
      </view>

      <template v-else>
        <view v-if="skuError" class="product__sku-error" @click="loadSkus">
          {{ skuError }} · 点击重试（库存与估清不可用）
        </view>

        <view v-for="spu in spus" :key="spu.id" class="product__card">
          <view class="product__head" @click="onCardClick(spu)">
            <view v-if="selectMode" class="product__check" :class="{ 'is-on': isSelected(spu.id) }">
              {{ isSelected(spu.id) ? '✓' : '' }}
            </view>
            <image
              v-if="spu.mainImage"
              :src="spu.mainImage"
              mode="aspectFill"
              class="product__thumb"
            />
            <view v-else class="product__thumb product__thumb--empty">图</view>

            <view class="product__info">
              <text class="product__name">{{ spu.spuName }}</text>
              <view class="product__meta">
                <text class="mt-price product__price">{{ fenToYuan(spu.priceOverride ?? spu.skuMinPrice) }}</text>
                <text class="product__sales">月售 {{ spu.monthSales ?? 0 }}</text>
              </view>
            </view>

            <text class="mt-tag product__status" :class="spuStatusClass(spu.status)">
              {{ spuStatusText(spu.status) }}
            </text>
          </view>

          <view v-if="!selectMode" class="product__ops">
            <view class="product__op">
              <text class="product__op-label">上架</text>
              <switch
                :key="`shelf-${spu.id}-${switchKey}`"
                :checked="spu.status === 0"
                :disabled="operatingId === spu.id"
                color="#FF7A00"
                class="mt-switch"
                @change="onShelfToggle(spu, $event)"
              />
            </view>
            <view class="product__op">
              <text class="product__op-label">估清</text>
              <switch
                :key="`sold-${spu.id}-${switchKey}`"
                :checked="spu.status === 2"
                :disabled="operatingId === spu.id"
                color="#FF7A00"
                class="mt-switch"
                @change="onSoldOutToggle(spu, $event)"
              />
            </view>
          </view>

          <!-- SKU 列表：库存与规格级估清 -->
          <view class="product__skus">
            <view v-if="(skusBySpu[spu.spuId] || []).length === 0" class="product__no-sku">
              暂无规格数据{{ skuError ? '（库存接口异常）' : '' }}
            </view>

            <view
              v-for="sku in skusBySpu[spu.spuId] || []"
              :key="sku.skuId"
              class="product__sku"
            >
              <view class="product__sku-main">
                <text class="product__sku-name">{{ sku.skuName }}</text>
                <text v-if="specText(sku.specJson)" class="product__sku-spec">{{ specText(sku.specJson) }}</text>
              </view>

              <view class="product__sku-stock">
                <text class="product__stock-label">库存</text>
                <text class="product__stock-value" :class="{ 'is-zero': (sku.availableQty ?? 0) <= 0 }">
                  {{ sku.availableQty ?? '--' }}
                </text>
              </view>

              <text
                v-if="sku.soldOut"
                class="mt-tag mt-tag--warn product__sku-tag"
              >已估清</text>

              <button
                class="mt-btn mt-btn--ghost mt-btn--small product__sku-btn"
                :disabled="skuError !== ''"
                @click="openStockEditor(sku)"
              >
                改库存
              </button>

              <switch
                :key="`sku-${sku.skuId}-${switchKey}`"
                :checked="sku.soldOut === true"
                :disabled="skuError !== '' || operatingSkuId === sku.skuId"
                color="#FF7A00"
                class="mt-switch"
                @change="onSkuSoldOutToggle(sku, $event)"
              />
            </view>
          </view>
        </view>

        <view v-if="hasMore" class="product__more" @click="loadMore">
          {{ loading ? '加载中…' : '加载更多' }}
        </view>
        <view v-else class="product__end">没有更多商品了</view>
      </template>
    </view>

    <!-- 批量估清操作条 -->
    <view v-if="selectMode" class="mt-fixed-bottom product__batch">
      <view class="product__batch-main">
        <text class="product__batch-count">已选 {{ selectedIds.size }} 个商品</text>
        <input
          v-model="batchReason"
          class="product__batch-input"
          type="text"
          maxlength="60"
          placeholder="估清原因（必填，如：食材售罄）"
          placeholder-class="mt-placeholder"
        />
      </view>
      <button
        class="mt-btn mt-btn--primary product__batch-btn"
        :disabled="batchSubmitting || selectedIds.size === 0"
        @click="submitBatchSoldOut"
      >
        {{ batchSubmitting ? '提交中…' : '批量估清' }}
      </button>
    </view>

    <!-- 改库存 -->
    <view v-if="stockEditorVisible" class="mt-mask" @click="closeStockEditor" />
    <view v-if="stockEditorVisible" class="mt-sheet">
      <text class="mt-sheet__title">修改库存</text>
      <view class="mt-sheet__tip">
        {{ editingSku?.skuName || '' }} · 当前可用 {{ editingSku?.availableQty ?? '--' }}
      </view>
      <input
        v-model="stockInput"
        class="product__stock-input"
        type="number"
        placeholder="请输入可用库存（≥0 的整数）"
        placeholder-class="mt-placeholder"
        @confirm="submitStock"
      />
      <view class="mt-sheet__actions">
        <button class="mt-btn mt-btn--plain" :disabled="stockSubmitting" @click="closeStockEditor">
          取消
        </button>
        <button class="mt-btn mt-btn--primary" :disabled="stockSubmitting" @click="submitStock">
          {{ stockSubmitting ? '保存中…' : '保存' }}
        </button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import {
  markSkusSoldOut,
  pageMerchantSkus,
  pageShopSpus,
  updateShopSpuStatus,
  updateSkuStock,
} from '@/api/product'
import type { ShopSpuVO, SkuVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useShopStore } from '@/stores/shop'
import { fenToYuan } from '@/utils/money'

/**
 * 商品管理。
 *
 * 数据来源两个接口：
 * - GET /merchant/shop-spus：门店商品状态（0 上架 / 1 下架 / 2 估清）
 * - GET /merchant/skus：SKU 与库存（按 spuId 分组渲染在商品下方）
 * 商品级上下架/估清改 t_shop_spu.status；规格级估清与库存改 t_sku_stock。
 * 批量估清走 POST /merchant/sold-out（必须带原因），会把所选商品的全部 SKU 置为估清。
 */

const PAGE_SIZE = 20
const SKU_PAGE_SIZE = 100
const SKU_MAX_PAGES = 5
const PRODUCT_PATH = '/pages/product/List'

const auth = useAuthStore()
const shop = useShopStore()

const spus = ref<ShopSpuVO[]>([])
const skusBySpu = ref<Record<number, SkuVO[]>>({})
const pageNum = ref(0)
const total = ref(0)
const loading = ref(false)
const error = ref('')
const skuError = ref('')
const operatingId = ref<number | null>(null)
const operatingSkuId = ref<number | null>(null)
const switchKey = ref(0)

const selectMode = ref(false)
const selectedIds = ref<Set<number>>(new Set())
const batchReason = ref('')
const batchSubmitting = ref(false)

const stockEditorVisible = ref(false)
const editingSku = ref<SkuVO | null>(null)
const stockInput = ref('')
const stockSubmitting = ref(false)

const hasMore = computed(() => spus.value.length < total.value)

onShow(() => {
  if (!auth.ensureLogin(PRODUCT_PATH)) {
    return
  }
  void load(true)
})

onPullDownRefresh(() => {
  void load(true)
})

onReachBottom(() => {
  loadMore()
})

async function load(reset: boolean): Promise<void> {
  if (!reset && loading.value) {
    return
  }
  if (shop.currentShopId === null) {
    await ensureShop()
  }
  if (shop.currentShopId === null) {
    error.value = '未选择门店，请返回工作台重新选择'
    loading.value = false
    uni.stopPullDownRefresh()
    return
  }

  loading.value = true
  error.value = ''
  const nextPage = reset ? 1 : pageNum.value + 1
  try {
    const page = await pageShopSpus({
      shopId: shop.currentShopId,
      pageNum: nextPage,
      pageSize: PAGE_SIZE,
      silent: true,
    })
    const list = page.list || []
    spus.value = reset ? list : spus.value.concat(list)
    total.value = Number(page.total) || spus.value.length
    pageNum.value = page.pageNum || nextPage
    if (reset) {
      selectedIds.value = new Set()
      await loadSkus()
    }
  } catch (err) {
    error.value = (err as Error)?.message || '商品加载失败'
    if (reset) {
      spus.value = []
      total.value = 0
    }
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

async function ensureShop(): Promise<void> {
  try {
    const list = await shop.loadShops(true)
    if (shop.currentShopId === null && list.length > 0) {
      const preferred = list.find((item) => item.id === auth.shopId) || list[0]
      shop.selectShop(preferred.id)
    }
  } catch (error) {
    // 由调用方统一提示
  }
}

/**
 * 一次性拉全店 SKU 并按 spuId 分组。
 * 后端没有「按 spuId 查 SKU」的商家接口，SKU 列表只支持整店分页，
 * 因此最多翻 5 页（500 条），超出部分不展示，库存与估清会提示数量缺口。
 */
async function loadSkus(): Promise<void> {
  skuError.value = ''
  const grouped: Record<number, SkuVO[]> = {}
  try {
    for (let page = 1; page <= SKU_MAX_PAGES; page += 1) {
      const result = await pageMerchantSkus({
        shopId: shop.currentShopId,
        pageNum: page,
        pageSize: SKU_PAGE_SIZE,
        silent: true,
      })
      ;(result.list || []).forEach((sku) => {
        const key = Number(sku.spuId)
        if (!grouped[key]) {
          grouped[key] = []
        }
        grouped[key].push(sku)
      })
      if (page * SKU_PAGE_SIZE >= (result.total || 0)) {
        break
      }
    }
    skusBySpu.value = grouped
  } catch (err) {
    skuError.value = (err as Error)?.message || '规格与库存加载失败'
    skusBySpu.value = grouped
  }
}

function loadMore(): void {
  if (loading.value || !hasMore.value) {
    return
  }
  void load(false)
}

function spuStatusText(status: number): string {
  // t_shop_spu.status 的枚举文案，接口不下发名称
  if (status === 0) {
    return '上架中'
  }
  if (status === 1) {
    return '已下架'
  }
  if (status === 2) {
    return '已估清'
  }
  return '未知'
}

function spuStatusClass(status: number): string {
  if (status === 0) {
    return 'mt-tag--ok'
  }
  if (status === 2) {
    return 'mt-tag--warn'
  }
  return 'mt-tag--off'
}

function specText(specJson: string | null): string {
  if (!specJson) {
    return ''
  }
  try {
    const parsed = JSON.parse(specJson) as Record<string, unknown>
    const values = Object.values(parsed).map((value) => String(value))
    return values.join(' / ')
  } catch (error) {
    return specJson
  }
}

/* ---------------------------- 上下架 / 估清 ---------------------------- */

async function onShelfToggle(spu: ShopSpuVO, event: { detail: { value: boolean } }): Promise<void> {
  const next = Boolean(event.detail.value)
  if (operatingId.value !== null) {
    switchKey.value += 1
    return
  }
  if (next && spu.status === 0) {
    return
  }
  if (!next && spu.status === 1) {
    return
  }

  if (!next) {
    const confirmed = await showConfirm(
      '确认下架',
      `下架「${spu.spuName}」后用户端菜单立即不可见，已下单不受影响。`,
      '确认下架',
    )
    if (!confirmed) {
      switchKey.value += 1
      return
    }
  }

  operatingId.value = spu.id
  try {
    await updateShopSpuStatus(spu.id, next ? 0 : 1)
    spu.status = next ? 0 : 1
    uni.showToast({ title: next ? '已上架' : '已下架', icon: 'none' })
  } catch (err) {
    switchKey.value += 1
  } finally {
    operatingId.value = null
  }
}

async function onSoldOutToggle(spu: ShopSpuVO, event: { detail: { value: boolean } }): Promise<void> {
  const next = Boolean(event.detail.value)
  if (operatingId.value !== null) {
    switchKey.value += 1
    return
  }
  if (next && spu.status === 2) {
    return
  }
  if (!next && spu.status !== 2) {
    return
  }

  if (next) {
    const confirmed = await showConfirm(
      '确认估清',
      `估清「${spu.spuName}」后该商品全部规格在用户端显示售完，补货后请关闭开关恢复。`,
      '确认估清',
    )
    if (!confirmed) {
      switchKey.value += 1
      return
    }
  }

  operatingId.value = spu.id
  try {
    await updateShopSpuStatus(spu.id, next ? 2 : 0)
    spu.status = next ? 2 : 0
    void loadSkus()
    uni.showToast({ title: next ? '已估清' : '已恢复售卖', icon: 'none' })
  } catch (err) {
    switchKey.value += 1
  } finally {
    operatingId.value = null
  }
}

/* ------------------------------ 规格级库存 ------------------------------ */

async function onSkuSoldOutToggle(sku: SkuVO, event: { detail: { value: boolean } }): Promise<void> {
  const next = Boolean(event.detail.value)
  if (operatingSkuId.value !== null) {
    switchKey.value += 1
    return
  }
  operatingSkuId.value = sku.skuId
  try {
    const updated = await updateSkuStock(sku.skuId, { soldOut: next })
    applySkuUpdate(updated)
    uni.showToast({ title: next ? '已估清该规格' : '已恢复该规格', icon: 'none' })
  } catch (err) {
    switchKey.value += 1
  } finally {
    operatingSkuId.value = null
  }
}

function openStockEditor(sku: SkuVO): void {
  editingSku.value = sku
  stockInput.value = sku.availableQty === null || sku.availableQty === undefined
    ? ''
    : String(sku.availableQty)
  stockEditorVisible.value = true
}

function closeStockEditor(): void {
  if (stockSubmitting.value) {
    return
  }
  stockEditorVisible.value = false
}

async function submitStock(): Promise<void> {
  if (stockSubmitting.value || !editingSku.value) {
    return
  }
  const raw = String(stockInput.value).trim()
  if (!/^\d+$/.test(raw)) {
    uni.showToast({ title: '库存必须是不小于 0 的整数', icon: 'none' })
    return
  }
  const availableQty = Number(raw)
  if (!Number.isSafeInteger(availableQty)) {
    uni.showToast({ title: '库存数值过大', icon: 'none' })
    return
  }

  stockSubmitting.value = true
  try {
    const updated = await updateSkuStock(editingSku.value.skuId, { availableQty })
    applySkuUpdate(updated)
    stockEditorVisible.value = false
    uni.showToast({ title: '库存已更新', icon: 'none' })
  } catch (err) {
    // http 层已提示
  } finally {
    stockSubmitting.value = false
  }
}

function applySkuUpdate(updated: SkuVO): void {
  const key = Number(updated.spuId)
  const list = skusBySpu.value[key] || []
  const index = list.findIndex((item) => item.skuId === updated.skuId)
  if (index >= 0) {
    list.splice(index, 1, updated)
  } else {
    list.push(updated)
  }
  skusBySpu.value = { ...skusBySpu.value, [key]: list }
}

/* ------------------------------ 批量估清 ------------------------------ */

function toggleSelectMode(): void {
  selectMode.value = !selectMode.value
  if (!selectMode.value) {
    selectedIds.value = new Set()
    batchReason.value = ''
  }
}

function onCardClick(spu: ShopSpuVO): void {
  if (!selectMode.value) {
    return
  }
  const next = new Set(selectedIds.value)
  if (next.has(spu.id)) {
    next.delete(spu.id)
  } else {
    next.add(spu.id)
  }
  selectedIds.value = next
}

function isSelected(spuId: number): boolean {
  return selectedIds.value.has(spuId)
}

async function submitBatchSoldOut(): Promise<void> {
  if (batchSubmitting.value || selectedIds.value.size === 0) {
    return
  }
  if (shop.currentShopId === null) {
    uni.showToast({ title: '未选择门店', icon: 'none' })
    return
  }
  const reason = batchReason.value.trim()
  if (!reason) {
    uni.showToast({ title: '请填写估清原因', icon: 'none' })
    return
  }

  const skuIds: number[] = []
  selectedIds.value.forEach((shopSpuId) => {
    const spu = spus.value.find((item) => item.id === shopSpuId)
    if (!spu) {
      return
    }
    ;(skusBySpu.value[spu.spuId] || []).forEach((sku) => skuIds.push(sku.skuId))
  })
  if (skuIds.length === 0) {
    uni.showToast({ title: '所选商品没有可估清的规格', icon: 'none' })
    return
  }
  if (skuIds.length > 200) {
    uni.showToast({ title: '单次最多估清 200 个规格，请减少选择', icon: 'none' })
    return
  }

  const confirmed = await showConfirm(
    '确认批量估清',
    `将估清 ${selectedIds.value.size} 个商品（${skuIds.length} 个规格），原因：${reason}`,
    '确认估清',
  )
  if (!confirmed) {
    return
  }

  batchSubmitting.value = true
  try {
    await markSkusSoldOut(shop.currentShopId, skuIds, reason)
    uni.showToast({ title: '已批量估清', icon: 'none' })
    selectMode.value = false
    selectedIds.value = new Set()
    batchReason.value = ''
    await load(true)
  } catch (err) {
    // http 层已提示
  } finally {
    batchSubmitting.value = false
  }
}

function showConfirm(title: string, content: string, confirmText: string): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title,
      content,
      confirmText,
      cancelText: '取消',
      success: (result) => resolve(Boolean(result.confirm)),
      fail: () => resolve(false),
    })
  })
}
</script>

<style scoped>
.product {
  min-height: 100vh;
  background-color: #f2f4f7;
  /* 底部原值（为页面底部操作区预留 150rpx + 安全区）保留作兜底 */
  padding-bottom: calc(150rpx + env(safe-area-inset-bottom));
  /*
   * 本页是 tab 页：在原有 150rpx 预留之上再叠加 fixed tabBar 高度。
   * --window-bottom 在 H5 已含安全区，故与 env() 取较大者（max）而非相加，避免刘海机双算。
   */
  padding-bottom: calc(150rpx + max(var(--window-bottom, 0px), env(safe-area-inset-bottom)));
}

.product__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 20rpx;
  background-color: #ffffff;
  border-bottom: 1rpx solid #eef1f5;
}

.product__count {
  font-size: 21rpx;
  color: #7a8699;
}

.product__toolbar-actions {
  display: flex;
  gap: 18rpx;
}

.product__tool {
  font-size: 21rpx;
  color: #e85d04;
  font-weight: 600;
}

.product__tool.is-active {
  color: #b42318;
}

.product__body {
  padding: 14rpx 20rpx 30rpx;
}

.product__empty {
  background-color: #ffffff;
  border-radius: 12rpx;
}

.product__sku-error {
  margin-bottom: 10rpx;
  padding: 10rpx 14rpx;
  border-radius: 8rpx;
  background-color: #fff6ea;
  color: #a2520a;
  font-size: 20rpx;
  line-height: 1.5;
}

.product__card {
  margin-bottom: 12rpx;
  padding: 16rpx;
  border-radius: 12rpx;
  background-color: #ffffff;
}

.product__head {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.product__check {
  flex: none;
  width: 40rpx;
  height: 40rpx;
  line-height: 38rpx;
  text-align: center;
  border-radius: 8rpx;
  border: 2rpx solid #cfd7e2;
  color: #ffffff;
  font-size: 24rpx;
}

.product__check.is-on {
  background-color: #ff7a00;
  border-color: #ff7a00;
}

.product__thumb {
  flex: none;
  width: 92rpx;
  height: 92rpx;
  border-radius: 10rpx;
  background-color: #f2f4f7;
}

.product__thumb--empty {
  line-height: 92rpx;
  text-align: center;
  color: #b3bdcb;
  font-size: 24rpx;
}

.product__info {
  flex: 1;
  min-width: 0;
}

.product__name {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: #1b2432;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.product__meta {
  display: flex;
  align-items: center;
  gap: 14rpx;
  margin-top: 4rpx;
}

.product__price {
  font-size: 25rpx;
}

.product__sales {
  font-size: 20rpx;
  color: #8d99aa;
}

.product__status {
  flex: none;
}

.product__ops {
  display: flex;
  gap: 30rpx;
  margin-top: 12rpx;
  padding: 8rpx 10rpx;
  border-radius: 8rpx;
  background-color: #f7f9fc;
}

.product__op {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.product__op-label {
  font-size: 21rpx;
  color: #55637a;
}

.product__skus {
  margin-top: 10rpx;
  border-top: 1rpx solid #f0f3f7;
}

.product__no-sku {
  padding: 12rpx 4rpx 4rpx;
  font-size: 20rpx;
  color: #a0a8b5;
}

.product__sku {
  display: flex;
  align-items: center;
  gap: 10rpx;
  padding: 10rpx 0;
  border-bottom: 1rpx solid #f6f8fb;
}

.product__sku:last-child {
  border-bottom: none;
}

.product__sku-main {
  flex: 1;
  min-width: 0;
}

.product__sku-name {
  display: block;
  font-size: 23rpx;
  color: #33455f;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.product__sku-spec {
  display: block;
  font-size: 19rpx;
  color: #8d99aa;
}

.product__sku-stock {
  flex: none;
  display: flex;
  align-items: baseline;
  gap: 4rpx;
}

.product__stock-label {
  font-size: 19rpx;
  color: #8d99aa;
}

.product__stock-value {
  font-size: 24rpx;
  font-weight: 600;
  color: #1b2432;
  font-variant-numeric: tabular-nums;
}

.product__stock-value.is-zero {
  color: #b42318;
}

.product__sku-tag {
  flex: none;
}

.product__sku-btn {
  flex: none;
  height: 48rpx;
  line-height: 48rpx;
  padding: 0 14rpx;
  font-size: 20rpx;
}

.product__more,
.product__end {
  padding: 20rpx 0;
  text-align: center;
  font-size: 21rpx;
  color: #8d99aa;
}

.product__more {
  color: #e85d04;
  font-weight: 600;
}

.product__batch {
  align-items: stretch;
}

.product__batch-main {
  flex: 1;
  min-width: 0;
}

.product__batch-count {
  display: block;
  font-size: 21rpx;
  color: #33455f;
  font-weight: 600;
}

.product__batch-input {
  height: 60rpx;
  margin-top: 4rpx;
  padding: 0 12rpx;
  border-radius: 8rpx;
  background-color: #f7f9fc;
  font-size: 22rpx;
  color: #1b2432;
}

.product__batch-btn {
  flex: none;
  width: 200rpx;
  height: 84rpx;
  line-height: 84rpx;
  font-size: 25rpx;
}

.product__stock-input {
  width: 100%;
  height: 84rpx;
  margin-top: 16rpx;
  padding: 0 16rpx;
  border-radius: 10rpx;
  background-color: #f7f9fc;
  border: 2rpx solid #e4e9f0;
  font-size: 30rpx;
  text-align: center;
  color: #1b2432;
  font-variant-numeric: tabular-nums;
}
</style>
