import { request } from './http'
import type { DashboardView } from './types'

/** GET /api/v1/admin/report/dashboard，权限码 report:dashboard:view */
export function getDashboard(): Promise<DashboardView> {
  return request<DashboardView>({
    url: '/v1/admin/report/dashboard',
    method: 'GET',
  })
}
