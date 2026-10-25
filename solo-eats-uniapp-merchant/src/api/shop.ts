import { request } from './http'
import type { MerchantShopVO, ShopDetailVO } from './types'

/**
 * 门店接口。
 * - 商家侧只能读写本商家门店，merchantId 由后端从令牌解析，前端不传；
 * - shopId 只作为「当前操作门店」的查询参数，权限校验仍以令牌为准
 *   （后端 PermissionGuard#resolveShopId 会拒绝与令牌不一致的 shopId）。
 */

/** GET /merchant/shops */
export function listMerchantShops(silent = false): Promise<MerchantShopVO[]> {
  return request<MerchantShopVO[]>({ url: '/v1/merchant/shops', silent })
}

/**
 * GET /c/shops/{id}：公开只读详情，含营业时段与自取配置。
 * 商家侧没有对应 GET，设置页回填只能用它（不改后端）。
 */
export function fetchShopDetail(shopId: number, silent = false): Promise<ShopDetailVO> {
  return request<ShopDetailVO>({ url: `/v1/c/shops/${shopId}`, silent })
}

/** 0 休息 / 1 营业 / 2 打烊（3 停用只能由平台设置） */
export function updateShopStatus(shopId: number, status: number): Promise<void> {
  return request<void>({
    url: `/v1/merchant/shops/${shopId}/status`,
    method: 'PUT',
    data: { status },
  })
}

export interface BusinessHourPayload {
  dayOfWeek: number
  /** HH:mm */
  startTime: string
  /** HH:mm，早于 startTime 时后端按跨天处理 */
  endTime: string
}

/** PUT /merchant/shops/{id}/business-hours：全量替换，至少 1 条 */
export function saveBusinessHours(
  shopId: number,
  hours: BusinessHourPayload[],
): Promise<void> {
  return request<void>({
    url: `/v1/merchant/shops/${shopId}/business-hours`,
    method: 'PUT',
    data: { hours },
  })
}

export interface PickupPayload {
  pickupEnabled: boolean
  pickupPrepareMinutes: number
  pickupTimeoutMinutes: number
  /** 0~8 位字母或数字，可为空串 */
  pickupCodePrefix: string
  /** 4~10 */
  pickupCodeLength: number
  /** 1~120 */
  pickupCodeValidMinutes: number
  /** 分 */
  pickupDiscount: number
}

/** PUT /merchant/shops/{id}/pickup；null 字段后端保留原值，这里始终传全量 */
export function updatePickup(shopId: number, payload: PickupPayload): Promise<void> {
  return request<void>({
    url: `/v1/merchant/shops/${shopId}/pickup`,
    method: 'PUT',
    data: { ...payload },
  })
}

/**
 * PUT /merchant/shops/{id} 的请求体。DTO 中 shopName/address/lng/lat/
 * deliveryTypes/minOrderAmount 为必填，未编辑字段传 null 后端不覆盖。
 */
export interface SaveShopPayload {
  shopName: string
  address: string
  lng: number
  lat: number
  deliveryTypes: number
  minOrderAmount: number
  deliveryFee: number | null
  packagingFee: number | null
  prepMinutes: number | null
  /** 以下为可空透传字段，设置页不编辑，统一传 null 表示保持原值 */
  brandId?: number | null
  categoryId?: number | null
  province?: string | null
  city?: string | null
  district?: string | null
  contactName?: string | null
  contactPhone?: string | null
  logoUrl?: string | null
  coverUrl?: string | null
  imageUrls?: string[] | null
  announcement?: string | null
  deliveryRadiusM?: number | null
  sortWeight?: number | null
  settleAccountId?: number | null
}

export function updateShop(shopId: number, payload: SaveShopPayload): Promise<ShopDetailVO> {
  return request<ShopDetailVO>({
    url: `/v1/merchant/shops/${shopId}`,
    method: 'PUT',
    data: {
      shopName: payload.shopName,
      address: payload.address,
      lng: payload.lng,
      lat: payload.lat,
      deliveryTypes: payload.deliveryTypes,
      minOrderAmount: payload.minOrderAmount,
      deliveryFee: payload.deliveryFee,
      packagingFee: payload.packagingFee,
      prepMinutes: payload.prepMinutes,
      brandId: payload.brandId ?? null,
      categoryId: payload.categoryId ?? null,
      province: payload.province ?? null,
      city: payload.city ?? null,
      district: payload.district ?? null,
      contactName: payload.contactName ?? null,
      contactPhone: payload.contactPhone ?? null,
      logoUrl: payload.logoUrl ?? null,
      coverUrl: payload.coverUrl ?? null,
      imageUrls: payload.imageUrls ?? null,
      announcement: payload.announcement ?? null,
      deliveryRadiusM: payload.deliveryRadiusM ?? null,
      sortWeight: payload.sortWeight ?? null,
      settleAccountId: payload.settleAccountId ?? null,
    },
  })
}
