import { request } from './http'
import type { DashboardView } from './types'

/** 经营总览聚合粒度。后端会把非法值归一到 day。 */
export type ReportGranularity = 'day' | 'week' | 'month'

export interface ReportRangeParams {
  /** yyyy-MM-dd */
  dateFrom: string
  /** yyyy-MM-dd */
  dateTo: string
}

export interface ReportOverviewParams extends ReportRangeParams {
  granularity: ReportGranularity
}

/** OverviewSeries.Point.java，金额单位分。 */
export interface OverviewPoint {
  /** day: 2026-10-26；week: 2026-W43；month: 2026-10 */
  period: string
  periodStart: string
  periodEnd: string
  gmv: number
  orderCount: number
  avgOrderAmount: number
  refundAmount: number
  refundOrderCount: number
  /** 0~1 比例，如 0.0500 表示 5%。注意与漏斗/财务/用户报表的百分比数值口径不同。 */
  refundRate: number
  /** 0~1 比例，如 0.1250 表示 12.5%。 */
  repurchaseRate: number
}

/** OverviewSeries.java */
export interface OverviewSeries {
  dateFrom: string
  dateTo: string
  granularity: ReportGranularity
  points: OverviewPoint[]
}

/** FunnelView.java，四个转化率均为百分比数值（10.00 表示 10.00%）。 */
export interface FunnelView {
  exposeCount: number
  clickCount: number
  cartCount: number
  orderCount: number
  payCount: number
  clickRate: number
  cartRate: number
  orderRate: number
  payRate: number
}

/** FinanceReportView.java，金额单位分；refundRate 为百分比数值（10.00 表示 10.00%）。 */
export interface FinanceReportView {
  commissionIncome: number
  settlementExpense: number
  subsidyCost: number
  discountCost: number
  withdrawExpense: number
  refundOffset: number
  grossProfit: number
  refundRate: number
}

/** UserReportView.java，repurchaseRate 为百分比数值（10.00 表示 10.00%）。 */
export interface UserReportView {
  newUserCount: number
  activeUserCount: number
  repurchaseUserCount: number
  repurchaseRate: number
}

/** GET /api/v1/admin/report/dashboard，权限码 report:dashboard:view */
export function getDashboard(): Promise<DashboardView> {
  return request<DashboardView>({
    url: '/v1/admin/report/dashboard',
    method: 'GET',
  })
}

/** GET /api/v1/admin/report/overview，权限码 report:overview:view */
export function getReportOverview(params: ReportOverviewParams): Promise<OverviewSeries> {
  return request<OverviewSeries>({
    url: '/v1/admin/report/overview',
    method: 'GET',
    params,
  })
}

/** GET /api/v1/admin/report/funnel，权限码 report:funnel:view */
export function getReportFunnel(params: ReportRangeParams): Promise<FunnelView> {
  return request<FunnelView>({
    url: '/v1/admin/report/funnel',
    method: 'GET',
    params,
  })
}

/** GET /api/v1/admin/report/finance，权限码 report:finance:view */
export function getReportFinance(params: ReportRangeParams): Promise<FinanceReportView> {
  return request<FinanceReportView>({
    url: '/v1/admin/report/finance',
    method: 'GET',
    params,
  })
}

/** GET /api/v1/admin/report/users，权限码 report:user:view */
export function getReportUsers(params: ReportRangeParams): Promise<UserReportView> {
  return request<UserReportView>({
    url: '/v1/admin/report/users',
    method: 'GET',
    params,
  })
}
