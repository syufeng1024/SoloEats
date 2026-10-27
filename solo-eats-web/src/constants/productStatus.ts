import type { TagType } from './orderStatus'

/**
 * 商品中心字典。
 * 取值以 Flyway V9__product_catalog.sql、V10__product_spec_addon_review.sql 列注释为准，
 * 与 serviceStatus.ts 保持同一份元数据结构：{ label, type }。
 */
export interface ProductStatusMeta {
  label: string
  type: TagType
}

export type ProductStatusMap = Record<number, ProductStatusMeta>

function buildMap(entries: Array<[number, string, TagType]>): ProductStatusMap {
  return Object.fromEntries(
    entries.map(([code, label, type]) => [code, { label, type }]),
  )
}

function buildOptions(map: ProductStatusMap): Array<{ value: number; label: string }> {
  return Object.entries(map).map(([value, meta]) => ({
    value: Number(value),
    label: meta.label,
  }))
}

/** t_category.status：0启用 1停用 */
export const CATEGORY_STATUS = buildMap([
  [0, '启用', 'success'],
  [1, '停用', 'info'],
])

export const CATEGORY_STATUS_OPTIONS = buildOptions(CATEGORY_STATUS)

/** t_product_review.status：0待审 1已通过 2已隐藏 */
export const REVIEW_STATUS = buildMap([
  [0, '待审', 'warning'],
  [1, '已通过', 'success'],
  [2, '已隐藏', 'info'],
])

export const REVIEW_STATUS_OPTIONS = buildOptions(REVIEW_STATUS)

/** AdminReviewController scoreLevel：1差评（1~2 星）2中评（3 星）3好评（4~5 星） */
export const REVIEW_SCORE_LEVEL = buildMap([
  [1, '差评（1~2 星）', 'danger'],
  [2, '中评（3 星）', 'warning'],
  [3, '好评（4~5 星）', 'success'],
])

export const REVIEW_SCORE_LEVEL_OPTIONS = buildOptions(REVIEW_SCORE_LEVEL)

/** t_spu.status：0草稿 1待审 2已上架 3已下架 */
export const PRODUCT_PUBLISH_STATUS = buildMap([
  [0, '草稿', 'info'],
  [1, '待审', 'warning'],
  [2, '已上架', 'success'],
  [3, '已下架', 'info'],
])

export const PRODUCT_PUBLISH_STATUS_OPTIONS = buildOptions(PRODUCT_PUBLISH_STATUS)

/** t_spec_group.group_type：1单选 2多选 */
export const SPEC_GROUP_TYPE = buildMap([
  [1, '单选', 'primary'],
  [2, '多选', 'warning'],
])

export const SPEC_GROUP_TYPE_OPTIONS = buildOptions(SPEC_GROUP_TYPE)

/** t_media_item.media_type：1图片 2视频 */
export const MEDIA_TYPE = buildMap([
  [1, '图片', 'primary'],
  [2, '视频', 'warning'],
])

export const MEDIA_TYPE_OPTIONS = buildOptions(MEDIA_TYPE)

/** 规格选项、加料项、素材分组、素材项的通用 status：0启用 1停用 */
export const ENABLE_STATUS = buildMap([
  [0, '启用', 'success'],
  [1, '停用', 'info'],
])

export const ENABLE_STATUS_OPTIONS = buildOptions(ENABLE_STATUS)

/**
 * 估清原因后端是自由文本（t_sold_out_record.reason varchar(128)），
 * 不是枚举；这里只提供长度上限与查询提示，不做取值映射。
 */
export const SOLD_OUT_REASON_MAX_LENGTH = 128

export function labelOf(
  map: ProductStatusMap,
  value: number | null | undefined,
): string {
  if (value === null || value === undefined) {
    return '—'
  }
  return map[value]?.label ?? '—'
}

export function tagOf(
  map: ProductStatusMap,
  value: number | null | undefined,
): TagType {
  if (value === null || value === undefined) {
    return 'info'
  }
  return map[value]?.type ?? 'info'
}
