import { request } from './http'
import type { SettlementDetailVO, SettlementListVO } from './types'

/**
 * 商家结算单。
 * GET /merchant/settlements 需要 merchantId 查询参数，后端用 PermissionGuard#assertMerchant
 * 与令牌比对，前端传的是令牌解析出的 t_merchant.id，不能传其它商家。
 * 接口没有 pageNum/pageSize，一次返回全部账单 + 当前筛选条件下的小计 summary。
 */

/** GET /merchant/settlements?merchantId=&status= */
export function listSettlements(
  merchantId: number,
  status?: number | null,
  silent = false,
): Promise<SettlementListVO> {
  const data: Record<string, unknown> = { merchantId }
  if (status !== undefined && status !== null) {
    data.status = status
  }
  return request<SettlementListVO>({
    url: '/v1/merchant/settlements',
    data,
    silent,
  })
}

/** GET /merchant/settlements/{billId}?merchantId= */
export function getSettlementDetail(
  billId: number,
  merchantId: number,
  silent = false,
): Promise<SettlementDetailVO> {
  return request<SettlementDetailVO>({
    url: `/v1/merchant/settlements/${billId}`,
    data: { merchantId },
    silent,
  })
}
