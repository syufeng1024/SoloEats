export const ROLES = {
  MERCHANT: 'MERCHANT',
  ADMIN: 'ADMIN',
} as const

export type ConsoleRole = (typeof ROLES)[keyof typeof ROLES]

export const ROLE_LABELS: Record<string, string> = {
  [ROLES.MERCHANT]: '商家',
  [ROLES.ADMIN]: '平台管理员',
  C_USER: '用户',
  RIDER: '骑手',
}

export function roleLabel(role: string | undefined | null): string {
  if (!role) {
    return '未知角色'
  }
  return ROLE_LABELS[role] ?? role
}

/**
 * 角色族匹配：账号角色码是细粒度的（ADMIN_OPERATION / MERCHANT_MANAGER），
 * 路由与菜单声明的是族码（ADMIN / MERCHANT）。禁止退化为精确相等。
 */
export function matchesRole(actual: string, required: string): boolean {
  return actual === required || actual.startsWith(`${required}_`)
}

export function hasAnyRole(actualRoles: string[], required: string): boolean {
  return actualRoles.some((role) => matchesRole(role, required))
}
