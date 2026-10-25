import { request } from './http'
import type { PageResult, ShopSpuVO, SkuVO } from './types'

/**
 * 门店商品接口。
 * - 商品维度的上架/下架/估清走 t_shop_spu.status（0 上架 / 1 下架 / 2 估清）
 * - 规格维度的库存与估清走 t_sku_stock（availableQty / soldOut）
 * - 批量估清走 t_sold_out_record，必须带非空原因（后端 requireReason）
 */

export interface ShopSpuQuery {
  shopId: number
  status?: number
  keyword?: string
  pageNum?: number
  pageSize?: number
  silent?: boolean
}

/** GET /merchant/shop-spus；shopId 必填（后端 resolveOptionalShopId 后判空） */
export function pageShopSpus(query: ShopSpuQuery): Promise<PageResult<ShopSpuVO>> {
  const data: Record<string, unknown> = {
    shopId: query.shopId,
    pageNum: query.pageNum ?? 1,
    pageSize: query.pageSize ?? 20,
  }
  if (query.status !== undefined && query.status !== null) {
    data.status = query.status
  }
  if (query.keyword) {
    data.keyword = query.keyword
  }
  return request<PageResult<ShopSpuVO>>({
    url: '/v1/merchant/shop-spus',
    data,
    silent: query.silent,
  })
}

/** PUT /merchant/shop-spus/{id}/status：0 上架 / 1 下架 / 2 估清 */
export function updateShopSpuStatus(id: number, status: number): Promise<void> {
  return request<void>({
    url: `/v1/merchant/shop-spus/${id}/status`,
    method: 'PUT',
    data: { status },
  })
}

export interface SkuQuery {
  shopId?: number | null
  status?: number
  keyword?: string
  pageNum?: number
  pageSize?: number
  silent?: boolean
}

/** GET /merchant/skus；后端用令牌内 shopId，传值与令牌不一致会 10004 */
export function pageMerchantSkus(query: SkuQuery = {}): Promise<PageResult<SkuVO>> {
  const data: Record<string, unknown> = {
    pageNum: query.pageNum ?? 1,
    pageSize: query.pageSize ?? 20,
  }
  if (query.shopId !== undefined && query.shopId !== null) {
    data.shopId = query.shopId
  }
  if (query.status !== undefined && query.status !== null) {
    data.status = query.status
  }
  if (query.keyword) {
    data.keyword = query.keyword
  }
  return request<PageResult<SkuVO>>({
    url: '/v1/merchant/skus',
    data,
    silent: query.silent,
  })
}

export interface UpdateStockPayload {
  availableQty?: number | null
  soldOut?: boolean | null
}

/** PUT /merchant/skus/{skuId}/stock；availableQty 与 soldOut 至少一个 */
export function updateSkuStock(skuId: number, payload: UpdateStockPayload): Promise<SkuVO> {
  return request<SkuVO>({
    url: `/v1/merchant/skus/${skuId}/stock`,
    method: 'PUT',
    data: {
      availableQty: payload.availableQty ?? null,
      soldOut: payload.soldOut ?? null,
    },
  })
}

/** POST /merchant/sold-out：批量估清，reason 不能为空，skuIds 最多 200 */
export function markSkusSoldOut(
  shopId: number,
  skuIds: number[],
  reason: string,
): Promise<void> {
  return request<void>({
    url: '/v1/merchant/sold-out',
    method: 'POST',
    data: { shopId, skuIds, reason },
  })
}

/** DELETE /merchant/sold-out：批量恢复（body 同 POST，无 reason） */
export function restoreSkusSoldOut(shopId: number, skuIds: number[]): Promise<void> {
  return request<void>({
    url: '/v1/merchant/sold-out',
    method: 'DELETE',
    data: { shopId, skuIds },
  })
}
