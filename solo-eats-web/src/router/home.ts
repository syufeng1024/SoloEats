import { ROLES, hasAnyRole } from '@/constants/roles'

export function homeForRole(roles: string[]): string {
  if (hasAnyRole(roles, ROLES.ADMIN)) {
    return '/admin/overview'
  }
  if (hasAnyRole(roles, ROLES.MERCHANT)) {
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
  // 商家入驻审核页挂在 /merchant/apply，但只对平台管理员开放
  if (path.startsWith('/merchant/apply')) {
    return hasAnyRole(roles, ROLES.ADMIN)
  }
  if (path.startsWith('/merchant')) {
    return hasAnyRole(roles, ROLES.MERCHANT)
  }
  if (path.startsWith('/admin')) {
    return hasAnyRole(roles, ROLES.ADMIN)
  }
  return false
}
