import { ROLES } from '@/constants/roles'

export function homeForRole(roles: string[]): string {
  if (roles.includes(ROLES.ADMIN)) {
    return '/admin/overview'
  }
  if (roles.includes(ROLES.MERCHANT)) {
    return '/merchant/orders'
  }
  return '/login'
}

/**
 * 只允许跳回本角色可访问的站内路径，避免 ?redirect= 被外部地址利用。
 */
export function isRedirectAllowed(path: string, roles: string[]): boolean {
  if (!path.startsWith('/') || path.startsWith('//')) {
    return false
  }
  if (path.startsWith('/merchant')) {
    return roles.includes(ROLES.MERCHANT)
  }
  if (path.startsWith('/admin')) {
    return roles.includes(ROLES.ADMIN)
  }
  return false
}
