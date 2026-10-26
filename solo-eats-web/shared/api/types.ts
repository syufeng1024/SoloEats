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

/** 登录请求：role 为 mock 快捷登录，username/password 为账号密码登录，二选一 */
export interface LoginPayload {
  role?: string
  userId?: number
  merchantId?: number
  shopId?: number
  riderId?: number
  username?: string
  password?: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  expiresIn: number
  roles: string[]
  /** T01 账号密码登录返回的权限码；mock 登录可能为空数组 */
  permissions?: string[]
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

/** MerchantView.java，金额单位分，commissionRate 为 0~0.5 的比例值 */
export interface MerchantView {
  id: number
  merchantNo: string
  merchantName: string
  brandName: string | null
  categoryId: number | null
  contactName: string
  contactPhone: string
  contactEmail: string | null
  province: string | null
  city: string | null
  district: string | null
  address: string | null
  status: number
  commissionRate: number
  level: number
  auditAt: string | null
  auditRemark: string | null
  createdAt: string
}

/** QualificationView.java */
export interface MerchantQualificationView {
  id: number
  qualType: number
  fileId: number
  fileUrl: string | null
  fileName: string | null
  expireAt: string | null
  status: number
  auditRemark: string | null
  createdAt: string
}

/** AuditLogView.java */
export interface MerchantAuditLogView {
  id: number
  fromStatus: number
  toStatus: number
  action: string
  operatorId: number | null
  operatorName: string | null
  remark: string | null
  createdAt: string
}

/** MerchantDetailView.java，legalIdCard 由后端脱敏后返回 */
export interface MerchantDetailView extends MerchantView {
  businessLicenseNo: string | null
  legalPerson: string | null
  legalIdCard: string | null
  auditBy: number | null
  openedAt: string | null
  remark: string | null
  updatedAt: string
  qualifications: MerchantQualificationView[]
  auditLogs: MerchantAuditLogView[]
}

/** ShopView.java，金额单位分 */
export interface ShopView {
  id: number
  merchantId: number
  shopNo: string
  shopName: string
  city: string | null
  district: string | null
  address: string
  lng: number
  lat: number
  status: number
  statusText: string
  deliveryTypes: number
  minOrderAmount: number
  deliveryFee: number
  packagingFee: number
  prepMinutes: number
  rating: number
  monthSales: number
  pickupEnabled: number
  createdAt: string
}

/** ShopDetailView.java */
export interface ShopBusinessHourView {
  dayOfWeek: number
  startTime: string
  endTime: string
  crossDay: boolean
}

export interface ShopDetailView extends ShopView {
  brandId: number | null
  categoryId: number | null
  province: string | null
  contactName: string | null
  contactPhone: string | null
  logoUrl: string | null
  coverUrl: string | null
  imageUrls: string[] | null
  announcement: string | null
  deliveryRadiusM: number
  ratingTaste: number
  ratingPackage: number
  ratingDelivery: number
  pickupPrepareMinutes: number
  pickupTimeoutMinutes: number
  pickupCodePrefix: string | null
  pickupCodeLength: number
  pickupCodeValidMinutes: number
  pickupDiscount: number
  settleAccountId: number | null
  businessHours: ShopBusinessHourView[]
  updatedAt: string
}

/** CategoryNode.java */
export interface CategoryNode {
  id: number
  parentId: number | null
  categoryName: string
  iconUrl: string | null
  level: number
  sort: number
  status: number
  children: CategoryNode[] | null
}

/** SpuView.java，金额单位分 */
export interface SpuView {
  id: number
  merchantId: number | null
  categoryId: number | null
  spuName: string
  subtitle: string | null
  description: string | null
  detailHtml: string | null
  mainImage: string | null
  images: string[] | null
  tags: string[] | null
  packagingFee: number
  minPurchaseQty: number
  limitPerOrder: number
  status: number
  sortWeight: number
  monthSales: number
  updatedAt: string
}

/** CouponTemplateView.java，金额单位分，discountRate 为 0~1 的比例值 */
export interface CouponTemplateView {
  id: number
  name: string
  couponType: number
  thresholdAmount: number
  discountAmount: number
  discountRate: number | null
  bearer: number
  totalQty: number
  issuedQty: number
  validStart: string | null
  validEnd: string | null
  validDays: number | null
  status: number
  createdAt: string
}

/** CouponGrantRecordView.java */
export interface CouponGrantRecordView {
  id: number
  userId: number
  templateId: number
  status: number
  expireAt: string
  usedAt: string | null
  orderId: number | null
  createdAt: string
}

/** DashboardView.java，金额单位分 */
export interface DashboardView {
  todayGmv: number
  orderCount: number
  avgOrderAmount: number
  onlineRiders: number
  exceptionOrders: number
  newUsers: number
  inProgressOrders: number
}
