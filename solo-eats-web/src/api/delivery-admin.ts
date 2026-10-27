import { request } from './http'
import type { PageResult } from './types'

/**
 * 配送中心运营后台接口。
 * 字段与后端 view/DTO 一一对应：
 * - RiderView.java / RiderDetailView.java / RiderCertificationView.java
 * - AuditCertificationRequest.java / UpdateRiderStatusRequest.java / UpdateRiderLimitsRequest.java
 * - DispatchConfigView.java / SaveDispatchConfigRequest.java
 * - DeliveryOrderView.java / TrackPointView.java / ReassignDeliveryRequest.java
 * - FreightTemplateView.java / SaveFreightTemplateRequest.java / BindFreightTemplateRequest.java
 */

/** RiderView.java，手机号只返回掩码。 */
export interface RiderView {
  id: number
  riderNo: string
  realName: string | null
  /** 后端只返回掩码；种子数据可能为 null */
  phoneMask: string | null
  /** 1电动车 2摩托车 3自行车 4步行 */
  vehicleType: number
  /** t_rider.cert_status：0未提交 1待审核 2已通过 3已驳回 */
  certStatus: number
  /** 0正常 1停用 2拉黑 */
  status: number
  /** 0离线 1在线 2接单中 */
  onlineStatus: number
  rating: number
  /** 准时率，0~100 的百分比数值 */
  onTimeRate: number
  totalOrders: number
  lastOnlineAt: string | null
  createdAt: string
}

/** RiderCertificationView.java，认证记录状态与 t_rider.cert_status 是两套编码。 */
export interface RiderCertificationView {
  id: number
  realName: string
  /** LocalDate，yyyy-MM-dd */
  healthCertExpireAt: string | null
  /** 0待审 1通过 2驳回 */
  status: number
  auditBy: number | null
  auditAt: string | null
  auditRemark: string | null
  createdAt: string
}

/** RiderDetailView.java，certifications 按 id 倒序。 */
export interface RiderDetailView {
  rider: RiderView
  certifications: RiderCertificationView[]
  todayOnlineSeconds: number
}

export interface RiderListParams {
  certStatus?: number
  status?: number
  keyword?: string
  pageNum?: number
  pageSize?: number
}

/** AuditCertificationRequest.java */
export interface AuditCertificationPayload {
  /** 1通过 2驳回 */
  status: 1 | 2
  remark?: string
}

/** UpdateRiderStatusRequest.java */
export interface UpdateRiderStatusPayload {
  /** 0正常 1停用 2拉黑 */
  status: 0 | 1 | 2
}

/** UpdateRiderLimitsRequest.java */
export interface UpdateRiderLimitsPayload {
  maxConcurrentOrders: number
  pickupRadiusM: number
}

/** DispatchConfigView.java */
export interface DispatchConfigView {
  id: number
  /** 1全局 2城市 3门店（后端只支持 1、3） */
  configScope: number
  shopId: number | null
  distanceWeight: number
  loadWeight: number
  ratingWeight: number
  timeoutWeight: number
  dispatchExpireSeconds: number
  maxRetry: number
  grabRadiusM: number
  grabMaxOrders: number
  /** 1开启抢单大厅 */
  grabEnabled: number
  /** 1开启自动派单 */
  autoDispatchEnabled: number
  /** 0启用 1停用 */
  status: number
  updatedAt: string
}

/** SaveDispatchConfigRequest.java */
export interface SaveDispatchConfigPayload {
  configScope: number
  shopId?: number | null
  distanceWeight: number
  loadWeight: number
  ratingWeight: number
  timeoutWeight: number
  dispatchExpireSeconds: number
  maxRetry: number
  grabRadiusM: number
  grabMaxOrders: number
  grabEnabled?: number
  autoDispatchEnabled?: number
  status?: number
}

/** DeliveryOrderView.java，deliveryFee 单位分。 */
export interface DeliveryOrderView {
  id: number
  deliveryNo: string
  orderId: number
  shopId: number
  riderId: number | null
  /** 0待派单 10已派单 20已接单 30已取货 40配送中 50已送达 70已取消 71已改派 */
  status: number
  pickupAddress: string
  pickupLng: number
  pickupLat: number
  receiverAddress: string
  receiverLng: number
  receiverLat: number
  distanceM: number
  deliveryFee: number
  dispatchCount: number
  promiseDeliverAt: string | null
  acceptedAt: string | null
  pickedUpAt: string | null
  deliveredAt: string | null
  createdAt: string
}

export interface DeliveryListParams {
  status?: number
  shopId?: number
  riderId?: number
  /** yyyy-MM-dd */
  dateFrom?: string
  /** yyyy-MM-dd */
  dateTo?: string
  pageNum?: number
  pageSize?: number
}

/** TrackPointView.java */
export interface TrackPointView {
  id: number
  deliveryId: number
  riderId: number | null
  lng: number
  lat: number
  speed: number | null
  accuracy: number | null
  reportAt: string
}

/**
 * ReassignDeliveryRequest.java。
 * riderId 是当前骑手，用于后端校验；后端会把配送单释放回待派单，
 * 不接收、也不会直接指定新骑手。
 */
export interface ReassignDeliveryPayload {
  riderId: number
  reason?: string
}

/** FreightTemplateView.Rule */
export interface FreightTemplateRuleView {
  id: number
  /** 1距离区间 2时段加价 3恶劣天气 4重量 5楼层 */
  ruleType: number
  conditionJson: string | null
  /** 单位分 */
  feeDelta: number
  sort: number
  /** 0启用 1停用 */
  status: number
}

/** FreightTemplateView.java，金额单位分。 */
export interface FreightTemplateView {
  id: number
  templateName: string
  baseFee: number
  baseDistanceM: number
  perStepDistanceM: number
  perStepFee: number
  maxDistanceM: number
  /** null 表示不设满额减免 */
  freeOverAmount: number | null
  /** 0启用 1停用 */
  status: number
  rules: FreightTemplateRuleView[]
  updatedAt: string
}

export interface FreightTemplateListParams {
  pageNum?: number
  pageSize?: number
}

/** SaveFreightTemplateRequest.Rule，保存时全量覆盖，不回传 id。 */
export interface FreightTemplateRulePayload {
  ruleType: number
  conditionJson?: string | null
  feeDelta: number
  sort?: number
  status?: number
}

/** SaveFreightTemplateRequest.java */
export interface SaveFreightTemplatePayload {
  templateName: string
  baseFee: number
  baseDistanceM?: number
  perStepDistanceM?: number
  perStepFee: number
  maxDistanceM?: number
  freeOverAmount?: number | null
  status?: number
  rules?: FreightTemplateRulePayload[]
}

/** BindFreightTemplateRequest.java */
export interface BindFreightTemplateShopPayload {
  shopId: number
}

// ---------------------------------------------------------------- 骑手

/** GET /api/v1/admin/riders */
export function listRiders(params: RiderListParams): Promise<PageResult<RiderView>> {
  return request<PageResult<RiderView>>({
    url: '/v1/admin/riders',
    method: 'GET',
    params,
  })
}

/** GET /api/v1/admin/riders/{id} */
export function getRiderDetail(id: number): Promise<RiderDetailView> {
  return request<RiderDetailView>({
    url: `/v1/admin/riders/${id}`,
    method: 'GET',
  })
}

/** POST /api/v1/admin/riders/{id}/certification/audit */
export function auditRiderCertification(
  id: number,
  payload: AuditCertificationPayload,
): Promise<void> {
  return request<void>({
    url: `/v1/admin/riders/${id}/certification/audit`,
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/riders/{id}/status */
export function updateRiderStatus(
  id: number,
  payload: UpdateRiderStatusPayload,
): Promise<void> {
  return request<void>({
    url: `/v1/admin/riders/${id}/status`,
    method: 'PUT',
    data: payload,
  })
}

/** PUT /api/v1/admin/riders/{id}/limits，覆盖式保存两个字段 */
export function updateRiderLimits(
  id: number,
  payload: UpdateRiderLimitsPayload,
): Promise<void> {
  return request<void>({
    url: `/v1/admin/riders/${id}/limits`,
    method: 'PUT',
    data: payload,
  })
}

// ---------------------------------------------------------------- 派单策略

/** GET /api/v1/admin/dispatch-config，不传 shopId 时返回全局生效配置 */
export function getDispatchConfig(shopId?: number): Promise<DispatchConfigView> {
  return request<DispatchConfigView>({
    url: '/v1/admin/dispatch-config',
    method: 'GET',
    params: { shopId },
  })
}

/** PUT /api/v1/admin/dispatch-config */
export function saveDispatchConfig(
  payload: SaveDispatchConfigPayload,
): Promise<DispatchConfigView> {
  return request<DispatchConfigView>({
    url: '/v1/admin/dispatch-config',
    method: 'PUT',
    data: payload,
  })
}

// ---------------------------------------------------------------- 配送单

/** GET /api/v1/admin/deliveries */
export function listDeliveries(
  params: DeliveryListParams,
): Promise<PageResult<DeliveryOrderView>> {
  return request<PageResult<DeliveryOrderView>>({
    url: '/v1/admin/deliveries',
    method: 'GET',
    params,
  })
}

/** GET /api/v1/admin/deliveries/{id}/track，返回全部轨迹点 */
export function getDeliveryTrack(id: number): Promise<TrackPointView[]> {
  return request<TrackPointView[]>({
    url: `/v1/admin/deliveries/${id}/track`,
    method: 'GET',
  })
}

/** POST /api/v1/admin/deliveries/{id}/reassign */
export function reassignDelivery(
  id: number,
  payload: ReassignDeliveryPayload,
): Promise<void> {
  return request<void>({
    url: `/v1/admin/deliveries/${id}/reassign`,
    method: 'POST',
    data: payload,
  })
}

// ---------------------------------------------------------------- 运费模板

/** GET /api/v1/admin/freight-templates */
export function listFreightTemplates(
  params: FreightTemplateListParams,
): Promise<PageResult<FreightTemplateView>> {
  return request<PageResult<FreightTemplateView>>({
    url: '/v1/admin/freight-templates',
    method: 'GET',
    params,
  })
}

/** POST /api/v1/admin/freight-templates */
export function createFreightTemplate(
  payload: SaveFreightTemplatePayload,
): Promise<FreightTemplateView> {
  return request<FreightTemplateView>({
    url: '/v1/admin/freight-templates',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/freight-templates/{id}，rules 全量覆盖 */
export function updateFreightTemplate(
  id: number,
  payload: SaveFreightTemplatePayload,
): Promise<FreightTemplateView> {
  return request<FreightTemplateView>({
    url: `/v1/admin/freight-templates/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** POST /api/v1/admin/freight-templates/{id}/bind-shop */
export function bindFreightTemplateShop(
  id: number,
  payload: BindFreightTemplateShopPayload,
): Promise<void> {
  return request<void>({
    url: `/v1/admin/freight-templates/${id}/bind-shop`,
    method: 'POST',
    data: payload,
  })
}
