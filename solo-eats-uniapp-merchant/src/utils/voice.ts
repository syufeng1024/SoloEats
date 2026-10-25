/**
 * 新单语音播报。
 * - H5：window.speechSynthesis（浏览器 TTS），不支持或未授权则静默
 * - 其他平台：阶段A 不接原生 TTS，条件编译降级为静默
 *
 * 播报失败不能阻断订单处理（浏览器策略要求用户手势、无中文语音包等），一律吞掉异常。
 */

const VOICE_ENABLED_KEY = 'voiceEnabled'

let voiceEnabled = uni.getStorageSync(VOICE_ENABLED_KEY) !== false

export function isVoiceEnabled(): boolean {
  return voiceEnabled
}

export function setVoiceEnabled(enabled: boolean): void {
  voiceEnabled = enabled
  uni.setStorageSync(VOICE_ENABLED_KEY, enabled)
  if (!enabled) {
    cancelSpeech()
  }
}

function cancelSpeech(): void {
  // #ifdef H5
  try {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      window.speechSynthesis.cancel()
    }
  } catch (error) {
    // 忽略：部分浏览器在无用户手势时调用会抛错
  }
  // #endif
}

export function speak(text: string): void {
  if (!voiceEnabled || !text) {
    return
  }
  // #ifdef H5
  try {
    if (typeof window === 'undefined' || !('speechSynthesis' in window)) {
      return
    }
    // 新播报覆盖旧播报，避免高峰期排队积压
    window.speechSynthesis.cancel()
    const utterance = new SpeechSynthesisUtterance(text)
    utterance.lang = 'zh-CN'
    utterance.rate = 1.05
    utterance.volume = 1
    window.speechSynthesis.speak(utterance)
  } catch (error) {
    // 浏览器 TTS 不可用时静默降级
  }
  // #endif
  // #ifndef H5
  // 阶段A 非 H5 平台不接 plus.speech，保持静默，避免调用不存在的原生模块
  // #endif
}

/** 订单号按 4 位分组播报，避免 TTS 把它当成一个巨大的数字 */
function spellOrderNo(orderNo: string): string {
  const digits = String(orderNo || '').replace(/\s+/g, '')
  return digits.replace(/(.{4})/g, '$1 ').trim()
}

/** 新单提醒：播报订单号 */
export function speakNewOrder(orderNo: string): void {
  speak(`您有新的外卖订单，订单号 ${spellOrderNo(orderNo)}，请及时处理`)
}

/** 核销成功播报 */
export function speakPickupVerified(orderNo: string): void {
  speak(`核销成功，订单号 ${spellOrderNo(orderNo)}，请将餐品交给顾客`)
}
