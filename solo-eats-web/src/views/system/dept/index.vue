<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createAdminDept,
  deleteAdminDept,
  getAdminDeptTree,
  updateAdminDept,
  type AdminDeptNode,
  type SaveDeptPayload,
} from '@/api/system-admin'
import {
  SYSTEM_STATUS,
  SYSTEM_STATUS_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/systemStatus'
import { errorMessage } from '@/views/report/helpers'

interface DeptParentOption {
  id: number
  label: string
  children?: DeptParentOption[]
}

const loading = ref(false)
const loadError = ref('')
const actionError = ref('')
const depts = ref<AdminDeptNode[]>([])

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const editingPhoneMask = ref<string | null>(null)
const submitting = ref(false)

const form = reactive({
  parentId: 0,
  deptName: '',
  leader: '',
  phone: '',
  sort: 0,
  status: 0,
})

function buildDeptOptions(nodes: AdminDeptNode[], excludeId: number | null): DeptParentOption[] {
  const result: DeptParentOption[] = []
  for (const node of nodes) {
    if (node.id === excludeId) {
      continue
    }
    const children = buildDeptOptions(node.children ?? [], excludeId)
    const option: DeptParentOption = { id: node.id, label: node.deptName }
    if (children.length > 0) {
      option.children = children
    }
    result.push(option)
  }
  return result
}

const parentOptions = computed<DeptParentOption[]>(() => {
  const nodes = buildDeptOptions(depts.value, editingId.value)
  const root: DeptParentOption = { id: 0, label: '无（根部门）' }
  if (nodes.length > 0) {
    root.children = nodes
  }
  return [root]
})

const parentOptionIds = computed(() => {
  const ids = new Set<number>()
  const walk = (nodes: DeptParentOption[]): void => {
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

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    depts.value = await getAdminDeptTree()
  } catch (error) {
    depts.value = []
    loadError.value = errorMessage(error, '部门树加载失败')
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  Object.assign(form, {
    parentId: 0,
    deptName: '',
    leader: '',
    phone: '',
    sort: 0,
    status: 0,
  })
  editingPhoneMask.value = null
}

function openCreateRoot(): void {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openCreateChild(row: AdminDeptNode): void {
  editingId.value = null
  resetForm()
  form.parentId = row.id
  dialogVisible.value = true
}

function openEdit(row: AdminDeptNode): void {
  editingId.value = row.id
  editingPhoneMask.value = row.phone
  Object.assign(form, {
    parentId: row.parentId,
    deptName: row.deptName,
    leader: row.leader ?? '',
    phone: '',
    sort: row.sort,
    status: row.status,
  })
  dialogVisible.value = true
}

function validateForm(): string | null {
  const deptName = form.deptName.trim()
  if (deptName === '') {
    return '部门名称不能为空'
  }
  if (deptName.length > 64) {
    return '部门名称不能超过 64 个字符'
  }
  if (form.leader.trim().length > 64) {
    return '负责人不能超过 64 个字符'
  }

  const phone = form.phone.trim()
  if (phone.length > 32) {
    return '联系电话不能超过 32 个字符'
  }
  if (phone.includes('*')) {
    return '联系电话已脱敏，请不要把掩码写回；留空表示保持原值'
  }
  if (!parentOptionIds.value.has(form.parentId)) {
    return '请选择有效的上级部门'
  }
  if (!Number.isInteger(form.sort) || form.sort < 0) {
    return '排序必须是非负整数'
  }
  if (form.status !== 0 && form.status !== 1) {
    return '状态不正确'
  }
  return null
}

function buildPayload(): SaveDeptPayload {
  const phone = form.phone.trim()
  return {
    parentId: form.parentId,
    deptName: form.deptName.trim(),
    leader: form.leader.trim(),
    phone: phone === '' ? null : phone,
    sort: form.sort,
    status: form.status,
  }
}

async function submitDept(): Promise<void> {
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
      await createAdminDept(payload)
      ElMessage.success('部门已创建')
    } else {
      await updateAdminDept(editingId.value, payload)
      ElMessage.success('部门已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    actionError.value = errorMessage(error, '部门保存失败')
  } finally {
    submitting.value = false
  }
}

async function confirmDelete(row: AdminDeptNode): Promise<void> {
  if ((row.children?.length ?? 0) > 0) {
    ElMessage.warning('存在子部门，请先删除子节点')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认删除部门「${row.deptName}」？仍有账号引用时后端会拒绝删除。`,
      '删除部门',
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
    await deleteAdminDept(row.id)
    ElMessage.success('部门已删除')
    await load()
  } catch (error) {
    const message = errorMessage(error, '部门删除失败')
    if (message.includes('存在子部门')) {
      actionError.value = `${message}。请在本页展开树表逐级删除。`
    } else if (message.includes('账号引用')) {
      actionError.value = `${message}。请到用户管理把相关账号调整到其他部门后再删除。`
    } else {
      actionError.value = message
    }
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>部门管理</h2>
        <p>
          数据源：<span class="mono">GET /api/v1/admin/depts/tree</span>。
          联系电话由后端掩码返回，编辑时留空表示保持原值；
          存在子部门或账号引用时后端拒绝删除。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateRoot">新建根部门</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />
    <el-alert v-if="actionError" type="error" :closable="false" show-icon :title="actionError" />

    <el-card shadow="never">
      <el-table
        v-loading="loading"
        :data="depts"
        border
        stripe
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
      >
        <el-table-column prop="deptName" label="部门名称" min-width="240" />
        <el-table-column label="负责人" width="130">
          <template #default="{ row }">{{ row.leader || '—' }}</template>
        </el-table-column>
        <el-table-column label="联系电话" width="140">
          <template #default="{ row }">{{ row.phone || '—' }}</template>
        </el-table-column>
        <el-table-column label="子部门" width="85" align="right">
          <template #default="{ row }">{{ row.children?.length ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="75" align="right" />
        <el-table-column label="状态" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(SYSTEM_STATUS, row.status)" size="small">
              {{ labelOf(SYSTEM_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openCreateChild(row)">新增子部门</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="confirmDelete(row)">删除</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无部门，先新建根部门。" :image-size="80" />
        </template>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新建部门' : '编辑部门'"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form label-width="96px">
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            node-key="id"
            check-strictly
            :render-after-expand="false"
            :props="{ label: 'label', children: 'children' }"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="部门名称" required>
          <el-input v-model="form.deptName" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.leader" maxlength="64" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input
            v-model="form.phone"
            maxlength="32"
            :placeholder="
              editingPhoneMask
                ? `当前 ${editingPhoneMask}，留空保持不变`
                : '选填'
            "
          />
          <div v-if="editingId !== null" class="form-hint">
            后端只返回掩码号码；留空提交 null，原号码保持不变。
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
        <el-button type="primary" :loading="submitting" @click="submitDept">
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
</style>
