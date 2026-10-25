/**
 * 与后端 VO 一一对齐的类型。字段名、可空性均以 Java record 为准，不要自行改名：
 * - 金额字段一律为分（number，Java Long）
 * - 时间字段为 LocalDateTime 的 ISO 字符串（如 2026-10-25T12:03:00）
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

/** GET /c/shops → NearbyShopView */
export interface ShopVO {
  id: number
  shopName: string
  logoUrl: string | null
  coverUrl: string | null
  address: string | null
  lng: number | null
  lat: number | null
  status: number | null
  deliveryTypes: number | null
  minOrderAmount: number | null
  deliveryFee: number | null
  prepMinutes: number | null
  rating: number | null
  monthSales: number | null
  pickupEnabled: number | null
  distanceM: number | null
}

export interface BusinessHourVO {
  dayOfWeek: number
  startTime: string
  endTime: string
  crossDay: boolean
}

/** GET /c/shops/{id} → ShopDetailView */
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

export interface MenuSkuVO {
  skuId: number
  skuName: string
  specJson: string | null
  imageUrl: string | null
  price: number
  availableQty: number | null
  soldOut: boolean
}

export interface MenuAddonItemVO {
  itemId: number
  itemName: string
  price: number
  stock: number | null
}

export interface MenuAddonGroupVO {
  groupId: number
  groupName: string
  minSelect: number | null
  maxSelect: number | null
  items: MenuAddonItemVO[]
}

/** GET /c/shops/{shopId}/menu 里的 MenuItemView，即一个可售 SPU */
export interface SpuVO {
  spuId: number
  spuName: string
  subtitle: string | null
  mainImage: string | null
  minPrice: number | null
  monthSales: number | null
  tags: string[] | null
  soldOut: boolean
  packagingFee: number | null
  minPurchaseQty: number | null
  limitPerOrder: number | null
  skus: MenuSkuVO[]
  addonGroups: MenuAddonGroupVO[]
}

/** GET /c/shops/{shopId}/menu → ShopMenuView[] */
export interface ShopMenuVO {
  categoryId: number
  categoryName: string
  items: SpuVO[]
}

/** GET /c/spus/{spuId} → SpuDetailView（菜单接口已聚合规格与加料，此接口仅作补充） */
export interface SpuDetailVO {
  spu: {
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
    packagingFee: number | null
    minPurchaseQty: number | null
    limitPerOrder: number | null
    status: number | null
    sortWeight: number | null
    monthSales: number | null
    updatedAt: string | null
  }
  specGroups: Array<{
    id: number
    groupName: string
    groupType: number | null
    isRequired: number | null
    sort: number | null
    options: Array<{
      id: number
      optionName: string
      priceDelta: number | null
      sort: number | null
      status: number | null
    }>
  }>
  addonGroups: Array<{
    id: number
    groupName: string
    minSelect: number | null
    maxSelect: number | null
    sort: number | null
    items: Array<{
      id: number
      itemName: string
      price: number
      stock: number | null
      sort: number | null
      status: number | null
    }>
  }>
}

/** POST /c/shops/{id}/delivery-check → DeliveryCheckResult */
export interface DeliveryCheckVO {
  inRange: boolean
  reason: string | null
  minOrderAmount: number | null
  deliveryFee: number | null
  expectedDeliveryAt: string | null
  prepMinutes: number | null
  deliveryDistanceM: number | null
}

/** POST /c/freight/estimate → FreightEstimateView */
export interface FreightEstimateVO {
  deliveryFee: number
  distanceM: number
  breakdown: Array<{ name: string; amount: number }>
}

/** POST /orders → OrderVO */
export interface OrderVO {
  orderId: number
  orderNo: string
  status: number
  fulfillmentType: number
  goodsAmount: number
  packagingFee: number
  deliveryFee: number
  discountAmount: number
  platformDiscount: number
  merchantDiscount: number
  payAmount: number
  pickupCode: string | null
  items: OrderItemVO[]
}

/** GET /orders → PageResult<UserOrderView> */
export interface OrderListItemVO {
  orderId: number
  orderNo: string
  shopId: number
  status: number
  statusName: string
  fulfillmentType: number
  fulfillmentTypeName: string
  goodsAmount: number
  packagingFee: number
  deliveryFee: number
  payAmount: number
  itemCount: number
  pickupCode: string | null
  createdAt: string
}

/** 列表与创建响应共用的字段口径；List 页用 OrderListItemVO，创建响应用 OrderVO */
export type OrderSummaryVO = OrderListItemVO

export interface OrderItemVO {
  skuId: number
  skuName: string
  quantity: number
  unitPrice: number
  originalAmount: number
  payableAmount: number
}

export interface OrderTimelineVO {
  fromStatus: number | null
  fromStatusName: string | null
  toStatus: number | null
  toStatusName: string | null
  event: string | null
  operatorType: number | null
  createdAt: string | null
}

/** GET /orders/{id} → UserOrderDetailView */
export interface OrderDetailVO {
  orderId: number
  orderNo: string
  shopId: number
  status: number
  statusName: string
  fulfillmentType: number
  fulfillmentTypeName: string
  goodsAmount: number
  packagingFee: number
  deliveryFee: number
  payAmount: number
  itemCount: number
  pickupCode: string | null
  createdAt: string
  receiverName: string | null
  receiverPhoneMask: string | null
  receiverAddress: string | null
  remark: string | null
  refundStatus: number | null
  refundedAmount: number | null
  items: OrderItemVO[]
  timeline: OrderTimelineVO[]
}

/** GET /c/addresses → AddressView（receiverPhone 已脱敏） */
export interface AddressVO {
  id: number
  receiverName: string
  receiverPhone: string
  province: string | null
  city: string | null
  district: string | null
  address: string | null
  houseNumber: string | null
  lng: number | null
  lat: number | null
  tag: string | null
  isDefault: boolean
}

/** GET /c/wallet → WalletView */
export interface WalletVO {
  balance: number
  frozenAmount: number
  totalRecharge: number
  totalConsume: number
}

/** GET /c/users/me → MemberView（phone 已脱敏） */
export interface MemberVO {
  userId: number
  phone: string
  nickname: string | null
  avatarUrl: string | null
  gender: number | null
  birthday: string | null
  status: number | null
  orderCount: number | null
  totalAmount: number | null
  lastOrderAt: string | null
  cancelApplyAt: string | null
  registeredAt: string | null
}

/** POST /c/auth/login → SmsLoginResult */
export interface SmsLoginVO {
  token: string
  tokenType: string
  expiresIn: number
  userId: number
  nickname: string | null
  isNewUser: boolean
  roles: string[]
}

/** GET /c/deliveries/{deliveryId}/track → TrackPointView */
export interface TrackPointVO {
  id: number
  deliveryId: number
  riderId: number | null
  lng: number
  lat: number
  speed: number | null
  accuracy: number | null
  reportAt: string | null
}

export interface RiderPositionVO {
  lng: number
  lat: number
  lastPing: string | null
}

/** GET /c/deliveries/{deliveryId}/track → DeliveryTrackView */
export interface DeliveryTrackVO {
  points: TrackPointVO[]
  riderPosition: RiderPositionVO | null
}

/** POST /payments/pay → PaymentVO */
export interface PaymentVO {
  paymentId: number
  paymentNo: string
  orderId: number
  channel: number
  amount: number
  status: number
}

/** GET /c/review-tags → ReviewTagView */
export interface ReviewTagVO {
  id: number
  tagName: string
  tagType: number | null
  sort: number | null
}

/** POST /c/reviews → ReviewView */
export interface ReviewVO {
  id: number
  orderId: number
  shopId: number
  spuId: number | null
  skuId: number | null
  userId: number | null
  overallScore: number
  tasteScore: number | null
  packageScore: number | null
  deliveryScore: number | null
  content: string | null
  images: string[] | null
  tags: string[] | null
  anonymous: number | null
  status: number | null
  topFlag: number | null
  merchantReply: string | null
  replyAt: string | null
  appendContent: string | null
  appendAt: string | null
  createdAt: string | null
}
