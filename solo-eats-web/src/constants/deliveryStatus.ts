import type { TagType } from './orderStatus'

/**
 * 配送中心字典。
 * 取值来源：
 * - solo-eats-delivery/domain/enums/{RiderStatus,RiderCertStatus,DeliveryOrderStatus,VehicleType}.java
 * - Flyway V14__delivery_rider.sql、V16__delivery_freight_income.sql 列注释
 *
 * 注意两套认证编码：
 * - RIDER_CERT_STATUS 对应 t_rider.cert_status，4 个值（含未提交）
 * - CERT_RECORD_STATUS 对应 t_rider_certification.status，3 个值（待审/通过/驳回）
 */
export interface DeliveryStatusMeta {
  label: string
  type: TagType
}

export type DeliveryStatusMap = Record<number, DeliveryStatusMeta>

function buildMap(entries: Array<[number, string, TagType]>): DeliveryStatusMap {
  return Object.fromEntries(
    entries.map(([code, label, type]) => [code, { label, type }]),
  )
}

function buildOptions(map: DeliveryStatusMap): Array<{ value: number; label: string }> {
  return Object.entries(map).map(([value, meta]) => ({
    value: Number(value),
    label: meta.label,
  }))
}

/** t_rider.cert_status：0未提交 1待审核 2已通过 3已驳回 */
export const RIDER_CERT_STATUS = buildMap([
  [0, '未提交', 'info'],
  [1, '待审核', 'warning'],
  [2, '已通过', 'success'],
  [3, '已驳回', 'danger'],
])

export const RIDER_CERT_STATUS_OPTIONS = buildOptions(RIDER_CERT_STATUS)

/** t_rider_certification.status：0待审 1通过 2驳回 */
export const CERT_RECORD_STATUS = buildMap([
  [0, '待审', 'warning'],
  [1, '通过', 'success'],
  [2, '驳回', 'danger'],
])

export const CERT_RECORD_STATUS_OPTIONS = buildOptions(CERT_RECORD_STATUS)

/** t_rider.status：0正常 1停用 2拉黑 */
export const RIDER_STATUS = buildMap([
  [0, '正常', 'success'],
  [1, '停用', 'warning'],
  [2, '拉黑', 'danger'],
])

export const RIDER_STATUS_OPTIONS = buildOptions(RIDER_STATUS)

/** t_rider.online_status：0离线 1在线 2接单中 */
export const RIDER_ONLINE_STATUS = buildMap([
  [0, '离线', 'info'],
  [1, '在线', 'success'],
  [2, '接单中', 'primary'],
])

export const RIDER_ONLINE_STATUS_OPTIONS = buildOptions(RIDER_ONLINE_STATUS)

/** t_rider.vehicle_type：1电动车 2摩托车 3自行车 4步行 */
export const VEHICLE_TYPE = buildMap([
  [1, '电动车', 'primary'],
  [2, '摩托车', 'warning'],
  [3, '自行车', 'info'],
  [4, '步行', 'info'],
])

export const VEHICLE_TYPE_OPTIONS = buildOptions(VEHICLE_TYPE)

/**
 * t_delivery_order.status：
 * 0待派单 10已派单 20已接单 30已取货 40配送中 50已送达 70已取消。
 * 71已改派仅存在于 DeliveryOrderStatus 枚举，SQL 注释未列出。
 */
export const DELIVERY_STATUS = buildMap([
  [0, '待派单', 'warning'],
  [10, '已派单', 'primary'],
  [20, '已接单', 'primary'],
  [30, '已取货', 'primary'],
  [40, '配送中', 'primary'],
  [50, '已送达', 'success'],
  [70, '已取消', 'info'],
  [71, '已改派', 'warning'],
])

export const DELIVERY_STATUS_OPTIONS = buildOptions(DELIVERY_STATUS)

/** 后端改派接口允许的状态：10/20/30/40 */
export const REASSIGNABLE_DELIVERY_STATUS: readonly number[] = [10, 20, 30, 40]

/**
 * t_dispatch_config.config_scope：1全局 2城市 3门店。
 * 城市级缺少 city_id，后端 SaveDispatchConfig 明确拒绝 2，表单选项只给 1、3。
 */
export const DISPATCH_CONFIG_SCOPE = buildMap([
  [1, '全局', 'info'],
  [2, '城市', 'warning'],
  [3, '门店', 'primary'],
])

export const DISPATCH_CONFIG_SCOPE_OPTIONS = buildOptions(DISPATCH_CONFIG_SCOPE).filter(
  (option) => option.value === 1 || option.value === 3,
)

/** t_dispatch_config.status：0启用 1停用 */
export const DISPATCH_CONFIG_STATUS = buildMap([
  [0, '启用', 'success'],
  [1, '停用', 'info'],
])

export const DISPATCH_CONFIG_STATUS_OPTIONS = buildOptions(DISPATCH_CONFIG_STATUS)

/** t_dispatch_config.grab_enabled / auto_dispatch_enabled：0关闭 1开启 */
export const DISPATCH_SWITCH = buildMap([
  [0, '关闭', 'info'],
  [1, '开启', 'success'],
])

export const DISPATCH_SWITCH_OPTIONS = buildOptions(DISPATCH_SWITCH)

/** t_freight_template.status：0启用 1停用 */
export const FREIGHT_TEMPLATE_STATUS = buildMap([
  [0, '启用', 'success'],
  [1, '停用', 'info'],
])

export const FREIGHT_TEMPLATE_STATUS_OPTIONS = buildOptions(FREIGHT_TEMPLATE_STATUS)

/** t_freight_rule.rule_type：1距离区间 2时段加价 3恶劣天气 4重量 5楼层 */
export const FREIGHT_RULE_TYPE = buildMap([
  [1, '距离区间', 'primary'],
  [2, '时段加价', 'warning'],
  [3, '恶劣天气', 'danger'],
  [4, '重量', 'info'],
  [5, '楼层', 'info'],
])

export const FREIGHT_RULE_TYPE_OPTIONS = buildOptions(FREIGHT_RULE_TYPE)

/** t_freight_rule.status：0启用 1停用 */
export const FREIGHT_RULE_STATUS = buildMap([
  [0, '启用', 'success'],
  [1, '停用', 'info'],
])

export const FREIGHT_RULE_STATUS_OPTIONS = buildOptions(FREIGHT_RULE_STATUS)

export function labelOf(
  map: DeliveryStatusMap,
  value: number | null | undefined,
): string {
  if (value === null || value === undefined) {
    return '—'
  }
  return map[value]?.label ?? '—'
}

export function tagOf(
  map: DeliveryStatusMap,
  value: number | null | undefined,
): TagType {
  if (value === null || value === undefined) {
    return 'info'
  }
  return map[value]?.type ?? 'info'
}
