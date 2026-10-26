import { request } from './http'
import type { MerchantSkuView, PageResult } from './types'

export interface MerchantSkuListParams {
  shopId?: number
  status?: number
  keyword?: string
  pageNum?: number
  pageSize?: number
}

export interface CreateSkuPayload {
  spuId: number
  skuName: string
  specJson?: string
  imageUrl?: string
  price: number
  status?: number
  initialStock?: number
}

export interface UpdateSkuPayload {
  skuName?: string
  specJson?: string
  imageUrl?: string
  price?: number
  status?: number
}

export interface UpdateSkuStockPayload {
  availableQty?: number
  soldOut?: boolean
}

export function listMerchantSkus(
  params: MerchantSkuListParams,
): Promise<PageResult<MerchantSkuView>> {
  return request<PageResult<MerchantSkuView>>({
    url: '/v1/merchant/skus',
    method: 'GET',
    params,
  })
}

export function createSku(payload: CreateSkuPayload): Promise<MerchantSkuView> {
  return request<MerchantSkuView>({
    url: '/v1/merchant/skus',
    method: 'POST',
    data: payload,
  })
}

export function updateSku(skuId: number, payload: UpdateSkuPayload): Promise<MerchantSkuView> {
  return request<MerchantSkuView>({
    url: `/v1/merchant/skus/${skuId}`,
    method: 'PUT',
    data: payload,
  })
}

export function updateSkuStock(
  skuId: number,
  payload: UpdateSkuStockPayload,
): Promise<MerchantSkuView> {
  return request<MerchantSkuView>({
    url: `/v1/merchant/skus/${skuId}/stock`,
    method: 'PUT',
    data: payload,
  })
}
