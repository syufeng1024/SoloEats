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

/** t_sys_tenant.tenant_type：1平台 2商家 */
export const TENANT_TYPE = buildMap([
  [1, '平台', 'primary'],
  [2, '商家', 'warning'],
])

export const TENANT_TYPE_OPTIONS = buildOptions(TENANT_TYPE)

/** t_sys_tenant.status：0启用 1停用 2过期（由 expire_at 派生） */
export const TENANT_STATUS = buildMap([
  [0, '启用', 'success'],
  [1, '停用', 'info'],
  [2, '已过期', 'danger'],
])

export const TENANT_STATUS_OPTIONS = buildOptions(TENANT_STATUS)

/** 人工启停只接受 0/1，2 不允许写入 */
export const TENANT_MANUAL_STATUS_OPTIONS = TENANT_STATUS_OPTIONS.filter(
  (option) => option.value !== 2,
)

/** t_sys_package.status：0启用 1停用 */
export const PACKAGE_STATUS = SYSTEM_STATUS
export const PACKAGE_STATUS_OPTIONS = SYSTEM_STATUS_OPTIONS

/** t_sys_dict_type.status：0启用 1停用 */
export const DICT_TYPE_STATUS = SYSTEM_STATUS
export const DICT_TYPE_STATUS_OPTIONS = SYSTEM_STATUS_OPTIONS

/** t_sys_dict_item.is_default 与通用 0/1 标志 */
export const FLAG = buildMap([
  [1, '是', 'success'],
  [0, '否', 'info'],
])

export const FLAG_OPTIONS = buildOptions(FLAG)

/** t_sys_mail_account.ssl_enabled：0关闭 1 SSL */
export const MAIL_SSL = buildMap([
  [0, '关闭', 'info'],
  [1, 'SSL', 'success'],
])

export const MAIL_SSL_OPTIONS = buildOptions(MAIL_SSL)

/** t_sys_mail_log.status：0待发 1成功 2失败 */
export const MAIL_LOG_STATUS = buildMap([
  [0, '待发', 'warning'],
  [1, '成功', 'success'],
  [2, '失败', 'danger'],
])

export const MAIL_LOG_STATUS_OPTIONS = buildOptions(MAIL_LOG_STATUS)

/** t_sys_notice.notice_type：1公告 2通知 */
export const NOTICE_TYPE = buildMap([
  [1, '公告', 'primary'],
  [2, '通知', 'warning'],
])

export const NOTICE_TYPE_OPTIONS = buildOptions(NOTICE_TYPE)

/** t_sys_notice.target_type：1全部 2平台 3商家 4骑手 */
export const NOTICE_TARGET_TYPE = buildMap([
  [1, '全部', 'primary'],
  [2, '平台', 'warning'],
  [3, '商家', 'success'],
  [4, '骑手', 'info'],
])

export const NOTICE_TARGET_TYPE_OPTIONS = buildOptions(NOTICE_TARGET_TYPE)

/** t_sys_notice.status：0草稿 1已发布 2已撤回 */
export const NOTICE_STATUS = buildMap([
  [0, '草稿', 'info'],
  [1, '已发布', 'success'],
  [2, '已撤回', 'warning'],
])

export const NOTICE_STATUS_OPTIONS = buildOptions(NOTICE_STATUS)

/** t_sys_login_log.login_type：1密码 2验证码 3mock */
export const LOGIN_TYPE = buildMap([
  [1, '密码', 'primary'],
  [2, '验证码', 'warning'],
  [3, 'mock', 'info'],
])

export const LOGIN_TYPE_OPTIONS = buildOptions(LOGIN_TYPE)

/** t_sys_login_log.success：1成功 0失败 */
export const LOGIN_SUCCESS = buildMap([
  [1, '成功', 'success'],
  [0, '失败', 'danger'],
])

export const LOGIN_SUCCESS_OPTIONS = buildOptions(LOGIN_SUCCESS)

/** t_sys_oper_log.result_code：0成功；非 0 为具体错误码，页面自行 fallback */
export const OPER_RESULT = buildMap([[0, '成功', 'success']])

export const OPER_RESULT_OPTIONS = buildOptions(OPER_RESULT)
