import { request } from './http'
import type { PaymentVO } from './types'

/**
 * P0 只走 Mock 通道。后端 /payments/pay 接受 channel 1~3，但当前没有
 * 「可用支付渠道」查询接口（T13 卡里提到的下发接口后端尚未实现），
 * 因此固定 channel=1，不新增后端契约；沙箱接入卡落地后改为从接口读取。
 */
export const DEFAULT_PAY_CHANNEL = 1

/** POST /payments/pay（必须带 Idempotency-Key） */
export function payOrder(
  orderId: number,
  channel: number = DEFAULT_PAY_CHANNEL,
  idempotencyKey?: string,
): Promise<PaymentVO> {
  return request<PaymentVO>({
    url: '/v1/payments/pay',
    method: 'POST',
    idempotencyKey,
    data: { orderId, channel },
  })
}
