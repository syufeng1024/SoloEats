/**
 * 语音播报。
 * - H5：window.speechSynthesis（浏览器 TTS），不支持则静默
 * - 其他平台：阶段A 不接原生 TTS，条件编译降级为静默
 *
 * 播报失败（浏览器未授权音频、无中文语音包）一律吞掉，不能因为播报失败阻断派单流程。
 */

const VOICE_STORAGE_KEY = 'rider:voiceEnabled'

/** 设置页写入的开关在下次启动时生效；读不到或值非法一律按开启处理 */
function readStoredVoiceEnabled(): boolean {
  try {
    if (typeof uni === 'undefined') {
      return true
    }
    const stored = uni.getStorageSync(VOICE_STORAGE_KEY)
    return stored === '' || stored === null || stored === undefined ? true : Boolean(stored)
  } catch (error) {
    return true
  }
}

let voiceEnabled = readStoredVoiceEnabled()

export function setVoiceEnabled(enabled: boolean): void {
  voiceEnabled = enabled
  try {
    if (typeof uni !== 'undefined') {
      uni.setStorageSync(VOICE_STORAGE_KEY, enabled)
    }
  } catch (error) {
    // 存储不可用时只影响下次启动的默认值，不阻断播报开关
  }
  if (!enabled) {
    cancelSpeech()
  }
}

export function isVoiceEnabled(): boolean {
  return voiceEnabled
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
    // 新派单播报覆盖旧播报，避免排队积压
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
