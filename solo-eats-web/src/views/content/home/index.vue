<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createHomeFloor,
  deleteHomeFloor,
  getHomePage,
  previewHomePage,
  publishHomePage,
  sortHomeFloors,
  updateHomeFloor,
  type BannerView,
  type FloorView,
  type HomePageView,
  type SaveHomeFloorPayload,
} from '@/api/content-admin'
import {
  CONTENT_STATUS,
  CONTENT_STATUS_OPTIONS,
  FLOOR_AUDIENCE,
  FLOOR_AUDIENCE_OPTIONS,
  FLOOR_TYPE,
  FLOOR_TYPE_OPTIONS,
  HOME_PAGE_STATUS,
  JUMP_TYPE,
  labelOf,
  tagOf,
} from '@/constants/contentStatus'
import { formatDateTime } from '@/utils/datetime'
import { errorMessage } from '@/views/report/helpers'

const PAGE_CODES = [
  { value: 'USER_HOME', label: '用户首页 USER_HOME' },
  { value: 'USER_MINE', label: '我的页面 USER_MINE' },
]

const KNOWN_CONFIG_KEYS = [
  'bannerId',
  'bannerIds',
  'categoryId',
  'categoryIds',
  'shopId',
  'shopIds',
  'spuId',
  'spuIds',
  'couponTemplateId',
  'limit',
]

const loading = ref(false)
const loadError = ref('')
const actionError = ref('')
const publishError = ref('')
const publishing = ref(false)
const sorting = ref(false)
const page = ref<HomePageView | null>(null)
const pageCode = ref('USER_HOME')

const floors = computed(() => page.value?.floors ?? [])
const sortedFloors = computed(() =>
  [...floors.value].sort((a, b) => a.sort - b.sort || a.id - b.id),
)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const dialogError = ref('')
const editingConfig = ref<Record<string, unknown>>({})

const form = reactive({
  floorType: 1,
  title: '',
  sort: 0,
  status: 1,
  effectiveFrom: '',
  effectiveTo: '',
  audience: 0,
})

const configDraft = reactive({
  bannerIds: '',
  categoryIds: '',
  spuIds: '',
  couponTemplateId: 0,
  shopIds: '',
  limit: 6,
})

const previewVisible = ref(false)
const previewLoading = ref(false)
const previewError = ref('')
const previewPage = ref<HomePageView | null>(null)
const previewBanners = computed<BannerView[]>(() => previewPage.value?.banners ?? [])
const previewFloors = computed(() =>
  [...(previewPage.value?.floors ?? [])].sort((a, b) => a.sort - b.sort || a.id - b.id),
)

function nowDateTime(): string {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

function toDateTimeInput(value: string | null | undefined): string {
  return value ? formatDateTime(value) : ''
}

function idText(value: unknown): string {
  if (Array.isArray(value)) {
    return value.map((item) => String(item)).filter((item) => item.length > 0).join(',')
  }
  if (typeof value === 'number' || typeof value === 'string') {
    return String(value)
  }
  return ''
}

function positiveInt(value: unknown, fallback: number): number {
  const numeric = Number(value)
  return Number.isInteger(numeric) && numeric > 0 ? numeric : fallback
}

function floorReferenceText(row: FloorView): string {
  const config = row.config ?? {}
  const parts: string[] = []
  const bannerIds = idText(config.bannerIds ?? config.bannerId)
  if (bannerIds) parts.push(`Banner=${bannerIds}`)
  const categoryIds = idText(config.categoryIds ?? config.categoryId)
  if (categoryIds) parts.push(`品类=${categoryIds}`)
  const shopIds = idText(config.shopIds ?? config.shopId)
  if (shopIds) parts.push(`门店=${shopIds}`)
  const spuIds = idText(config.spuIds ?? config.spuId)
  if (spuIds) parts.push(`商品=${spuIds}`)
  const couponTemplateId = idText(config.couponTemplateId)
  if (couponTemplateId) parts.push(`券模板=${couponTemplateId}`)
  const limit = idText(config.limit)
  if (limit) parts.push(`条数=${limit}`)
  return parts.length > 0 ? parts.join('；') : '—'
}

function configText(row: FloorView): string {
  return JSON.stringify(row.config ?? {})
}

function aggregateText(row: FloorView): string {
  const parts: string[] = []
  if (row.banners && row.banners.length > 0) parts.push(`Banner=${row.banners.length}`)
  if (row.shops && row.shops.length > 0) parts.push(`门店=${row.shops.length}`)
  if (row.products && row.products.length > 0) parts.push(`商品=${row.products.length}`)
  if (row.notices && row.notices.length > 0) parts.push(`公告=${row.notices.length}`)
  return parts.length > 0 ? parts.join('；') : '—'
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  actionError.value = ''
  publishError.value = ''
  try {
    page.value = await getHomePage(pageCode.value)
  } catch (error) {
    page.value = null
    loadError.value = errorMessage(error, '首页配置加载失败')
  } finally {
    loading.value = false
  }
}

function reload(): void {
  void load()
}

async function openPreview(): Promise<void> {
  previewVisible.value = true
  previewLoading.value = true
  previewError.value = ''
  try {
    previewPage.value = await previewHomePage(pageCode.value)
  } catch (error) {
    previewPage.value = null
    previewError.value = errorMessage(error, '预览加载失败')
  } finally {
    previewLoading.value = false
  }
}

async function publish(): Promise<void> {
  publishError.value = ''
  actionError.value = ''
  const pageName = page.value?.pageName ?? pageCode.value
  try {
    await ElMessageBox.confirm(
      `确认发布「${pageName}」？发布前会校验启用楼层引用的品类/店铺/商品是否存在且可用，失败会列出具体原因。`,
      '发布首页',
      {
        confirmButtonText: '确认发布',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }
  publishing.value = true
  try {
    await publishHomePage(pageCode.value)
    ElMessage.success('首页已发布')
    await load()
  } catch (error) {
    // 90020 的具体引用错误由后端 message 原样返回
    publishError.value = errorMessage(error, '首页发布失败')
  } finally {
    publishing.value = false
  }
}

async function moveFloor(index: number, delta: number): Promise<void> {
  const list = sortedFloors.value
  const target = index + delta
  if (target < 0 || target >= list.length) {
    return
  }
  const ids = list.map((item) => item.id)
  const moved = ids[index]
  ids.splice(index, 1)
  ids.splice(target, 0, moved)
  sorting.value = true
  actionError.value = ''
  try {
    await sortHomeFloors(ids)
    ElMessage.success('楼层顺序已更新')
    await load()
  } catch (error) {
    actionError.value = errorMessage(error, '楼层排序失败')
  } finally {
    sorting.value = false
  }
}

function resetForm(): void {
  form.floorType = 1
  form.title = ''
  form.sort = 0
  form.status = 1
  form.effectiveFrom = nowDateTime()
  form.effectiveTo = ''
  form.audience = 0
  configDraft.bannerIds = ''
  configDraft.categoryIds = ''
  configDraft.spuIds = ''
  configDraft.couponTemplateId = 0
  configDraft.shopIds = ''
  configDraft.limit = 6
  editingConfig.value = {}
}

function openCreate(): void {
  editingId.value = null
  resetForm()
  form.sort = sortedFloors.value.length + 1
  dialogError.value = ''
  dialogVisible.value = true
}

function applyConfigToDraft(floorType: number, config: Record<string, unknown>): void {
  if (floorType === 1) {
    configDraft.bannerIds = idText(config.bannerIds ?? config.bannerId)
  } else if (floorType === 2) {
    configDraft.categoryIds = idText(config.categoryIds ?? config.categoryId)
  } else if (floorType === 3 || floorType === 6) {
    configDraft.spuIds = idText(config.spuIds ?? config.spuId)
  } else if (floorType === 4) {
    configDraft.couponTemplateId = positiveInt(config.couponTemplateId, 0)
  } else if (floorType === 5) {
    configDraft.shopIds = idText(config.shopIds ?? config.shopId)
  } else if (floorType === 7) {
    configDraft.limit = positiveInt(config.limit, 6)
  }
}

function openEdit(row: FloorView): void {
  editingId.value = row.id
  resetForm()
  form.floorType = row.floorType
  form.title = row.title
  form.sort = row.sort
  form.status = row.status
  form.effectiveFrom = toDateTimeInput(row.effectiveFrom)
  form.effectiveTo = toDateTimeInput(row.effectiveTo)
  form.audience = row.audience
  editingConfig.value = row.config ? { ...row.config } : {}
  applyConfigToDraft(row.floorType, editingConfig.value)
  dialogError.value = ''
  dialogVisible.value = true
}

function onFloorTypeChange(): void {
  configDraft.bannerIds = ''
  configDraft.categoryIds = ''
  configDraft.spuIds = ''
  configDraft.couponTemplateId = 0
  configDraft.shopIds = ''
  configDraft.limit = 6
}

function parseIdList(text: string): number[] | null {
  const parts = text
    .split(/[,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean)
  const ids: number[] = []
  for (const part of parts) {
    if (!/^\d+$/.test(part)) {
      return null
    }
    const id = Number(part)
    if (!Number.isInteger(id) || id <= 0) {
      return null
    }
    if (!ids.includes(id)) {
      ids.push(id)
    }
  }
  return ids
}

function buildPayload(): SaveHomeFloorPayload | null {
  const title = form.title.trim()
  if (!title) {
    dialogError.value = '楼层标题不能为空'
    return null
  }
  if (title.length > 64) {
    dialogError.value = '楼层标题不能超过 64 个字符'
    return null
  }
  if (!Number.isInteger(form.sort) || form.sort < 0) {
    dialogError.value = '排序必须是不小于 0 的整数'
    return null
  }
  if (!form.effectiveFrom) {
    dialogError.value = '请选择生效开始时间'
    return null
  }
  if (form.effectiveTo && form.effectiveTo <= form.effectiveFrom) {
    dialogError.value = '生效结束时间必须晚于开始时间'
    return null
  }

  const config: Record<string, unknown> = { ...editingConfig.value }
  for (const key of KNOWN_CONFIG_KEYS) {
    delete config[key]
  }

  if (form.floorType === 1) {
    const ids = parseIdList(configDraft.bannerIds)
    if (ids === null) {
      dialogError.value = 'Banner ID 必须是正整数，多个用逗号分隔'
      return null
    }
    if (ids.length > 0) config.bannerIds = ids
  } else if (form.floorType === 2) {
    const ids = parseIdList(configDraft.categoryIds)
    if (ids === null) {
      dialogError.value = '品类 ID 必须是正整数，多个用逗号分隔'
      return null
    }
    if (ids.length === 0) {
      dialogError.value = '金刚区至少填写一个品类 ID'
      return null
    }
    config.categoryIds = ids
  } else if (form.floorType === 3) {
    const ids = parseIdList(configDraft.spuIds)
    if (ids === null) {
      dialogError.value = '商品 ID 必须是正整数，多个用逗号分隔'
      return null
    }
    if (ids.length === 0) {
      dialogError.value = '秒杀楼层至少填写一个商品 ID'
      return null
    }
    config.spuIds = ids
  } else if (form.floorType === 4) {
    if (!Number.isInteger(configDraft.couponTemplateId) || configDraft.couponTemplateId < 1) {
      dialogError.value = '神券楼层必须填写正整数券模板 ID'
      return null
    }
    config.couponTemplateId = configDraft.couponTemplateId
  } else if (form.floorType === 5) {
    const ids = parseIdList(configDraft.shopIds)
    if (ids === null) {
      dialogError.value = '门店 ID 必须是正整数，多个用逗号分隔'
      return null
    }
    if (ids.length === 0) {
      dialogError.value = '推荐店铺楼层至少填写一个门店 ID'
      return null
    }
    config.shopIds = ids
  } else if (form.floorType === 6) {
    const ids = parseIdList(configDraft.spuIds)
    if (ids === null) {
      dialogError.value = '商品 ID 必须是正整数，多个用逗号分隔'
      return null
    }
    if (ids.length === 0) {
      dialogError.value = '猜你喜欢楼层至少填写一个商品 ID'
      return null
    }
    config.spuIds = ids
  } else if (form.floorType === 7) {
    if (!Number.isInteger(configDraft.limit) || configDraft.limit < 1 || configDraft.limit > 20) {
      dialogError.value = '公告条数必须是 1~20 的整数'
      return null
    }
    config.limit = configDraft.limit
  } else {
    dialogError.value = '楼层类型只能是 1~7'
    return null
  }

  return {
    pageCode: pageCode.value,
    floorType: form.floorType,
    title,
    config,
    sort: form.sort,
    effectiveFrom: form.effectiveFrom,
    effectiveTo: form.effectiveTo || null,
    audience: form.audience,
    status: form.status,
  }
}

async function submitFloor(): Promise<void> {
  dialogError.value = ''
  const payload = buildPayload()
  if (!payload) {
    return
  }
  submitting.value = true
  try {
    if (editingId.value === null) {
      await createHomeFloor(payload)
      ElMessage.success('楼层已创建')
    } else {
      await updateHomeFloor(editingId.value, payload)
      ElMessage.success('楼层已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    dialogError.value = errorMessage(error, '楼层保存失败')
  } finally {
    submitting.value = false
  }
}

async function confirmDelete(row: FloorView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认删除楼层「${row.title}」？若页面已发布，后端会先把页面下线。`,
      '删除楼层',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }
  actionError.value = ''
  try {
    await deleteHomeFloor(row.id)
    ElMessage.success('楼层已删除')
    await load()
  } catch (error) {
    actionError.value = errorMessage(error, '楼层删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>首页装修</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/content/home</span>。楼层按 sort 升序展示，
          发布时校验启用楼层的品类/店铺/商品引用。
        </p>
      </div>
      <div class="toolbar">
        <el-select v-model="pageCode" class="page-select" @change="reload">
          <el-option
            v-for="item in PAGE_CODES"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button @click="openPreview">预览</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增楼层</el-button>
        <el-button type="warning" :loading="publishing" @click="publish">发布</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />
    <el-alert v-if="actionError" type="error" :closable="false" show-icon :title="actionError" />
    <el-alert
      v-if="publishError"
      type="error"
      :closable="false"
      show-icon
      :title="publishError"
    />

    <el-card shadow="never">
      <div v-if="page" class="page-summary">
        <span>页面：{{ page.pageName }}（<span class="mono">{{ page.pageCode }}</span>）</span>
        <el-tag :type="tagOf(HOME_PAGE_STATUS, page.status)" size="small">
          {{ labelOf(HOME_PAGE_STATUS, page.status) }}
        </el-tag>
        <span>版本：{{ page.version }}</span>
        <span>最近发布：{{ page.publishAt ? formatDateTime(page.publishAt) : '—' }}</span>
        <span>楼层数：{{ page.floors.length }}</span>
      </div>

      <el-table
        v-loading="loading"
        :data="sortedFloors"
        border
        stripe
        row-key="id"
      >
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="楼层类型" width="110">
          <template #default="{ row }">
            <el-tag :type="tagOf(FLOOR_TYPE, row.floorType)" size="small">
              {{ labelOf(FLOOR_TYPE, row.floorType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="140" />
        <el-table-column label="引用对象" min-width="200">
          <template #default="{ row }">
            <span class="mono">{{ floorReferenceText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(CONTENT_STATUS, row.status)" size="small">
              {{ labelOf(CONTENT_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="投放人群" width="100" align="center">
          <template #default="{ row }">{{ labelOf(FLOOR_AUDIENCE, row.audience) }}</template>
        </el-table-column>
        <el-table-column label="生效时间" min-width="220">
          <template #default="{ row }">
            {{ formatDateTime(row.effectiveFrom) }} ~
            {{ row.effectiveTo ? formatDateTime(row.effectiveTo) : '长期' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row, $index }">
            <el-button
              link
              type="primary"
              :disabled="$index === 0 || sorting"
              @click="moveFloor($index, -1)"
            >
              上移
            </el-button>
            <el-button
              link
              type="primary"
              :disabled="$index === sortedFloors.length - 1 || sorting"
              @click="moveFloor($index, 1)"
            >
              下移
            </el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="confirmDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无楼层，先新增一个。" :image-size="80" />
        </template>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新增楼层' : '编辑楼层'"
      width="680px"
      :close-on-click-modal="false"
    >
      <el-alert
        v-if="dialogError"
        class="dialog-alert"
        type="error"
        :closable="false"
        show-icon
        :title="dialogError"
      />
      <el-form label-width="110px">
        <el-form-item label="楼层类型" required>
          <el-select v-model="form.floorType" class="full-width" @change="onFloorTypeChange">
            <el-option
              v-for="item in FLOOR_TYPE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="楼层标题" required>
          <el-input v-model="form.title" maxlength="64" show-word-limit />
        </el-form-item>

        <el-form-item v-if="form.floorType === 1" label="Banner ID">
          <el-input v-model="configDraft.bannerIds" placeholder="如 1,2,3；可留空" />
          <div class="form-hint">
            轮播楼层实际下发广告位 HOME_TOP 的启用 Banner；bannerIds 仅作配置记录，发布不校验。引用对象需已存在。
          </div>
        </el-form-item>
        <el-form-item v-else-if="form.floorType === 2" label="品类 ID" required>
          <el-input v-model="configDraft.categoryIds" placeholder="如 1,2,3" />
          <div class="form-hint">引用对象需已存在；发布时校验品类存在且启用。</div>
        </el-form-item>
        <el-form-item v-else-if="form.floorType === 3" label="商品 ID" required>
          <el-input v-model="configDraft.spuIds" placeholder="如 1,2,3" />
          <div class="form-hint">引用对象需已存在；发布时校验 SPU 存在。</div>
        </el-form-item>
        <el-form-item v-else-if="form.floorType === 4" label="券模板 ID" required>
          <el-input-number v-model="configDraft.couponTemplateId" :min="0" :max="999999999" />
          <div class="form-hint">引用对象需已存在；发布时不校验券模板。</div>
        </el-form-item>
        <el-form-item v-else-if="form.floorType === 5" label="门店 ID" required>
          <el-input v-model="configDraft.shopIds" placeholder="如 1,2,3" />
          <div class="form-hint">引用对象需已存在；发布时校验门店存在且可接单。</div>
        </el-form-item>
        <el-form-item v-else-if="form.floorType === 6" label="商品 ID" required>
          <el-input v-model="configDraft.spuIds" placeholder="如 1,2,3" />
          <div class="form-hint">引用对象需已存在；发布时校验 SPU 存在。</div>
        </el-form-item>
        <el-form-item v-else-if="form.floorType === 7" label="公告条数" required>
          <el-input-number v-model="configDraft.limit" :min="1" :max="20" />
          <div class="form-hint">公告条数量 1~20。</div>
        </el-form-item>

        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="投放人群">
          <el-select v-model="form.audience" class="full-width">
            <el-option
              v-for="item in FLOOR_AUDIENCE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" class="full-width">
            <el-option
              v-for="item in CONTENT_STATUS_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="生效开始" required>
          <el-date-picker
            v-model="form.effectiveFrom"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="生效结束">
          <el-date-picker
            v-model="form.effectiveTo"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            class="full-width"
            placeholder="留空表示长期"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitFloor">
          {{ editingId === null ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="previewVisible"
      title="首页预览"
      width="980px"
      :close-on-click-modal="false"
    >
      <div v-loading="previewLoading" class="preview-body">
        <el-alert
          v-if="previewError"
          type="error"
          :closable="false"
          show-icon
          :title="previewError"
        />
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="预览仅返回启用楼层；以下按 sort 升序展平展示配置与聚合结果，不渲染真实 UI。"
        />

        <template v-if="previewPage">
          <el-descriptions :column="3" border size="small" class="preview-desc">
            <el-descriptions-item label="页面编码">
              <span class="mono">{{ previewPage.pageCode }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="页面名称">{{ previewPage.pageName }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="tagOf(HOME_PAGE_STATUS, previewPage.status)" size="small">
                {{ labelOf(HOME_PAGE_STATUS, previewPage.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="版本">{{ previewPage.version }}</el-descriptions-item>
            <el-descriptions-item label="最近发布">
              {{ previewPage.publishAt ? formatDateTime(previewPage.publishAt) : '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="启用楼层数">
              {{ previewPage.floors.length }}
            </el-descriptions-item>
          </el-descriptions>

          <h4 class="preview-title">Banner</h4>
          <el-table v-if="previewBanners.length > 0" :data="previewBanners" border size="small">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="title" label="标题" min-width="150" />
            <el-table-column prop="imageUrl" label="图片地址" min-width="220" show-overflow-tooltip />
            <el-table-column label="跳转" min-width="150">
              <template #default="{ row }">
                {{ labelOf(JUMP_TYPE, row.jumpType) }}
                <span v-if="row.jumpValue" class="mono"> / {{ row.jumpValue }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="sort" label="排序" width="70" />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="tagOf(CONTENT_STATUS, row.status)" size="small">
                  {{ labelOf(CONTENT_STATUS, row.status) }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="无 Banner" :image-size="60" />

          <h4 class="preview-title">楼层</h4>
          <el-table :data="previewFloors" border size="small">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">
                <el-tag :type="tagOf(FLOOR_TYPE, row.floorType)" size="small">
                  {{ labelOf(FLOOR_TYPE, row.floorType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="130" />
            <el-table-column prop="sort" label="排序" width="70" />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="tagOf(CONTENT_STATUS, row.status)" size="small">
                  {{ labelOf(CONTENT_STATUS, row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="配置" min-width="260">
              <template #default="{ row }">
                <span class="mono preview-config">{{ configText(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="聚合数据" min-width="180">
              <template #default="{ row }">{{ aggregateText(row) }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-select {
  width: 200px;
}

.page-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.full-width {
  width: 100%;
}

.form-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
  margin-top: 4px;
}

.dialog-alert {
  margin-bottom: 12px;
}

.preview-body {
  min-height: 120px;
}

.preview-desc {
  margin-top: 12px;
}

.preview-title {
  margin: 16px 0 8px;
  font-size: 14px;
}

.preview-config {
  word-break: break-all;
  font-size: 12px;
}
</style>
