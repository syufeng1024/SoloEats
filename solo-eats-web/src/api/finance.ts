import { request } from './http'
import type { PageResult } from './types'

/**
 * T16 资金中心视图类型，字段与
 * solo-eats-settlement/src/main/java/com/soloeats/settlement/service/view/*.java 一一对应。
 * 金额字段单位为分（后端 long），commissionRate 为 0~1 的比例值（BigDecimal）。
 */

/** PayoutView.java，数据源 t_payout_order */
export interface PayoutView {
  id: number
  payoutNo: string
  /** 1商家 2骑手 */
  subjectType: number
  /** 商家 ID 或骑手 ID，随 subjectType 变化 */
  subjectId: number
  /** 提现金额，分 */
  amount: number
  /** 1支付宝 2银行卡 */
  channel: number
  /** 0待审核 10审核通过 20打款中 30已到账 40已驳回 */
  status: number
  auditRemark: string | null
  /** 审核人 t_sys_user.id */
  auditBy: number | null
  auditAt: string | null
  paidAt: string | null
  createdAt: string
}

/** CommissionRuleView.java，数据源 t_commission_rule */
export interface CommissionRuleView {
  id: number
  /** 1全局 2品类 3商家 4活动 */
  ruleScope: number
  /** 全局规则为 null */
  refId: number | null
  /** 0~1 的比例值，如 0.0600 */
  commissionRate: number
  /** 订单金额下限，分；null 表示不限 */
  minAmount: number | null
  /** 订单金额上限，分；null 表示不限 */
  maxAmount: number | null
  effectiveFrom: string
  /** null 表示长期有效 */
  effectiveTo: string | null
  priority: number
  /** 0停用 1启用 */
  status: number
  remark: string | null
  createdAt: string
  updatedAt: string
}

/** CommissionRecordView.java，数据源 t_commission_record */
export interface CommissionRecordView {
  id: number
  orderId: number
  merchantId: number | null
  shopId: number | null
  categoryId: number | null
  /** 抽佣基数，分 */
  baseAmount: number
  /** 命中规则 ID；内置默认费率时为 null */
  ruleId: number | null
  /** 命中规则范围；内置默认费率时为 null */
  ruleScope: number | null
  commissionRate: number
  /** 佣金金额，分 */
  commissionAmount: number
  /** 归集的结算单 ID，未归集为 null */
  settlementBillId: number | null
  createdAt: string
}

/** FundFlowView.java，数据源 t_fund_flow */
export interface FundFlowView {
  id: number
  /** 1佣金收入 2结算支出 3补贴支出 4优惠成本 5提现支出 6退款冲抵 */
  flowType: number
  /** 业务类型，如 commission_record / payout_order */
  bizType: string
  bizId: number
  /** 1入 2出 */
  direction: number
  /** 流水金额，分 */
  amount: number
  /** 平台无统一账户余额，后端恒返回 null */
  balanceAfter: number | null
  remark: string | null
  createdAt: string
}

/** FundFlowSummary.java，金额单位分；无流水时后端返回全 0 */
export interface FundFlowSummary {
  commissionIncome: number
  settlementExpense: number
  subsidyCost: number
  discountCost: number
  payoutExpense: number
  refundOffset: number
  /** profit = commissionIncome - settlementExpense - subsidyCost - payoutExpense */
  profit: number
}

export interface PayoutListParams {
  subjectType?: number
  subjectId?: number
  status?: number
  /** yyyy-MM-dd */
  dateFrom?: string
  /** yyyy-MM-dd */
  dateTo?: string
  pageNum?: number
  pageSize?: number
}

export interface PayoutAuditPayload {
  pass: boolean
  remark?: string | null
}

export interface CommissionRuleListParams {
  ruleScope?: number
  refId?: number
  status?: number
  pageNum?: number
  pageSize?: number
}

export interface CommissionRuleSavePayload {
  ruleScope: number
  refId?: number | null
  /** 0~1 的比例值，最多 4 位小数 */
  commissionRate: number
  /** 分；null 表示不限 */
  minAmount?: number | null
  /** 分；null 表示不限 */
  maxAmount?: number | null
  effectiveFrom?: string | null
  effectiveTo?: string | null
  priority?: number
  status?: number
  remark?: string | null
}

export interface CommissionRecordListParams {
  merchantId?: number
  categoryId?: number
  /** yyyy-MM-dd */
  dateFrom?: string
  /** yyyy-MM-dd */
  dateTo?: string
  pageNum?: number
  pageSize?: number
}

export interface FundFlowListParams {
  flowType?: number
  /** yyyy-MM-dd */
  dateFrom?: string
  /** yyyy-MM-dd */
  dateTo?: string
  pageNum?: number
  pageSize?: number
}

export interface FundFlowSummaryParams {
  /** yyyy-MM-dd */
  dateFrom?: string
  /** yyyy-MM-dd */
  dateTo?: string
}

/** GET /api/v1/admin/payouts */
export function listPayouts(
  params: PayoutListParams,
): Promise<PageResult<PayoutView>> {
  return request<PageResult<PayoutView>>({
    url: '/v1/admin/payouts',
    method: 'GET',
    params,
  })
}

/** POST /api/v1/admin/payouts/{id}/audit，pass=true 通过，false 驳回 */
export function auditPayout(id: number, payload: PayoutAuditPayload): Promise<void> {
  return request<void>({
    url: `/v1/admin/payouts/${id}/audit`,
    method: 'POST',
    data: payload,
  })
}

/** POST /api/v1/admin/payouts/{id}/pay，审核通过（status=10）后打款 */
export function payPayout(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/payouts/${id}/pay`,
    method: 'POST',
  })
}

/** POST /api/v1/admin/payouts/{id}/arrive，打款中（status=20）后登记到账 */
export function arrivePayout(id: number): Promise<void> {
  return request<void>({
    url: `/v1/admin/payouts/${id}/arrive`,
    method: 'POST',
  })
}

/** GET /api/v1/admin/commission-rules */
export function listCommissionRules(
  params: CommissionRuleListParams,
): Promise<PageResult<CommissionRuleView>> {
  return request<PageResult<CommissionRuleView>>({
    url: '/v1/admin/commission-rules',
    method: 'GET',
    params,
  })
}

/** GET /api/v1/admin/commission-rules/{id} */
export function getCommissionRule(id: number): Promise<CommissionRuleView> {
  return request<CommissionRuleView>({
    url: `/v1/admin/commission-rules/${id}`,
    method: 'GET',
  })
}

/** POST /api/v1/admin/commission-rules，后端返回创建后的完整视图 */
export function createCommissionRule(
  payload: CommissionRuleSavePayload,
): Promise<CommissionRuleView> {
  return request<CommissionRuleView>({
    url: '/v1/admin/commission-rules',
    method: 'POST',
    data: payload,
  })
}

/** PUT /api/v1/admin/commission-rules/{id} */
export function updateCommissionRule(
  id: number,
  payload: CommissionRuleSavePayload,
): Promise<CommissionRuleView> {
  return request<CommissionRuleView>({
    url: `/v1/admin/commission-rules/${id}`,
    method: 'PUT',
    data: payload,
  })
}

/** GET /api/v1/admin/commission-records */
export function listCommissionRecords(
  params: CommissionRecordListParams,
): Promise<PageResult<CommissionRecordView>> {
  return request<PageResult<CommissionRecordView>>({
    url: '/v1/admin/commission-records',
    method: 'GET',
    params,
  })
}

/** GET /api/v1/admin/fund-flows */
export function listFundFlows(
  params: FundFlowListParams,
): Promise<PageResult<FundFlowView>> {
  return request<PageResult<FundFlowView>>({
    url: '/v1/admin/fund-flows',
    method: 'GET',
    params,
  })
}

/** GET /api/v1/admin/fund-flows/summary */
export function getFundFlowSummary(
  params: FundFlowSummaryParams = {},
): Promise<FundFlowSummary> {
  return request<FundFlowSummary>({
    url: '/v1/admin/fund-flows/summary',
    method: 'GET',
    params,
  })
}
