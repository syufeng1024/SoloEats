import { request } from './http'
import type { MerchantDashboardVO } from './types'

/**
 * GET /merchant/report/dashboard。
 * 只返回 5 个可直查计数：今日订单数 / 待接单 / 备餐中 / 待自取 / 今日完成。
 * 营业额等金额聚合属于 T11，后端未下发前前端不得自行求和。
 */
export function fetchDashboard(silent = false): Promise<MerchantDashboardVO> {
  return request<MerchantDashboardVO>({
    url: '/v1/merchant/report/dashboard',
    silent,
  })
}
