import { request } from './http'
import type { ShopAccountView, ShopDetailView, ShopView } from './types'

/**
 * GET /api/v1/merchant/shops
 * merchantId 由后端从 JWT 读取；门店账号只返回本店，商家管理员返回本商家全部门店。
 */
export function listMerchantShops(): Promise<ShopView[]> {
  return request<ShopView[]>({
    url: '/v1/merchant/shops',
    method: 'GET',
  })
}

/**
 * 商家端没有 GET /merchant/shops/{id}，详情读取用户端公开接口
 * GET /api/v1/c/shops/{id}（ShopClientController，返回 ShopDetailView）。
 */
export function getShopDetail(id: number): Promise<ShopDetailView> {
  return request<ShopDetailView>({
    url: `/v1/c/shops/${id}`,
    method: 'GET',
  })
}

/** SaveShopRequest.java，金额字段单位为分；merchantId 由后端从 JWT 覆盖 */
export interface MerchantSaveShopPayload {
  shopName: string
  brandId?: number | null
  categoryId?: number | null
  province?: string | null
  city?: string | null
  district?: string | null
  address: string
  lng: number
  lat: number
  contactName?: string | null
  contactPhone?: string | null
  logoUrl?: string | null
  coverUrl?: string | null
  imageUrls?: string[] | null
  announcement?: string | null
  deliveryTypes: number
  minOrderAmount: number
  deliveryFee?: number | null
  packagingFee?: number | null
  prepMinutes?: number | null
  deliveryRadiusM?: number | null
  sortWeight?: number | null
  settleAccountId?: number | null
}

/** PUT /api/v1/merchant/shops/{id}，全量覆盖可编辑字段 */
export function updateMerchantShop(
  id: number,
  payload: MerchantSaveShopPayload,
): Promise<ShopDetailView> {
  return request<ShopDetailView>({
    url: `/v1/merchant/shops/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** SaveBusinessHourRequest.Hour，startTime/endTime 为 HH:mm，end < start 按跨天处理 */
export interface MerchantBusinessHourPayload {
  dayOfWeek: number
  startTime: string
  endTime: string
}

/** PUT /api/v1/merchant/shops/{id}/business-hours，全量替换（先删后插） */
export function saveMerchantBusinessHours(
  id: number,
  hours: MerchantBusinessHourPayload[],
): Promise<void> {
  return request<void>({
    url: `/v1/merchant/shops/${id}/business-hours`,
    method: 'PUT',
    data: { hours },
  })
}

/** PUT /api/v1/merchant/shops/{id}/status，商家端只允许 0休息 1营业 2打烊 */
export function updateMerchantShopStatus(id: number, status: number): Promise<void> {
  return request<void>({
    url: `/v1/merchant/shops/${id}/status`,
    method: 'PUT',
    data: { status },
  })
}

/** SaveShopRequest.PickupRequest，pickupDiscount 单位为分 */
export interface MerchantPickupPayload {
  pickupEnabled: boolean
  pickupPrepareMinutes: number
  pickupTimeoutMinutes: number
  pickupCodePrefix: string
  pickupCodeLength: number
  pickupCodeValidMinutes: number
  pickupDiscount: number
}

/** PUT /api/v1/merchant/shops/{id}/pickup */
export function saveMerchantPickup(
  id: number,
  payload: MerchantPickupPayload,
): Promise<void> {
  return request<void>({
    url: `/v1/merchant/shops/${id}/pickup`,
    method: 'PUT',
    data: payload,
  })
}

/** GET /api/v1/merchant/shops/{id}/decoration，未配置时返回字符串 "{}" */
export function getShopDecoration(id: number): Promise<string> {
  return request<string>({
    url: `/v1/merchant/shops/${id}/decoration`,
    method: 'GET',
  })
}

/** PUT /api/v1/merchant/shops/{id}/decoration，config 必须是 JSON 对象，序列化后 ≤20000 字符 */
export function saveShopDecoration(id: number, config: Record<string, unknown>): Promise<string> {
  return request<string>({
    url: `/v1/merchant/shops/${id}/decoration`,
    method: 'PUT',
    data: { config },
  })
}

/** CreateShopAccountRequest.java，accountRole 1店长 2店员 */
export interface CreateShopAccountPayload {
  username: string
  password: string
  nickname?: string | null
  phone?: string | null
  accountRole: 1 | 2
}

/** GET /api/v1/merchant/shops/{id}/accounts */
export function listShopAccounts(shopId: number): Promise<ShopAccountView[]> {
  return request<ShopAccountView[]>({
    url: `/v1/merchant/shops/${shopId}/accounts`,
    method: 'GET',
  })
}

/** POST /api/v1/merchant/shops/{id}/accounts */
export function createShopAccount(
  shopId: number,
  payload: CreateShopAccountPayload,
): Promise<ShopAccountView> {
  return request<ShopAccountView>({
    url: `/v1/merchant/shops/${shopId}/accounts`,
    method: 'POST',
    data: payload,
  })
}

/** DELETE /api/v1/merchant/shops/{id}/accounts/{userId}，只解绑门店关联，不删除系统用户 */
export function unbindShopAccount(shopId: number, userId: number): Promise<void> {
  return request<void>({
    url: `/v1/merchant/shops/${shopId}/accounts/${userId}`,
    method: 'DELETE',
  })
}
