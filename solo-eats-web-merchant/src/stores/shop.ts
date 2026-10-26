import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { MERCHANT_SHOP_STORAGE_KEY } from '@shared/api/http'
import { listMerchantShops } from '@/api/shop'
import { useAuthStore } from '@/stores/auth'
import type { ShopView } from '@/api/types'

function readStoredShopId(): number | null {
  try {
    const raw = localStorage.getItem(MERCHANT_SHOP_STORAGE_KEY)
    if (!raw || !/^[1-9]\d*$/.test(raw)) {
      return null
    }
    const parsed = Number(raw)
    return Number.isSafeInteger(parsed) ? parsed : null
  } catch {
    return null
  }
}

/**
 * 当前经营门店。
 *
 * - 商家管理员（JWT 无 shopId）：可在本商家全部门店间切换，请求头带 X-Shop-Id，
 *   订单/SKU 查询同时下发 shopId；后端 PermissionGuard 对空令牌门店放行所传值。
 * - 门店账号（JWT 有 shopId）：后端按令牌门店校验，切到其它门店会被 10004 拒绝，
 *   因此这里只允许选中令牌绑定门店，其它选项在布局里置灰。
 */
export const useShopStore = defineStore('merchant-shop', () => {
  const auth = useAuthStore()

  const shops = ref<ShopView[]>([])
  const currentShopId = ref<number | null>(readStoredShopId())
  const loading = ref(false)
  const error = ref('')

  const currentShop = computed<ShopView | null>(() => {
    if (currentShopId.value === null) {
      return null
    }
    return shops.value.find((item) => item.id === currentShopId.value) ?? null
  })

  const boundShopId = computed(() => auth.shopId)
  const canSwitch = computed(() => boundShopId.value === null && shops.value.length > 1)
  const statusText = computed(() => currentShop.value?.statusText ?? '')

  async function loadShops(force = false): Promise<ShopView[]> {
    if (!force && shops.value.length > 0) {
      return shops.value
    }
    loading.value = true
    error.value = ''
    try {
      const list = await listMerchantShops()
      shops.value = Array.isArray(list) ? list : []
      reconcile()
      return shops.value
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '门店列表加载失败'
      throw cause
    } finally {
      loading.value = false
    }
  }

  function reconcile(): void {
    const bound = boundShopId.value
    if (bound !== null) {
      currentShopId.value = bound
      persist(bound)
      return
    }
    const exists = shops.value.some((item) => item.id === currentShopId.value)
    if (!exists) {
      selectShop(shops.value[0]?.id ?? null)
    }
  }

  /** 返回 false 表示被令牌绑定门店拒绝，调用方负责提示。 */
  function selectShop(shopId: number | null): boolean {
    const bound = boundShopId.value
    if (bound !== null && shopId !== bound) {
      return false
    }
    currentShopId.value = shopId
    persist(shopId)
    return true
  }

  function persist(shopId: number | null): void {
    try {
      if (shopId === null) {
        localStorage.removeItem(MERCHANT_SHOP_STORAGE_KEY)
      } else {
        localStorage.setItem(MERCHANT_SHOP_STORAGE_KEY, String(shopId))
      }
    } catch {
      // 隐私模式下 localStorage 不可写，当前会话内仍以内存值为准
    }
  }

  function reset(): void {
    shops.value = []
    currentShopId.value = null
    loading.value = false
    error.value = ''
    persist(null)
  }

  return {
    shops,
    currentShopId,
    currentShop,
    boundShopId,
    canSwitch,
    statusText,
    loading,
    error,
    loadShops,
    selectShop,
    reset,
  }
})
