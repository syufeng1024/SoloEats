import { request } from './http'
import type {
  AdminOrderMonitorResult,
  SettlementGenerateResult,
} from './types'

export interface AdminOrderMonitorParams {
  merchantId?: number
  shopId?: number
  status?: number
  keyword?: string
  dateFrom?: string
  dateTo?: string
  pageNum?: number
  pageSize?: number
}

/** GET /api/v1/admin/orders/monitor */
export function monitorOrders(
  params: AdminOrderMonitorParams,
): Promise<AdminOrderMonitorResult> {
  return request<AdminOrderMonitorResult>({
    url: '/v1/admin/orders/monitor',
    method: 'GET',
    params,
  })
}

/** POST /api/v1/admin/settlements/generate，归集当日可结算订单并生成商家账单 */
export function generateSettlements(): Promise<SettlementGenerateResult> {
  return request<SettlementGenerateResult>({
    url: '/v1/admin/settlements/generate',
    method: 'POST',
  })
}
