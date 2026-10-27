import { request } from './http'
import type { PageResult } from './types'

/**
 * 内容运营后台接口。字段来源：
 * AdminContentController / AdminMessageTemplateController 及其 DTO/VO。
 * 金额无；日期请求格式统一 yyyy-MM-dd HH:mm:ss。
 */

// ---------------------------------------------------------------- 首页装修

/** BannerView.java */
export interface BannerView {
  id: number
  slotCode: string
  title: string
  imageUrl: string
  jumpType: number
  jumpValue: string | null
  sort: number
  effectiveFrom: string
  effectiveTo: string | null
  clickCount: number
  viewCount: number
  status: number
}

/** RecommendedShopView.java */
export interface RecommendedShopView {
  id: number
  shopName: string
  logoUrl: string | null
  coverUrl: string | null
  rating: number
  monthSales: number
  minOrderAmount: number
  deliveryFee: number
  prepMinutes: number
}

/** RecommendedSpuView.java */
export interface RecommendedSpuView {
  spuId: number
  spuName: string
  subtitle: string | null
  mainImage: string | null
  shopId: number
  shopName: string
  price: number
  monthSales: number
}

/** NoticeBriefView.java */
export interface NoticeBriefView {
  id: number
  title: string
  publishAt: string
}

/** FloorView.java，config 为 config_json，字段按 floorType 区分 */
export interface FloorView {
  id: number
  pageId: number
  floorType: number
  floorTypeName?: string
  title: string
  config: Record<string, unknown> | null
  sort: number
  effectiveFrom: string
  effectiveTo: string | null
  audience: number
  status: number
  banners?: BannerView[]
  shops?: RecommendedShopView[]
  products?: RecommendedSpuView[]
  notices?: NoticeBriefView[]
}

/** HomePageView.java，@JsonInclude(NON_NULL) 字段可能缺省 */
export interface HomePageView {
  id: number
  pageCode: string
  pageName: string
  status: number
  version: number
  publishAt?: string | null
  banners?: BannerView[]
  floors: FloorView[]
}

/** SaveHomeFloorRequest.java */
export interface SaveHomeFloorPayload {
  pageCode?: string
  floorType: number
  title: string
  config: Record<string, unknown>
  sort: number
  effectiveFrom: string
  effectiveTo: string | null
  audience: number
  status: number
}

/** SortHomeFloorsRequest.java，按数组顺序写入 sort=1..n */
export interface SortHomeFloorsPayload {
  ids: number[]
}

/** PublishHomePageRequest.java */
export interface PublishHomePagePayload {
  pageCode: string
}

/** GET /api/v1/admin/content/home */
export function getHomePage(pageCode: string): Promise<HomePageView> {
  return request<HomePageView>({
    url: '/v1/admin/content/home',
    method: 'GET',
    params: { pageCode },
  })
}

/** GET /api/v1/admin/content/home/preview，仅返回启用楼层 */
export function previewHomePage(pageCode: string): Promise<HomePageView> {
  return request<HomePageView>({
    url: '/v1/admin/content/home/preview',
    method: 'GET',
    params: { pageCode },
  })
}

/** POST /api/v1/admin/content/home/floors，返回新楼层 id */
export function createHomeFloor(payload: SaveHomeFloorPayload): Promise<number> {
  return request<number>({
    url: '/v1/admin/content/home/floors',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/content/home/floors/{id} */
export function updateHomeFloor(
  id: number,
  payload: SaveHomeFloorPayload,
): Promise<void> {
  return request<void>({
    url: `/v1/admin/content/home/floors/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** DELETE /api/v1/admin/content/home/floors/{id} */
export function deleteHomeFloor(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/content/home/floors/${id}`,
    method: 'DELETE',
  })
}

/** PUT /api/v1/admin/content/home/floors/sort */
export function sortHomeFloors(ids: number[]): Promise<void> {
  return request<void>({
    url: '/v1/admin/content/home/floors/sort',
    method: 'PUT',
    data: { ids } satisfies SortHomeFloorsPayload,
  })
}

/** POST /api/v1/admin/content/home/publish，引用无效时返回 90020 */
export function publishHomePage(pageCode: string): Promise<void> {
  return request<void>({
    url: '/v1/admin/content/home/publish',
    method: 'POST',
    data: { pageCode } satisfies PublishHomePagePayload,
  })
}

// ---------------------------------------------------------------- 广告位

export interface BannerListParams {
  slotCode?: string
  status?: number
  pageNum: number
  pageSize: number
}

/** SaveBannerRequest.java */
export interface SaveBannerPayload {
  slotCode: string
  title: string
  imageUrl: string
  jumpType: number
  jumpValue: string | null
  sort: number
  effectiveFrom: string
  effectiveTo: string | null
  status: number
}

/** GET /api/v1/admin/banners */
export function listBanners(
  params: BannerListParams,
): Promise<PageResult<BannerView>> {
  return request<PageResult<BannerView>>({
    url: '/v1/admin/banners',
    method: 'GET',
    params,
  })
}

/** GET /api/v1/admin/banners/{id} */
export function getBanner(id: number): Promise<BannerView> {
  return request<BannerView>({
    url: `/v1/admin/banners/${id}`,
    method: 'GET',
  })
}

/** POST /api/v1/admin/banners */
export function createBanner(payload: SaveBannerPayload): Promise<number> {
  return request<number>({
    url: '/v1/admin/banners',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/banners/{id} */
export function updateBanner(
  id: number,
  payload: SaveBannerPayload,
): Promise<void> {
  return request<void>({
    url: `/v1/admin/banners/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** DELETE /api/v1/admin/banners/{id} */
export function deleteBanner(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/banners/${id}`,
    method: 'DELETE',
  })
}

// ---------------------------------------------------------------- 搜索词

/** SearchKeywordView.java */
export interface SearchKeywordView {
  id: number
  keyword: string
  keywordType: number
  keywordTypeName: string
  weight: number
  status: number
  createdAt: string
  updatedAt: string
}

export interface SearchKeywordListParams {
  keywordType?: number
  pageNum: number
  pageSize: number
}

/** SaveSearchKeywordRequest.java */
export interface SaveSearchKeywordPayload {
  keyword: string
  keywordType: number
  weight: number
  status: number
}

/** GET /api/v1/admin/search-keywords */
export function listSearchKeywords(
  params: SearchKeywordListParams,
): Promise<PageResult<SearchKeywordView>> {
  return request<PageResult<SearchKeywordView>>({
    url: '/v1/admin/search-keywords',
    method: 'GET',
    params,
  })
}

/** POST /api/v1/admin/search-keywords */
export function createSearchKeyword(
  payload: SaveSearchKeywordPayload,
): Promise<number> {
  return request<number>({
    url: '/v1/admin/search-keywords',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/search-keywords/{id} */
export function updateSearchKeyword(
  id: number,
  payload: SaveSearchKeywordPayload,
): Promise<void> {
  return request<void>({
    url: `/v1/admin/search-keywords/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** DELETE /api/v1/admin/search-keywords/{id} */
export function deleteSearchKeyword(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/search-keywords/${id}`,
    method: 'DELETE',
  })
}

// ---------------------------------------------------------------- App 版本

/** AppVersionView.java */
export interface AppVersionView {
  id: number
  appType: number
  appTypeName: string
  versionName: string
  versionCode: number
  updateDesc: string | null
  downloadUrl: string
  forceUpdate: number
  grayPercent: number
  status: number
  publishAt: string | null
  createdAt: string
  updatedAt: string
}

export interface AppVersionListParams {
  appType?: number
  pageNum: number
  pageSize: number
}

/** SaveAppVersionRequest.java */
export interface SaveAppVersionPayload {
  appType: number
  versionName: string
  versionCode: number
  updateDesc: string | null
  downloadUrl: string
  forceUpdate: number
  grayPercent: number
  status: number
  publishAt: string | null
}

/** GET /api/v1/admin/app-versions */
export function listAppVersions(
  params: AppVersionListParams,
): Promise<PageResult<AppVersionView>> {
  return request<PageResult<AppVersionView>>({
    url: '/v1/admin/app-versions',
    method: 'GET',
    params,
  })
}

/** POST /api/v1/admin/app-versions，版本重复时返回 90021 */
export function createAppVersion(
  payload: SaveAppVersionPayload,
): Promise<number> {
  return request<number>({
    url: '/v1/admin/app-versions',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/app-versions/{id} */
export function updateAppVersion(
  id: number,
  payload: SaveAppVersionPayload,
): Promise<void> {
  return request<void>({
    url: `/v1/admin/app-versions/${id}`,
    method: 'PUT',
    data: payload,
  })
}

// ---------------------------------------------------------------- 消息模板

/** MessageTemplateView.java */
export interface MessageTemplateView {
  id: number
  templateCode: string
  templateName: string
  templateType: number
  titleTpl: string
  contentTpl: string
  channels: string
  status: number
  createdAt: string
  updatedAt: string
}

export interface MessageTemplateListParams {
  keyword?: string
  templateType?: number
  status?: number
  pageNum: number
  pageSize: number
}

/** SaveMessageTemplateRequest.java */
export interface SaveMessageTemplatePayload {
  templateCode: string
  templateName: string
  templateType: number
  titleTpl: string
  contentTpl: string
  channels: string
  status: number
}

/** GET /api/v1/admin/message-templates */
export function listMessageTemplates(
  params: MessageTemplateListParams,
): Promise<PageResult<MessageTemplateView>> {
  return request<PageResult<MessageTemplateView>>({
    url: '/v1/admin/message-templates',
    method: 'GET',
    params,
  })
}

/** POST /api/v1/admin/message-templates */
export function createMessageTemplate(
  payload: SaveMessageTemplatePayload,
): Promise<MessageTemplateView> {
  return request<MessageTemplateView>({
    url: '/v1/admin/message-templates',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/message-templates/{id} */
export function updateMessageTemplate(
  id: number,
  payload: SaveMessageTemplatePayload,
): Promise<MessageTemplateView> {
  return request<MessageTemplateView>({
    url: `/v1/admin/message-templates/${id}`,
    method: 'PUT',
    data: payload,
  })
}
