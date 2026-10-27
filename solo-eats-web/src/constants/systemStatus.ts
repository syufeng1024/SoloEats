import type { TagType } from './orderStatus'

/**
 * 系统管理字典。
 * 取值以 Flyway V4__system_rbac.sql、V5__system_tenant_org_dict.sql 列注释
 * 与 SaveUserRequest / SaveRoleRequest / SaveMenuRequest / SaveDeptRequest / SavePostRequest 为准。
 */
export interface SystemStatusMeta {
  label: string
  type: TagType
}

export type SystemStatusMap = Record<number, SystemStatusMeta>

function buildMap(entries: Array<[number, string, TagType]>): SystemStatusMap {
  return Object.fromEntries(
    entries.map(([code, label, type]) => [code, { label, type }]),
  )
}

function buildOptions(map: SystemStatusMap): Array<{ value: number; label: string }> {
  return Object.entries(map).map(([value, meta]) => ({
    value: Number(value),
    label: meta.label,
  }))
}

/** t_sys_user/t_sys_role/t_sys_menu/t_sys_dept/t_sys_post.status：0启用 1停用 */
export const SYSTEM_STATUS = buildMap([
  [0, '启用', 'success'],
  [1, '停用', 'info'],
])

export const SYSTEM_STATUS_OPTIONS = buildOptions(SYSTEM_STATUS)

/** t_sys_user.user_type：1平台 2商家 3骑手 */
export const USER_TYPE = buildMap([
  [1, '平台', 'primary'],
  [2, '商家', 'warning'],
  [3, '骑手', 'success'],
])

export const USER_TYPE_OPTIONS = buildOptions(USER_TYPE)

/** t_sys_role.role_type：1平台 2商家 3骑手 */
export const ROLE_TYPE = USER_TYPE
export const ROLE_TYPE_OPTIONS = USER_TYPE_OPTIONS

/** t_sys_role.data_scope：1全部 2本租户 3本商家 4本门店 5本人 */
export const DATA_SCOPE = buildMap([
  [1, '全部数据', 'danger'],
  [2, '本租户', 'warning'],
  [3, '本商家', 'warning'],
  [4, '本门店', 'primary'],
  [5, '仅本人', 'info'],
])

export const DATA_SCOPE_OPTIONS = buildOptions(DATA_SCOPE)

/** t_sys_menu.menu_type：1目录 2菜单 3按钮 */
export const MENU_TYPE = buildMap([
  [1, '目录', 'primary'],
  [2, '菜单', 'success'],
  [3, '按钮', 'warning'],
])

export const MENU_TYPE_OPTIONS = buildOptions(MENU_TYPE)

/** 菜单管理页只编辑目录/菜单；按钮节点不在 tree 接口返回范围内 */
export const MENU_MANAGE_TYPE_OPTIONS = MENU_TYPE_OPTIONS.filter(
  (option) => option.value !== 3,
)

/** t_sys_menu.visible：0显示 1隐藏 */
export const MENU_VISIBLE = buildMap([
  [0, '显示', 'success'],
  [1, '隐藏', 'info'],
])

export const MENU_VISIBLE_OPTIONS = buildOptions(MENU_VISIBLE)

export function labelOf(
  map: SystemStatusMap,
  value: number | null | undefined,
): string {
  if (value === null || value === undefined) {
    return '—'
  }
  return map[value]?.label ?? '—'
}

export function tagOf(
  map: SystemStatusMap,
  value: number | null | undefined,
): TagType {
  if (value === null || value === undefined) {
    return 'info'
  }
  return map[value]?.type ?? 'info'
}
