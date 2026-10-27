<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useShopStore } from '@/stores/shop'
import { getShopDecoration, saveShopDecoration } from '@/api/shop'

const auth = useAuthStore()
const shop = useShopStore()

const shopId = computed(() => shop.currentShopId ?? auth.shopId ?? null)
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const raw = ref('{}')
const original = ref('{}')

const dirty = computed(() => raw.value !== original.value)
const compactLength = computed(() => {
  try {
    return JSON.stringify(JSON.parse(raw.value)).length
  } catch {
    return raw.value.length
  }
})
const overLimit = computed(() => compactLength.value > 20000)

function prettyJson(text: string): string {
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}

async function load(): Promise<void> {
  const id = shopId.value
  if (id === null) {
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const text = await getShopDecoration(id)
    const formatted = prettyJson(text || '{}')
    raw.value = formatted
    original.value = formatted
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '装修配置加载失败'
  } finally {
    loading.value = false
  }
}

function formatJson(): void {
  try {
    raw.value = JSON.stringify(JSON.parse(raw.value), null, 2)
    ElMessage.success('已格式化')
  } catch {
    ElMessage.error('JSON 格式错误，无法格式化')
  }
}

function reset(): void {
  raw.value = original.value
}

function parseConfig(): Record<string, unknown> | null {
  let parsed: unknown
  try {
    parsed = JSON.parse(raw.value)
  } catch {
    ElMessage.error('JSON 格式错误，请检查逗号与引号')
    return null
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    ElMessage.error('装修配置必须是 JSON 对象（不能是数组或基本类型）')
    return null
  }
  const compact = JSON.stringify(parsed)
  if (compact.length > 20000) {
    ElMessage.error(`序列化后 ${compact.length} 字符，超过后端 20000 字符上限`)
    return null
  }
  return parsed as Record<string, unknown>
}

async function save(): Promise<void> {
  const id = shopId.value
  if (id === null) {
    return
  }
  const config = parseConfig()
  if (config === null) {
    return
  }
  saving.value = true
  try {
    const saved = await saveShopDecoration(id, config)
    const formatted = prettyJson(saved || JSON.stringify(config))
    raw.value = formatted
    original.value = formatted
    ElMessage.success('装修配置已保存')
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

watch(shopId, () => {
  void load()
})

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>门店装修</h2>
        <p>
          数据源：<span class="mono">GET/PUT /api/v1/merchant/shops/{{ shopId ?? '{id}' }}/decoration</span>。
          后端只校验「必须是 JSON 对象」且序列化后 ≤20000 字符，字段结构由商家端与用户端约定，本页不做结构约束。
        </p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-empty
      v-if="shopId === null && !loading"
      description="当前令牌未绑定门店。请先在右上角选择门店。"
    />

    <el-card v-if="shopId !== null" v-loading="loading" shadow="never">
      <div class="toolbar decoration-toolbar">
        <span class="muted">
          序列化后 {{ compactLength }} / 20000 字符
          <el-tag v-if="dirty" type="warning" size="small" effect="plain" class="dirty-tag">
            未保存
          </el-tag>
        </span>
        <div class="toolbar-spacer" />
        <el-button :icon="Refresh" :loading="loading" @click="load">重新加载</el-button>
        <el-button @click="formatJson">格式化</el-button>
      </div>

      <el-input
        v-model="raw"
        type="textarea"
        :rows="20"
        class="json-editor mono"
        placeholder="{}"
      />

      <el-alert
        v-if="overLimit"
        type="error"
        :closable="false"
        show-icon
        title="已超过 20000 字符上限，后端会拒绝保存"
        class="limit-alert"
      />

      <div class="form-actions">
        <el-button :disabled="!dirty" @click="reset">撤销修改</el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="overLimit || !dirty"
          @click="save"
        >
          保存装修配置
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.decoration-toolbar {
  margin-bottom: 12px;
}

.dirty-tag {
  margin-left: 8px;
}

.json-editor :deep(.el-textarea__inner) {
  font-family: 'JetBrains Mono', 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  line-height: 1.7;
  tab-size: 2;
}

.limit-alert {
  margin-top: 10px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 14px;
}
</style>
