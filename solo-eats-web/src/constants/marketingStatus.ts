import type { TagType } from './orderStatus'

/**
 * 营销中心字典。取值来自后端 t_promotion / t_seckill_activity 列注释，
 * 与 financeStatus.ts 保持同一份元数据结构：{ label, type }。
 */
export interface MarketingStatusMeta {
  label: string
  type: TagType
}

export type MarketingStatusMap = Record<number, MarketingStatusMeta>

function buildMap(entries: Array<[number, string, TagType]>): MarketingStatusMap {
  return Object.fromEntries(
    entries.map(([code, label, type]) => [code, { label, type }]),
  )
}

function buildOptions(map: MarketingStatusMap): Array<{ value: number; label: string }> {
  return Object.entries(map).map(([value, meta]) => ({
    value: Number(value),
    label: meta.label,
  }))
}

/** t_promotion.promotion_type：1满减 2折扣（3新人立减后端未开放） */
export const PROMOTION_TYPE = buildMap([
  [1, '满减', 'primary'],
  [2, '折扣', 'warning'],
])

export const PROMOTION_TYPE_OPTIONS = buildOptions(PROMOTION_TYPE)

/** t_promotion.bearer：1平台 2商家 */
export const PROMOTION_BEARER = buildMap([
  [1, '平台', 'primary'],
  [2, '商家', 'success'],
])

export const PROMOTION_BEARER_OPTIONS = buildOptions(PROMOTION_BEARER)

/** t_promotion.scope_type：1全平台 2指定商家 3指定门店 4指定品类 5指定商品 */
export const PROMOTION_SCOPE = buildMap([
  [1, '全平台', 'info'],
  [2, '指定商家', 'primary'],
  [3, '指定门店', 'primary'],
  [4, '指定品类', 'warning'],
  [5, '指定商品', 'warning'],
])

export const PROMOTION_SCOPE_OPTIONS = buildOptions(PROMOTION_SCOPE)

/** t_promotion.status：0草稿 1进行中 2已结束 3暂停 */
export const PROMOTION_STATUS = buildMap([
  [0, '草稿', 'info'],
  [1, '进行中', 'success'],
  [2, '已结束', 'info'],
  [3, '暂停', 'warning'],
])

export const PROMOTION_STATUS_OPTIONS = buildOptions(PROMOTION_STATUS)

/** t_seckill_activity.status：0待开始 1进行中 2已结束；读取时按时间窗推导 */
export const SECKILL_STATUS = buildMap([
  [0, '待开始', 'warning'],
  [1, '进行中', 'success'],
  [2, '已结束', 'info'],
])

export const SECKILL_STATUS_OPTIONS = buildOptions(SECKILL_STATUS)

/** t_promotion_scope.ref_type：1商家 2门店 3品类 4SPU 5SKU */
export const SCOPE_REF_TYPE = buildMap([
  [1, '商家', 'primary'],
  [2, '门店', 'primary'],
  [3, '品类', 'warning'],
  [4, 'SPU', 'warning'],
  [5, 'SKU', 'warning'],
])

export const SCOPE_REF_TYPE_OPTIONS = buildOptions(SCOPE_REF_TYPE)

/** 未知值统一返回破折号，避免把 undefined 渲染成空白。 */
export function labelOf(
  map: MarketingStatusMap,
  value: number | null | undefined,
): string {
  if (value === null || value === undefined) {
    return '—'
  }
  return map[value]?.label ?? '—'
}

/** el-tag 的 type；未知值回落 info。 */
export function tagOf(
  map: MarketingStatusMap,
  value: number | null | undefined,
): TagType {
  if (value === null || value === undefined) {
    return 'info'
  }
  return map[value]?.type ?? 'info'
}
