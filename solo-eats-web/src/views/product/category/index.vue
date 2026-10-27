<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createCategory,
  deleteCategory,
  getCategoryTree,
  updateCategory,
  type CategoryNode,
  type CategoryPayload,
} from '@/api/product-admin'
import {
  CATEGORY_STATUS,
  CATEGORY_STATUS_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/productStatus'
import { errorMessage } from '@/views/report/helpers'

const loading = ref(false)
const loadError = ref('')
const categories = ref<CategoryNode[]>([])

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const editingHasChildren = ref(false)
const submitting = ref(false)

const form = reactive({
  parentId: 0,
  categoryName: '',
  iconUrl: '',
  sort: 0,
  status: 0,
})

/** 只有一级品类可作为父级；编辑自身时把自己排除，避免后端「父级必须是二级」类报错。 */
const parentOptions = computed(() =>
  categories.value
    .filter((node) => node.id !== editingId.value)
    .map((node) => ({ value: node.id, label: node.categoryName })),
)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    categories.value = await getCategoryTree()
  } catch (error) {
    categories.value = []
    loadError.value = errorMessage(error, '品类树加载失败')
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  Object.assign(form, {
    parentId: 0,
    categoryName: '',
    iconUrl: '',
    sort: 0,
    status: 0,
  })
}

function openCreateRoot(): void {
  editingId.value = null
  editingHasChildren.value = false
  resetForm()
  dialogVisible.value = true
}

function openCreateChild(row: CategoryNode): void {
  editingId.value = null
  editingHasChildren.value = false
  resetForm()
  form.parentId = row.id
  dialogVisible.value = true
}

function openEdit(row: CategoryNode): void {
  editingId.value = row.id
  editingHasChildren.value = (row.children?.length ?? 0) > 0
  Object.assign(form, {
    parentId: row.parentId ?? 0,
    categoryName: row.categoryName,
    iconUrl: row.iconUrl ?? '',
    sort: row.sort,
    status: row.status,
  })
  dialogVisible.value = true
}

async function submitCategory(): Promise<void> {
  const categoryName = form.categoryName.trim()
  if (!categoryName) {
    ElMessage.warning('品类名称不能为空')
    return
  }
  const iconUrl = form.iconUrl.trim()

  const payload: CategoryPayload = {
    parentId: form.parentId > 0 ? form.parentId : 0,
    categoryName,
    iconUrl: iconUrl || null,
    sort: form.sort,
    status: form.status,
  }

  submitting.value = true
  try {
    if (editingId.value === null) {
      await createCategory(payload)
      ElMessage.success(form.parentId > 0 ? '子品类已创建' : '一级品类已创建')
    } else {
      await updateCategory(editingId.value, payload)
      ElMessage.success('品类已更新')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    submitting.value = false
  }
}

async function confirmDelete(row: CategoryNode): Promise<void> {
  if ((row.children?.length ?? 0) > 0) {
    ElMessage.warning('该品类下存在子品类，请先删除子品类后再删除')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认删除品类「${row.categoryName}」？已被商品引用的品类后端会拒绝删除。`,
      '删除品类',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  try {
    await deleteCategory(row.id)
    ElMessage.success('品类已删除')
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>品类管理</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/categories/tree</span>。最多两级，
          <span class="mono">parentId=0</span> 为一级品类；存在子品类或已被 SPU 引用时后端拒绝删除。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateRoot">新增一级品类</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <el-table
        v-loading="loading"
        :data="categories"
        border
        stripe
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
      >
        <el-table-column prop="categoryName" label="品类名称" min-width="220" />
        <el-table-column label="图标" width="90" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.iconUrl"
              :src="row.iconUrl"
              :preview-src-list="[row.iconUrl]"
              preview-teleported
              fit="cover"
              class="category-icon"
            />
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="层级" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.level === 1 ? 'primary' : 'info'" size="small">
              {{ row.level === 1 ? '一级' : '二级' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="排序" width="80" align="right">
          <template #default="{ row }">{{ row.sort }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(CATEGORY_STATUS, row.status)" size="small">
              {{ labelOf(CATEGORY_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="子品类" width="90" align="right">
          <template #default="{ row }">{{ row.children?.length ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.level === 1"
              link
              type="primary"
              @click="openCreateChild(row)"
            >
              新增子级
            </el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="confirmDelete(row)">删除</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无品类，先创建一级品类。" :image-size="80" />
        </template>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新建品类' : '编辑品类'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form label-width="84px">
        <el-form-item label="上级品类">
          <el-select
            v-model="form.parentId"
            :disabled="editingHasChildren"
            class="full-width"
          >
            <el-option :value="0" label="无（一级品类）" />
            <el-option
              v-for="option in parentOptions"
              :key="option.value"
              :value="option.value"
              :label="option.label"
            />
          </el-select>
          <div v-if="editingHasChildren" class="form-hint">
            该品类下已有子品类，后端不允许调整父级。
          </div>
        </el-form-item>
        <el-form-item label="品类名称" required>
          <el-input
            v-model="form.categoryName"
            maxlength="64"
            show-word-limit
            placeholder="例如：面食"
          />
        </el-form-item>
        <el-form-item label="图标 URL">
          <el-input
            v-model="form.iconUrl"
            maxlength="512"
            placeholder="t_file 代理地址，留空表示不设置"
          />
          <el-image
            v-if="form.iconUrl.trim()"
            :src="form.iconUrl.trim()"
            fit="cover"
            class="form-icon-preview"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :precision="0" :controls="false" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio
              v-for="option in CATEGORY_STATUS_OPTIONS"
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
        <el-button type="primary" :loading="submitting" @click="submitCategory">
          {{ editingId === null ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.category-icon {
  width: 36px;
  height: 36px;
  border-radius: 4px;
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

.form-icon-preview {
  width: 48px;
  height: 48px;
  border-radius: 4px;
  margin-top: 8px;
}
</style>
