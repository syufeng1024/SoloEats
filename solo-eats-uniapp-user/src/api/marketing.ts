import { request } from './http'

/**
 * 用户端营销接口：满减活动、秒杀场次/预占、红包。
 *
 * 所有金额字段单位均为「分」，展示时走 fenToYuan。
 * 优惠券 C 端查询接口当前不存在，这里不提供 fetchCoupons，避免调用不存在的端点。
 */

/* ------------------------------------------------------------------ */
/* 满减 / 折扣活动                                                     */
/* ------------------------------------------------------------------ */

export interface PromotionRuleVO {
  /** 门槛金额，分 */
  thresholdAmount: number
  /** 满减金额，分；折扣活动为 null */
  discountAmount: number | null
  /** 折扣率，如 0.85；满减活动为 null */
  discountRate: number | null
  /** 折扣封顶金额，分 */
  maxDiscount: number | null
  sort: number | null
}

/** GET /c/promotions → PromotionAvailableView，已过滤为进行中且命中门店/商品范围。 */
export interface PromotionAvailableVO {
  id: number
  promotionName: string
  /** 1满减 2折扣 3新人立减 */
  promotionType: number
  /** 1平台 2商家 */
  bearer: number | null
  startAt: string | null
  endAt: string | null
  rules: PromotionRuleVO[]
}

export function fetchPromotions(
  params: { shopId?: number; spuIds?: number[] } = {},
  silent = false,
): Promise<PromotionAvailableVO[]> {
  const data: Record<string, unknown> = {}
  if (params.shopId && params.shopId > 0) {
    data.shopId = params.shopId
  }
  if (params.spuIds && params.spuIds.length > 0) {
    data.spuIds = params.spuIds
  }
  return request<PromotionAvailableVO[]>({ url: '/v1/c/promotions', data, silent })
}

/* ------------------------------------------------------------------ */
/* 秒杀                                                                */
/* ------------------------------------------------------------------ */

export interface SeckillItemVO {
  itemId: number
  skuId: number
  spuId: number
  /** 秒杀价，分 */
  seckillPrice: number
  seckillStock: number
  remainStock: number
  limitPerUser: number
}

/** GET /c/seckill/sessions → SeckillSessionView。status：0待开始 1进行中 2已结束。 */
export interface SeckillSessionVO {
  id: number
  activityName: string
  sessionStart: string | null
  sessionEnd: string | null
  preheatAt: string | null
  status: number
  items: SeckillItemVO[]
}

/** POST /c/seckill/{itemId}/reserve → SeckillReserveResult。 */
export interface SeckillReserveResultVO {
  /** t_seckill_order.id */
  orderId: number
  itemId: number
  qty: number
  /** 秒杀价，分 */
  seckillPrice: number
  /** 预占凭证，格式 "SK" + orderId；下单链路凭此核销 */
  reserveToken: string
}

export function fetchSeckillSessions(silent = false): Promise<SeckillSessionVO[]> {
  return request<SeckillSessionVO[]>({ url: '/v1/c/seckill/sessions', silent })
}

/**
 * 预占秒杀库存。后端限制 10 次/60 秒，并且每人每商品只能预占一次
 * （已预占未释放时返回 SECKILL_LIMIT_EXCEEDED）。
 */
export function reserveSeckill(itemId: number, qty = 1): Promise<SeckillReserveResultVO> {
  return request<SeckillReserveResultVO>({
    url: `/v1/c/seckill/${itemId}/reserve`,
    method: 'POST',
    data: { qty },
  })
}

/* ------------------------------------------------------------------ */
/* 红包                                                                */
/* ------------------------------------------------------------------ */

/**
 * GET /c/red-packets → RedPacketView[]。
 * totalAmount 是整个红包池总额，不是单人金额；单人金额由 grab 返回。
 */
export interface RedPacketVO {
  id: number
  packetType: number | null
  /** 红包池总额，分 */
  totalAmount: number
  totalQty: number
  remainQty: number
  startAt: string | null
  endAt: string | null
  grabbed: boolean
}

export function fetchRedPackets(silent = false): Promise<RedPacketVO[]> {
  return request<RedPacketVO[]>({ url: '/v1/c/red-packets', silent })
}

/** POST /c/red-packets/{id}/grab，返回本次领到的金额（分）。重复领取返回已领金额。 */
export function grabRedPacket(packetId: number): Promise<number> {
  return request<number>({
    url: `/v1/c/red-packets/${packetId}/grab`,
    method: 'POST',
  })
}
