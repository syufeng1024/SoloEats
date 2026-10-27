<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createBanner,
  deleteBanner,
  listBanners,
  updateBanner,
  type BannerView,
  type SaveBannerPayload,
} from '@/api/content-admin'
import {
  BANNER_SLOTS,
  CONTENT_STATUS,
  CONTENT_STATUS_OPTIONS,
  JUMP_TYPE,
  JUMP_TYPE_OPTIONS,
  bannerSlotLabel,
  labelOf,
  tagOf,
} from '@/constants/contentStatus'
import { formatDateTime } from '@/utils/datetime'
import { errorMessage } from '@/views/report/helpers'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const actionError = ref('')
const rows = ref<BannerView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const slotCode = ref('')
const status = ref<number | undefined>(undefined)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const dialogError = ref('')

const form = reactive({
  slotCode: 'HOME_TOP',
  title: '',
  imageUrl: '',
  jumpType: 1,
  jumpValue: '',
  sort: 0,
  effectiveFrom: '',
  effectiveTo: '',
  status: 1,
})

function nowDateTime(): string {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

function toDateTimeInput(value: string | null | undefined): string {
  return value ? formatDateTime(value) : ''
}

function statusParam(): number | undefined {
  return typeof status.value === 'number' ? status.value : undefined
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listBanners({
      slotCode: slotCode.value || undefined,
      status: statusParam(),
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    rows.value = result.list
    total.value = result.total
  } catch (error) {
    rows.value = []
    total.value = 0
    loadError.value = errorMessage(error, '广告位加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function reset(): void {
  slotCode.value = ''
  status.value = undefined
  pageNum.value = 1
  void load()
}

function handlePageChange(page: number): void {
  pageNum.value = page
  void load()
}

function handleSizeChange(size: number): void {
  pageSize.value = size
  pageNum.value = 1
  void load()
}

function resetForm(): void {
  form.slotCode = 'HOME_TOP'
  form.title = ''
  form.imageUrl = ''
  form.jumpType = 1
  form.jumpValue = ''
  form.sort = 0
  form.effectiveFrom = nowDateTime()
  form.effectiveTo = ''
  form.status = 1
}

function openCreate(): void {
  editingId.value = null
  resetForm()
  dialogError.value = ''
  dialogVisible.value = true
}

function openEdit(row: BannerView): void {
  editingId.value = row.id
  form.slotCode = row.slotCode
  form.title = row.title
  form.imageUrl = row.imageUrl
  form.jumpType = row.jumpType
  form.jumpValue = row.jumpValue ?? ''
  form.sort = row.sort
  form.effectiveFrom = toDateTimeInput(row.effectiveFrom)
  form.effectiveTo = toDateTimeInput(row.effectiveTo)
  form.status = row.status
  dialogError.value = ''
  dialogVisible.value = true
}

function onJumpTypeChange(): void {
  if (form.jumpType === 5) {
    form.jumpValue = ''
  }
}

function buildPayload(): SaveBannerPayload | null {
  const slot = form.slotCode.trim()
  if (!slot) {
    dialogError.value = '请选择广告位'
    return null
  }
  const title = form.title.trim()
  if (!title) {
    dialogError.value = '广告标题不能为空'
    return null
  }
  if (title.length > 128) {
    dialogError.value = '广告标题不能超过 128 个字符'
    return null
  }
  const imageUrl = form.imageUrl.trim()
  if (!imageUrl) {
    dialogError.value = '图片地址不能为空'
    return null
  }
  if (imageUrl.length > 512) {
    dialogError.value = '图片地址不能超过 512 个字符'
    return null
  }
  if (!/^(\/api\/v1\/files\/|https?:\/\/)/.test(imageUrl)) {
    dialogError.value = '图片地址必须是 /api/v1/files/ 前缀或 http(s) 地址'
    return null
  }
  if (form.jumpType < 1 || form.jumpType > 5) {
    dialogError.value = '跳转类型只能是 1~5'
    return null
  }
  const jumpValue = form.jumpValue.trim()
  if (form.jumpType !== 5 && !jumpValue) {
    dialogError.value = '非“无跳转”类型必须填写跳转目标'
    return null
  }
  if (jumpValue.length > 255) {
    dialogError.value = '跳转目标不能超过 255 个字符'
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
  return {
    slotCode: slot,
    title,
    imageUrl,
    jumpType: form.jumpType,
    jumpValue: form.jumpType === 5 ? null : jumpValue,
    sort: form.sort,
    effectiveFrom: form.effectiveFrom,
    effectiveTo: form.effectiveTo || null,
    status: form.status,
  }
}

async function submitBanner(): Promise<void> {
  dialogError.value = ''
  const payload = buildPayload()
  if (!payload) {
    return
  }
  submitting.value = true
  try {
    if (editingId.value === null) {
      await createBanner(payload)
      ElMessage.success('广告已创建')
    } else {
      await updateBanner(editingId.value, payload)
      ElMessage.success('广告已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    dialogError.value = errorMessage(error, '广告保存失败')
  } finally {
    submitting.value = false
  }
}

async function confirmDelete(row: BannerView): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除广告「${row.title}」？`, '删除广告', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  actionError.value = ''
  try {
    await deleteBanner(row.id)
    ElMessage.success('广告已删除')
    await load()
  } catch (error) {
    actionError.value = errorMessage(error, '广告删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>广告位管理</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/banners</span>。图片地址支持
          <span class="mono">/api/v1/files/</span> 前缀或 http(s) 地址。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建广告</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />
    <el-alert v-if="actionError" type="error" :closable="false" show-icon :title="actionError" />

    <el-card shadow="never">
      <div class="filter-bar">
        <el-select v-model="slotCode" clearable placeholder="广告位" class="filter-select">
          <el-option
            v-for="item in BANNER_SLOTS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select v-model="status" clearable placeholder="状态" class="filter-select">
          <el-option
            v-for="item in CONTENT_STATUS_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="id">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column label="图片" width="110">
          <template #default="{ row }">
            <el-image
              v-if="row.imageUrl"
              :src="row.imageUrl"
              :preview-src-list="[row.imageUrl]"
              preview-teleported
              fit="cover"
              class="banner-image"
            />
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="广告位" width="110">
          <template #default="{ row }">{{ bannerSlotLabel(row.slotCode) }}</template>
        </el-table-column>
        <el-table-column label="跳转" min-width="180">
          <template #default="{ row }">
            {{ labelOf(JUMP_TYPE, row.jumpType) }}
            <span v-if="row.jumpValue" class="mono"> / {{ row.jumpValue }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(CONTENT_STATUS, row.status)" size="small">
              {{ labelOf(CONTENT_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效时间" min-width="220">
          <template #default="{ row }">
            {{ formatDateTime(row.effectiveFrom) }} ~
            {{ row.effectiveTo ? formatDateTime(row.effectiveTo) : '长期' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="confirmDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无广告，先新建一条。" :image-size="80" />
        </template>
      </el-table>

      <el-pagination
        class="pagination"
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :page-sizes="PAGE_SIZE_OPTIONS"
        :current-page="pageNum"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新建广告' : '编辑广告'"
      width="620px"
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
      <el-form label-width="100px">
        <el-form-item label="广告位" required>
          <el-select v-model="form.slotCode" class="full-width">
            <el-option
              v-for="item in BANNER_SLOTS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="图片地址" required>
          <el-input
            v-model="form.imageUrl"
            maxlength="512"
            placeholder="/api/v1/files/... 或 https://..."
          />
          <el-image
            v-if="form.imageUrl.trim()"
            :src="form.imageUrl.trim()"
            fit="cover"
            class="form-image-preview"
          />
        </el-form-item>
        <el-form-item label="跳转类型" required>
          <el-select v-model="form.jumpType" class="full-width" @change="onJumpTypeChange">
            <el-option
              v-for="item in JUMP_TYPE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="跳转目标">
          <el-input
            v-model="form.jumpValue"
            maxlength="255"
            :disabled="form.jumpType === 5"
            :placeholder="form.jumpType === 5 ? '无跳转' : '商品/店铺/活动 ID 或页面路径'"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" />
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
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitBanner">
          {{ editingId === null ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.filter-select {
  width: 160px;
}

.banner-image {
  width: 84px;
  height: 48px;
  border-radius: 4px;
}

.form-image-preview {
  width: 120px;
  height: 68px;
  margin-top: 8px;
  border-radius: 4px;
}

.full-width {
  width: 100%;
}

.dialog-alert {
  margin-bottom: 12px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
