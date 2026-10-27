import { request } from './http'
import type { PageResult } from './types'

/**
 * 营销中心（满减/折扣 + 秒杀）后台接口。
 * 金额字段一律为分（后端 long）；discountRate 是 0~1 的小数（decimal(4,2)）。
 * 列表接口不展开子表：promotion 的 rules/scopes 与 seckill 的 items 均为空数组，
 * 需要子表时单独请求详情。
 */

/** PromotionView.RuleItem，thresholdAmount 后端为原始 long */
export interface PromotionRuleView {
  id: number
  thresholdAmount: number
  discountAmount: number | null
  discountRate: number | null
  maxDiscount: number | null
  sort: number
}

/** PromotionView.ScopeItem */
export interface PromotionScopeView {
  refType: number
  refId: number
}

/** PromotionView，GET /v1/admin/promotions/{id} 返回完整子表 */
export interface PromotionView {
  id: number
  promotionType: number
  promotionName: string
  bearer: number
  merchantId: number | null
  shopId: number | null
  scopeType: number
  startAt: string
  endAt: string
  stackable: number
  priority: number
  status: number
  totalBudget: number | null
  usedBudget: number
  createdAt: string
  updatedAt: string
  rules: PromotionRuleView[]
  scopes: PromotionScopeView[]
}

export interface PromotionListParams {
  status?: number
  promotionType?: number
  bearer?: number
  keyword?: string
  pageNum?: number
  pageSize?: number
}

/** PromotionSaveRequest.RuleItem；与活动类型互斥的字段传 null */
export interface PromotionRuleSavePayload {
  thresholdAmount: number
  discountAmount?: number | null
  discountRate?: number | null
  maxDiscount?: number | null
  sort: number
}

/** PromotionSaveRequest.ScopeRefItem */
export interface PromotionScopeRefPayload {
  refType: number
  refId: number
}

/** PromotionSaveRequest；status/usedBudget 不走该入口 */
export interface PromotionSavePayload {
  promotionType: number
  promotionName: string
  bearer: number
  merchantId?: number | null
  shopId?: number | null
  scopeType: number
  startAt: string
  endAt: string
  stackable: number
  priority: number
  totalBudget?: number | null
  rules: PromotionRuleSavePayload[]
  scopeRefs: PromotionScopeRefPayload[]
}

/** SeckillActivityView.ItemView，remainStock 由后端按 max(stock - sold, 0) 计算 */
export interface SeckillItemView {
  id: number
  shopId: number
  spuId: number
  skuId: number
  seckillPrice: number
  seckillStock: number
  soldQty: number
  remainStock: number
  limitPerUser: number
}

/** SeckillActivityView；列表接口 items 为空，详情才有 */
export interface SeckillActivityView {
  id: number
  activityName: string
  sessionStart: string
  sessionEnd: string
  preheatAt: string | null
  status: number
  items: SeckillItemView[]
  createdAt: string
  updatedAt: string
}

export interface SeckillActivityListParams {
  status?: number
  pageNum?: number
  pageSize?: number
}

/** SeckillActivitySaveRequest.Item，seckillPrice 单位分 */
export interface SeckillItemSavePayload {
  shopId: number
  spuId: number
  skuId: number
  seckillPrice: number
  seckillStock: number
  limitPerUser: number
}

/** SeckillActivitySaveRequest；status 由后端按时间窗推导，不在请求内 */
export interface SeckillActivitySavePayload {
  activityName: string
  sessionStart: string
  sessionEnd: string
  preheatAt?: string | null
  items: SeckillItemSavePayload[]
}

/** GET /api/v1/admin/promotions，权限码 marketing:promotion:list */
export function listPromotions(
  params: PromotionListParams,
): Promise<PageResult<PromotionView>> {
  return request<PageResult<PromotionView>>({
    url: '/v1/admin/promotions',
    method: 'GET',
    params,
  })
}

/** GET /api/v1/admin/promotions/{id} */
export function getPromotion(id: number): Promise<PromotionView> {
  return request<PromotionView>({
    url: `/v1/admin/promotions/${id}`,
    method: 'GET',
  })
}

/** POST /api/v1/admin/promotions，返回新活动 id，初始状态 0 草稿 */
export function createPromotion(payload: PromotionSavePayload): Promise<number> {
  return request<number>({
    url: '/v1/admin/promotions',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/promotions/{id}，档位与范围项整体覆盖 */
export function updatePromotion(
  id: number,
  payload: PromotionSavePayload,
): Promise<void> {
  return request<void>({
    url: `/v1/admin/promotions/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** PUT /api/v1/admin/promotions/{id}/status，status：0草稿 1进行中 2已结束 3暂停 */
export function updatePromotionStatus(id: number, status: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/promotions/${id}/status`,
    method: 'PUT',
    data: { status },
  })
}

/** GET /api/v1/admin/seckill/activities，权限码 marketing:seckill:list */
export function listSeckillActivities(
  params: SeckillActivityListParams,
): Promise<PageResult<SeckillActivityView>> {
  return request<PageResult<SeckillActivityView>>({
    url: '/v1/admin/seckill/activities',
    method: 'GET',
    params,
  })
}

/** GET /api/v1/admin/seckill/activities/{id} */
export function getSeckillActivity(id: number): Promise<SeckillActivityView> {
  return request<SeckillActivityView>({
    url: `/v1/admin/seckill/activities/${id}`,
    method: 'GET',
  })
}

/** POST /api/v1/admin/seckill/activities，返回新场次 id */
export function createSeckillActivity(
  payload: SeckillActivitySavePayload,
): Promise<number> {
  return request<number>({
    url: '/v1/admin/seckill/activities',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/seckill/activities/{id}，items 整体同步；已预占商品不可移除 */
export function updateSeckillActivity(
  id: number,
  payload: SeckillActivitySavePayload,
): Promise<void> {
  return request<void>({
    url: `/v1/admin/seckill/activities/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** 会员等级后台视图；growthThreshold 为近 365 天成长值门槛 */
export interface MemberLevelView {
  id: number
  levelCode: string
  levelName: string
  levelNo: number
  growthThreshold: number
  star: number
  benefitsJson: string | null
  iconUrl: string | null
  sort: number
  status: number
  createdAt: string
  updatedAt: string
}

/** 会员等级新建/编辑入参；status 为空时后端按 0 启用处理 */
export interface MemberLevelSavePayload {
  levelCode: string
  levelName: string
  levelNo: number
  growthThreshold: number
  star: number
  benefitsJson?: string | null
  iconUrl?: string | null
  sort?: number
  status?: number
}

/** GET /api/v1/admin/member-levels，权限码 marketing:member:list */
export function listMemberLevels(): Promise<MemberLevelView[]> {
  return request<MemberLevelView[]>({
    url: '/v1/admin/member-levels',
    method: 'GET',
  })
}

/** POST /api/v1/admin/member-levels，返回新等级 id */
export function createMemberLevel(
  payload: MemberLevelSavePayload,
): Promise<number> {
  return request<number>({
    url: '/v1/admin/member-levels',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/member-levels/{id} */
export function updateMemberLevel(
  id: number,
  payload: MemberLevelSavePayload,
): Promise<void> {
  return request<void>({
    url: `/v1/admin/member-levels/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** POST /api/v1/admin/member-levels/recalc，返回实际发生变更的人数 */
export function recalcMemberLevels(): Promise<number> {
  return request<number>({
    url: '/v1/admin/member-levels/recalc',
    method: 'POST',
  })
}
