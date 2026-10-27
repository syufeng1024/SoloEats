import type { TagType } from './orderStatus'

/**
 * 会员中心枚举字典。
 * 取值以 t_member_user / t_member_wallet_log 表注释与后端枚举为准，
 * 前端只做展示映射，不得自行推断。
 */
export interface MemberStatusMeta {
  label: string
  type: TagType
}

export type MemberStatusMap = Record<number, MemberStatusMeta>

function buildMap(entries: Array<[number, string, TagType]>): MemberStatusMap {
  return Object.fromEntries(
    entries.map(([code, label, type]) => [code, { label, type }]),
  )
}

function buildOptions(map: MemberStatusMap): Array<{ value: number; label: string }> {
  return Object.entries(map).map(([value, meta]) => ({
    value: Number(value),
    label: meta.label,
  }))
}

/** t_member_user.status：0 正常 1 禁用 2 拉黑（实体注释为「已注销/拉黑」） */
export const MEMBER_STATUS = buildMap([
  [0, '正常', 'success'],
  [1, '禁用', 'warning'],
  [2, '拉黑', 'danger'],
])

export const MEMBER_STATUS_OPTIONS = buildOptions(MEMBER_STATUS)

/** t_member_user.gender：0 未知 1 男 2 女 */
export const MEMBER_GENDER = buildMap([
  [0, '未知', 'info'],
  [1, '男', 'primary'],
  [2, '女', 'danger'],
])

/** t_member_wallet_log.direction：1 收入 2 支出。金额列恒为正，方向由该字段区分 */
export const WALLET_DIRECTION = buildMap([
  [1, '收入', 'success'],
  [2, '支出', 'danger'],
])

export const WALLET_DIRECTION_OPTIONS = buildOptions(WALLET_DIRECTION)

/** t_member_wallet_log.biz_type varchar(16)，落库为 WalletBizType 枚举名 */
export const WALLET_BIZ_TYPE: Record<string, string> = {
  RECHARGE: '充值',
  CONSUME: '消费',
  REFUND: '退款',
  ADJUST: '手工调账',
  WITHDRAW: '提现',
  FREEZE: '冻结',
  UNFREEZE: '解冻',
}

export const WALLET_BIZ_TYPE_OPTIONS = Object.entries(WALLET_BIZ_TYPE).map(
  ([value, label]) => ({ value, label }),
)

/** t_member_tag.auto_tag：0 手动 1 自动。自动打标规则只落 tag_rule，执行逻辑后端未实现。 */
export const TAG_AUTO_FLAG = buildMap([
  [0, '手动', 'info'],
  [1, '自动', 'primary'],
])

export const TAG_AUTO_FLAG_OPTIONS = buildOptions(TAG_AUTO_FLAG)

/** t_member_tag.status：0 启用 1 停用 */
export const TAG_STATUS = buildMap([
  [0, '启用', 'success'],
  [1, '停用', 'info'],
])

export const TAG_STATUS_OPTIONS = buildOptions(TAG_STATUS)

/** 调账方向是前端表单概念，不是后端字段：提交时把方向折算成带符号的 amount（分）。 */
export type AdjustDirection = 'in' | 'out'

export const ADJUST_DIRECTION_OPTIONS: Array<{
  value: AdjustDirection
  label: string
}> = [
  { value: 'in', label: '增加余额' },
  { value: 'out', label: '扣减余额' },
]

export function labelOf(
  map: MemberStatusMap,
  value: number | null | undefined,
): string {
  if (value === null || value === undefined) {
    return '—'
  }
  return map[value]?.label ?? '—'
}

/** el-tag 的 type；未知值回落 info。 */
export function tagOf(
  map: MemberStatusMap,
  value: number | null | undefined,
): TagType {
  if (value === null || value === undefined) {
    return 'info'
  }
  return map[value]?.type ?? 'info'
}

export function labelOfBizType(value: string | null | undefined): string {
  if (!value) {
    return '—'
  }
  return WALLET_BIZ_TYPE[value] ?? value
}
