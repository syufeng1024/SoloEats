import { request } from './http'
import type {
  AdminSettlementStatsResult,
  SettlementDetailResult,
  SettlementListResult,
} from './types'

export interface AdminSettlementStatsParams {
  merchantId?: number
  status?: number
  dateFrom?: string
  dateTo?: string
  pageNum?: number
  pageSize?: number
}

/** GET /api/v1/admin/settlements/stats */
export function getAdminSettlementStats(
  params: AdminSettlementStatsParams,
): Promise<AdminSettlementStatsResult> {
  return request<AdminSettlementStatsResult>({
    url: '/v1/admin/settlements/stats',
    method: 'GET',
    params,
  })
}

/**
 * GET /api/v1/merchant/settlements?merchantId=&status=
 * 后端 MerchantSettlementController.list 只接受 merchantId 与可选 status，无分页参数。
 */
export function listMerchantSettlements(
  merchantId: number,
  status?: number,
): Promise<SettlementListResult> {
  return request<SettlementListResult>({
    url: '/v1/merchant/settlements',
    method: 'GET',
    params: { merchantId, status },
  })
}

/** GET /api/v1/merchant/settlements/{billId}?merchantId= */
export function getMerchantSettlementDetail(
  billId: number,
  merchantId: number,
): Promise<SettlementDetailResult> {
  return request<SettlementDetailResult>({
    url: `/v1/merchant/settlements/${billId}`,
    method: 'GET',
    params: { merchantId },
  })
}
