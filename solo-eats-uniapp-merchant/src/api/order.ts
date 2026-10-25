import { request } from './http'
import type {
  MerchantOrderDetailVO,
  MerchantOrderVO,
  OrderStatusVO,
  PageResult,
} from './types'

/**
 * 商家订单接口。
 * 接单/拒单/出餐在 OrderController（/orders/{orderId}/...），
 * 列表/详情/自取核销在 MerchantOrderController（/merchant/orders/...）。
 */

/** 后端 OrderStatus，仅用于页面的按钮分支，不用于渲染中文 */
export const ORDER_STATUS = {
  WAIT_ACCEPT: 10,
  PREPARING: 20,
  WAIT_RIDER_PICKUP: 30,
  WAIT_USER_PICKUP: 31,
  DELIVERING: 40,
  DELIVERED: 50,
  PICKED_UP: 51,
  COMPLETED: 60,
  CANCELLED: 70,
} as const

export function isFinalStatus(status: number): boolean {
  return status === ORDER_STATUS.COMPLETED || status === ORDER_STATUS.CANCELLED
}

export interface MerchantOrderListQuery {
  /** 后端只支持单值精确过滤；多状态 Tab 由页面并行请求后合并 */
  status?: number
  shopId?: number | null
  keyword?: string
  pageNum?: number
  pageSize?: number
  silent?: boolean
}

/** GET /merchant/orders */
export function pageMerchantOrders(
  query: MerchantOrderListQuery = {},
): Promise<PageResult<MerchantOrderVO>> {
  const data: Record<string, unknown> = {
    pageNum: query.pageNum ?? 1,
    pageSize: query.pageSize ?? 20,
  }
  if (query.status !== undefined && query.status !== null) {
    data.status = query.status
  }
  if (query.shopId !== undefined && query.shopId !== null) {
    data.shopId = query.shopId
  }
  if (query.keyword) {
    data.keyword = query.keyword
  }
  return request<PageResult<MerchantOrderVO>>({
    url: '/v1/merchant/orders',
    data,
    silent: query.silent,
  })
}

/** GET /merchant/orders/{orderId}，含时间轴与脱敏手机号 */
export function getMerchantOrder(
  orderId: number,
  silent = false,
): Promise<MerchantOrderDetailVO> {
  return request<MerchantOrderDetailVO>({
    url: `/v1/merchant/orders/${orderId}`,
    silent,
  })
}

/** POST /orders/{orderId}/accept：10 → 20 */
export function acceptOrder(orderId: number): Promise<OrderStatusVO> {
  return request<OrderStatusVO>({
    url: `/v1/orders/${orderId}/accept`,
    method: 'POST',
    silent: true,
  })
}

/**
 * POST /orders/{orderId}/reject：取消并退款，reason 必填。
 * 幂等键由 http 层按请求生成，后端按 Idempotency-Key + 状态机双重兜底。
 */
export function rejectOrder(orderId: number, reason: string): Promise<unknown> {
  return request<unknown>({
    url: `/v1/orders/${orderId}/reject`,
    method: 'POST',
    data: { reason },
    silent: true,
  })
}

/** POST /orders/{orderId}/prepared：20 → 30（配送）/ 31（自取） */
export function preparedOrder(orderId: number): Promise<OrderStatusVO> {
  return request<OrderStatusVO>({
    url: `/v1/orders/${orderId}/prepared`,
    method: 'POST',
    silent: true,
  })
}

/**
 * POST /merchant/orders/{orderId}/pickup/verify：31 → 51。
 * 失败码：10001 非自取/取餐码不正确；20001 状态不可核销（重复核销即此码）。
 */
export function verifyPickupCode(
  orderId: number,
  pickupCode: string,
): Promise<OrderStatusVO> {
  return request<OrderStatusVO>({
    url: `/v1/merchant/orders/${orderId}/pickup/verify`,
    method: 'POST',
    data: { pickupCode },
    silent: true,
  })
}
