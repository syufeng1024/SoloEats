<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createAdminMenu,
  deleteAdminMenu,
  getAdminMenuTree,
  updateAdminMenu,
  type SaveMenuPayload,
} from '@/api/system-admin'
import type { MenuNode } from '@/api/types'
import {
  MENU_MANAGE_TYPE_OPTIONS,
  MENU_TYPE,
  MENU_VISIBLE,
  MENU_VISIBLE_OPTIONS,
  SYSTEM_STATUS,
  SYSTEM_STATUS_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/systemStatus'
import { resolveMenuIcon } from '@/utils/menu'
import { errorMessage } from '@/views/report/helpers'

interface MenuParentOption {
  id: number
  label: string
  children?: MenuParentOption[]
}

const loading = ref(false)
const loadError = ref('')
const actionError = ref('')
const menus = ref<MenuNode[]>([])

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)

const form = reactive({
  parentId: 0,
  menuName: '',
  menuType: 1,
  path: '',
  component: '',
  perms: '',
  icon: '',
  sort: 0,
  visible: 0,
  status: 0,
})

function buildDirectoryOptions(nodes: MenuNode[], excludeId: number | null): MenuParentOption[] {
  const result: MenuParentOption[] = []
  for (const node of nodes) {
    if (node.id === excludeId) {
      continue
    }
    if (node.menuType !== 1) {
      continue
    }
    const children = buildDirectoryOptions(node.children ?? [], excludeId)
    const option: MenuParentOption = { id: node.id, label: node.menuName }
    if (children.length > 0) {
      option.children = children
    }
    result.push(option)
  }
  return result
}

const parentOptions = computed<MenuParentOption[]>(() => {
  const directories = buildDirectoryOptions(menus.value, editingId.value)
  if (form.menuType === 2) {
    return directories
  }
  const root: MenuParentOption = { id: 0, label: '根级（无上级）' }
  if (directories.length > 0) {
    root.children = directories
  }
  return [root]
})

const parentOptionIds = computed(() => {
  const ids = new Set<number>()
  const walk = (nodes: MenuParentOption[]): void => {
    for (const node of nodes) {
      ids.add(node.id)
      if (node.children) {
        walk(node.children)
      }
    }
  }
  walk(parentOptions.value)
  return ids
})

const componentPreview = computed(() => {
  const normalized = normalizeComponent(form.component)
  return normalized === '' ? '' : `src/views/${normalized}.vue`
})

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    menus.value = await getAdminMenuTree()
  } catch (error) {
    menus.value = []
    loadError.value = errorMessage(error, '菜单树加载失败')
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  Object.assign(form, {
    parentId: 0,
    menuName: '',
    menuType: 1,
    path: '',
    component: '',
    perms: '',
    icon: '',
    sort: 0,
    visible: 0,
    status: 0,
  })
}

function openCreateRoot(): void {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openCreateChild(row: MenuNode): void {
  editingId.value = null
  resetForm()
  form.menuType = 2
  form.parentId = row.id
  dialogVisible.value = true
}

function openEdit(row: MenuNode): void {
  editingId.value = row.id
  Object.assign(form, {
    parentId: row.parentId,
    menuName: row.menuName,
    menuType: row.menuType,
    path: row.path ?? '',
    component: row.component ?? '',
    perms: row.perms ?? '',
    icon: row.icon ?? '',
    sort: row.sort,
    visible: row.visible,
    status: row.status,
  })
  dialogVisible.value = true
}

function normalizeComponent(value: string): string {
  return value
    .trim()
    .replace(/\\/g, '/')
    .replace(/^\/+/, '')
    .replace(/^src\/views\//i, '')
    .replace(/\.vue$/i, '')
}

function validateForm(): string | null {
  const menuName = form.menuName.trim()
  if (menuName === '') {
    return '菜单名称不能为空'
  }
  if (menuName.length > 64) {
    return '菜单名称不能超过 64 个字符'
  }
  if (form.menuType !== 1 && form.menuType !== 2) {
    return '菜单类型只能是目录或菜单'
  }
  if (!parentOptionIds.value.has(form.parentId)) {
    return form.menuType === 2 ? '菜单必须选择有效的上级目录' : '请选择有效的上级菜单'
  }
  if (form.menuType === 2 && form.parentId === 0) {
    return '菜单必须挂在目录下'
  }

  const path = form.path.trim()
  if (path.length > 200) {
    return '路由路径不能超过 200 个字符'
  }
  if (path !== '' && !path.startsWith('/')) {
    return '路由路径必须以 / 开头'
  }
  if (form.menuType === 2 && path === '') {
    return '菜单必须填写路由路径'
  }

  const component = normalizeComponent(form.component)
  if (form.menuType === 2 && component === '') {
    return '菜单必须填写组件路径'
  }
  if (component.includes('..')) {
    return '组件路径不能包含 ..'
  }
  if (component.length > 200) {
    return '组件路径不能超过 200 个字符'
  }
  if (form.perms.trim().length > 128) {
    return '权限标识不能超过 128 个字符'
  }
  if (form.icon.trim().length > 64) {
    return '图标标识不能超过 64 个字符'
  }
  if (!Number.isInteger(form.sort) || form.sort < 0) {
    return '排序必须是非负整数'
  }
  if (form.visible !== 0 && form.visible !== 1) {
    return '显隐取值不正确'
  }
  if (form.status !== 0 && form.status !== 1) {
    return '状态取值不正确'
  }
  return null
}

function buildPayload(): SaveMenuPayload {
  return {
    parentId: form.parentId,
    menuName: form.menuName.trim(),
    menuType: form.menuType,
    path: form.path.trim(),
    component: form.menuType === 1 ? '' : normalizeComponent(form.component),
    perms: form.perms.trim(),
    icon: form.icon.trim(),
    sort: form.sort,
    visible: form.visible,
    status: form.status,
  }
}

async function submitMenu(): Promise<void> {
  const invalid = validateForm()
  if (invalid !== null) {
    ElMessage.warning(invalid)
    return
  }

  const payload = buildPayload()
  submitting.value = true
  actionError.value = ''
  try {
    if (editingId.value === null) {
      await createAdminMenu(payload)
      ElMessage.success('菜单已创建')
    } else {
      await updateAdminMenu(editingId.value, payload)
      ElMessage.success('菜单已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    actionError.value = errorMessage(error, '菜单保存失败')
  } finally {
    submitting.value = false
  }
}

async function confirmDelete(row: MenuNode): Promise<void> {
  if ((row.children?.length ?? 0) > 0) {
    ElMessage.warning('存在子菜单，请先删除子节点')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认删除菜单「${row.menuName}」？删除后所有角色对该菜单的授权会同步撤销。`,
      '删除菜单',
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
    await deleteAdminMenu(row.id)
    ElMessage.success('菜单已删除')
    await load()
  } catch (error) {
    const message = errorMessage(error, '菜单删除失败')
    actionError.value = message.includes('存在子菜单')
      ? `${message}。菜单树接口不返回按钮节点；若该菜单下只有按钮，请到后端/数据库处理。`
      : message
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>菜单管理</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/menus/tree</span>。
          接口只返回目录与菜单，按钮节点不在本页维护；
          component 必须与 <span class="mono">src/views/</span> 下文件路径一致，否则路由会落到占位页。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateRoot">新建根目录</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />
    <el-alert v-if="actionError" type="error" :closable="false" show-icon :title="actionError" />

    <el-card shadow="never">
      <el-table
        v-loading="loading"
        :data="menus"
        border
        stripe
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
      >
        <el-table-column label="菜单名称" min-width="220">
          <template #default="{ row }">
            <el-icon v-if="row.icon" class="menu-icon">
              <component :is="resolveMenuIcon(row.icon)" />
            </el-icon>
            <span>{{ row.menuName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(MENU_TYPE, row.menuType)" size="small">
              {{ labelOf(MENU_TYPE, row.menuType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="路径" min-width="170">
          <template #default="{ row }">
            <span class="mono">{{ row.path || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="组件" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.component || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="权限标识" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.perms || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="图标" width="110">
          <template #default="{ row }">
            <span class="mono">{{ row.icon || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="75" align="right" />
        <el-table-column label="显隐" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(MENU_VISIBLE, row.visible)" size="small">
              {{ labelOf(MENU_VISIBLE, row.visible) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(SYSTEM_STATUS, row.status)" size="small">
              {{ labelOf(SYSTEM_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.menuType === 1"
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
          <el-empty description="暂无菜单，先新建根目录。" :image-size="80" />
        </template>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新建菜单' : '编辑菜单'"
      width="680px"
      :close-on-click-modal="false"
    >
      <el-form label-width="104px">
        <el-form-item label="上级菜单" required>
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            node-key="id"
            check-strictly
            :render-after-expand="false"
            :props="{ label: 'label', children: 'children' }"
            class="full-width"
          />
          <div class="form-hint">
            目录/菜单的上级必须是目录；新建菜单时不能挂根。
          </div>
        </el-form-item>
        <el-form-item label="菜单名称" required>
          <el-input v-model="form.menuName" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-radio-group
            v-model="form.menuType"
            :disabled="editingId !== null"
          >
            <el-radio
              v-for="option in MENU_MANAGE_TYPE_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio>
          </el-radio-group>
          <div v-if="editingId !== null" class="form-hint">
            类型编辑后只读，避免把已有隐藏按钮子节点的菜单改成目录。
          </div>
        </el-form-item>
        <el-form-item label="路由路径" :required="form.menuType === 2">
          <el-input
            v-model="form.path"
            maxlength="200"
            placeholder="例如 /system/user"
          />
        </el-form-item>
        <el-form-item label="组件" :required="form.menuType === 2">
          <el-input
            v-model="form.component"
            :disabled="form.menuType === 1"
            maxlength="200"
            placeholder="例如 system/user/index"
          />
          <div class="form-hint">
            不要带 <span class="mono">src/views/</span> 前缀和 <span class="mono">.vue</span>；
            保存后解析为
            <span class="mono">{{ componentPreview || '（目录无组件）' }}</span>。
          </div>
        </el-form-item>
        <el-form-item label="权限标识">
          <el-input
            v-model="form.perms"
            maxlength="128"
            placeholder="例如 system:user:create"
          />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" maxlength="64" placeholder="例如 user" />
          <div v-if="form.icon.trim()" class="icon-preview">
            <el-icon><component :is="resolveMenuIcon(form.icon)" /></el-icon>
            <span class="mono">{{ form.icon.trim() }}</span>
          </div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number
            v-model="form.sort"
            :min="0"
            :precision="0"
            :controls="false"
          />
        </el-form-item>
        <el-form-item label="显隐">
          <el-select v-model="form.visible" class="full-width">
            <el-option
              v-for="option in MENU_VISIBLE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" class="full-width">
            <el-option
              v-for="option in SYSTEM_STATUS_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitMenu">
          {{ editingId === null ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.full-width {
  width: 100%;
}

.form-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
  margin-top: 4px;
}

.menu-icon {
  margin-right: 6px;
  vertical-align: -2px;
}

.icon-preview {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  color: var(--el-text-color-secondary);
}
</style>
