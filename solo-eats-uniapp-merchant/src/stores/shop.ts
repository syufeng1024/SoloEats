import { reactive } from 'vue'
import { listMerchantShops } from '@/api/shop'
import type { MerchantShopVO } from '@/api/types'

/**
 * 当前门店。
 *
 * 口径：
 * - 门店列表来自 GET /merchant/shops（商家管理员可见本商家全部门店）；
 * - 选中门店写入本地 `shopId`，由 api/http.ts 注入 X-Shop-Id 头，
 *   同时在订单/商品查询里作为 shopId 参数下发；
 * - 后端当前按令牌内 shopId 校验收写接口（PermissionGuard#resolveShopId），
 *   因此令牌只绑定单店时切换到其它门店会被 10004 拒绝。前端不绕过，
 *   在切换浮层里明确提示改用该门店账号登录。
 * - 营业状态 0 休息 / 1 营业 / 2 打烊 / 3 停用，中文一律取接口 statusText。
 */

const CURRENT_SHOP_KEY = 'shopId'

function readStoredShopId(): number | null {
  const value = Number(uni.getStorageSync(CURRENT_SHOP_KEY))
  return Number.isFinite(value) && value > 0 ? value : null
}

export interface ShopStore {
  shops: MerchantShopVO[]
  currentShopId: number | null
  loading: boolean
  error: string
  readonly currentShop: MerchantShopVO | null
  readonly status: number | null
  readonly statusText: string
  readonly isOpen: boolean
  loadShops(force?: boolean): Promise<MerchantShopVO[]>
  applyList(list: MerchantShopVO[]): void
  selectShop(shopId: number | null): void
  applyStatus(status: number, statusText?: string | null): void
  reset(): void
}

const state = reactive<ShopStore>({
  shops: [],
  currentShopId: readStoredShopId(),
  loading: false,
  error: '',

  get currentShop(): MerchantShopVO | null {
    if (this.currentShopId === null) {
      return null
    }
    return this.shops.find((item) => item.id === this.currentShopId) || null
  },

  get status(): number | null {
    return this.currentShop?.status ?? null
  },

  get statusText(): string {
    return this.currentShop?.statusText || ''
  },

  get isOpen(): boolean {
    return this.status === 1
  },

  async loadShops(force = false): Promise<MerchantShopVO[]> {
    if (!force && this.shops.length > 0) {
      return this.shops
    }
    this.loading = true
    this.error = ''
    try {
      const list = await listMerchantShops()
      this.applyList(Array.isArray(list) ? list : [])
      return this.shops
    } catch (error) {
      this.error = (error as Error)?.message || '门店列表加载失败'
      throw error
    } finally {
      this.loading = false
    }
  },

  applyList(list: MerchantShopVO[]): void {
    this.shops = list
    if (list.length === 0) {
      this.currentShopId = null
      uni.removeStorageSync(CURRENT_SHOP_KEY)
      return
    }
    const exists = list.some((item) => item.id === this.currentShopId)
    if (!exists) {
      // 优先沿用令牌绑定门店，否则取列表第一项
      const tokenShopId = Number(uni.getStorageSync('tokenShopId')) || null
      const preferred = list.find((item) => item.id === tokenShopId) || list[0]
      this.selectShop(preferred.id)
    }
  },

  selectShop(shopId: number | null): void {
    this.currentShopId = shopId
    if (shopId === null) {
      uni.removeStorageSync(CURRENT_SHOP_KEY)
      return
    }
    uni.setStorageSync(CURRENT_SHOP_KEY, shopId)
  },

  applyStatus(status: number, statusText?: string | null): void {
    const shop = this.currentShop
    if (!shop) {
      return
    }
    shop.status = status
    if (statusText !== undefined && statusText !== null) {
      shop.statusText = statusText
    }
  },

  reset(): void {
    this.shops = []
    this.currentShopId = null
    this.loading = false
    this.error = ''
    uni.removeStorageSync(CURRENT_SHOP_KEY)
  },
})

export function useShopStore(): ShopStore {
  return state
}
