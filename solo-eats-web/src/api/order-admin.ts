import { request } from './http'
import type { AdminOrderMonitorResult, PageResult } from './types'

/**
 * 运营后台订单中心接口。字段与后端 view 一一对应：
 * - AdminOrderDetailView.java / AdminOrderDetailItemView.java
 * - AdminRefundView.java / AdminRefundItemView.java
 * - AdminExceptionOrderView.java
 * 金额字段单位均为分（后端 long）。
 */

export interface AdminOrderMonitorParams {
  merchantId?: number
  shopId?: number
  status?: number
  /** 订单级退款标识：0 无退款 / 1 部分退款 / 2 全额退款；用于筛出「已退款」单据 */
  refundStatus?: number
  keyword?: string
  /** yyyy-MM-dd */
  dateFrom?: string
  dateTo?: string
  pageNum?: number
  pageSize?: number
}

export interface AdminOrderDetailItemView {
  itemId: number
  skuId: number | null
  skuName: string
  specJson: string | null
  imageUrl: string | null
  /** 分 */
  originalUnitPrice: number
  quantity: number
  /** 分 */
  originalAmount: number
  /** 分 */
  payableAmount: number
  refundedQty: number
  /** 分 */
  refundedAmount: number
}

export interface AdminOrderDetailRefundView {
  refundId: number
  refundNo: string
  /** 1全额 2部分 3迟到支付 */
  refundType: number
  refundTypeName: string
  /** 分 */
  amount: number
  /** 0申请 10商家同意 11商家拒绝 12平台仲裁 20退款中 30成功 31失败 40取消 50自动同意 */
  status: number
  statusName: string
  reason: string
  merchantReviewAt: string | null
  successAt: string | null
  createdAt: string
}

export interface AdminOrderTimelineView {
  fromStatus: number | null
  fromStatusName: string | null
  toStatus: number
  toStatusName: string
  event: string
  /** 1用户 2商家 3骑手 4平台 5系统 */
  operatorType: number | null
  createdAt: string
}

export interface AdminOrderDetailView {
  orderId: number
  orderNo: string
  userId: number
  merchantId: number
  shopId: number
  status: number
  statusName: string
  /** 1配送 2自取 */
  fulfillmentType: number
  fulfillmentTypeName: string | null
  /** 0未支付 1已支付 2已退款 3部分退款 */
  paymentStatus: number
  /** 0无 1部分 2全额 */
  refundStatus: number
  /** 0待结算 1已结算 2冻结 */
  settlementStatus: number
  exceptionType: number
  exceptionTypeName: string | null
  /** 分 */
  goodsAmount: number
  /** 分 */
  packagingFee: number
  /** 分 */
  deliveryFee: number
  /** 分 */
  discountAmount: number
  /** 分 */
  platformDiscount: number
  /** 分 */
  merchantDiscount: number
  /** 分 */
  payAmount: number
  /** 分 */
  refundedAmount: number
  receiverName: string | null
  /** 后端已脱敏 */
  receiverPhone: string | null
  receiverAddress: string | null
  remark: string | null
  /** 后端已脱敏 */
  pickupPhone: string | null
  pickupCode: string | null
  pickupReadyAt: string | null
  pickedUpAt: string | null
  expectedDeliveryAt: string | null
  completedAt: string | null
  cancelledAt: string | null
  cancelReason: string | null
  createdAt: string
  updatedAt: string | null
  items: AdminOrderDetailItemView[]
  refund: AdminOrderDetailRefundView | null
  timeline: AdminOrderTimelineView[]
}

export interface AdminRefundView {
  refundId: number
  refundNo: string
  orderId: number
  orderNo: string | null
  orderStatus: number | null
  orderStatusName: string | null
  /** 1全额 2部分 3迟到支付 */
  refundType: number
  refundTypeName: string
  /** 分 */
  amount: number
  /** 0申请 10商家同意 11商家拒绝 12平台仲裁 20退款中 30成功 31失败 40取消 50自动同意 */
  status: number
  statusName: string
  reason: string
  merchantReviewAt: string | null
  successAt: string | null
  createdAt: string
}

export interface AdminRefundItemView {
  itemId: number
  orderItemId: number
  skuId: number
  qty: number
  /** 分 */
  refundAmount: number
  restockFlag: number
  createdAt: string
}

export interface AdminRefundDetailView {
  refund: AdminRefundView
  items: AdminRefundItemView[]
}

export interface AdminExceptionOrderView {
  orderId: number
  orderNo: string
  merchantId: number
  shopId: number
  /** 订单状态，不是 t_order_exception.status */
  status: number
  statusName: string
  /** 1无骑手 2联系不上 3地址异常 4餐品破损 5商家超时 6自取超时 7其他 */
  exceptionType: number
  exceptionTypeName: string
  /** 分 */
  payAmount: number
  createdAt: string
}

export interface AdminRefundListParams {
  status?: number
  /** 退款单号 / 订单号 */
  keyword?: string
  /** yyyy-MM-dd */
  dateFrom?: string
  dateTo?: string
  pageNum?: number
  pageSize?: number
}

export interface AdminExceptionListParams {
  exceptionType?: number
  /** 订单状态 */
  status?: number
  merchantId?: number
  shopId?: number
  keyword?: string
  /** yyyy-MM-dd */
  dateFrom?: string
  dateTo?: string
  pageNum?: number
  pageSize?: number
}

/** GET /api/v1/admin/orders/monitor */
export function listAdminOrders(
  params: AdminOrderMonitorParams,
): Promise<AdminOrderMonitorResult> {
  return request<AdminOrderMonitorResult>({
    url: '/v1/admin/orders/monitor',
    method: 'GET',
    params,
  })
}

/** GET /api/v1/admin/orders/{orderId} */
export function getAdminOrder(orderId: number): Promise<AdminOrderDetailView> {
  return request<AdminOrderDetailView>({
    url: `/v1/admin/orders/${orderId}`,
    method: 'GET',
  })
}

/** GET /api/v1/admin/refunds */
export function listAdminRefunds(
  params: AdminRefundListParams,
): Promise<PageResult<AdminRefundView>> {
  return request<PageResult<AdminRefundView>>({
    url: '/v1/admin/refunds',
    method: 'GET',
    params,
  })
}

/** GET /api/v1/admin/refunds/{id} */
export function getAdminRefund(id: number): Promise<AdminRefundDetailView> {
  return request<AdminRefundDetailView>({
    url: `/v1/admin/refunds/${id}`,
    method: 'GET',
  })
}

/** GET /api/v1/admin/orders/exceptions */
export function listExceptionOrders(
  params: AdminExceptionListParams,
): Promise<PageResult<AdminExceptionOrderView>> {
  return request<PageResult<AdminExceptionOrderView>>({
    url: '/v1/admin/orders/exceptions',
    method: 'GET',
    params,
  })
}
