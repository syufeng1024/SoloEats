/**
 * 金额工具：接口与数据库一律用「分」(bigint)，页面展示时才转「元」。
 *
 * 为什么不用 Number(yuan) * 100：JS 浮点会让 8.29 * 100 = 828.9999999999999，
 * 再做一次 round 虽然多半正确，但 1.005 这类边界仍会漂移。
 * 这里逐位解析字符串并手工进位，输入与输出都是整数分。
 */

const YUAN_PATTERN = /^-?\d+(\.\d+)?$/

/** 分 → 元，固定两位小数。null/undefined 按 0 处理。 */
export function fenToYuan(fen: number | null | undefined): string {
  const numeric = Number(fen ?? 0)
  if (!Number.isFinite(numeric)) {
    return '0.00'
  }
  const integer = Math.trunc(numeric)
  const negative = integer < 0
  const abs = Math.abs(integer)
  return `${negative ? '-' : ''}${Math.floor(abs / 100)}.${String(abs % 100).padStart(2, '0')}`
}

/**
 * 元 → 分。字符串解析，第三位小数四舍五入（如 "8.295" → 830）。
 * 输入非法直接抛错，由调用方转成中文提示，避免 NaN 流进订单。
 */
export function yuanToFen(yuan: string | number | null | undefined): number {
  const raw = String(yuan ?? '').trim()
  if (!YUAN_PATTERN.test(raw)) {
    throw new Error('金额格式不正确')
  }
  const negative = raw.startsWith('-')
  const unsigned = negative ? raw.slice(1) : raw
  const [integerPart, decimalPart = ''] = unsigned.split('.')
  const padded = `${decimalPart}000`.slice(0, 3)
  let fen = Number(integerPart) * 100 + Number(padded.slice(0, 2))
  if (Number(padded.charAt(2)) >= 5) {
    fen += 1
  }
  return negative ? -fen : fen
}

/** 整数分求和，拒绝浮点累加。 */
export function sumFen(values: Array<number | null | undefined>): number {
  return values.reduce<number>((total, value) => total + Math.trunc(Number(value ?? 0)), 0)
}

/** 单价（分）× 数量，整数乘法。 */
export function fenMultiply(price: number | null | undefined, quantity: number | null | undefined): number {
  return Math.trunc(Number(price ?? 0)) * Math.trunc(Number(quantity ?? 0))
}

/** 分 → 元数字串（不带 ¥），用于需要拼接的文案。 */
export function formatYuan(fen: number | null | undefined): string {
  return `¥${fenToYuan(fen)}`
}
