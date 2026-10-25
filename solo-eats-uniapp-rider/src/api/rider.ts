import { request } from './http'
import type {
  DeliveryActionVO,
  HallOrderVO,
  IncomeDetailVO,
  IncomeOverviewVO,
  LoginVO,
  PageResult,
  PendingDispatchVO,
  RiderCertStatusVO,
  RiderTaskItemVO,
  RiderTrackPointVO,
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

/**
 * 任务详情里的商品明细由订单模块提供，骑手端没有单独接口，
 * 这里只导出类型别名，避免页面重复声明。
 */
export type TaskItem = RiderTaskItemVO
