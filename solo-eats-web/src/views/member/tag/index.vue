<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createMemberTag,
  listMemberTags,
  updateMemberTag,
  type SaveTagPayload,
  type TagView,
} from '@/api/member-admin'
import {
  TAG_AUTO_FLAG,
  TAG_AUTO_FLAG_OPTIONS,
  TAG_STATUS,
  TAG_STATUS_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/memberStatus'
import { errorMessage } from '@/views/report/helpers'

/**
 * 标签定义维护。后端 GET 不分页，一次返回全部（按 id 升序），故本页不做分页。
 * 没有 DELETE 接口：停用通过 status=1 表达。
 */
const loading = ref(false)
const loadError = ref('')
const tags = ref<TagView[]>([])

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)

const form = reactive({
  name: '',
  ruleDesc: '',
  autoFlag: 0,
  status: 0,
})

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    tags.value = await listMemberTags()
  } catch (error) {
    tags.value = []
    loadError.value = errorMessage(error, '标签列表加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editingId.value = null
  Object.assign(form, { name: '', ruleDesc: '', autoFlag: 0, status: 0 })
  dialogVisible.value = true
}

function openEdit(row: TagView): void {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    ruleDesc: row.ruleDesc ?? '',
    autoFlag: row.autoFlag,
    status: row.status,
  })
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  const name = form.name.trim()
  if (!name) {
    ElMessage.warning('标签名称不能为空')
    return
  }
  if (name.length > 64) {
    ElMessage.warning('标签名称最长 64 个字符')
    return
  }
  const ruleDesc = form.ruleDesc.trim()
  if (ruleDesc.length > 255) {
    ElMessage.warning('分群规则最长 255 个字符')
    return
  }

  const payload: SaveTagPayload = {
    name,
    ruleDesc: ruleDesc || null,
    autoFlag: form.autoFlag,
    status: form.status,
  }

  submitting.value = true
  try {
    if (editingId.value === null) {
      await createMemberTag(payload)
      ElMessage.success('标签已创建')
    } else {
      await updateMemberTag(editingId.value, payload)
      ElMessage.success('标签已更新')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
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
        <h2>会员标签</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/member-tags</span>，后端不分页。
          <span class="mono">TagView</span> 只有名称、规则、自动标记、状态与覆盖人数，没有颜色与创建时间字段；
          后端未提供 DELETE 接口，停用请把状态改为「停用」。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建标签</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <el-table v-loading="loading" :data="tags" border stripe row-key="id">
        <el-table-column label="标签 ID" width="90">
          <template #default="{ row }">
            <span class="mono">{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="标签名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="分群规则" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ruleDesc || '—' }}</template>
        </el-table-column>
        <el-table-column label="来源" width="90">
          <template #default="{ row }">
            <el-tag :type="tagOf(TAG_AUTO_FLAG, row.autoFlag)" size="small" effect="plain">
              {{ labelOf(TAG_AUTO_FLAG, row.autoFlag) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="tagOf(TAG_STATUS, row.status)" size="small">
              {{ labelOf(TAG_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="覆盖人数" width="110" align="right">
          <template #default="{ row }">{{ row.memberCount }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无标签，先新建一个。" :image-size="80" />
        </template>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新建标签' : '编辑标签'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form label-width="90px">
        <el-form-item label="标签名称" required>
          <el-input
            v-model="form.name"
            maxlength="64"
            show-word-limit
            placeholder="标签名全局唯一，重复会被后端拒绝"
          />
        </el-form-item>
        <el-form-item label="分群规则">
          <el-input
            v-model="form.ruleDesc"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="选填，例如：近 30 天下单 ≥ 3 次。自动打标执行逻辑后端尚未实现"
          />
        </el-form-item>
        <el-form-item label="来源" required>
          <el-radio-group v-model="form.autoFlag">
            <el-radio
              v-for="option in TAG_AUTO_FLAG_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态" required>
          <el-radio-group v-model="form.status">
            <el-radio
              v-for="option in TAG_STATUS_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">
          {{ editingId === null ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-lead .mono {
  word-break: break-all;
}
</style>
