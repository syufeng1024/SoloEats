<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createAppVersion,
  listAppVersions,
  updateAppVersion,
  type AppVersionView,
  type SaveAppVersionPayload,
} from '@/api/content-admin'
import {
  APP_TYPE,
  APP_TYPE_OPTIONS,
  CONTENT_STATUS,
  CONTENT_STATUS_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/contentStatus'
import { formatDateTime } from '@/utils/datetime'
import { errorMessage } from '@/views/report/helpers'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const rows = ref<AppVersionView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const appType = ref<number | undefined>(undefined)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const dialogError = ref('')

const form = reactive({
  appType: 1,
  versionName: '',
  versionCode: 1,
  updateDesc: '',
  downloadUrl: '',
  forceUpdate: 0,
  grayPercent: 0,
  status: 1,
  publishAt: '',
})

function toDateTimeInput(value: string | null | undefined): string {
  return value ? formatDateTime(value) : ''
}

function appTypeParam(): number | undefined {
  return typeof appType.value === 'number' ? appType.value : undefined
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listAppVersions({
      appType: appTypeParam(),
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    rows.value = result.list
    total.value = result.total
  } catch (error) {
    rows.value = []
    total.value = 0
    loadError.value = errorMessage(error, 'App 版本加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function reset(): void {
  appType.value = undefined
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

function openCreate(): void {
  editingId.value = null
  form.appType = 1
  form.versionName = ''
  form.versionCode = 1
  form.updateDesc = ''
  form.downloadUrl = ''
  form.forceUpdate = 0
  form.grayPercent = 0
  form.status = 1
  form.publishAt = ''
  dialogError.value = ''
  dialogVisible.value = true
}

function openEdit(row: AppVersionView): void {
  editingId.value = row.id
  form.appType = row.appType
  form.versionName = row.versionName
  form.versionCode = row.versionCode
  form.updateDesc = row.updateDesc ?? ''
  form.downloadUrl = row.downloadUrl
  form.forceUpdate = row.forceUpdate
  form.grayPercent = row.grayPercent
  form.status = row.status
  form.publishAt = toDateTimeInput(row.publishAt)
  dialogError.value = ''
  dialogVisible.value = true
}

function buildPayload(): SaveAppVersionPayload | null {
  if (form.appType < 1 || form.appType > 3) {
    dialogError.value = '平台只能是用户端 / 骑手端 / 商家端'
    return null
  }
  const versionName = form.versionName.trim()
  if (!versionName) {
    dialogError.value = '版本名称不能为空'
    return null
  }
  if (versionName.length > 32) {
    dialogError.value = '版本名称不能超过 32 个字符'
    return null
  }
  if (!Number.isInteger(form.versionCode) || form.versionCode < 1) {
    dialogError.value = '版本号必须是正整数'
    return null
  }
  const updateDesc = form.updateDesc.trim()
  if (updateDesc.length > 1000) {
    dialogError.value = '更新说明不能超过 1000 个字符'
    return null
  }
  const downloadUrl = form.downloadUrl.trim()
  if (!downloadUrl) {
    dialogError.value = '下载地址不能为空'
    return null
  }
  if (downloadUrl.length > 512) {
    dialogError.value = '下载地址不能超过 512 个字符'
    return null
  }
  if (!/^(https?:\/\/|\/(?!\/))/.test(downloadUrl)) {
    dialogError.value = '下载地址必须是 http(s) 地址或以 / 开头的站内路径'
    return null
  }
  if (!Number.isInteger(form.grayPercent) || form.grayPercent < 0 || form.grayPercent > 100) {
    dialogError.value = '灰度比例必须是 0~100 的整数'
    return null
  }
  return {
    appType: form.appType,
    versionName,
    versionCode: form.versionCode,
    updateDesc: updateDesc || null,
    downloadUrl,
    forceUpdate: form.forceUpdate,
    grayPercent: form.grayPercent,
    status: form.status,
    publishAt: form.publishAt || null,
  }
}

async function submitVersion(): Promise<void> {
  dialogError.value = ''
  const payload = buildPayload()
  if (!payload) {
    return
  }
  submitting.value = true
  try {
    if (editingId.value === null) {
      await createAppVersion(payload)
      ElMessage.success('版本已创建')
    } else {
      await updateAppVersion(editingId.value, payload)
      ElMessage.success('版本已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    // 90021「该端已存在相同版本号」的 message 原文保留在弹框内
    dialogError.value = errorMessage(error, '版本保存失败')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>App 版本</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/app-versions</span>。同一端的
          <span class="mono">versionCode</span> 唯一，重复时后端返回 90021。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建版本</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <div class="filter-bar">
        <el-select v-model="appType" clearable placeholder="平台" class="filter-select">
          <el-option
            v-for="item in APP_TYPE_OPTIONS"
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
        <el-table-column label="平台" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(APP_TYPE, row.appType)" size="small">
              {{ labelOf(APP_TYPE, row.appType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="versionName" label="版本名称" width="120" />
        <el-table-column prop="versionCode" label="版本号" width="100" />
        <el-table-column
          prop="downloadUrl"
          label="下载地址"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column label="强制更新" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.forceUpdate === 1 ? 'danger' : 'info'" size="small">
              {{ row.forceUpdate === 1 ? '强制' : '可选' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="灰度比例" width="100" align="center">
          <template #default="{ row }">{{ row.grayPercent }}%</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(CONTENT_STATUS, row.status)" size="small">
              {{ labelOf(CONTENT_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" min-width="170">
          <template #default="{ row }">
            {{ row.publishAt ? formatDateTime(row.publishAt) : '立即生效' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="updateDesc"
          label="更新说明"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无版本记录。" :image-size="80" />
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
      :title="editingId === null ? '新建版本' : '编辑版本'"
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
        <el-form-item label="平台" required>
          <el-select v-model="form.appType" class="full-width">
            <el-option
              v-for="item in APP_TYPE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="版本名称" required>
          <el-input v-model="form.versionName" maxlength="32" show-word-limit placeholder="如 1.2.0" />
        </el-form-item>
        <el-form-item label="版本号" required>
          <el-input-number v-model="form.versionCode" :min="1" :max="2147483647" />
          <div class="form-hint">单调递增整数；同一端不可重复。</div>
        </el-form-item>
        <el-form-item label="下载地址" required>
          <el-input v-model="form.downloadUrl" maxlength="512" placeholder="https://... 或 /download/app.apk" />
        </el-form-item>
        <el-form-item label="更新说明">
          <el-input
            v-model="form.updateDesc"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="强制更新">
          <el-switch v-model="form.forceUpdate" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="灰度比例">
          <el-input-number v-model="form.grayPercent" :min="0" :max="100" />
          <div class="form-hint">0~100；按 userId/deviceId 取模判定。</div>
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
        <el-form-item label="发布时间">
          <el-date-picker
            v-model="form.publishAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            class="full-width"
            placeholder="留空表示立即生效"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitVersion">
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

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
