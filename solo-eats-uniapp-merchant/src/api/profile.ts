import { request } from './http'
import type { MerchantDetailVO } from './types'

/** GET /merchant/profile；merchantId 由后端从令牌解析，前端不传 */
export function fetchMerchantProfile(silent = false): Promise<MerchantDetailVO> {
  return request<MerchantDetailVO>({
    url: '/v1/merchant/profile',
    silent,
  })
}
