<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createMessageTemplate,
  listMessageTemplates,
  updateMessageTemplate,
  type MessageTemplateView,
  type SaveMessageTemplatePayload,
} from '@/api/content-admin'
import {
  MESSAGE_CHANNELS,
  MESSAGE_TEMPLATE_STATUS,
  MESSAGE_TEMPLATE_STATUS_OPTIONS,
  MESSAGE_TEMPLATE_TYPE,
  MESSAGE_TEMPLATE_TYPE_OPTIONS,
  formatChannels,
  labelOf,
  tagOf,
} from '@/constants/contentStatus'
import { debounce } from '@/utils/debounce'
import { formatDateTime } from '@/utils/datetime'
import { errorMessage } from '@/views/report/helpers'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const loading = ref(false)
const loadError = ref('')
const rows = ref<MessageTemplateView[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const appliedKeyword = ref('')
const templateType = ref<number | undefined>(undefined)
const status = ref<number | undefined>(undefined)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const dialogError = ref('')

const form = reactive({
  templateCode: '',
  templateName: '',
  templateType: 1,
  titleTpl: '',
  contentTpl: '',
  channels: ['SITE', 'WS'] as string[],
  status: 0,
})

function statusParam(): number | undefined {
  return typeof status.value === 'number' ? status.value : undefined
}

function templateTypeParam(): number | undefined {
  return typeof templateType.value === 'number' ? templateType.value : undefined
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await listMessageTemplates({
      keyword: appliedKeyword.value || undefined,
      templateType: templateTypeParam(),
      status: statusParam(),
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    rows.value = result.list
    total.value = result.total
  } catch (error) {
    rows.value = []
    total.value = 0
    loadError.value = errorMessage(error, '消息模板加载失败')
  } finally {
    loading.value = false
  }
}

const applyKeyword = debounce((value: string) => {
  appliedKeyword.value = value.trim()
  pageNum.value = 1
  void load()
}, 300)

watch(keyword, (value) => {
  applyKeyword(value)
})

function search(): void {
  appliedKeyword.value = keyword.value.trim()
  pageNum.value = 1
  void load()
}

function reset(): void {
  keyword.value = ''
  appliedKeyword.value = ''
  templateType.value = undefined
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

function openCreate(): void {
  editingId.value = null
  form.templateCode = ''
  form.templateName = ''
  form.templateType = 1
  form.titleTpl = ''
  form.contentTpl = ''
  form.channels = ['SITE', 'WS']
  form.status = 0
  dialogError.value = ''
  dialogVisible.value = true
}

function openEdit(row: MessageTemplateView): void {
  editingId.value = row.id
  form.templateCode = row.templateCode
  form.templateName = row.templateName
  form.templateType = row.templateType
  form.titleTpl = row.titleTpl
  form.contentTpl = row.contentTpl
  form.channels = row.channels
    .split(',')
    .map((item) => item.trim().toUpperCase())
    .filter(Boolean)
  form.status = row.status
  dialogError.value = ''
  dialogVisible.value = true
}

function buildPayload(): SaveMessageTemplatePayload | null {
  const templateCode = form.templateCode.trim().toUpperCase()
  if (!templateCode) {
    dialogError.value = '模板编码不能为空'
    return null
  }
  if (templateCode.length > 64) {
    dialogError.value = '模板编码不能超过 64 个字符'
    return null
  }
  const templateName = form.templateName.trim()
  if (!templateName) {
    dialogError.value = '模板名称不能为空'
    return null
  }
  if (templateName.length > 100) {
    dialogError.value = '模板名称不能超过 100 个字符'
    return null
  }
  if (form.templateType < 1 || form.templateType > 4) {
    dialogError.value = '模板类型只能是 1订单 / 2营销 / 3系统 / 4客服'
    return null
  }
  const titleTpl = form.titleTpl.trim()
  if (!titleTpl) {
    dialogError.value = '标题模板不能为空'
    return null
  }
  if (titleTpl.length > 200) {
    dialogError.value = '标题模板不能超过 200 个字符'
    return null
  }
  const contentTpl = form.contentTpl.trim()
  if (!contentTpl) {
    dialogError.value = '内容模板不能为空'
    return null
  }
  if (contentTpl.length > 1000) {
    dialogError.value = '内容模板不能超过 1000 个字符'
    return null
  }
  if (form.channels.length === 0) {
    dialogError.value = '至少选择一个下发通道'
    return null
  }
  return {
    templateCode,
    templateName,
    templateType: form.templateType,
    titleTpl,
    contentTpl,
    channels: form.channels.join(','),
    status: form.status,
  }
}

async function submitTemplate(): Promise<void> {
  dialogError.value = ''
  const payload = buildPayload()
  if (!payload) {
    return
  }
  submitting.value = true
  try {
    if (editingId.value === null) {
      await createMessageTemplate(payload)
      ElMessage.success('模板已创建')
    } else {
      await updateMessageTemplate(editingId.value, payload)
      ElMessage.success('模板已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    dialogError.value = errorMessage(error, '模板保存失败')
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
        <h2>消息模板</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/message-templates</span>。标题与内容支持
          <span class="mono">{key}</span> 占位，由应用层字符串替换，不引入模板引擎。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建模板</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <div class="filter-bar">
        <el-input
          v-model="keyword"
          clearable
          placeholder="模板编码 / 名称"
          class="keyword-input"
          @keyup.enter="search"
          @clear="search"
        />
        <el-select v-model="templateType" clearable placeholder="模板类型" class="filter-select">
          <el-option
            v-for="item in MESSAGE_TEMPLATE_TYPE_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select v-model="status" clearable placeholder="状态" class="filter-select">
          <el-option
            v-for="item in MESSAGE_TEMPLATE_STATUS_OPTIONS"
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
        <el-table-column prop="templateCode" label="模板编码" min-width="160" />
        <el-table-column prop="templateName" label="模板名称" min-width="140" />
        <el-table-column label="场景" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(MESSAGE_TEMPLATE_TYPE, row.templateType)" size="small">
              {{ labelOf(MESSAGE_TEMPLATE_TYPE, row.templateType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="titleTpl" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="contentTpl" label="内容" min-width="240" show-overflow-tooltip />
        <el-table-column label="渠道" min-width="160">
          <template #default="{ row }">{{ formatChannels(row.channels) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(MESSAGE_TEMPLATE_STATUS, row.status)" size="small">
              {{ labelOf(MESSAGE_TEMPLATE_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无消息模板。" :image-size="80" />
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
      :title="editingId === null ? '新建模板' : '编辑模板'"
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
      <el-form label-width="100px">
        <el-form-item label="模板编码" required>
          <el-input
            v-model="form.templateCode"
            maxlength="64"
            show-word-limit
            placeholder="如 ORDER_PAID"
          />
        </el-form-item>
        <el-form-item label="模板名称" required>
          <el-input v-model="form.templateName" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="模板类型" required>
          <el-select v-model="form.templateType" class="full-width">
            <el-option
              v-for="item in MESSAGE_TEMPLATE_TYPE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标题模板" required>
          <el-input v-model="form.titleTpl" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="内容模板" required>
          <el-input
            v-model="form.contentTpl"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
          />
          <div class="form-hint">
            支持 {key} 占位，key 仅限字母/数字/下划线，例如 {orderNo}、{amount}；缺少值时保留原样。
          </div>
        </el-form-item>
        <el-form-item label="下发通道" required>
          <el-select v-model="form.channels" multiple class="full-width">
            <el-option
              v-for="item in MESSAGE_CHANNELS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" class="full-width">
            <el-option
              v-for="item in MESSAGE_TEMPLATE_STATUS_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <div class="form-hint">注意：消息模板 0 启用、1 停用，与内容表相反。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitTemplate">
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

.keyword-input {
  width: 220px;
}

.filter-select {
  width: 150px;
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
