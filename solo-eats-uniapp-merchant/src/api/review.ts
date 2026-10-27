import { request } from './http'
import type { PageResult, ReviewVO } from './types'

/**
 * 商家评价。
 * GET /merchant/reviews 只返回本商家已审核通过的评价（未审核的不能回复）。
 * scoreLevel：1差评(1~2星) / 2中评(3星) / 3好评(4~5星)；replyStatus：0未回复 / 1已回复。
 */

export interface ReviewListQuery {
  scoreLevel?: number | null
  replyStatus?: number | null
  pageNum?: number
  pageSize?: number
  silent?: boolean
}

/** GET /merchant/reviews */
export function pageMerchantReviews(
  query: ReviewListQuery = {},
): Promise<PageResult<ReviewVO>> {
  const data: Record<string, unknown> = {
    pageNum: query.pageNum ?? 1,
    pageSize: query.pageSize ?? 20,
  }
  if (query.scoreLevel !== undefined && query.scoreLevel !== null) {
    data.scoreLevel = query.scoreLevel
  }
  if (query.replyStatus !== undefined && query.replyStatus !== null) {
    data.replyStatus = query.replyStatus
  }
  return request<PageResult<ReviewVO>>({
    url: '/v1/merchant/reviews',
    data,
    silent: query.silent,
  })
}

/** POST /merchant/reviews/{id}/reply；后端限制 500 字且只能回复一次 */
export function replyReview(reviewId: number, reply: string): Promise<void> {
  return request<void>({
    url: `/v1/merchant/reviews/${reviewId}/reply`,
    method: 'POST',
    data: { reply },
  })
}
