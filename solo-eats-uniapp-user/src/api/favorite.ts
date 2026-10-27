import { request } from './http'
import type { PageResult } from './types'

/** 与后端 Favorite.TARGET_TYPE_SHOP / PRODUCT 对齐 */
export const FAVORITE_TYPE_SHOP = 1
export const FAVORITE_TYPE_PRODUCT = 2

/** GET /c/favorites → FavoriteView */
export interface FavoriteVO {
  id: number
  targetType: number
  targetId: number
  createdAt: string | null
}

/**
 * POST /c/favorites。后端按 uk_user_target 幂等：
 * 已收藏再提交返回原记录，不会重复插入。
 */
export function addFavorite(targetType: number, targetId: number): Promise<FavoriteVO> {
  return request<FavoriteVO>({
    url: '/v1/c/favorites',
    method: 'POST',
    data: { targetType, targetId },
  })
}

/** DELETE /c/favorites/{targetType}/{targetId}，记录不存在也返回成功。 */
export function removeFavorite(targetType: number, targetId: number): Promise<void> {
  return request<void>({
    url: `/v1/c/favorites/${targetType}/${targetId}`,
    method: 'DELETE',
  })
}

export interface FavoriteQuery {
  targetType?: number
  /** 店铺页判断单个对象是否已收藏时传 */
  targetId?: number
  pageNum?: number
  pageSize?: number
  silent?: boolean
}

/** GET /c/favorites，按收藏时间倒序，targetType/targetId 均可选。 */
export function fetchFavorites(query: FavoriteQuery = {}): Promise<PageResult<FavoriteVO>> {
  const data: Record<string, unknown> = {
    pageNum: query.pageNum ?? 1,
    pageSize: query.pageSize ?? 20,
  }
  if (query.targetType !== undefined) {
    data.targetType = query.targetType
  }
  if (query.targetId !== undefined) {
    data.targetId = query.targetId
  }
  return request<PageResult<FavoriteVO>>({
    url: '/v1/c/favorites',
    data,
    silent: query.silent,
  })
}
