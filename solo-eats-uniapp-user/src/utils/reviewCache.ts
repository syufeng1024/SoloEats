import type { ReviewVO } from '@/api/types'

/**
 * 评价快照。
 *
 * 后端 ReviewController 没有 GET /c/reviews/{id}，
 * 详情页无法按 id 重新拉取，只能沿用「我的评价」列表里的完整 ReviewView。
 * 列表点击时写本地快照，详情页读取；追评成功后用后端返回值覆盖快照。
 */

const KEY_PREFIX = 'review:snapshot:'

export function cacheReview(review: ReviewVO): void {
  try {
    uni.setStorageSync(`${KEY_PREFIX}${review.id}`, JSON.stringify(review))
  } catch (err) {
    // 存储写满等异常不应阻断页面跳转
  }
}

export function readCachedReview(id: number): ReviewVO | null {
  try {
    const raw = uni.getStorageSync(`${KEY_PREFIX}${id}`)
    if (typeof raw !== 'string' || raw.length === 0) {
      return null
    }
    const parsed = JSON.parse(raw) as ReviewVO
    return parsed && typeof parsed.id === 'number' ? parsed : null
  } catch (err) {
    return null
  }
}
