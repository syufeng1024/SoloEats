/**
 * 商家端接口类型。字段名与后端 record 一一对齐，不做重命名：
 * - 金额一律为「分」（Java Long → number），展示时才用 fenToYuan 转元
 * - 时间为 LocalDateTime 序列化的 ISO 字符串，如 2026-10-25T12:03:00
 * - 状态中文一律取接口返回的 statusName / statusText，前端不维护映射表
 */

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

/** POST /auth/login → LoginResponse */
export interface LoginVO {
  token: string
  tokenType: string
  /** 秒 */
  expiresIn: number
  /** 细粒度角色码，如 MERCHANT_MANAGER；族匹配见 CLAUDE.md 4.1(1) */
  roles: string[]
  permissions: string[]
}

/** GET /merchant/shops → ShopView（不含营业时段与自取细则） */
export interface MerchantShopVO {
  id: number
  merchantId: number
  shopNo: string | null
  shopName: string
  city: string | null
  district: string | null
  address: string | null
  lng: number | null
  lat: number | null
  /** 0 休息 / 1 营业 / 2 打烊 / 3 停用（3 只能由平台设置） */
  status: number | null
  statusText: string | null
  deliveryTypes: number | null
  minOrderAmount: number | null
  deliveryFee: number | null
  packagingFee: number | null
  prepMinutes: number | null
  rating: number | null
  monthSales: number | null
  pickupEnabled: number | null
  createdAt: string | null
}

export interface BusinessHourVO {
  dayOfWeek: number
  startTime: string
  endTime: string
  crossDay: boolean
}

/**
 * GET /c/shops/{id} → ShopDetailView。
 * 商家侧没有「门店详情」接口（只有列表与写入），设置页用这个只读接口回填
 * 营业时段与自取配置，避免 PUT 全量覆盖时把未展示字段写空。
 */
export interface ShopDetailVO {
  id: number
  merchantId: number
  shopNo: string | null
  shopName: string
  brandId: number | null
  categoryId: number | null
  province: string | null
  city: string | null
  district: string | null
  address: string | null
  lng: number | null
  lat: number | null
  contactName: string | null
  contactPhone: string | null
  logoUrl: string | null
  coverUrl: string | null
  imageUrls: string[] | null
  announcement: string | null
  status: number | null
  statusText: string | null
  deliveryTypes: number | null
  minOrderAmount: number | null
  deliveryFee: number | null
  packagingFee: number | null
  prepMinutes: number | null
  deliveryRadiusM: number | null
  rating: number | null
  ratingTaste: number | null
  ratingPackage: number | null
  ratingDelivery: number | null
  monthSales: number | null
  pickupEnabled: number | null
  pickupPrepareMinutes: number | null
  pickupTimeoutMinutes: number | null
  pickupCodePrefix: string | null
  pickupCodeLength: number | null
  pickupCodeValidMinutes: number | null
  pickupDiscount: number | null
  settleAccountId: number | null
  businessHours: BusinessHourVO[] | null
  createdAt: string | null
  updatedAt: string | null
}

/** MerchantOrderItemView（列表） */
export interface MerchantOrderItemVO {
  skuId: number
  skuName: string
  quantity: number
  originalUnitPrice: number | null
  payableAmount: number | null
}

/** GET /merchant/orders → MerchantOrderView。receiverPhone 为明文，列表页不展示 */
export interface MerchantOrderVO {
  orderId: number
  orderNo: string
  merchantId: number
  shopId: number
  status: number
  statusName: string
  /** 1 配送 / 2 自取 */
  fulfillmentType: number
  userId: number | null
  goodsAmount: number | null
  packagingFee: number | null
  deliveryFee: number | null
  platformDiscount: number | null
  merchantDiscount: number | null
  payAmount: number | null
  refundedAmount: number | null
  receiverName: string | null
  receiverPhone: string | null
  receiverAddress: string | null
  remark: string | null
  pickupCode: string | null
  createdAt: string | null
  expectedDeliveryAt: string | null
  completedAt: string | null
  cancelledAt: string | null
  items: MerchantOrderItemVO[]
}

/** UserOrderItemView（详情） */
export interface MerchantOrderItemDetailVO {
  orderItemId: number
  skuId: number
  skuName: string
  quantity: number
  unitPrice: number | null
  originalAmount: number | null
  payableAmount: number | null
}

/** OrderTimelineView */
export interface OrderTimelineVO {
  fromStatus: number | null
  fromStatusName: string | null
  toStatus: number | null
  toStatusName: string | null
  event: string | null
  operatorType: number | null
  createdAt: string | null
}

/** GET /merchant/orders/{orderId} → MerchantOrderDetailView */
export interface MerchantOrderDetailVO {
  orderId: number
  orderNo: string
  shopId: number
  status: number
  statusName: string
  fulfillmentType: number
  fulfillmentTypeName: string | null
  goodsAmount: number | null
  packagingFee: number | null
  deliveryFee: number | null
  payAmount: number | null
  receiverName: string | null
  /** 已脱敏，如 138****8000 */
  receiverPhoneMask: string | null
  receiverAddress: string | null
  remark: string | null
  pickupCode: string | null
  pickupReadyAt: string | null
  createdAt: string | null
  /** t_order 无 paid_at 列，恒为 null，页面不得依赖 */
  paidAt: string | null
  items: MerchantOrderItemDetailVO[]
  timeline: OrderTimelineVO[]
}

/** GET /merchant/report/dashboard → MerchantDashboardView */
export interface MerchantDashboardVO {
  todayOrderCount: number
  pendingAcceptCount: number
  preparingCount: number
  waitPickupCount: number
  completedTodayCount: number
}

/** POST /orders/{orderId}/accept|prepared、核销 → OrderStatusVO */
export interface OrderStatusVO {
  orderId: number
  status: number
  statusName: string
}

/** GET /merchant/shop-spus → ShopSpuView。status：0 上架 / 1 下架 / 2 估清 */
export interface ShopSpuVO {
  id: number
  shopId: number
  spuId: number
  spuName: string
  mainImage: string | null
  priceOverride: number | null
  skuMinPrice: number | null
  status: number
  auditStatus: number | null
  sortWeight: number | null
  monthSales: number | null
  suspendedUntil: string | null
  remark: string | null
  updatedAt: string | null
}

/** GET /merchant/skus → MerchantSkuView（SKU + 库存合并） */
export interface SkuVO {
  skuId: number
  shopId: number
  spuId: number
  skuName: string
  specJson: string | null
  imageUrl: string | null
  price: number | null
  status: number | null
  availableQty: number | null
  lockedQty: number | null
  soldQty: number | null
  soldOut: boolean | null
  stockVersion: number | null
  updatedAt: string | null
}

/**
 * GET /merchant/settlements → SettlementBillView。
 * status：0已生成 / 10待审核 / 20已审核 / 30已打款 / 40已驳回。
 * 接口未下发 statusName，页面需本地映射（见 Finance.vue）。
 */
export interface SettlementBillVO {
  billId: number
  billNo: string
  merchantId: number
  shopId: number | null
  /** LocalDate 序列化为 yyyy-MM-dd */
  billDate: string | null
  /** 以下金额均为分 */
  grossAmount: number | null
  merchantDiscount: number | null
  platformDiscount: number | null
  commissionBase: number | null
  platformCommission: number | null
  merchantAmount: number | null
  performanceFee: number | null
  refundAmount: number | null
  status: number | null
  createdAt: string | null
}

/** 接口按当前 status 筛选条件聚合，覆盖全部账单而非当前页 */
export interface SettlementSummaryVO {
  billCount: number
  grossAmount: number
  merchantAmount: number
  platformCommission: number
  payableAmount: number
}

export interface SettlementListVO {
  bills: SettlementBillVO[]
  summary: SettlementSummaryVO
}

export interface SettlementOrderVO {
  orderId: number
  orderNo: string
  payAmount: number | null
  goodsAmount: number | null
  packagingFee: number | null
  deliveryFee: number | null
  merchantDiscount: number | null
  platformDiscount: number | null
  commissionAmount: number | null
  merchantAmount: number | null
  completedAt: string | null
  settleEligibleAt: string | null
}

export interface SettlementItemVO {
  itemId: number
  orderId: number | null
  /** 1货款 2佣金 3退款 4履约服务费 …（后端 SettlementItem.itemType） */
  itemType: number | null
  amount: number | null
  remark: string | null
}

export interface SettlementDetailVO {
  bill: SettlementBillVO
  orders: SettlementOrderVO[]
  items: SettlementItemVO[]
}

/** GET /merchant/reviews → ReviewView */
export interface ReviewVO {
  id: number
  orderId: number | null
  shopId: number | null
  spuId: number | null
  skuId: number | null
  /** 匿名评价为 null */
  userId: number | null
  overallScore: number
  tasteScore: number | null
  packageScore: number | null
  deliveryScore: number | null
  content: string | null
  images: string[] | null
  tags: string[] | null
  /** 0 否 / 1 是 */
  anonymous: number | null
  /** 0待审 / 1通过 / 2隐藏 */
  status: number | null
  topFlag: number | null
  merchantReply: string | null
  replyAt: string | null
  appendContent: string | null
  appendAt: string | null
  createdAt: string | null
}

/** GET /merchant/profile → MerchantDetailView。legalIdCard 已脱敏 */
export interface MerchantDetailVO {
  id: number
  merchantNo: string | null
  merchantName: string
  brandName: string | null
  categoryId: number | null
  contactName: string | null
  contactPhone: string | null
  contactEmail: string | null
  businessLicenseNo: string | null
  legalPerson: string | null
  legalIdCard: string | null
  province: string | null
  city: string | null
  district: string | null
  address: string | null
  /** 0草稿 1待审核 2已通过 3已驳回 4已停用 */
  status: number | null
  /** BigDecimal，如 0.1500 */
  commissionRate: number | null
  level: number | null
  auditBy: number | null
  auditAt: string | null
  auditRemark: string | null
  openedAt: string | null
  remark: string | null
  createdAt: string | null
  updatedAt: string | null
  qualifications: unknown[] | null
  auditLogs: unknown[] | null
}
