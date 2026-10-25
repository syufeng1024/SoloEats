import { formatDateTime } from './date'
import { fenToYuan } from './money'

/**
 * 小票预览：纯前端 Canvas 绘制 → 导出图片。
 * 不做真实打印（不接云打印/蓝牙打印机），只提供「保存图片」。
 *
 * Canvas 逻辑尺寸固定 600px，页面里把 canvas 移到可视区外，
 * 只展示导出的图片，这样不用为不同屏宽调整字号。
 */

export const RECEIPT_CANVAS_ID = 'receiptCanvas'
export const RECEIPT_WIDTH = 600

const PADDING = 32
const TOP = 28
const BOTTOM = 36

const COLOR_TEXT = '#16233a'
const COLOR_MUTED = '#7a8699'
const COLOR_LINE = '#d8dde5'
const COLOR_PRICE = '#e85d04'
const COLOR_BG = '#ffffff'

export interface ReceiptItem {
  name: string
  quantity: number
  /** 分 */
  amount: number
}

export interface ReceiptData {
  shopName: string
  orderNo: string
  createdAt: string | null
  /** 配送 / 自取，取接口 fulfillmentTypeName；缺省时由页面按 fulfillmentType 兜底 */
  fulfillmentTypeName: string
  pickupCode: string | null
  items: ReceiptItem[]
  goodsAmount: number
  packagingFee: number
  deliveryFee: number
  payAmount: number
  remark: string | null
}

/** 只声明用到的 CanvasContext 方法，避免依赖平台类型定义差异 */
export interface ReceiptContext {
  setFillStyle(color: string): void
  setFontSize(size: number): void
  setTextAlign(align: 'left' | 'center' | 'right'): void
  fillText(text: string, x: number, y: number): void
  fillRect(x: number, y: number, width: number, height: number): void
  draw(reserve?: boolean, callback?: () => void): void
  measureText?(text: string): { width: number }
}

type Block =
  | { type: 'title'; text: string }
  | { type: 'subtitle'; text: string }
  | { type: 'row'; label: string; value: string }
  | { type: 'item'; name: string; quantity: number; amount: number }
  | { type: 'code'; code: string }
  | { type: 'divider' }
  | { type: 'total'; label: string; value: number }
  | { type: 'note'; lines: string[] }

const BLOCK_HEIGHT: Record<Exclude<Block['type'], 'note'>, number> = {
  title: 46,
  subtitle: 32,
  row: 34,
  item: 30,
  code: 92,
  divider: 22,
  total: 44,
}

/** 按字符数截断，中文按 1 个字符计；Array.from 保证不切开代理对 */
function truncate(value: string, maxChars: number): string {
  const chars = Array.from(String(value ?? ''))
  return chars.length <= maxChars ? chars.join('') : `${chars.slice(0, maxChars - 1).join('')}…`
}

function wrap(value: string, maxChars: number): string[] {
  const chars = Array.from(String(value ?? ''))
  const lines: string[] = []
  for (let index = 0; index < chars.length; index += maxChars) {
    lines.push(chars.slice(index, index + maxChars).join(''))
  }
  return lines.length > 0 ? lines : ['']
}

function buildBlocks(data: ReceiptData): Block[] {
  const blocks: Block[] = [
    { type: 'title', text: truncate(data.shopName || '门店小票', 14) },
    { type: 'subtitle', text: '订单小票（预览）' },
    { type: 'divider' },
    { type: 'row', label: '订单号', value: data.orderNo || '--' },
    { type: 'row', label: '下单时间', value: formatDateTime(data.createdAt) },
    { type: 'row', label: '取餐方式', value: data.fulfillmentTypeName || '--' },
  ]

  if (data.pickupCode) {
    blocks.push({ type: 'code', code: data.pickupCode })
  }

  blocks.push({ type: 'divider' })
  if (data.items.length === 0) {
    blocks.push({ type: 'note', lines: ['暂无商品明细'] })
  } else {
    data.items.forEach((item) => {
      blocks.push({
        type: 'item',
        name: truncate(item.name, 20),
        quantity: item.quantity,
        amount: item.amount,
      })
    })
  }

  blocks.push(
    { type: 'divider' },
    { type: 'row', label: '商品金额', value: `¥${fenToYuan(data.goodsAmount)}` },
    { type: 'row', label: '打包费', value: `¥${fenToYuan(data.packagingFee)}` },
    { type: 'row', label: '配送费', value: `¥${fenToYuan(data.deliveryFee)}` },
    { type: 'total', label: '实付金额', value: data.payAmount },
  )

  if (data.remark) {
    blocks.push({ type: 'note', lines: wrap(`备注：${data.remark}`, 24) })
  }

  blocks.push({ type: 'note', lines: ['本小票由商家端生成，仅用于备餐核对'] })
  return blocks
}

function blockHeight(block: Block): number {
  if (block.type === 'note') {
    return block.lines.length * 24 + 8
  }
  return BLOCK_HEIGHT[block.type]
}

/** 页面据此设置 canvas 高度；与 drawReceipt 共用同一套块模型，不会出现截断 */
export function calcReceiptHeight(data: ReceiptData): number {
  const content = buildBlocks(data).reduce((total, block) => total + blockHeight(block), 0)
  return TOP + content + BOTTOM
}

function drawDivider(ctx: ReceiptContext, y: number): void {
  ctx.setFillStyle(COLOR_LINE)
  ctx.fillRect(PADDING, y + 10, RECEIPT_WIDTH - PADDING * 2, 1)
}

function drawBlock(ctx: ReceiptContext, block: Block, y: number): void {
  const right = RECEIPT_WIDTH - PADDING
  switch (block.type) {
    case 'title':
      ctx.setTextAlign('center')
      ctx.setFillStyle(COLOR_TEXT)
      ctx.setFontSize(32)
      ctx.fillText(block.text, RECEIPT_WIDTH / 2, y + 32)
      ctx.setTextAlign('left')
      break
    case 'subtitle':
      ctx.setTextAlign('center')
      ctx.setFillStyle(COLOR_MUTED)
      ctx.setFontSize(18)
      ctx.fillText(block.text, RECEIPT_WIDTH / 2, y + 22)
      ctx.setTextAlign('left')
      break
    case 'row':
      ctx.setFillStyle(COLOR_MUTED)
      ctx.setFontSize(20)
      ctx.setTextAlign('left')
      ctx.fillText(block.label, PADDING, y + 23)
      ctx.setFillStyle(COLOR_TEXT)
      ctx.setTextAlign('right')
      ctx.fillText(block.value, right, y + 23)
      ctx.setTextAlign('left')
      break
    case 'item':
      ctx.setFillStyle(COLOR_TEXT)
      ctx.setFontSize(21)
      ctx.setTextAlign('left')
      ctx.fillText(block.name, PADDING, y + 21)
      ctx.setFillStyle(COLOR_MUTED)
      ctx.setFontSize(19)
      ctx.fillText(`x${block.quantity}`, PADDING + 330, y + 21)
      ctx.setFillStyle(COLOR_TEXT)
      ctx.setFontSize(21)
      ctx.setTextAlign('right')
      ctx.fillText(`¥${fenToYuan(block.amount)}`, right, y + 21)
      ctx.setTextAlign('left')
      break
    case 'code':
      ctx.setFillStyle('#fff4e8')
      ctx.fillRect(PADDING, y + 8, RECEIPT_WIDTH - PADDING * 2, 76)
      ctx.setTextAlign('center')
      ctx.setFillStyle(COLOR_MUTED)
      ctx.setFontSize(17)
      ctx.fillText('取餐码', RECEIPT_WIDTH / 2, y + 30)
      ctx.setFillStyle(COLOR_PRICE)
      ctx.setFontSize(40)
      ctx.fillText(block.code, RECEIPT_WIDTH / 2, y + 70)
      ctx.setTextAlign('left')
      break
    case 'divider':
      drawDivider(ctx, y)
      break
    case 'total':
      ctx.setTextAlign('left')
      ctx.setFillStyle(COLOR_TEXT)
      ctx.setFontSize(23)
      ctx.fillText(block.label, PADDING, y + 28)
      ctx.setTextAlign('right')
      ctx.setFillStyle(COLOR_PRICE)
      ctx.setFontSize(30)
      ctx.fillText(`¥${fenToYuan(block.value)}`, right, y + 29)
      ctx.setTextAlign('left')
      break
    case 'note':
      ctx.setTextAlign('left')
      ctx.setFillStyle(COLOR_MUTED)
      ctx.setFontSize(18)
      block.lines.forEach((line, index) => {
        ctx.fillText(line, PADDING, y + 20 + index * 24)
      })
      break
    default:
      break
  }
}

/**
 * 绘制小票。调用前请先用 calcReceiptHeight 设置 canvas 高度，并等一帧让节点渲染完成。
 * resolve 时表示 draw 回调已执行，可安全导出图片。
 */
export function drawReceipt(
  ctx: ReceiptContext,
  data: ReceiptData,
  height: number,
  componentInstance?: unknown,
): Promise<void> {
  void componentInstance
  return new Promise<void>((resolve, reject) => {
    try {
      ctx.setFillStyle(COLOR_BG)
      ctx.fillRect(0, 0, RECEIPT_WIDTH, height)

      let y = TOP
      buildBlocks(data).forEach((block) => {
        drawBlock(ctx, block, y)
        y += blockHeight(block)
      })

      ctx.draw(false, () => resolve())
    } catch (error) {
      reject(error)
    }
  })
}

/** 导出临时图片，H5 返回 blob URL，App/小程序返回本地文件路径 */
export function exportReceiptImage(componentInstance?: unknown): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    uni.canvasToTempFilePath(
      {
        canvasId: RECEIPT_CANVAS_ID,
        fileType: 'png',
        quality: 1,
        success: (result) => resolve(result.tempFilePath),
        fail: (error) => reject(new Error(error?.errMsg || '小票图片生成失败')),
      },
      componentInstance as never,
    )
  })
}

/**
 * 保存到相册。
 * - H5 无相册能力，改用 <a download> 下载
 * - App/小程序走 saveImageToPhotosAlbum，用户拒绝授权时提示去设置
 */
export function saveReceiptImage(tempFilePath: string, fileName: string): Promise<void> {
  // #ifdef H5
  try {
    const link = document.createElement('a')
    link.href = tempFilePath
    link.download = fileName
    link.style.display = 'none'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    return Promise.resolve()
  } catch (error) {
    return Promise.reject(new Error('当前浏览器不支持下载，请长按图片保存'))
  }
  // #endif
  // #ifndef H5
  return new Promise<void>((resolve, reject) => {
    uni.saveImageToPhotosAlbum({
      filePath: tempFilePath,
      success: () => resolve(),
      fail: (error) => {
        const message = String(error?.errMsg || '')
        if (/auth|deny|denied/i.test(message)) {
          uni.showModal({
            title: '需要相册权限',
            content: '请在系统设置中允许保存图片到相册后重试',
            confirmText: '去设置',
            success: (result) => {
              if (result.confirm) {
                uni.openSetting({})
              }
              reject(new Error('未授权保存到相册'))
            },
          })
          return
        }
        reject(new Error(message || '保存图片失败'))
      },
    })
  })
  // #endif
}
