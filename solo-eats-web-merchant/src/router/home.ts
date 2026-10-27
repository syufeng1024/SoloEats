import { ROLES, hasAnyRole } from '@/constants/roles'

export const MERCHANT_HOME = '/orders'

export function homeForRole(roles: string[]): string {
  return hasAnyRole(roles, ROLES.MERCHANT) ? MERCHANT_HOME : '/login'
}

/** 只允许站内商家路径，避免 ?redirect= 被外部地址利用。 */
export function isRedirectAllowed(path: string, roles: string[]): boolean {
  if (!path.startsWith('/') || path.startsWith('//')) {
    return false
  }
  if (!hasAnyRole(roles, ROLES.MERCHANT)) {
    return false
  }
  return ['/orders', '/pickup', '/products', '/settlements', '/shop'].some(
    (prefix) => path === prefix || path.startsWith(`${prefix}/`) || path.startsWith(`${prefix}?`),
  )
}
