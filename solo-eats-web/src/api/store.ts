import { request } from './http'
import { createIdempotencyKey } from '@/utils/idempotency'
import type {
  PageResult,
  ShopDetailView,
  ShopView,
} from './types'

export interface AdminShopListParams {
  merchantId?: number
  status?: number
  city?: string
  keyword?: string
  pageNum?: number
  pageSize?: number
}

/** SaveShopRequest.java，金额字段单位为分 */
export interface SaveShopPayload {
  merchantId?: number
  shopName: string
  brandId?: number
  categoryId?: number
  province?: string
  city?: string
  district?: string
  address: string
  lng: number
  lat: number
  contactName?: string
  contactPhone?: string
  logoUrl?: string
  coverUrl?: string
  imageUrls?: string[]
  announcement?: string
  deliveryTypes: number
  minOrderAmount: number
  deliveryFee?: number
  packagingFee?: number
  prepMinutes?: number
  deliveryRadiusM?: number
  sortWeight?: number
  settleAccountId?: number
}

/** GET /api/v1/admin/shops，权限码 store:shop:list */
export function listShops(params: AdminShopListParams): Promise<PageResult<ShopView>> {
  return request<PageResult<ShopView>>({
    url: '/v1/admin/shops',
    method: 'GET',
    params,
  })
}

/**
 * POST /api/v1/admin/shops，权限码 store:shop:create。
 * merchantId 在管理端新增时必填，后端 createForAdmin 会校验。
 */
export function createShop(payload: SaveShopPayload): Promise<ShopDetailView> {
  return request<ShopDetailView>({
    url: '/v1/admin/shops',
    method: 'POST',
    data: payload,
    headers: { 'Idempotency-Key': createIdempotencyKey('shop-create') },
  })
}

/** PUT /api/v1/admin/shops/{id}，权限码 store:shop:update */
export function updateShop(id: number, payload: SaveShopPayload): Promise<ShopDetailView> {
  return request<ShopDetailView>({
    url: `/v1/admin/shops/${id}`,
    method: 'PUT',
    data: payload,
    headers: { 'Idempotency-Key': createIdempotencyKey(`shop-update-${id}`) },
  })
}

/** PUT /api/v1/admin/shops/{id}/status，status 0休息 1营业 2打烊 3停用 */
export function updateShopStatus(id: number, status: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/shops/${id}/status`,
    method: 'PUT',
    data: { status },
  })
}

/**
 * 管理端没有 GET /admin/shops/{id}，详情读取用户端公开接口
 * GET /api/v1/c/shops/{id}（ShopClientController，无角色限制）。
 */
export function getShopDetail(id: number): Promise<ShopDetailView> {
  return request<ShopDetailView>({
    url: `/v1/c/shops/${id}`,
    method: 'GET',
  })
}
