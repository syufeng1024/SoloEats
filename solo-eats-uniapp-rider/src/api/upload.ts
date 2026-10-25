import { getToken, handleUnauthorized, ApiError } from './http'
import type { ApiResponse, FileVO } from './types'

/**
 * 文件上传 POST /api/v1/files（multipart）。
 *
 * 后端 FileTypeValidator 的 bizType 白名单是固定的：
 * MERCHANT_QUAL / PRODUCT / AVATAR / TICKET / CONTENT / RECEIPT。
 * 骑手端映射：
 * - 实名认证证件照（身份证正反、健康证）→ MERCHANT_QUAL
 * - 送达留证照片 → RECEIPT
 * 后端未新增骑手专用 bizType，前端不改后端，只在这两个值里选。
 */
export const UPLOAD_BIZ_TYPE = {
  CERT: 'MERCHANT_QUAL',
  DELIVERY_EVIDENCE: 'RECEIPT',
} as const

export type UploadBizType = (typeof UPLOAD_BIZ_TYPE)[keyof typeof UPLOAD_BIZ_TYPE]

const UPLOAD_URL = '/api/v1/files'
const UPLOAD_TIMEOUT_MS = 30_000
const MAX_SIZE_BYTES = 10 * 1024 * 1024

function parseBody(raw: unknown): ApiResponse<FileVO> | null {
  if (typeof raw !== 'string' || raw.length === 0) {
    return null
  }
  try {
    return JSON.parse(raw) as ApiResponse<FileVO>
  } catch (error) {
    return null
  }
}

export function uploadFile(
  filePath: string,
  bizType: UploadBizType = UPLOAD_BIZ_TYPE.CERT,
): Promise<FileVO> {
  return new Promise<FileVO>((resolve, reject) => {
    if (!filePath) {
      reject(new Error('未选择文件'))
      return
    }
    const token = getToken()
    uni.uploadFile({
      url: UPLOAD_URL,
      filePath,
      name: 'file',
      formData: { bizType },
      header: token ? { Authorization: `Bearer ${token}` } : {},
      timeout: UPLOAD_TIMEOUT_MS,
      success: (response) => {
        const body = parseBody(response.data)
        if (!body) {
          reject(new Error(`上传失败（HTTP ${response.statusCode}）`))
          return
        }
        if (body.code === 10003) {
          const error = new ApiError(body.code, body.message || '登录状态已失效，请重新登录')
          handleUnauthorized(error)
          reject(error)
          return
        }
        if (body.code !== 0) {
          reject(new Error(body.message || '上传失败'))
          return
        }
        if (!body.data || !body.data.fileId) {
          reject(new Error('上传返回缺少 fileId'))
          return
        }
        resolve(body.data)
      },
      fail: () => {
        reject(new Error('上传失败，请检查网络后重试'))
      },
    })
  })
}

/**
 * 选图后上传。sizeType=compressed 控制体积（后端单文件上限 10MB）。
 * 返回 fileId 与本地预览路径，页面用本地路径预览，避免 img 无法携带 Authorization。
 */
export function chooseAndUploadImage(
  bizType: UploadBizType = UPLOAD_BIZ_TYPE.CERT,
): Promise<{ fileId: number; localPath: string }> {
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['camera', 'album'],
      success: async (result) => {
        const localPath = result.tempFilePaths?.[0]
        if (!localPath) {
          reject(new Error('未选择图片'))
          return
        }
        try {
          const file = await uploadFile(localPath, bizType)
          resolve({ fileId: file.fileId, localPath })
        } catch (error) {
          reject(error)
        }
      },
      fail: () => reject(new Error('已取消选择图片')),
    })
  })
}

export { MAX_SIZE_BYTES }
