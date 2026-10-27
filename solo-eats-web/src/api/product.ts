import { request } from './http'
import { createIdempotencyKey } from '@/utils/idempotency'
import type { CategoryNode, PageResult, SpuView } from './types'

export interface AdminSpuListParams {
  categoryId?: number
  status?: number
  keyword?: string
  pageNum?: number
  pageSize?: number
}

/**
 * SaveSpuRequest.java。
 * packagingFee 单位为分；status=2（上架）时后端强制要求 mainImage。
 */
export interface AdminSpuSavePayload {
  categoryId: number
  spuName: string
  subtitle?: string | null
  description?: string | null
  detailHtml?: string | null
  mainImage?: string | null
  images?: string[] | null
  tags?: string[] | null
  packagingFee?: number | null
  minPurchaseQty?: number | null
  limitPerOrder?: number | null
  status?: number | null
  sortWeight?: number | null
}

/** GET /api/v1/admin/spus，权限码 product:spu:list */
export function listSpus(params: AdminSpuListParams): Promise<PageResult<SpuView>> {
  return request<PageResult<SpuView>>({
    url: '/v1/admin/spus',
    method: 'GET',
    params,
  })
}

/** GET /api/v1/admin/categories/tree，权限码 product:category:list */
export function getCategoryTree(): Promise<CategoryNode[]> {
  return request<CategoryNode[]>({
    url: '/v1/admin/categories/tree',
    method: 'GET',
  })
}

/** POST /api/v1/admin/spus，权限码 product:spu:create，categoryId 必须是二级品类 */
export function createSpu(payload: AdminSpuSavePayload): Promise<SpuView> {
  return request<SpuView>({
    url: '/v1/admin/spus',
    method: 'POST',
    data: payload,
    headers: { 'Idempotency-Key': createIdempotencyKey('spu-create') },
  })
}

/** PUT /api/v1/admin/spus/{id}，权限码 product:spu:update */
export function updateSpu(id: number, payload: AdminSpuSavePayload): Promise<SpuView> {
  return request<SpuView>({
    url: `/v1/admin/spus/${id}`,
    method: 'PUT',
    data: payload,
    headers: { 'Idempotency-Key': createIdempotencyKey(`spu-update-${id}`) },
  })
}
