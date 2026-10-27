import { request } from './http'
import type { CategoryNode, PageResult } from './types'

/**
 * 商品中心运营后台接口。
 * 字段与后端 view/DTO 一一对应：
 * - CategoryNode.java / SaveCategoryRequest.java
 * - SpecGroupView.java / SaveSpecTemplateRequest.java
 * - AddonGroupView.java / SaveAddonTemplateRequest.java
 * - SoldOutRecordView.java / ReasonStatView.java / AdminSoldOutController.java
 * - ReviewView.java / ReviewStatusRequest.java / AdminReviewController.java
 * - MediaGroupView.java / MediaItemView.java / SaveMediaGroupRequest.java / SaveMediaItemRequest.java
 */

export type { CategoryNode }

/** CategoryNode.java */
export interface ProductCategoryNode extends CategoryNode {}

/** SpecGroupView.java，priceDelta 单位分，可为负。 */
export interface SpecOptionView {
  id: number
  optionName: string
  priceDelta: number
  sort: number
  /** 0启用 1停用 */
  status: number
}

export interface SpecGroupView {
  id: number
  groupName: string
  /** 1单选 2多选 */
  groupType: number
  /** 1必选 0可选 */
  isRequired: number
  sort: number
  options: SpecOptionView[]
}

/** AddonGroupView.java，price 单位分，maxSelect=0 表示不限。 */
export interface AddonItemView {
  id: number
  itemName: string
  price: number
  /** null 表示库存不限 */
  stock: number | null
  sort: number
  /** 0启用 1停用 */
  status: number
}

export interface AddonGroupView {
  id: number
  groupName: string
  minSelect: number
  /** 0 表示不限 */
  maxSelect: number
  sort: number
  items: AddonItemView[]
}

/** SoldOutRecordView.java */
export interface SoldOutRecordView {
  id: number
  shopId: number
  skuId: number
  spuId: number
  reason: string
  soldOutAt: string
  restoredAt: string | null
  operatorId: number | null
  operatorName: string | null
}

/** ReasonStatView.java */
export interface ReasonStatView {
  reason: string
  count: number
}

/** ReviewView.java，匿名评价 userId 为 null。 */
export interface ReviewView {
  id: number
  orderId: number
  shopId: number
  spuId: number
  skuId: number
  userId: number | null
  overallScore: number
  tasteScore: number | null
  packageScore: number | null
  deliveryScore: number | null
  content: string | null
  images: string[]
  tags: string[]
  /** 1匿名 0非匿名 */
  anonymous: number
  /** 0待审 1已通过 2已隐藏 */
  status: number
  /** 1置顶 0普通 */
  topFlag: number
  merchantReply: string | null
  replyAt: string | null
  appendContent: string | null
  appendAt: string | null
  createdAt: string
}

/** MediaGroupView.java */
export interface MediaGroupView {
  id: number
  groupName: string
  parentId: number
  sort: number
  /** 0启用 1停用 */
  status: number
}

/** MediaItemView.java，fileId 指向 t_file.id，本视图不含文件名与 URL。 */
export interface MediaItemView {
  id: number
  groupId: number
  fileId: number
  /** 1图片 2视频 */
  mediaType: number
  sort: number
  /** 0启用 1停用 */
  status: number
}

/** SaveCategoryRequest.java */
export interface CategoryPayload {
  /** 0 表示一级品类 */
  parentId?: number | null
  categoryName: string
  iconUrl?: string | null
  sort?: number | null
  /** 0启用 1停用 */
  status?: number | null
}

/** SaveSpecTemplateRequest.java */
export interface SpecOptionPayload {
  optionName: string
  /** 单位分，可为负 */
  priceDelta?: number | null
  sort?: number | null
  /** 0启用 1停用 */
  status?: number | null
}

export interface SpecTemplatePayload {
  groupName: string
  /** 1单选 2多选 */
  groupType?: number | null
  /** 1必选 0可选 */
  isRequired?: number | null
  sort?: number | null
  options: SpecOptionPayload[]
}

/** SaveAddonTemplateRequest.java */
export interface AddonItemPayload {
  itemName: string
  /** 单位分，必须 ≥0 */
  price?: number | null
  /** null 表示库存不限 */
  stock?: number | null
  sort?: number | null
  /** 0启用 1停用 */
  status?: number | null
}

export interface AddonTemplatePayload {
  groupName: string
  minSelect?: number | null
  /** 0 表示不限 */
  maxSelect?: number | null
  sort?: number | null
  items: AddonItemPayload[]
}

/** AdminSoldOutController 查询参数，start/end 格式 yyyy-MM-dd HH:mm:ss。 */
export interface SoldOutListParams {
  shopId?: number
  skuId?: number
  reason?: string
  start?: string
  end?: string
  pageNum?: number
  pageSize?: number
}

export interface ReasonStatsParams {
  /** 必传，未指定时页面不调用该接口 */
  shopId: number
  start?: string
  end?: string
}

/** AdminReviewController 查询参数，scoreLevel：1差评 2中评 3好评。 */
export interface ReviewListParams {
  shopId?: number
  spuId?: number
  riderId?: number
  /** 0待审 1已通过 2已隐藏 */
  status?: number
  /** 1差评 2中评 3好评 */
  scoreLevel?: number
  pageNum?: number
  pageSize?: number
}

/** ReviewStatusRequest.java，status 与 topFlag 至少提供一个。 */
export interface ReviewStatusPayload {
  /** 0待审 1已通过 2已隐藏 */
  status?: number | null
  /** 1置顶 0普通 */
  topFlag?: number | null
}

/** SaveMediaGroupRequest.java */
export interface MediaGroupPayload {
  groupName: string
  /** 0 表示根分组 */
  parentId?: number | null
  sort?: number | null
  status?: number | null
}

/** SaveMediaItemRequest.java */
export interface MediaItemPayload {
  fileId: number
  /** 1图片 2视频 */
  mediaType?: number | null
  sort?: number | null
  status?: number | null
}

// ---------------------------------------------------------------- 品类

/** GET /api/v1/admin/categories/tree（无分页），权限码 product:category:list */
export function getCategoryTree(): Promise<CategoryNode[]> {
  return request<CategoryNode[]>({
    url: '/v1/admin/categories/tree',
    method: 'GET',
  })
}

/** POST /api/v1/admin/categories */
export function createCategory(payload: CategoryPayload): Promise<CategoryNode> {
  return request<CategoryNode>({
    url: '/v1/admin/categories',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/categories/{id} */
export function updateCategory(
  id: number,
  payload: CategoryPayload,
): Promise<CategoryNode> {
  return request<CategoryNode>({
    url: `/v1/admin/categories/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** DELETE /api/v1/admin/categories/{id} */
export function deleteCategory(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/categories/${id}`,
    method: 'DELETE',
  })
}

// ---------------------------------------------------------------- 规格模板

/** GET /api/v1/admin/spec-templates（无分页） */
export function listSpecTemplates(): Promise<SpecGroupView[]> {
  return request<SpecGroupView[]>({
    url: '/v1/admin/spec-templates',
    method: 'GET',
  })
}

/** POST /api/v1/admin/spec-templates */
export function createSpecTemplate(
  payload: SpecTemplatePayload,
): Promise<SpecGroupView> {
  return request<SpecGroupView>({
    url: '/v1/admin/spec-templates',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/spec-templates/{id} */
export function updateSpecTemplate(
  id: number,
  payload: SpecTemplatePayload,
): Promise<SpecGroupView> {
  return request<SpecGroupView>({
    url: `/v1/admin/spec-templates/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** DELETE /api/v1/admin/spec-templates/{id} */
export function deleteSpecTemplate(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/spec-templates/${id}`,
    method: 'DELETE',
  })
}

// ---------------------------------------------------------------- 加料模板

/** GET /api/v1/admin/addon-templates（无分页） */
export function listAddonTemplates(): Promise<AddonGroupView[]> {
  return request<AddonGroupView[]>({
    url: '/v1/admin/addon-templates',
    method: 'GET',
  })
}

/** POST /api/v1/admin/addon-templates */
export function createAddonTemplate(
  payload: AddonTemplatePayload,
): Promise<AddonGroupView> {
  return request<AddonGroupView>({
    url: '/v1/admin/addon-templates',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/addon-templates/{id} */
export function updateAddonTemplate(
  id: number,
  payload: AddonTemplatePayload,
): Promise<AddonGroupView> {
  return request<AddonGroupView>({
    url: `/v1/admin/addon-templates/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** DELETE /api/v1/admin/addon-templates/{id} */
export function deleteAddonTemplate(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/addon-templates/${id}`,
    method: 'DELETE',
  })
}

// ---------------------------------------------------------------- 估清

/** GET /api/v1/admin/sold-out */
export function listSoldOutRecords(
  params: SoldOutListParams,
): Promise<PageResult<SoldOutRecordView>> {
  return request<PageResult<SoldOutRecordView>>({
    url: '/v1/admin/sold-out',
    method: 'GET',
    params,
  })
}

/** GET /api/v1/admin/sold-out/reason-stats，shopId 必传 */
export function getSoldOutReasonStats(
  params: ReasonStatsParams,
): Promise<ReasonStatView[]> {
  return request<ReasonStatView[]>({
    url: '/v1/admin/sold-out/reason-stats',
    method: 'GET',
    params,
  })
}

// ---------------------------------------------------------------- 评价

/** GET /api/v1/admin/reviews */
export function listReviews(
  params: ReviewListParams,
): Promise<PageResult<ReviewView>> {
  return request<PageResult<ReviewView>>({
    url: '/v1/admin/reviews',
    method: 'GET',
    params,
  })
}

/** PUT /api/v1/admin/reviews/{id}/status */
export function auditReview(
  id: number,
  payload: ReviewStatusPayload,
): Promise<ReviewView> {
  return request<ReviewView>({
    url: `/v1/admin/reviews/${id}/status`,
    method: 'PUT',
    data: payload,
  })
}

// ---------------------------------------------------------------- 素材库

/** GET /api/v1/admin/media-groups（无分页） */
export function listMediaGroups(): Promise<MediaGroupView[]> {
  return request<MediaGroupView[]>({
    url: '/v1/admin/media-groups',
    method: 'GET',
  })
}

/** POST /api/v1/admin/media-groups */
export function createMediaGroup(payload: MediaGroupPayload): Promise<MediaGroupView> {
  return request<MediaGroupView>({
    url: '/v1/admin/media-groups',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/media-groups/{id} */
export function updateMediaGroup(
  id: number,
  payload: MediaGroupPayload,
): Promise<MediaGroupView> {
  return request<MediaGroupView>({
    url: `/v1/admin/media-groups/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** DELETE /api/v1/admin/media-groups/{id} */
export function deleteMediaGroup(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/media-groups/${id}`,
    method: 'DELETE',
  })
}

/** GET /api/v1/admin/media-groups/{id}/items（无分页） */
export function listMediaItems(groupId: number): Promise<MediaItemView[]> {
  return request<MediaItemView[]>({
    url: `/v1/admin/media-groups/${groupId}/items`,
    method: 'GET',
  })
}

/** POST /api/v1/admin/media-groups/{id}/items */
export function addMediaItem(
  groupId: number,
  payload: MediaItemPayload,
): Promise<MediaItemView> {
  return request<MediaItemView>({
    url: `/v1/admin/media-groups/${groupId}/items`,
    method: 'POST',
    data: payload,
  })
}

/** DELETE /api/v1/admin/media-items/{id} */
export function deleteMediaItem(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/media-items/${id}`,
    method: 'DELETE',
  })
}
