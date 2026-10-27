import { request } from './http'
import type {
  DeliveryTrackVO,
  OrderDetailVO,
  OrderListItemVO,
  OrderVO,
  PageResult,
  ReviewTagVO,
  ReviewVO,
} from './types'

/** 后端 OrderStatus 枚举，与 solo-eats-order 的 OrderStatus 保持一致 */
export const ORDER_STATUS = {
  WAIT_PAY: 0,
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

export function isTerminalStatus(status: number): boolean {
  return status === ORDER_STATUS.COMPLETED || status === ORDER_STATUS.CANCELLED
}

export interface CreateOrderItemPayload {
  skuId: number
  quantity: number
}

export interface CreateOrderPayload {
  userId: number
  merchantId: number
  shopId: number
  /** 1 配送 2 自取 */
  fulfillmentType: number
  couponId?: number | null
  packagingFee: number
  deliveryFee: number
  receiverName?: string | null
  receiverPhone?: string | null
  receiverAddress?: string | null
  pickupPhone?: string | null
  remark?: string | null
  items: CreateOrderItemPayload[]
}

export interface OrderListQuery {
  /** 后端只支持单值精确过滤；多状态 Tab 由页面本地过滤 */
  status?: number
  pageNum?: number
  pageSize?: number
}

export interface CreateReviewPayload {
  orderId: number
  orderItemId: number
  overallScore: number
  tasteScore?: number | null
  packageScore?: number | null
  deliveryScore?: number | null
  content?: string | null
  images?: string[] | null
  tags?: string[] | null
  anonymous?: number | null
}

/** POST /orders（必须带 Idempotency-Key） */
export function createOrder(payload: CreateOrderPayload, idempotencyKey: string): Promise<OrderVO> {
  return request<OrderVO>({
    url: '/v1/orders',
    method: 'POST',
    idempotencyKey,
    data: {
      userId: payload.userId,
      merchantId: payload.merchantId,
      shopId: payload.shopId,
      fulfillmentType: payload.fulfillmentType,
      couponId: payload.couponId ?? null,
      packagingFee: payload.packagingFee,
      deliveryFee: payload.deliveryFee,
      receiverName: payload.receiverName ?? null,
      receiverPhone: payload.receiverPhone ?? null,
      receiverAddress: payload.receiverAddress ?? null,
      pickupPhone: payload.pickupPhone ?? null,
      remark: payload.remark ?? null,
      items: payload.items.map((item) => ({ skuId: item.skuId, quantity: item.quantity })),
    },
  })
}

/** GET /orders?status=&pageNum=&pageSize= */
export function fetchOrders(query: OrderListQuery = {}): Promise<PageResult<OrderListItemVO>> {
  const data: Record<string, unknown> = {
    pageNum: query.pageNum ?? 1,
    pageSize: query.pageSize ?? 10,
  }
  if (query.status !== undefined && query.status !== null) {
    data.status = query.status
  }
  return request<PageResult<OrderListItemVO>>({ url: '/v1/orders', data })
}

/**
 * GET /orders/{id}，含时间轴；归属不符后端返回 10004。
 * silent=true 用于详情页 10s 轮询与支付结果轮询，避免网络抖动时每轮弹一次 toast。
 */
export function fetchOrderDetail(orderId: number, silent = false): Promise<OrderDetailVO> {
  return request<OrderDetailVO>({ url: `/v1/orders/${orderId}`, silent })
}

/** POST /orders/{id}/cancel，用户侧 operatorType 固定 1 */
export function cancelOrder(orderId: number, reason: string, idempotencyKey: string): Promise<void> {
  return request<void>({
    url: `/v1/orders/${orderId}/cancel`,
    method: 'POST',
    idempotencyKey,
    data: { reason, operatorType: 1 },
  })
}

/** GET /c/deliveries/{deliveryId}/track */
export function fetchDeliveryTrack(deliveryId: number, silent = false): Promise<DeliveryTrackVO> {
  return request<DeliveryTrackVO>({ url: `/v1/c/deliveries/${deliveryId}/track`, silent })
}

/** POST /orders/{orderId}/refund → RefundVO，后端同步调用支付网关并返回退款单。 */
export interface RefundResultVO {
  refundId: number
  refundNo: string
  orderId: number
  amount: number
  refundStatus: number
  orderStatus: number
  channelRefundNo: string | null
}

/**
 * 用户端整单退款。必须带 Idempotency-Key，失败重试复用同一个 key。
 * 后端只允许「待接单 / 备餐中」订单，其余状态返回 ORDER_STATUS_INVALID。
 */
export function applyRefund(
  orderId: number,
  reason: string,
  idempotencyKey: string,
): Promise<RefundResultVO> {
  return request<RefundResultVO>({
    url: `/v1/orders/${orderId}/refund`,
    method: 'POST',
    idempotencyKey,
    data: { reason },
  })
}

/** GET /c/review-tags：评价标签字典 */
export function fetchReviewTags(): Promise<ReviewTagVO[]> {
  return request<ReviewTagVO[]>({ url: '/v1/c/review-tags' })
}

/** POST /c/reviews，后端 @Idempotent + uk_order_item 双重防重 */
export function submitReview(payload: CreateReviewPayload): Promise<ReviewVO> {
  return request<ReviewVO>({
    url: '/v1/c/reviews',
    method: 'POST',
    data: {
      orderId: payload.orderId,
      orderItemId: payload.orderItemId,
      overallScore: payload.overallScore,
      tasteScore: payload.tasteScore ?? null,
      packageScore: payload.packageScore ?? null,
      deliveryScore: payload.deliveryScore ?? null,
      content: payload.content ?? null,
      images: payload.images ?? null,
      tags: payload.tags ?? null,
      anonymous: payload.anonymous ?? 0,
    },
  })
}
