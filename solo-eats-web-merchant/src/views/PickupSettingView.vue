<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useShopStore } from '@/stores/shop'
import { getShopDetail, saveMerchantPickup } from '@/api/shop'
import type { ShopDetailView } from '@/api/types'
import { centsToYuan, yuanToFen } from '@/utils/money'

interface PickupFormState {
  pickupEnabled: boolean
  pickupPrepareMinutes: number
  pickupTimeoutMinutes: number
  pickupCodePrefix: string
  pickupCodeLength: number
  pickupCodeValidMinutes: number
  pickupDiscountYuan: number
}

const auth = useAuthStore()
const shop = useShopStore()

const shopId = computed(() => shop.currentShopId ?? auth.shopId ?? null)
const detail = ref<ShopDetailView | null>(null)
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const formRef = ref<FormInstance>()

const form = reactive<PickupFormState>({
  pickupEnabled: false,
  pickupPrepareMinutes: 20,
  pickupTimeoutMinutes: 120,
  pickupCodePrefix: '',
  pickupCodeLength: 6,
  pickupCodeValidMinutes: 30,
  pickupDiscountYuan: 0,
})

const codePreview = computed(() => {
  const prefix = form.pickupCodePrefix.trim().toUpperCase()
  return `${prefix}${'#'.repeat(form.pickupCodeLength)}`
})

const rules: FormRules = {
  pickupPrepareMinutes: [
    { required: true, message: '请输入自取备餐时长', trigger: 'blur' },
  ],
  pickupTimeoutMinutes: [
    { required: true, message: '请输入超时未取时长', trigger: 'blur' },
  ],
  pickupCodePrefix: [
    {
      validator: (_rule, value, callback) => {
        const prefix = String(value ?? '').trim()
        if (prefix && !/^[A-Za-z0-9]{0,8}$/.test(prefix)) {
          callback(new Error('取餐码前缀只能是 0~8 位字母或数字'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  pickupCodeLength: [
    { required: true, message: '请输入取餐码长度', trigger: 'blur' },
  ],
  pickupCodeValidMinutes: [
    { required: true, message: '请输入取餐码有效期', trigger: 'blur' },
  ],
}

function applyDetail(data: ShopDetailView): void {
  detail.value = data
  form.pickupEnabled = data.pickupEnabled === 1
  form.pickupPrepareMinutes = data.pickupPrepareMinutes
  form.pickupTimeoutMinutes = data.pickupTimeoutMinutes
  form.pickupCodePrefix = data.pickupCodePrefix ?? ''
  form.pickupCodeLength = data.pickupCodeLength
  form.pickupCodeValidMinutes = data.pickupCodeValidMinutes
  form.pickupDiscountYuan = centsToYuan(data.pickupDiscount)
}

async function loadDetail(): Promise<void> {
  const id = shopId.value
  if (id === null) {
    detail.value = null
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    applyDetail(await getShopDetail(id))
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '门店详情加载失败'
    detail.value = null
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  const id = shopId.value
  if (id === null) {
    return
  }
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  saving.value = true
  try {
    await saveMerchantPickup(id, {
      pickupEnabled: form.pickupEnabled,
      pickupPrepareMinutes: form.pickupPrepareMinutes,
      pickupTimeoutMinutes: form.pickupTimeoutMinutes,
      pickupCodePrefix: form.pickupCodePrefix.trim(),
      pickupCodeLength: form.pickupCodeLength,
      pickupCodeValidMinutes: form.pickupCodeValidMinutes,
      pickupDiscount: yuanToFen(form.pickupDiscountYuan),
    })
    ElMessage.success('自取设置已保存')
    await loadDetail()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

watch(shopId, () => {
  void loadDetail()
})

onMounted(loadDetail)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>自取设置</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/c/shops/{{ shopId ?? '{id}' }}</span>，
          保存调用 <span class="mono">PUT /api/v1/merchant/shops/{{ shopId ?? '{id}' }}/pickup</span>。
          关闭自取后该门店不再接受自取单；已生成的订单不受影响。
        </p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadDetail">刷新</el-button>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-empty
      v-if="shopId === null && !loading"
      description="当前令牌未绑定门店。请先在右上角选择门店。"
    />

    <el-card v-if="detail" v-loading="loading" shadow="never" class="setting-card">
      <div class="card-title">到店自取</div>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="150px">
        <el-form-item label="开启自取">
          <el-switch
            v-model="form.pickupEnabled"
            active-text="开启"
            inactive-text="关闭"
            inline-prompt
          />
          <span class="inline-hint muted">
            关闭后履约方式仍保留，但用户端不再展示自取入口（取决于 deliveryTypes 位）。
          </span>
        </el-form-item>

        <el-form-item label="备餐时长（分钟）" prop="pickupPrepareMinutes">
          <el-input-number
            v-model="form.pickupPrepareMinutes"
            :min="1"
            :max="120"
            class="num-input"
          />
          <span class="inline-hint muted">范围 1~120，下单后预计多久可出餐</span>
        </el-form-item>

        <el-form-item label="超时未取（分钟）" prop="pickupTimeoutMinutes">
          <el-input-number
            v-model="form.pickupTimeoutMinutes"
            :min="10"
            :max="1440"
            :step="10"
            class="num-input"
          />
          <span class="inline-hint muted">范围 10~1440，超过后由调度逻辑处理</span>
        </el-form-item>

        <el-form-item label="取餐码前缀" prop="pickupCodePrefix">
          <el-input
            v-model="form.pickupCodePrefix"
            maxlength="8"
            show-word-limit
            placeholder="可留空，0~8 位字母或数字"
            class="prefix-input"
          />
        </el-form-item>

        <el-form-item label="取餐码长度" prop="pickupCodeLength">
          <el-input-number v-model="form.pickupCodeLength" :min="4" :max="10" class="num-input" />
          <span class="inline-hint muted">范围 4~10 位</span>
        </el-form-item>

        <el-form-item label="取餐码有效期（分钟）" prop="pickupCodeValidMinutes">
          <el-input-number
            v-model="form.pickupCodeValidMinutes"
            :min="1"
            :max="120"
            class="num-input"
          />
          <span class="inline-hint muted">范围 1~120，超时后取餐码失效</span>
        </el-form-item>

        <el-form-item label="自取立减（元）">
          <el-input-number
            v-model="form.pickupDiscountYuan"
            :min="0"
            :max="1000"
            :precision="2"
            class="num-input"
          />
          <span class="inline-hint muted">
            取值范围 0~1000 元；提交时转换为分（0~100000）
          </span>
        </el-form-item>

        <el-form-item label="取餐码预览">
          <span class="code-preview mono">{{ codePreview }}</span>
        </el-form-item>
      </el-form>

      <div class="form-actions">
        <el-button type="primary" :loading="saving" @click="save">保存自取设置</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.setting-card {
  max-width: 860px;
}

.num-input {
  width: 170px;
}

.prefix-input {
  width: 260px;
}

.inline-hint {
  margin-left: 12px;
  font-size: 12px;
}

.code-preview {
  font-size: 18px;
  letter-spacing: 3px;
  color: var(--el-color-primary);
  font-weight: 600;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

@media (max-width: 720px) {
  .inline-hint {
    display: block;
    margin-left: 0;
    margin-top: 4px;
  }
}
</style>
