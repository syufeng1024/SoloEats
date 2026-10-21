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
