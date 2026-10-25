import { request } from './http'
import type {
  DeliveryCheckVO,
  FreightEstimateVO,
  PageResult,
  ShopDetailVO,
  ShopMenuVO,
  ShopVO,
  SpuDetailVO,
} from './types'

export interface NearbyShopQuery {
  lng: number
  lat: number
  radius?: number
  keyword?: string
  /** 后端默认 comprehensive，另有 distance/sales/rating 等口径由后端解释 */
  sortBy?: string
  pageNum?: number
  pageSize?: number
}

export interface DeliveryCheckPayload {
  lng: number
  lat: number
  /** 商品小计（分），不含打包费与配送费 */
  goodsAmount: number
  /** 1 配送 2 自取 */
  fulfillmentType: number
}

export interface FreightEstimatePayload {
  shopId: number
  lng: number
  lat: number
  goodsAmount: number
  weightKg?: number
  floor?: number
  fulfillmentType: number
}

/** GET /c/shops：附近门店（白名单接口，未登录也可浏览） */
export function fetchNearbyShops(query: NearbyShopQuery): Promise<PageResult<ShopVO>> {
  const data: Record<string, unknown> = {
    lng: query.lng,
    lat: query.lat,
    pageNum: query.pageNum ?? 1,
    pageSize: query.pageSize ?? 10,
    sortBy: query.sortBy ?? 'comprehensive',
  }
  if (query.radius !== undefined) {
    data.radius = query.radius
  }
  if (query.keyword) {
    data.keyword = query.keyword
  }
  return request<PageResult<ShopVO>>({ url: '/v1/c/shops', data })
}

/** GET /c/shops/{id} */
export function fetchShopDetail(shopId: number): Promise<ShopDetailVO> {
  return request<ShopDetailVO>({ url: `/v1/c/shops/${shopId}` })
}

/** GET /c/shops/{shopId}/menu：分类 → 商品 → SKU → 加料组一次返回 */
export function fetchShopMenu(shopId: number): Promise<ShopMenuVO[]> {
  return request<ShopMenuVO[]>({ url: `/v1/c/shops/${shopId}/menu` })
}

/** POST /c/shops/{id}/delivery-check：配送范围 + 起送价校验 */
export function checkDelivery(shopId: number, payload: DeliveryCheckPayload): Promise<DeliveryCheckVO> {
  return request<DeliveryCheckVO>({
    url: `/v1/c/shops/${shopId}/delivery-check`,
    method: 'POST',
    data: {
      lng: payload.lng,
      lat: payload.lat,
      goodsAmount: payload.goodsAmount,
      fulfillmentType: payload.fulfillmentType,
    },
  })
}

/** POST /c/freight/estimate：配送费试算（自取时 deliveryFee 返回 0） */
export function estimateFreight(payload: FreightEstimatePayload): Promise<FreightEstimateVO> {
  const data: Record<string, unknown> = {
    shopId: payload.shopId,
    lng: payload.lng,
    lat: payload.lat,
    goodsAmount: payload.goodsAmount,
    fulfillmentType: payload.fulfillmentType,
  }
  if (payload.weightKg !== undefined) {
    data.weightKg = payload.weightKg
  }
  if (payload.floor !== undefined) {
    data.floor = payload.floor
  }
  return request<FreightEstimateVO>({
    url: '/v1/c/freight/estimate',
    method: 'POST',
    data,
  })
}

/** GET /c/spus/{spuId}：菜单接口已包含规格/加料，此接口留作详情页补充 */
export function fetchSpuDetail(spuId: number): Promise<SpuDetailVO> {
  return request<SpuDetailVO>({ url: `/v1/c/spus/${spuId}` })
}
