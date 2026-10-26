import { request } from './http'
import type { ShopView } from './types'

/**
 * GET /api/v1/merchant/shops
 * merchantId 由后端从 JWT 读取；门店账号只返回本店，商家管理员返回本商家全部门店。
 */
export function listMerchantShops(): Promise<ShopView[]> {
  return request<ShopView[]>({
    url: '/v1/merchant/shops',
    method: 'GET',
  })
}
