import { request } from './http'
import type { PageResult, ShopVO } from './types'

/** GET /c/search/suggest → SearchSuggestView */
export interface SearchSuggestVO {
  defaultKeywords: string[]
  hotKeywords: string[]
  suggestions: string[]
}

export type ShopSortBy = 'comprehensive' | 'distance' | 'rating' | 'sales'

export interface ShopSearchQuery {
  lng: number
  lat: number
  keyword: string
  /** 米，后端限定 100~50000，不传默认 5000 */
  radius?: number
  sortBy?: ShopSortBy
  pageNum?: number
  pageSize?: number
}

/**
 * GET /c/search/suggest：白名单接口，未登录也可调用。
 * 传空 keyword 时只返回默认词与热搜词，联想词为空数组；屏蔽词由后端过滤，不会下发。
 * 联想词每敲一次键都会调用，这里 silent，避免网络抖动时连续弹 toast。
 */
export function fetchSearchSuggest(keyword?: string): Promise<SearchSuggestVO> {
  const data: Record<string, unknown> = {}
  const trimmed = (keyword || '').trim()
  if (trimmed) {
    data.keyword = trimmed
  }
  return request<SearchSuggestVO>({ url: '/v1/c/search/suggest', data, silent: true })
}

/**
 * GET /c/shops 携带 keyword 即店铺关键词搜索。
 * 注意：后端按店铺名或地址 LIKE 匹配，不支持按商品名反查店铺。
 */
export function searchShops(query: ShopSearchQuery): Promise<PageResult<ShopVO>> {
  const data: Record<string, unknown> = {
    lng: query.lng,
    lat: query.lat,
    keyword: query.keyword,
    pageNum: query.pageNum ?? 1,
    pageSize: query.pageSize ?? 10,
    sortBy: query.sortBy ?? 'comprehensive',
  }
  if (query.radius !== undefined && query.radius !== null) {
    data.radius = query.radius
  }
  return request<PageResult<ShopVO>>({ url: '/v1/c/shops', data })
}
