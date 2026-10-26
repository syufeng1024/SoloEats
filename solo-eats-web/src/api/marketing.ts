import { request } from './http'
import { createIdempotencyKey } from '@/utils/idempotency'
import type {
  CouponGrantRecordView,
  CouponTemplateView,
  PageResult,
} from './types'

export interface AdminCouponListParams {
  name?: string
  status?: number
  couponType?: number
  bearer?: number
  pageNum?: number
  pageSize?: number
}

export interface CouponTemplatePayload {
  name: string
  couponType?: number
  thresholdAmount?: number
  discountAmount?: number
  discountRate?: number | null
  bearer?: number
  totalQty?: number
  validStart?: string | null
  validEnd?: string | null
  validDays?: number | null
}

export interface CouponRecordListParams {
  status?: number
  pageNum?: number
  pageSize?: number
}

/** GET /api/v1/admin/coupons，权限码 marketing:coupon:list */
export function listCoupons(
  params: AdminCouponListParams,
): Promise<PageResult<CouponTemplateView>> {
  return request<PageResult<CouponTemplateView>>({
    url: '/v1/admin/coupons',
    method: 'GET',
    params,
  })
}

/** GET /api/v1/admin/coupons/{id} */
export function getCoupon(id: number): Promise<CouponTemplateView> {
  return request<CouponTemplateView>({
    url: `/v1/admin/coupons/${id}`,
    method: 'GET',
  })
}

/** POST /api/v1/admin/coupons，权限码 marketing:coupon:save */
export function createCoupon(
  payload: CouponTemplatePayload,
): Promise<CouponTemplateView> {
  return request<CouponTemplateView>({
    url: '/v1/admin/coupons',
    method: 'POST',
    data: payload,
    headers: { 'Idempotency-Key': createIdempotencyKey('coupon-create') },
  })
}

/** PUT /api/v1/admin/coupons/{id}，couponType/issuedQty 不可改 */
export function updateCoupon(
  id: number,
  payload: CouponTemplatePayload,
): Promise<CouponTemplateView> {
  return request<CouponTemplateView>({
    url: `/v1/admin/coupons/${id}`,
    method: 'PUT',
    data: payload,
    headers: { 'Idempotency-Key': createIdempotencyKey(`coupon-update-${id}`) },
  })
}

/**
 * PUT /api/v1/admin/coupons/{id}/status。
 * 注意 t_coupon_template.status 语义：0 启用、1 停用。
 */
export function updateCouponStatus(id: number, status: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/coupons/${id}/status`,
    method: 'PUT',
    data: { status },
  })
}

/** POST /api/v1/admin/coupons/{id}/grant，单次去重后不得超过 500 人 */
export function grantCoupon(id: number, userIds: number[]): Promise<number> {
  return request<number>({
    url: `/v1/admin/coupons/${id}/grant`,
    method: 'POST',
    data: { userIds },
    headers: { 'Idempotency-Key': createIdempotencyKey(`coupon-grant-${id}`) },
  })
}

/** GET /api/v1/admin/coupons/{id}/records */
export function listCouponRecords(
  id: number,
  params: CouponRecordListParams,
): Promise<PageResult<CouponGrantRecordView>> {
  return request<PageResult<CouponGrantRecordView>>({
    url: `/v1/admin/coupons/${id}/records`,
    method: 'GET',
    params,
  })
}
