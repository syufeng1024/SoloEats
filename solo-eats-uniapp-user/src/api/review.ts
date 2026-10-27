import { request } from './http'
import type { PageResult, ReviewVO } from './types'

/**
 * 评价：对齐 ReviewController。
 * - GET  /api/v1/c/reviews/mine
 * - POST /api/v1/c/reviews/{id}/append
 * 后端没有「按 id 查评价详情」的接口，详情页只能使用列表返回的完整 ReviewView。
 */

/** GET /c/reviews/mine?pageNum=&pageSize= */
export function fetchMyReviews(
  pageNum = 1,
  pageSize = 10,
  silent = false,
): Promise<PageResult<ReviewVO>> {
  return request<PageResult<ReviewVO>>({
    url: '/v1/c/reviews/mine',
    data: { pageNum, pageSize },
    silent,
  })
}

/**
 * POST /c/reviews/{id}/append
 * 后端校验：评价须为审核通过（status=1）且未追评过，content ≤ 500。
 */
export function appendReview(
  reviewId: number,
  content: string,
  images?: string[] | null,
): Promise<ReviewVO> {
  return request<ReviewVO>({
    url: `/v1/c/reviews/${reviewId}/append`,
    method: 'POST',
    data: {
      content,
      images: images && images.length > 0 ? images : null,
    },
  })
}
