import { request } from './http'
import { createIdempotencyKey } from '@/utils/idempotency'
import type {
  MerchantDetailView,
  MerchantView,
  PageResult,
} from './types'

export interface AdminMerchantListParams {
  status?: number
  keyword?: string
  categoryId?: number
  pageNum?: number
  pageSize?: number
}

export interface ApproveMerchantPayload {
  commissionRate?: number
  merchantLevel?: number
  adminUsername?: string
  initialPassword: string
  remark?: string
}

/** GET /api/v1/admin/merchants，权限码 merchant:list */
export function listMerchants(
  params: AdminMerchantListParams,
): Promise<PageResult<MerchantView>> {
  return request<PageResult<MerchantView>>({
    url: '/v1/admin/merchants',
    method: 'GET',
    params,
  })
}

/** GET /api/v1/admin/merchants/{id}，权限码 merchant:detail */
export function getMerchantDetail(id: number): Promise<MerchantDetailView> {
  return request<MerchantDetailView>({
    url: `/v1/admin/merchants/${id}`,
    method: 'GET',
  })
}

/** POST /api/v1/admin/merchants/{id}/approve，权限码 merchant:audit */
export function approveMerchant(
  id: number,
  payload: ApproveMerchantPayload,
): Promise<MerchantDetailView> {
  return request<MerchantDetailView>({
    url: `/v1/admin/merchants/${id}/approve`,
    method: 'POST',
    data: payload,
    headers: { 'Idempotency-Key': createIdempotencyKey(`merchant-approve-${id}`) },
  })
}

/** POST /api/v1/admin/merchants/{id}/reject，权限码 merchant:audit */
export function rejectMerchant(id: number, remark: string): Promise<MerchantDetailView> {
  return request<MerchantDetailView>({
    url: `/v1/admin/merchants/${id}/reject`,
    method: 'POST',
    data: { remark },
    headers: { 'Idempotency-Key': createIdempotencyKey(`merchant-reject-${id}`) },
  })
}

/** PUT /api/v1/admin/merchants/{id}/status，status 只能是 2 已通过 / 4 已停用 */
export function updateMerchantStatus(id: number, status: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/merchants/${id}/status`,
    method: 'PUT',
    data: { status },
  })
}

/** PUT /api/v1/admin/merchants/{id}/commission，commissionRate 为 0.0000~0.5000 */
export function updateMerchantCommission(id: number, commissionRate: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/merchants/${id}/commission`,
    method: 'PUT',
    data: { commissionRate },
  })
}
