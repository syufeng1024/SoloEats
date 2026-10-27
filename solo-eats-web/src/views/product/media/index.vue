<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  addMediaItem,
  createMediaGroup,
  deleteMediaGroup,
  deleteMediaItem,
  listMediaGroups,
  listMediaItems,
  updateMediaGroup,
  type MediaGroupPayload,
  type MediaGroupView,
  type MediaItemPayload,
  type MediaItemView,
} from '@/api/product-admin'
import {
  ENABLE_STATUS,
  ENABLE_STATUS_OPTIONS,
  MEDIA_TYPE,
  MEDIA_TYPE_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/productStatus'
import { errorMessage } from '@/views/report/helpers'

const groupsLoading = ref(false)
const groupError = ref('')
const groups = ref<MediaGroupView[]>([])
const selectedGroupId = ref<number | null>(null)

const itemsLoading = ref(false)
const itemError = ref('')
const items = ref<MediaItemView[]>([])

const groupDialogVisible = ref(false)
const groupEditingId = ref<number | null>(null)
const groupSubmitting = ref(false)
const groupForm = reactive({
  groupName: '',
  parentId: 0,
  sort: 0,
  status: 0,
})

const itemDialogVisible = ref(false)
const itemSubmitting = ref(false)
const itemForm = reactive({
  fileId: undefined as number | null | undefined,
  mediaType: 1,
  sort: 0,
  status: 0,
})

const selectedGroup = computed(
  () => groups.value.find((group) => group.id === selectedGroupId.value) ?? null,
)

/** 父分组可选当前分组之外的所有分组；0 表示根分组。 */
const parentOptions = computed(() =>
  groups.value
    .filter((group) => group.id !== groupEditingId.value)
    .map((group) => ({ value: group.id, label: group.groupName })),
)

async function loadGroups(): Promise<void> {
  groupsLoading.value = true
  groupError.value = ''
  try {
    const result = await listMediaGroups()
    groups.value = result
    const stillExists = result.some((group) => group.id === selectedGroupId.value)
    if (!stillExists) {
      selectedGroupId.value = result.length ? result[0].id : null
      if (selectedGroupId.value === null) {
        items.value = []
      }
    }
  } catch (error) {
    groups.value = []
    selectedGroupId.value = null
    items.value = []
    groupError.value = errorMessage(error, '素材分组加载失败')
  } finally {
    groupsLoading.value = false
  }
  if (selectedGroupId.value !== null) {
    await loadItems()
  }
}

async function loadItems(): Promise<void> {
  const groupId = selectedGroupId.value
  if (groupId === null) {
    items.value = []
    return
  }
  itemsLoading.value = true
  itemError.value = ''
  try {
    items.value = await listMediaItems(groupId)
  } catch (error) {
    items.value = []
    itemError.value = errorMessage(error, '素材项加载失败')
  } finally {
    itemsLoading.value = false
  }
}

function selectGroup(group: MediaGroupView): void {
  if (selectedGroupId.value === group.id) {
    return
  }
  selectedGroupId.value = group.id
  void loadItems()
}

function openCreateGroup(): void {
  groupEditingId.value = null
  Object.assign(groupForm, {
    groupName: '',
    parentId: 0,
    sort: 0,
    status: 0,
  })
  groupDialogVisible.value = true
}

function openEditGroup(group: MediaGroupView): void {
  groupEditingId.value = group.id
  Object.assign(groupForm, {
    groupName: group.groupName,
    parentId: group.parentId,
    sort: group.sort,
    status: group.status,
  })
  groupDialogVisible.value = true
}

async function submitGroup(): Promise<void> {
  const groupName = groupForm.groupName.trim()
  if (!groupName) {
    ElMessage.warning('分组名称不能为空')
    return
  }

  const payload: MediaGroupPayload = {
    groupName,
    parentId: groupForm.parentId > 0 ? groupForm.parentId : 0,
    sort: groupForm.sort,
    status: groupForm.status,
  }

  groupSubmitting.value = true
  try {
    if (groupEditingId.value === null) {
      await createMediaGroup(payload)
      ElMessage.success('素材分组已创建')
    } else {
      await updateMediaGroup(groupEditingId.value, payload)
      ElMessage.success('素材分组已更新')
    }
    groupDialogVisible.value = false
    await loadGroups()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    groupSubmitting.value = false
  }
}

async function confirmDeleteGroup(group: MediaGroupView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认删除分组「${group.groupName}」？分组下存在子分组或素材时后端会拒绝删除。`,
      '删除素材分组',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  try {
    await deleteMediaGroup(group.id)
    ElMessage.success('素材分组已删除')
    await loadGroups()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  }
}

function openAddItem(): void {
  if (selectedGroupId.value === null) {
    ElMessage.warning('请先选择左侧素材分组')
    return
  }
  Object.assign(itemForm, {
    fileId: undefined,
    mediaType: 1,
    sort: 0,
    status: 0,
  })
  itemDialogVisible.value = true
}

async function submitItem(): Promise<void> {
  const groupId = selectedGroupId.value
  if (groupId === null) {
    return
  }
  const fileId = itemForm.fileId
  if (typeof fileId !== 'number' || !Number.isInteger(fileId) || fileId <= 0) {
    ElMessage.warning('文件 ID 必须是正整数')
    return
  }

  const payload: MediaItemPayload = {
    fileId,
    mediaType: itemForm.mediaType,
    sort: itemForm.sort,
    status: itemForm.status,
  }

  itemSubmitting.value = true
  try {
    await addMediaItem(groupId, payload)
    ElMessage.success('素材已关联')
    itemDialogVisible.value = false
    await loadItems()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    itemSubmitting.value = false
  }
}

async function confirmDeleteItem(item: MediaItemView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认删除素材 #${item.id}（文件 ID ${item.fileId}）？仅解除素材库引用，不删除 t_file 原文件。`,
      '删除素材',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  try {
    await deleteMediaItem(item.id)
    ElMessage.success('素材已删除')
    await loadItems()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  }
}

onMounted(loadGroups)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>素材库</h2>
        <p>
          数据源：<span class="mono">/api/v1/admin/media-groups</span> 与
          <span class="mono">/media-groups/{id}/items</span>。素材项只存
          <span class="mono">t_file.id</span>（fileId），视图不含文件名、大小与 URL，因此仅展示文件 ID。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="groupsLoading" @click="loadGroups">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateGroup">新建分组</el-button>
      </div>
    </div>

    <div class="media-layout">
      <el-card shadow="never" class="group-panel">
        <template #header>
          <div class="panel-header">
            <span>素材分组</span>
            <span class="muted sub">{{ groups.length }} 个</span>
          </div>
        </template>

        <el-alert
          v-if="groupError"
          type="error"
          :closable="false"
          show-icon
          :title="groupError"
        />

        <div v-loading="groupsLoading" class="group-list">
          <div
            v-for="group in groups"
            :key="group.id"
            class="group-item"
            :class="{ active: group.id === selectedGroupId }"
            @click="selectGroup(group)"
          >
            <div class="group-main">
              <div class="group-name">{{ group.groupName }}</div>
              <div class="muted sub">
                #{{ group.id }} · 排序 {{ group.sort }} ·
                <el-tag :type="tagOf(ENABLE_STATUS, group.status)" size="small">
                  {{ labelOf(ENABLE_STATUS, group.status) }}
                </el-tag>
              </div>
            </div>
            <div class="group-actions" @click.stop>
              <el-button link type="primary" @click="openEditGroup(group)">重命名</el-button>
              <el-button link type="danger" @click="confirmDeleteGroup(group)">删除</el-button>
            </div>
          </div>

          <el-empty
            v-if="!groups.length && !groupsLoading"
            description="暂无素材分组。"
            :image-size="60"
          />
        </div>
      </el-card>

      <el-card shadow="never" class="item-panel">
        <template #header>
          <div class="panel-header">
            <span>
              {{ selectedGroup ? `${selectedGroup.groupName} 的素材` : '素材项' }}
            </span>
            <el-button
              type="primary"
              :icon="Plus"
              :disabled="selectedGroup === null"
              @click="openAddItem"
            >
              关联已有文件
            </el-button>
          </div>
        </template>

        <el-alert
          v-if="itemError"
          type="error"
          :closable="false"
          show-icon
          :title="itemError"
        />

        <el-empty
          v-if="selectedGroup === null"
          description="请先在左侧选择一个素材分组。"
          :image-size="80"
        />
        <template v-else>
          <el-table v-loading="itemsLoading" :data="items" border stripe row-key="id">
            <el-table-column prop="id" label="素材 ID" width="90" />
            <el-table-column label="文件 ID" min-width="150">
              <template #default="{ row }">
                <span class="mono">t_file #{{ row.fileId }}</span>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="tagOf(MEDIA_TYPE, row.mediaType)" size="small">
                  {{ labelOf(MEDIA_TYPE, row.mediaType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="排序" width="80" align="right">
              <template #default="{ row }">{{ row.sort }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="tagOf(ENABLE_STATUS, row.status)" size="small">
                  {{ labelOf(ENABLE_STATUS, row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" @click="confirmDeleteItem(row)">删除</el-button>
              </template>
            </el-table-column>

            <template #empty>
              <el-empty description="该分组下暂无素材。" :image-size="80" />
            </template>
          </el-table>
        </template>
      </el-card>
    </div>

    <el-dialog
      v-model="groupDialogVisible"
      :title="groupEditingId === null ? '新建素材分组' : '编辑素材分组'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form label-width="84px">
        <el-form-item label="分组名称" required>
          <el-input
            v-model="groupForm.groupName"
            maxlength="64"
            show-word-limit
            placeholder="例如：门店门头照"
          />
        </el-form-item>
        <el-form-item label="父分组">
          <el-select v-model="groupForm.parentId" class="full-width">
            <el-option :value="0" label="无（根分组）" />
            <el-option
              v-for="option in parentOptions"
              :key="option.value"
              :value="option.value"
              :label="option.label"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="groupForm.sort" :min="0" :precision="0" :controls="false" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="groupForm.status">
            <el-radio
              v-for="option in ENABLE_STATUS_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="groupSubmitting" @click="submitGroup">
          {{ groupEditingId === null ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="itemDialogVisible"
      title="关联已有文件"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form label-width="84px">
        <el-form-item label="文件 ID" required>
          <el-input-number
            v-model="itemForm.fileId"
            :min="1"
            :precision="0"
            :controls="false"
            placeholder="t_file.id"
            class="full-width"
          />
          <div class="form-hint">
            文件需先经 T03 FileController 上传取得 t_file.id；本页不做上传。
          </div>
        </el-form-item>
        <el-form-item label="素材类型">
          <el-radio-group v-model="itemForm.mediaType">
            <el-radio
              v-for="option in MEDIA_TYPE_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="itemForm.sort" :min="0" :precision="0" :controls="false" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="itemForm.status">
            <el-radio
              v-for="option in ENABLE_STATUS_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="itemSubmitting" @click="submitItem">
          关联
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.media-layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.group-list {
  min-height: 120px;
}

.group-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid transparent;
}

.group-item:hover {
  background: var(--el-fill-color-light);
}

.group-item.active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
}

.group-main {
  min-width: 0;
}

.group-name {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.group-actions {
  flex-shrink: 0;
}

.sub {
  font-size: 12px;
  margin-top: 2px;
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

@media (max-width: 900px) {
  .media-layout {
    grid-template-columns: 1fr;
  }
}
</style>
