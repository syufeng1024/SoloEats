import { request } from './http'
import { createIdempotencyKey } from '@/utils/idempotency'
import type {
  CancelVO,
  MerchantOrderView,
  OrderStatusVO,
  PageResult,
  RefundVO,
  RiderDispatchResult,
} from './types'

export interface MerchantOrderListParams {
  shopId?: number
  status?: number
  keyword?: string
  pageNum?: number
  pageSize?: number
}

/** GET /api/v1/merchant/orders，merchantId 由后端从 JWT 读取 */
export function listMerchantOrders(
  params: MerchantOrderListParams,
): Promise<PageResult<MerchantOrderView>> {
  return request<PageResult<MerchantOrderView>>({
    url: '/v1/merchant/orders',
    method: 'GET',
    params,
  })
}

/** POST /api/v1/orders/{orderId}/merchant/call-rider，商家呼叫骑手派单 */
export function callRider(orderId: number): Promise<RiderDispatchResult> {
  return request<RiderDispatchResult>({
    url: `/v1/orders/${orderId}/merchant/call-rider`,
    method: 'POST',
  })
}

/** POST /api/v1/orders/{orderId}/accept，WAIT_ACCEPT -> PREPARING */
export function acceptOrder(orderId: number): Promise<OrderStatusVO> {
  return request<OrderStatusVO>({
    url: `/v1/orders/${orderId}/accept`,
    method: 'POST',
  })
}

/** POST /api/v1/orders/{orderId}/prepared，PREPARING -> WAIT_RIDER_PICKUP / WAIT_USER_PICKUP */
export function markOrderPrepared(orderId: number): Promise<OrderStatusVO> {
  return request<OrderStatusVO>({
    url: `/v1/orders/${orderId}/prepared`,
    method: 'POST',
  })
}

/** POST /api/v1/orders/{orderId}/reject，WAIT_ACCEPT -> CANCELLED，触发退款 */
export function rejectOrder(orderId: number, reason: string): Promise<RefundVO> {
  return request<RefundVO>({
    url: `/v1/orders/${orderId}/reject`,
    method: 'POST',
    data: { reason },
    headers: { 'Idempotency-Key': createIdempotencyKey(`merchant-reject-${orderId}`) },
  })
}

/** POST /api/v1/orders/{orderId}/cancel，operatorType=2 表示商家操作 */
export function cancelMerchantOrder(orderId: number, reason: string): Promise<CancelVO> {
  return request<CancelVO>({
    url: `/v1/orders/${orderId}/cancel`,
    method: 'POST',
    data: { reason, operatorType: 2 },
    headers: { 'Idempotency-Key': createIdempotencyKey(`merchant-cancel-${orderId}`) },
  })
}
