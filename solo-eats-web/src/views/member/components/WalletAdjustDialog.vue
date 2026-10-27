<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { adjustMemberWallet, type WalletView } from '@/api/member-admin'
import {
  ADJUST_DIRECTION_OPTIONS,
  type AdjustDirection,
} from '@/constants/memberStatus'
import { formatYuan, yuanToFen } from '@/utils/money'

/**
 * 会员钱包手工调账弹框。金额表单按元输入，提交时折算为分：
 * 增加 → 正数，扣减 → 负数（后端 AdjustWalletRequest.amount 有符号）。
 * 后端限制绝对值 1 ~ 100000 分，即 0.01 ~ 1000.00 元。
 */
const props = defineProps<{
  visible: boolean
  userId: number | null
  /** 弹框里展示的用户标识，如「#12 张三」 */
  memberLabel?: string
}>()

const emit = defineEmits<{
  (event: 'update:visible', value: boolean): void
  (event: 'success', wallet: WalletView): void
}>()

const MAX_YUAN = 1000

const submitting = ref(false)
const form = reactive<{
  direction: AdjustDirection
  amountYuan: number | undefined
  reason: string
}>({
  direction: 'in',
  amountYuan: undefined,
  reason: '',
})

/** 同一次弹框提交复用同一个 requestId，失败重试不会重复入账。 */
const requestId = ref('')

function newRequestId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `adj-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
}

function reset(): void {
  form.direction = 'in'
  form.amountYuan = undefined
  form.reason = ''
  requestId.value = newRequestId()
}

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      reset()
    }
  },
)

function close(): void {
  emit('update:visible', false)
}

async function submit(): Promise<void> {
  const userId = props.userId
  if (userId === null) {
    ElMessage.warning('缺少用户 ID，无法调账')
    return
  }

  const yuan = Number(form.amountYuan)
  if (!Number.isFinite(yuan) || yuan <= 0) {
    ElMessage.warning('调账金额必须大于 0 元')
    return
  }
  if (yuan > MAX_YUAN) {
    ElMessage.warning(`单笔调账金额不能超过 ${MAX_YUAN} 元`)
    return
  }
  const fen = yuanToFen(yuan)
  if (fen <= 0) {
    ElMessage.warning('调账金额精确到分后必须大于 0')
    return
  }
  if (fen > MAX_YUAN * 100) {
    ElMessage.warning(`单笔调账金额不能超过 ${MAX_YUAN} 元`)
    return
  }

  const reason = form.reason.trim()
  if (!reason) {
    ElMessage.warning('调账原因不能为空')
    return
  }

  submitting.value = true
  try {
    const wallet = await adjustMemberWallet(userId, {
      amount: form.direction === 'out' ? -fen : fen,
      reason,
      requestId: requestId.value,
    })
    ElMessage.success(form.direction === 'out' ? '扣款已入账' : '充值已入账')
    emit('success', wallet)
    close()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="钱包调账"
    width="480px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form label-width="90px">
      <el-form-item label="会员">
        <span>{{ memberLabel || (userId === null ? '—' : `#${userId}`) }}</span>
      </el-form-item>
      <el-form-item label="调账方向" required>
        <el-radio-group v-model="form.direction">
          <el-radio
            v-for="option in ADJUST_DIRECTION_OPTIONS"
            :key="option.value"
            :value="option.value"
          >
            {{ option.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="金额（元）" required>
        <el-input-number
          v-model="form.amountYuan"
          :min="0.01"
          :max="MAX_YUAN"
          :precision="2"
          :step="1"
          :controls="false"
          placeholder="0.01 ~ 1000.00"
          class="full-width"
        />
        <div class="form-hint">方向单独选择，金额始终填正数，避免重复负号。</div>
      </el-form-item>
      <el-form-item label="备注" required>
        <el-input
          v-model="form.reason"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
          placeholder="必填，调账流水会记录为「操作人：原因」"
        />
      </el-form-item>
      <el-form-item v-if="form.amountYuan && Number(form.amountYuan) > 0" label="提交金额">
        <span class="amount">
          {{ form.direction === 'out' ? '-' : '+' }}{{ formatYuan(yuanToFen(form.amountYuan)) }}
        </span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认调账</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.full-width {
  width: 100%;
}

.form-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
  margin-top: 4px;
}

.amount {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
</style>
