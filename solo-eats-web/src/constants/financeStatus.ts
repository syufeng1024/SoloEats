import type { TagType } from './orderStatus'

/**
 * 资金中心枚举字典。
 * 取值以后端迁移 V19/V27 表注释为准，前端只做展示映射，不得自行推断。
 */
export interface FinanceStatusMeta {
  label: string
  type: TagType
}

export type FinanceStatusMap = Record<number, FinanceStatusMeta>

function buildMap(entries: Array<[number, string, TagType]>): FinanceStatusMap {
  return Object.fromEntries(
    entries.map(([code, label, type]) => [code, { label, type }]),
  )
}

function buildOptions(map: FinanceStatusMap): Array<{ value: number; label: string }> {
  return Object.entries(map).map(([value, meta]) => ({
    value: Number(value),
    label: meta.label,
  }))
}

/** t_payout_order.status：0待审核 10审核通过 20打款中 30已到账 40已驳回（不是 0~4） */
export const PAYOUT_STATUS = buildMap([
  [0, '待审核', 'warning'],
  [10, '审核通过', 'primary'],
  [20, '打款中', 'primary'],
  [30, '已到账', 'success'],
  [40, '已驳回', 'danger'],
])

export const PAYOUT_STATUS_OPTIONS = buildOptions(PAYOUT_STATUS)

/** t_payout_order.subject_type：1商家 2骑手 */
export const PAYOUT_SUBJECT = buildMap([
  [1, '商家', 'primary'],
  [2, '骑手', 'success'],
])

export const PAYOUT_SUBJECT_OPTIONS = buildOptions(PAYOUT_SUBJECT)

/** t_payout_order.channel：1支付宝 2银行卡 */
export const PAYOUT_CHANNEL = buildMap([
  [1, '支付宝', 'primary'],
  [2, '银行卡', 'info'],
])

export const PAYOUT_CHANNEL_OPTIONS = buildOptions(PAYOUT_CHANNEL)

/** t_commission_rule.rule_scope：1全局 2品类 3商家 4活动 */
export const RULE_SCOPE = buildMap([
  [1, '全局', 'info'],
  [2, '品类', 'primary'],
  [3, '商家', 'success'],
  [4, '活动', 'warning'],
])

export const RULE_SCOPE_OPTIONS = buildOptions(RULE_SCOPE)

/** t_commission_rule.status：0停用 1启用 */
export const RULE_STATUS = buildMap([
  [0, '停用', 'info'],
  [1, '启用', 'success'],
])

export const RULE_STATUS_OPTIONS = buildOptions(RULE_STATUS)

/** t_fund_flow.flow_type：1佣金收入 2结算支出 3补贴支出 4优惠成本 5提现支出 6退款冲抵 */
export const FLOW_TYPE = buildMap([
  [1, '佣金收入', 'success'],
  [2, '结算支出', 'warning'],
  [3, '补贴支出', 'warning'],
  [4, '优惠成本', 'info'],
  [5, '提现支出', 'danger'],
  [6, '退款冲抵', 'info'],
])

export const FLOW_TYPE_OPTIONS = buildOptions(FLOW_TYPE)

/** t_fund_flow.direction：1入 2出 */
export const FLOW_DIRECTION = buildMap([
  [1, '入', 'success'],
  [2, '出', 'danger'],
])

export const FLOW_DIRECTION_OPTIONS = buildOptions(FLOW_DIRECTION)

/** 未知值统一返回破折号，避免把 undefined 渲染成空白。 */
export function labelOf(
  map: FinanceStatusMap,
  value: number | null | undefined,
): string {
  if (value === null || value === undefined) {
    return '—'
  }
  return map[value]?.label ?? '—'
}

/** el-tag 的 type；未知值回落 info。 */
export function tagOf(
  map: FinanceStatusMap,
  value: number | null | undefined,
): TagType {
  if (value === null || value === undefined) {
    return 'info'
  }
  return map[value]?.type ?? 'info'
}
