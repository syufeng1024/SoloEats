import { request } from './http'
import type {
  DeliveryActionVO,
  FaqVO,
  HallOrderVO,
  ImSessionVO,
  IncomeDetailVO,
  IncomeOverviewVO,
  LoginVO,
  PageResult,
  PendingDispatchVO,
  RiderAnomalyVO,
  RiderAttendanceDayVO,
  RiderBalanceVO,
  RiderCertStatusVO,
  RiderStatsVO,
  RiderTaskItemVO,
  RiderTrackPointVO,
  WithdrawOrderVO,
} from './types'

/**
 * 骑手端接口。路径前缀 /v1 + http.ts 的 BASE_URL=/api。
 * riderId 全部由后端从登录态解析，前端不传、也不能传。
 */

/** POST /auth/login，账号密码登录（骑手账号 rider001 / Rider@123） */
export function loginByPassword(username: string, password: string): Promise<LoginVO> {
  return request<LoginVO>({
    url: '/v1/auth/login',
    method: 'POST',
    data: { username, password },
  })
}

export interface SubmitCertificationPayload {
  realName: string
  idCard: string
  idCardFrontFileId: number | null
  idCardBackFileId: number | null
  healthCertFileId: number | null
  /** yyyy-MM-dd */
  healthCertExpireAt: string
  /** 1 电动车 / 2 摩托 / 3 自行车 / 4 步行 */
  vehicleType: number
}

/** POST /rider/certification，返回 t_rider.id */
export function submitCertification(payload: SubmitCertificationPayload): Promise<number> {
  return request<number>({
    url: '/v1/rider/certification',
    method: 'POST',
    data: {
      realName: payload.realName,
      idCard: payload.idCard,
      idCardFrontFileId: payload.idCardFrontFileId,
      idCardBackFileId: payload.idCardBackFileId,
      healthCertFileId: payload.healthCertFileId,
      healthCertExpireAt: payload.healthCertExpireAt,
      vehicleType: payload.vehicleType,
    },
  })
}

/** GET /rider/certification；未创建档案时 certStatus=0 */
export function fetchCertification(): Promise<RiderCertStatusVO> {
  return request<RiderCertStatusVO>({ url: '/v1/rider/certification' })
}

export interface OnlinePayload {
  online: boolean
  /** 上线时必填 */
  lng?: number | null
  lat?: number | null
  /** 下线原因，最长 128 */
  reason?: string | null
}

/** PUT /rider/online，上线必须带经纬度 */
export function setOnlineState(payload: OnlinePayload): Promise<void> {
  return request<void>({
    url: '/v1/rider/online',
    method: 'PUT',
    data: {
      online: payload.online,
      lng: payload.lng ?? null,
      lat: payload.lat ?? null,
      reason: payload.reason ?? null,
    },
  })
}

/** GET /rider/orders/pending，系统派单待确认（含服务端算好的剩余秒数） */
export function fetchPendingDispatches(silent = false): Promise<PendingDispatchVO[]> {
  return request<PendingDispatchVO[]>({ url: '/v1/rider/orders/pending', silent })
}

export interface HallQuery {
  lng?: number | null
  lat?: number | null
  /** distance | fee | time，默认 distance（后端排序） */
  sortBy?: string
  silent?: boolean
}

/** GET /rider/hall，需已上线且认证通过 */
export function fetchHall(query: HallQuery = {}): Promise<HallOrderVO[]> {
  const data: Record<string, unknown> = { sortBy: query.sortBy ?? 'distance' }
  if (query.lng !== undefined && query.lng !== null) {
    data.lng = query.lng
  }
  if (query.lat !== undefined && query.lat !== null) {
    data.lat = query.lat
  }
  return request<HallOrderVO[]>({ url: '/v1/rider/hall', data, silent: query.silent })
}

/** POST /rider/dispatch/{deliveryId}/accept */
export function acceptDispatch(deliveryId: number): Promise<DeliveryActionVO> {
  return request<DeliveryActionVO>({
    url: `/v1/rider/dispatch/${deliveryId}/accept`,
    method: 'POST',
  })
}

/** POST /rider/dispatch/{deliveryId}/reject，reason 必填且 ≤128 */
export function rejectDispatch(deliveryId: number, reason: string): Promise<void> {
  return request<void>({
    url: `/v1/rider/dispatch/${deliveryId}/reject`,
    method: 'POST',
    data: { reason },
  })
}

/** POST /rider/deliveries/{id}/grab，服务端分布式锁保证只有一人成功 */
export function grabDelivery(deliveryId: number): Promise<DeliveryActionVO> {
  return request<DeliveryActionVO>({
    url: `/v1/rider/deliveries/${deliveryId}/grab`,
    method: 'POST',
  })
}

/** POST /rider/deliveries/{id}/pickup，20 → 40 */
export function pickupDelivery(deliveryId: number): Promise<DeliveryActionVO> {
  return request<DeliveryActionVO>({
    url: `/v1/rider/deliveries/${deliveryId}/pickup`,
    method: 'POST',
    silent: true,
  })
}

export interface DeliverPayload {
  lng: number | null
  lat: number | null
  photoFileId: number | null
}

/** POST /rider/deliveries/{id}/delivered，40 → 50 */
export function deliverDelivery(
  deliveryId: number,
  payload: DeliverPayload,
): Promise<DeliveryActionVO> {
  return request<DeliveryActionVO>({
    url: `/v1/rider/deliveries/${deliveryId}/delivered`,
    method: 'POST',
    data: {
      lng: payload.lng,
      lat: payload.lat,
      photoFileId: payload.photoFileId,
    },
    silent: true,
  })
}

export interface AnomalyPayload {
  /** 1 超时 / 2 联系不上用户 / 3 地址错误 / 4 餐品破损 / 5 商家出餐慢 / 6 用户拒收 */
  type: number
  remark?: string | null
  images?: string[] | null
}

/** POST /rider/deliveries/{id}/anomaly */
export function reportAnomaly(deliveryId: number, payload: AnomalyPayload): Promise<void> {
  return request<void>({
    url: `/v1/rider/deliveries/${deliveryId}/anomaly`,
    method: 'POST',
    data: {
      type: payload.type,
      remark: payload.remark ?? null,
      images: payload.images ?? null,
    },
  })
}

/** POST /rider/track，一次最多 20 点；失败由 location.ts 留队列补传，故 silent */
export function reportTracks(
  deliveryId: number,
  points: RiderTrackPointVO[],
): Promise<void> {
  return request<void>({
    url: '/v1/rider/track',
    method: 'POST',
    data: { deliveryId, points },
    silent: true,
  })
}

/** GET /rider/income/overview */
export function fetchIncomeOverview(): Promise<IncomeOverviewVO> {
  return request<IncomeOverviewVO>({ url: '/v1/rider/income/overview', silent: true })
}

export interface IncomeDetailQuery {
  dateFrom?: string
  dateTo?: string
  pageNum?: number
  pageSize?: number
}

/** GET /rider/income/details，按 settlement_date 倒序 */
export function fetchIncomeDetails(
  query: IncomeDetailQuery = {},
): Promise<PageResult<IncomeDetailVO>> {
  const data: Record<string, unknown> = {
    pageNum: query.pageNum ?? 1,
    pageSize: query.pageSize ?? 20,
  }
  if (query.dateFrom) {
    data.dateFrom = query.dateFrom
  }
  if (query.dateTo) {
    data.dateTo = query.dateTo
  }
  return request<PageResult<IncomeDetailVO>>({ url: '/v1/rider/income/details', data })
}

/* ------------------------------------------------------------------ */
/* 提现                                                                */
/* ------------------------------------------------------------------ */

/** GET /rider/payouts/balance，可提现余额（分） */
export function fetchWithdrawBalance(): Promise<RiderBalanceVO> {
  return request<RiderBalanceVO>({ url: '/v1/rider/payouts/balance' })
}

export interface WithdrawApplyPayload {
  /** 单位分 */
  amount: number
  /** 1支付宝 2银行卡 */
  channel: number
}

/** POST /rider/payouts，返回新建提现单 */
export function applyWithdraw(payload: WithdrawApplyPayload): Promise<WithdrawOrderVO> {
  return request<WithdrawOrderVO>({
    url: '/v1/rider/payouts',
    method: 'POST',
    data: {
      amount: payload.amount,
      channel: payload.channel,
    },
  })
}

export interface WithdrawQuery {
  /** 0待审核 10审核通过 20打款中 30已到账 40已驳回 */
  status?: number
  pageNum?: number
  pageSize?: number
}

/** GET /rider/payouts，按 id 倒序 */
export function fetchWithdrawals(
  query: WithdrawQuery = {},
): Promise<PageResult<WithdrawOrderVO>> {
  const data: Record<string, unknown> = {
    pageNum: query.pageNum ?? 1,
    pageSize: query.pageSize ?? 20,
  }
  if (query.status !== undefined && query.status !== null) {
    data.status = query.status
  }
  return request<PageResult<WithdrawOrderVO>>({ url: '/v1/rider/payouts', data })
}

/* ------------------------------------------------------------------ */
/* 评分、考勤、异常记录                                                */
/* ------------------------------------------------------------------ */

/** GET /rider/stats；onTimeRate 为百分比数值 0~100 */
export function fetchRiderStats(): Promise<RiderStatsVO> {
  return request<RiderStatsVO>({ url: '/v1/rider/stats' })
}

export interface AttendanceQuery {
  /** yyyy-MM-dd，缺省为最近 7 天 */
  dateFrom?: string
  dateTo?: string
}

/** GET /rider/attendance，按日返回在线次数与估算在线分钟数 */
export function fetchAttendance(
  query: AttendanceQuery = {},
): Promise<RiderAttendanceDayVO[]> {
  const data: Record<string, unknown> = {}
  if (query.dateFrom) {
    data.dateFrom = query.dateFrom
  }
  if (query.dateTo) {
    data.dateTo = query.dateTo
  }
  return request<RiderAttendanceDayVO[]>({ url: '/v1/rider/attendance', data })
}

export interface AnomalyQuery {
  pageNum?: number
  pageSize?: number
}

/** GET /rider/anomalies，本人上报记录，按 id 倒序 */
export function fetchAnomalies(
  query: AnomalyQuery = {},
): Promise<PageResult<RiderAnomalyVO>> {
  return request<PageResult<RiderAnomalyVO>>({
    url: '/v1/rider/anomalies',
    data: {
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20,
    },
  })
}

/* ------------------------------------------------------------------ */
/* 帮助中心与客服                                                      */
/* ------------------------------------------------------------------ */

/** GET /c/faqs；该路径为白名单接口，骑手账号可直接访问 */
export function fetchFaqs(keyword?: string): Promise<FaqVO[]> {
  const data: Record<string, unknown> = {}
  if (keyword) {
    data.keyword = keyword
  }
  return request<FaqVO[]>({ url: '/v1/c/faqs', data })
}

export interface CreateImSessionPayload {
  orderId?: number
  /** 对端类型：2商家 3骑手 4客服 */
  peerType: number
  peerId: number
}

/**
 * POST /im/sessions。
 * 注意：后端 getOrCreate 对「商家/骑手/客服」主体只接受 peerType=1（对端必须是消费者），
 * 骑手发起 peerType=4 的客服会话会被拒绝（10001）。Contact 页据此做降级展示。
 */
export function createImSession(payload: CreateImSessionPayload): Promise<ImSessionVO> {
  const data: Record<string, unknown> = {
    peerType: payload.peerType,
    peerId: payload.peerId,
  }
  if (payload.orderId !== undefined && payload.orderId !== null) {
    data.orderId = payload.orderId
  }
  return request<ImSessionVO>({
    url: '/v1/im/sessions',
    method: 'POST',
    data,
    // 骑手侧发起会被后端拒绝，静默交给 Contact 页做统一的降级提示
    silent: true,
  })
}

/**
 * 任务详情里的商品明细由订单模块提供，骑手端没有单独接口，
 * 这里只导出类型别名，避免页面重复声明。
 */
export type TaskItem = RiderTaskItemVO
