import { request } from './http'
import type { CategoryNode, PageResult, SpuView } from './types'

export interface AdminSpuListParams {
  categoryId?: number
  status?: number
  keyword?: string
  pageNum?: number
  pageSize?: number
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
