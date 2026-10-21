/**
 * 所有类型字段均来自后端 record / 实体，未做重命名。
 * 金额字段（*Amount、*Fee、price）单位为分，类型为后端 long。
 */

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  traceId: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface LoginPayload {
  role: string
  userId?: number
  merchantId?: number
  shopId?: number
  riderId?: number
}

export interface LoginResponse {
  token: string
  tokenType: string
  expiresIn: number
  roles: string[]
}

export interface OrderStatusVO {
  orderId: number
  status: number
  statusName: string
}

export interface MerchantOrderItemView {
  skuId: number | null
  skuName: string
  quantity: number
  originalUnitPrice: number
  payableAmount: number
}

export interface MerchantOrderView {
  orderId: number
  orderNo: string
  merchantId: number
  shopId: number
  status: number
  statusName: string
  fulfillmentType: number
  userId: number
  goodsAmount: number
  packagingFee: number
  deliveryFee: number
  platformDiscount: number
  merchantDiscount: number
  payAmount: number
  refundedAmount: number
  receiverName: string | null
  receiverPhone: string | null
  receiverAddress: string | null
  remark: string | null
  pickupCode: string | null
  createdAt: string
  expectedDeliveryAt: string | null
  completedAt: string | null
  cancelledAt: string | null
  items: MerchantOrderItemView[]
}

export interface AdminOrderView {
  orderId: number
  orderNo: string
  merchantId: number
  shopId: number
  userId: number
  status: number
  statusName: string
  fulfillmentType: number
  paymentStatus: number
  refundStatus: number
  settlementStatus: number
  payAmount: number
  refundedAmount: number
  receiverName: string | null
  receiverPhone: string | null
  receiverAddress: string | null
  createdAt: string
  expectedDeliveryAt: string | null
  completedAt: string | null
  cancelledAt: string | null
  cancelReason: string | null
}

export interface AdminOrderSummary {
  total: number
  waitAccept: number
  preparing: number
  waitRiderPickup: number
  waitUserPickup: number
  delivering: number
  completed: number
  cancelled: number
  paidAmount: number
  refundedAmount: number
}

export interface AdminOrderMonitorResult {
  orders: AdminOrderView[]
  total: number
  pageNum: number
  pageSize: number
  summary: AdminOrderSummary
}

export interface RiderDispatchResult {
  orderId: number
  orderStatus: number
  orderStatusName: string
  deliveryNo: string
  deliveryStatus: number
  dispatchCount: number
  dispatchExpireAt: string
}

export interface MerchantSkuView {
  skuId: number
  shopId: number
  spuId: number
  skuName: string
  specJson: string | null
  imageUrl: string | null
  price: number
  status: number
  availableQty: number
  lockedQty: number
  soldQty: number
  soldOut: boolean
  stockVersion: number
  updatedAt: string
}

export interface AdminSettlementSummary {
  billCount: number
  grossAmount: number
  merchantDiscount: number
  platformDiscount: number
  commissionBase: number
  platformCommission: number
  performanceFee: number
  refundAmount: number
  payableAmount: number
  paidAmount: number
  paidCount: number
  pendingCount: number
}

export interface AdminSettlementStatsResult {
  bills: SettlementBillView[]
  summary: AdminSettlementSummary
  total: number
  pageNum: number
  pageSize: number
}

export interface RefundVO {
  refundId: number
  refundNo: string
  orderId: number
  amount: number
  refundStatus: number
  orderStatus: number
  channelRefundNo: string | null
}

export interface CancelVO {
  orderId: number
  orderStatus: number
  refundNo: string | null
  refundAmount: number
}

/** SettlementSummary.java */
export interface SettlementSummary {
  billCount: number
  grossAmount: number
  merchantAmount: number
  platformCommission: number
  payableAmount: number
}

/** SettlementBillView.java */
export interface SettlementBillView {
  billId: number
  billNo: string
  merchantId: number
  shopId: number | null
  billDate: string
  grossAmount: number
  merchantDiscount: number
  platformDiscount: number
  commissionBase: number
  platformCommission: number
  merchantAmount: number
  performanceFee: number
  refundAmount: number
  status: number
  createdAt: string
}

/** SettlementListResult.java */
export interface SettlementListResult {
  bills: SettlementBillView[]
  summary: SettlementSummary
}

/** SettlementOrderView.java */
export interface SettlementOrderView {
  orderId: number
  orderNo: string
  payAmount: number
  goodsAmount: number
  packagingFee: number
  deliveryFee: number
  merchantDiscount: number
  platformDiscount: number
  commissionAmount: number
  merchantAmount: number
  completedAt: string | null
  settleEligibleAt: string | null
}

/** SettlementItemView.java */
export interface SettlementItemView {
  itemId: number
  orderId: number | null
  itemType: number
  amount: number
  remark: string | null
}

/** SettlementDetailResult.java */
export interface SettlementDetailResult {
  bill: SettlementBillView
  orders: SettlementOrderView[]
  items: SettlementItemView[]
}

/** SettlementGenerateResult.java */
export interface SettlementGenerateResult {
  billCount: number
  orderCount: number
  billIds: number[]
}
