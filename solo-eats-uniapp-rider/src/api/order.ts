import { request } from './http'
import type { PageResult, RiderTaskDetailVO, RiderTaskVO } from './types'

/**
 * 骑手任务（配送单）查询。
 * 数据源是 t_delivery_order，与用户端订单接口不是同一套语义：
 * - 列表 GET /rider/deliveries，status 为配送单状态（20 已接单 / 40 配送中 / 50 已送达）
 * - 详情 GET /rider/deliveries/{deliveryId}，后端校验配送单归属，非本人返回 10004
 */

export interface MyDeliveryQuery {
  /** 配送单状态，不传为全部 */
  status?: number
  pageNum?: number
  pageSize?: number
}

export function pageMyDeliveries(
  query: MyDeliveryQuery = {},
): Promise<PageResult<RiderTaskVO>> {
  const data: Record<string, unknown> = {
    pageNum: query.pageNum ?? 1,
    pageSize: query.pageSize ?? 10,
  }
  if (query.status !== undefined && query.status !== null) {
    data.status = query.status
  }
  return request<PageResult<RiderTaskVO>>({ url: '/v1/rider/deliveries', data })
}

/** silent=true 用于操作后静默刷新，避免网络抖动时连续弹 toast */
export function getMyDelivery(
  deliveryId: number,
  silent = false,
): Promise<RiderTaskDetailVO> {
  return request<RiderTaskDetailVO>({
    url: `/v1/rider/deliveries/${deliveryId}`,
    silent,
  })
}
