<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import {
  createSpu,
  getCategoryTree,
  getSpu,
  updateSpu,
  type AdminSpuSavePayload,
} from '@/api/product'
import type { CategoryNode } from '@/api/types'
import { SPU_STATUS_OPTIONS } from '@/constants/businessStatus'
import { centsToYuan, yuanToFen } from '@/utils/money'

const route = useRoute()
const router = useRouter()

interface CategoryOption {
  id: number
  label: string
}

const editingId = ref<number | null>(null)
const isEdit = computed(() => editingId.value !== null)

const loading = ref(false)
const loadError = ref('')
const submitting = ref(false)
const submitError = ref('')
const categories = ref<CategoryOption[]>([])

const form = reactive({
  categoryId: undefined as number | undefined,
  spuName: '',
  subtitle: '',
  description: '',
  detailHtml: '',
  mainImage: '',
  imagesText: '',
  tagsText: '',
  packagingFeeYuan: 0,
  minPurchaseQty: 1,
  limitPerOrder: 0,
  status: 0,
  sortWeight: 0,
})

const mainImagePreview = computed(() => form.mainImage.trim())

const imagesPreview = computed(() =>
  form.imagesText
    .split(/\r?\n|,/)
    .map((item) => item.trim())
    .filter((item) => item.length > 0),
)

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

/** 后端要求 categoryId 指向二级品类，一级节点只作为分组标题。 */
function collectLevelTwo(nodes: CategoryNode[], acc: CategoryOption[]): void {
  for (const node of nodes) {
    if (node.level === 2) {
      acc.push({ id: node.id, label: node.categoryName })
      continue
    }
    if (node.children?.length) {
      collectLevelTwo(node.children, acc)
    }
  }
}

async function loadCategories(): Promise<void> {
  try {
    const tree = await getCategoryTree()
    const options: CategoryOption[] = []
    collectLevelTwo(tree, options)
    categories.value = options
  } catch (error) {
    loadError.value = errorMessage(error, '品类加载失败')
  }
}

function splitLines(value: string): string[] {
  return value
    .split(/\r?\n|,/)
    .map((item) => item.trim())
    .filter((item) => item.length > 0)
}

function splitTags(value: string): string[] {
  return value
    .split(/[,，]/)
    .map((item) => item.trim())
    .filter((item) => item.length > 0)
}

function trimOrNull(value: string): string | null {
  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : null
}

function applySpu(spu: {
  categoryId: number | null
  spuName: string
  subtitle: string | null
  description: string | null
  detailHtml: string | null
  mainImage: string | null
  images: string[] | null
  tags: string[] | null
  packagingFee: number
  minPurchaseQty: number
  limitPerOrder: number
  status: number
  sortWeight: number
}): void {
  form.categoryId = spu.categoryId ?? undefined
  form.spuName = spu.spuName
  form.subtitle = spu.subtitle ?? ''
  form.description = spu.description ?? ''
  form.detailHtml = spu.detailHtml ?? ''
  form.mainImage = spu.mainImage ?? ''
  form.imagesText = (spu.images ?? []).join('\n')
  form.tagsText = (spu.tags ?? []).join(',')
  form.packagingFeeYuan = centsToYuan(spu.packagingFee)
  form.minPurchaseQty = spu.minPurchaseQty
  form.limitPerOrder = spu.limitPerOrder
  form.status = spu.status
  form.sortWeight = spu.sortWeight
}

/** 管理端编辑回显走 GET /admin/spus/{id}，草稿/待审/下架商品同样可读。 */
async function loadForEdit(id: number): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    applySpu(await getSpu(id))
  } catch (error) {
    loadError.value = errorMessage(error, '商品详情加载失败')
  } finally {
    loading.value = false
  }
}

function validate(): string | null {
  const categoryId = form.categoryId
  if (typeof categoryId !== 'number' || !categories.value.some((item) => item.id === categoryId)) {
    return '请选择二级品类'
  }

  const name = form.spuName.trim()
  if (!name) {
    return '商品名称不能为空'
  }
  if (name.length > 128) {
    return '商品名称最多 128 个字符'
  }
  if (form.subtitle.trim().length > 128) {
    return '副标题最多 128 个字符'
  }
  if (form.description.trim().length > 500) {
    return '商品描述最多 500 个字符'
  }

  const mainImage = form.mainImage.trim()
  if (mainImage.length > 512) {
    return '主图 URL 最多 512 个字符'
  }
  if (form.status === 2 && !mainImage) {
    return '上架状态必须填写主图'
  }

  const images = splitLines(form.imagesText)
  if (images.length > 9) {
    return '商品图最多 9 张'
  }
  if (images.some((item) => item.length > 512)) {
    return '单张商品图 URL 最多 512 个字符'
  }

  const tags = splitTags(form.tagsText)
  if (tags.length > 5) {
    return '标签最多 5 个'
  }
  if (tags.some((item) => item.length > 16)) {
    return '单个标签最多 16 个字符'
  }

  const packagingFee = yuanToFen(form.packagingFeeYuan)
  if (!Number.isInteger(packagingFee) || packagingFee < 0) {
    return '打包费必须是不小于 0 的金额'
  }
  if (!Number.isInteger(form.minPurchaseQty) || form.minPurchaseQty < 1) {
    return '起购数量必须是不小于 1 的整数'
  }
  if (!Number.isInteger(form.limitPerOrder) || form.limitPerOrder < 0) {
    return '每单限购必须是不小于 0 的整数'
  }
  if (!Number.isInteger(form.sortWeight)) {
    return '排序权重必须是整数'
  }
  if (![0, 1, 2, 3].includes(form.status)) {
    return '商品状态只能是 0 ~ 3'
  }
  return null
}

function buildPayload(): AdminSpuSavePayload {
  const images = splitLines(form.imagesText)
  const tags = splitTags(form.tagsText)
  return {
    categoryId: form.categoryId as number,
    spuName: form.spuName.trim(),
    subtitle: trimOrNull(form.subtitle),
    description: trimOrNull(form.description),
    detailHtml: trimOrNull(form.detailHtml),
    mainImage: trimOrNull(form.mainImage),
    images: images.length ? images : null,
    tags: tags.length ? tags : null,
    packagingFee: yuanToFen(form.packagingFeeYuan),
    minPurchaseQty: form.minPurchaseQty,
    limitPerOrder: form.limitPerOrder,
    status: form.status,
    sortWeight: form.sortWeight,
  }
}

async function submit(): Promise<void> {
  submitError.value = ''
  const validationError = validate()
  if (validationError !== null) {
    submitError.value = validationError
    ElMessage.warning(validationError)
    return
  }
  submitting.value = true
  try {
    const payload = buildPayload()
    if (editingId.value === null) {
      await createSpu(payload)
      ElMessage.success('商品已创建')
    } else {
      await updateSpu(editingId.value, payload)
      ElMessage.success('商品已更新')
    }
    await router.push({ name: 'admin-spu-list' })
  } catch (error) {
    submitError.value = errorMessage(error, '商品保存失败')
  } finally {
    submitting.value = false
  }
}

function goBack(): void {
  void router.push({ name: 'admin-spu-list' })
}

onMounted(async () => {
  await loadCategories()
  const rawId = route.params.id
  const idText = Array.isArray(rawId) ? rawId[0] : rawId
  const parsed = Number(idText)
  if (idText !== undefined && Number.isInteger(parsed) && parsed > 0) {
    editingId.value = parsed
    await loadForEdit(parsed)
  }
})
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>{{ isEdit ? '编辑商品' : '新建商品' }}</h2>
        <p>
          写入接口：<span class="mono">{{ isEdit ? 'PUT /api/v1/admin/spus/{id}' : 'POST /api/v1/admin/spus' }}</span>。
          品类必须选二级；<span class="mono">status=2 上架</span> 时后端强制要求主图。
          金额字段表单按元输入，提交前换算为分。
        </p>
      </div>
      <el-button :icon="ArrowLeft" @click="goBack">返回商品库</el-button>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card v-loading="loading" shadow="never">
      <el-alert
        v-if="categories.length === 0 && !loading"
        type="warning"
        :closable="false"
        show-icon
        title="没有可用的二级品类，先在上游配置品类后再新建商品。"
        class="form-alert"
      />

      <el-form label-width="120px" class="spu-form">
        <el-form-item label="商品名称" required>
          <el-input
            v-model="form.spuName"
            maxlength="128"
            show-word-limit
            placeholder="例如：招牌牛肉饭"
          />
        </el-form-item>

        <el-form-item label="二级品类" required>
          <el-select
            v-model="form.categoryId"
            filterable
            placeholder="选择二级品类"
            :disabled="categories.length === 0"
            class="category-select"
          >
            <el-option
              v-for="item in categories"
              :key="item.id"
              :label="item.label"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="副标题">
          <el-input
            v-model="form.subtitle"
            maxlength="128"
            show-word-limit
            placeholder="展示在商品名下方，可留空"
          />
        </el-form-item>

        <el-form-item label="商品描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="纯文本简介，最多 500 字"
          />
        </el-form-item>

        <el-form-item label="详情 HTML">
          <el-input
            v-model="form.detailHtml"
            type="textarea"
            :rows="5"
            placeholder="可留空；写入前由后端按富文本规则处理"
          />
        </el-form-item>

        <el-form-item label="主图 URL">
          <div class="url-field">
            <el-input
              v-model="form.mainImage"
              maxlength="512"
              show-word-limit
              placeholder="/api/v1/files/... 或允许的文件域名 URL"
            />
            <el-image
              v-if="mainImagePreview"
              :src="mainImagePreview"
              :preview-src-list="[mainImagePreview]"
              fit="cover"
              preview-teleported
              class="main-preview"
            />
          </div>
        </el-form-item>

        <el-form-item label="商品图 URL">
          <div class="url-field">
            <el-input
              v-model="form.imagesText"
              type="textarea"
              :rows="4"
              placeholder="每行一个 URL，最多 9 张"
            />
            <div v-if="imagesPreview.length" class="image-list">
              <el-image
                v-for="(url, index) in imagesPreview"
                :key="`${url}-${index}`"
                :src="url"
                :preview-src-list="imagesPreview"
                :initial-index="index"
                fit="cover"
                preview-teleported
                class="image-thumb"
              />
            </div>
          </div>
        </el-form-item>

        <el-form-item label="标签">
          <el-input
            v-model="form.tagsText"
            placeholder="逗号分隔，最多 5 个，单个不超过 16 字"
          />
        </el-form-item>

        <el-form-item label="打包费（元）">
          <el-input-number
            v-model="form.packagingFeeYuan"
            :min="0"
            :precision="2"
            :step="0.5"
            class="number-input"
          />
        </el-form-item>

        <el-form-item label="起购数量">
          <el-input-number
            v-model="form.minPurchaseQty"
            :min="1"
            :precision="0"
            :step="1"
            class="number-input"
          />
        </el-form-item>

        <el-form-item label="每单限购">
          <el-input-number
            v-model="form.limitPerOrder"
            :min="0"
            :precision="0"
            :step="1"
            class="number-input"
          />
          <span class="muted form-hint">0 表示不限购。</span>
        </el-form-item>

        <el-form-item label="商品状态">
          <el-select v-model="form.status" class="number-input">
            <el-option
              v-for="option in SPU_STATUS_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="排序权重">
          <el-input-number
            v-model="form.sortWeight"
            :precision="0"
            :step="1"
            class="number-input"
          />
        </el-form-item>

        <el-alert
          v-if="submitError"
          type="error"
          :closable="false"
          show-icon
          :title="submitError"
          class="form-alert"
        />

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">
            {{ isEdit ? '保存修改' : '创建商品' }}
          </el-button>
          <el-button @click="goBack">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.spu-form {
  max-width: 820px;
}

.form-alert {
  margin-bottom: 14px;
}

.category-select {
  width: 280px;
}

.number-input {
  width: 220px;
}

.form-hint {
  margin-left: 10px;
  font-size: 12px;
}

.url-field {
  width: 100%;
}

.main-preview {
  width: 88px;
  height: 88px;
  margin-top: 8px;
  border-radius: 8px;
  border: 1px solid var(--app-border);
}

.image-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.image-thumb {
  width: 64px;
  height: 64px;
  border-radius: 6px;
  border: 1px solid var(--app-border);
}
</style>
