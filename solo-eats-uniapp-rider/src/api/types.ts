/**
 * 骑手端接口类型，字段与后端 record 一一对应，改名前先核对 Java 侧：
 * - 金额一律为分（Java Long → number），展示时用 fenToYuan
 * - 时间字段是 LocalDateTime ISO 字符串（如 2026-10-25T12:03:00），可能为 null
 * - t_delivery_order 没有收货人姓名/手机号列，receiverName / receiverPhoneMask 恒为 null
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

/**
 * 后端 DeliveryOrderStatus，仅用于分支判断。
 * 中文名称一律使用接口返回的 statusName，前端不维护状态中文映射。
 */
export const RIDER_DELIVERY_STATUS = {
  WAIT_ASSIGN: 0,
  ASSIGNED: 10,
  ACCEPTED: 20,
  PICKED_UP: 30,
  DELIVERING: 40,
  DELIVERED: 50,
  CANCELLED: 70,
  REASSIGNED: 71,
} as const

export type RiderDeliveryStatus =
  (typeof RIDER_DELIVERY_STATUS)[keyof typeof RIDER_DELIVERY_STATUS]

/** 骑手认证状态：0 未提交 / 1 待审核 / 2 已通过 / 3 已驳回 */
export type RiderCertStatus = 0 | 1 | 2 | 3

/** GET /rider/certification → MyCertificationView */
export interface RiderCertStatusVO {
  riderId: number | null
  riderNo: string | null
  realName: string | null
  certStatus: RiderCertStatus
  certRemark: string | null
  healthCertExpireAt: string | null
  vehicleType: number | null
}

/** 任务详情中的商品行 → OrderItemBriefView */
export interface RiderTaskItemVO {
  skuId: number
  skuName: string
  quantity: number
  unitPrice: number
  payableAmount: number
}

/** GET /rider/deliveries → RiderTaskView */
export interface RiderTaskVO {
  deliveryId: number
  deliveryNo: string
  orderId: number
  shopId: number
  status: RiderDeliveryStatus
  statusName: string
  pickupAddress: string | null
  receiverName: string | null
  receiverAddress: string | null
  receiverPhoneMask: string | null
  distanceM: number | null
  deliveryFee: number | null
  promiseDeliverAt: string | null
  acceptedAt: string | null
  pickedUpAt: string | null
  deliveredAt: string | null
  dispatchExpireAt: string | null
  itemCount: number
}

/** GET /rider/deliveries/{deliveryId} → RiderTaskDetailView */
export interface RiderTaskDetailVO extends RiderTaskVO {
  items: RiderTaskItemVO[] | null
}

/** GET /rider/hall → HallOrderView */
export interface HallOrderVO {
  deliveryId: number
  deliveryNo: string
  shopId: number
  pickupAddress: string | null
  receiverAddress: string | null
  distanceM: number | null
  deliveryFee: number | null
  createdAt: string | null
}

/** GET /rider/orders/pending → PendingDispatchView，remainingSeconds 由服务端计算 */
export interface PendingDispatchVO {
  deliveryId: number
  orderId: number
  deliveryNo: string
  shopId: number
  pickupAddress: string | null
  receiverAddress: string | null
  deliveryFee: number | null
  remainingSeconds: number
}

/** POST /rider/dispatch/{id}/accept 等写操作 → DeliveryActionView */
export interface DeliveryActionVO {
  deliveryId: number
  orderId: number
  riderId: number
  status: RiderDeliveryStatus
}

/** GET /rider/income/overview → RiderIncomeOverview */
export interface IncomeOverviewVO {
  todayAmount: number
  todayOrders: number
  weekAmount: number
  monthAmount: number
  onTimeRate: number | null
}

/** GET /rider/income/details → RiderIncomeDetailView；status 0 待结算 / 1 已结算 */
export interface IncomeDetailVO {
  id: number
  deliveryOrderId: number
  orderId: number
  baseFee: number
  distanceFee: number
  timeSubsidy: number
  weatherSubsidy: number
  tipAmount: number
  penaltyAmount: number
  compensationAmount: number
  totalAmount: number
  settlementDate: string
  status: number
  createdAt: string | null
}

/** 提现单状态：0待审核 / 10审核通过 / 20打款中 / 30已到账 / 40已驳回 */
export const WITHDRAW_STATUS = {
  PENDING: 0,
  APPROVED: 10,
  PAYING: 20,
  ARRIVED: 30,
  REJECTED: 40,
} as const

/** GET /rider/payouts/balance → RiderBalanceView，单位分 */
export interface RiderBalanceVO {
  availableBalance: number
}

/** GET /rider/payouts → PayoutView，金额单位分 */
export interface WithdrawOrderVO {
  id: number
  payoutNo: string
  subjectType: number
  subjectId: number
  amount: number
  /** 1支付宝 2银行卡 */
  channel: number
  status: number
  auditRemark: string | null
  auditBy: number | null
  auditAt: string | null
  paidAt: string | null
  createdAt: string | null
}

/** GET /rider/stats → RiderStatsView；onTimeRate 是百分比 0~100，不是 0~1 */
export interface RiderStatsVO {
  rating: number | null
  onTimeRate: number | null
  totalOrders: number | null
  certStatus: number | null
}

/** GET /rider/attendance → RiderAttendanceDayView；minutes 为首末上线差值估算 */
export interface RiderAttendanceDayVO {
  date: string
  onlineTimes: number | null
  onlineMinutes: number | null
}

/** GET /rider/anomalies → RiderAnomalyView */
export interface RiderAnomalyVO {
  id: number
  deliveryId: number
  type: number
  typeName: string
  remark: string | null
  imagesJson: string | null
  createdAt: string | null
}

/** GET /c/faqs → FaqView（帮助中心白名单接口） */
export interface FaqVO {
  id: number
  category: string
  question: string
  answer: string
  sort: number | null
  viewCount: number | null
  status: number | null
  createdAt: string | null
  updatedAt: string | null
}

/** POST /im/sessions → ImSessionView */
export interface ImSessionVO {
  id: number
  sessionNo: string
  orderId: number | null
  userId: number
  peerType: number
  peerId: number
  lastMessage: string | null
  lastMessageAt: string | null
  unread: number
  status: number
  createdAt: string
}

/** POST /auth/login → LoginResponse */
export interface LoginVO {
  token: string
  tokenType: string
  expiresIn: number
  roles: string[] | null
  permissions: string[] | null
}

/** POST /files → FileView */
export interface FileVO {
  fileId: number
  url: string
  fileKey: string
  sizeBytes: number
  contentType: string
  originalName: string
}

/** POST /rider/track 的单点结构 → ReportTrackRequest.TrackPoint */
export interface RiderTrackPointVO {
  lng: number
  lat: number
  speed?: number | null
  accuracy?: number | null
  reportAt?: string | null
}
