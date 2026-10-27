<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createMemberLevel,
  listMemberLevels,
  recalcMemberLevels,
  updateMemberLevel,
  type MemberLevelSavePayload,
  type MemberLevelView,
} from '@/api/promotion'

interface MemberLevelForm {
  levelCode: string
  levelName: string
  levelNo: number
  growthThreshold: number
  star: number
  benefitsJson: string
  iconUrl: string
  sort: number
  status: number
}

const loading = ref(false)
const recalculating = ref(false)
const submitting = ref(false)
const loadError = ref('')
const levels = ref<MemberLevelView[]>([])

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

const form = reactive<MemberLevelForm>({
  levelCode: '',
  levelName: '',
  levelNo: 1,
  growthThreshold: 0,
  star: 1,
  benefitsJson: '',
  iconUrl: '',
  sort: 0,
  status: 0,
})

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    levels.value = await listMemberLevels()
  } catch (error) {
    loadError.value = errorMessage(error, '会员等级加载失败')
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  editingId.value = null
  Object.assign(form, {
    levelCode: '',
    levelName: '',
    levelNo: 1,
    growthThreshold: 0,
    star: 1,
    benefitsJson: '',
    iconUrl: '',
    sort: 0,
    status: 0,
  })
}

function openCreate(): void {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: MemberLevelView): void {
  editingId.value = row.id
  Object.assign(form, {
    levelCode: row.levelCode,
    levelName: row.levelName,
    levelNo: row.levelNo,
    growthThreshold: row.growthThreshold,
    star: row.star,
    benefitsJson: row.benefitsJson ?? '',
    iconUrl: row.iconUrl ?? '',
    sort: row.sort,
    status: row.status,
  })
  dialogVisible.value = true
}

function toPayload(): MemberLevelSavePayload | null {
  const levelCode = form.levelCode.trim()
  if (!levelCode) {
    ElMessage.warning('等级编码不能为空')
    return null
  }
  const levelName = form.levelName.trim()
  if (!levelName) {
    ElMessage.warning('等级名称不能为空')
    return null
  }

  const benefitsJson = form.benefitsJson.trim()
  if (benefitsJson) {
    try {
      const parsed: unknown = JSON.parse(benefitsJson)
      if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
        throw new Error('not an object')
      }
    } catch {
      ElMessage.warning('权益必须是 JSON 对象，如 {"couponCount":4,"freeDeliveryTimes":3}')
      return null
    }
  }

  return {
    levelCode,
    levelName,
    levelNo: form.levelNo,
    growthThreshold: form.growthThreshold,
    star: form.star,
    benefitsJson: benefitsJson || null,
    iconUrl: form.iconUrl.trim() || null,
    sort: form.sort,
    status: form.status,
  }
}

async function submit(): Promise<void> {
  const payload = toPayload()
  if (!payload) {
    return
  }

  submitting.value = true
  try {
    if (editingId.value === null) {
      await createMemberLevel(payload)
      ElMessage.success('会员等级已创建')
    } else {
      await updateMemberLevel(editingId.value, payload)
      ElMessage.success('会员等级已更新')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    submitting.value = false
  }
}

async function recalc(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      '将按近 365 天成长值重算全部会员等级，过程可能持续数秒，确认继续？',
      '确认重算会员等级',
      {
        confirmButtonText: '开始重算',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  recalculating.value = true
  try {
    const changed = await recalcMemberLevels()
    ElMessage.success(`重算完成，实际更新 ${changed} 人`)
    await load()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    recalculating.value = false
  }
}

function starText(star: number): string {
  const count = Math.max(0, Math.min(3, star))
  return '★'.repeat(count) + '☆'.repeat(3 - count)
}

function benefitsSummary(json: string | null): string {
  if (!json) {
    return '未配置'
  }
  try {
    const parsed = JSON.parse(json) as Record<string, unknown>
    const parts: string[] = []
    if (typeof parsed.couponCount === 'number') {
      parts.push(`神券 ${parsed.couponCount} 张`)
    }
    if (typeof parsed.freeDeliveryTimes === 'number') {
      parts.push(`免配送 ${parsed.freeDeliveryTimes} 次`)
    }
    if (parsed.exclusiveService === true) {
      parts.push('专属客服')
    }
    return parts.length ? parts.join(' / ') : json
  } catch {
    return json
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>会员等级</h2>
        <p>
          数据源：<span class="mono">/api/v1/admin/member-levels</span>，权限码
          <span class="mono">marketing:member:list</span> /
          <span class="mono">marketing:member:update</span>。
          等级按近 365 天成长值门槛从高到低匹配；重算只更新成长值或等级发生变化的用户。
        </p>
      </div>
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="openCreate">新建等级</el-button>
        <el-button :loading="recalculating" @click="recalc">重算会员等级</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card shadow="never">
      <el-table v-loading="loading" :data="levels" border stripe row-key="id">
        <el-table-column prop="levelNo" label="等级号" width="80" align="center" />
        <el-table-column prop="levelCode" label="编码" width="100" />
        <el-table-column prop="levelName" label="名称" min-width="120" />
        <el-table-column label="星级" width="110">
          <template #default="{ row }">
            <span class="star">{{ starText(row.star) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="成长值门槛" width="120" align="right">
          <template #default="{ row }">
            <span class="mono">{{ row.growthThreshold }}</span>
          </template>
        </el-table-column>
        <el-table-column label="权益" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ benefitsSummary(row.benefitsJson) }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" align="right" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'info'" size="small">
              {{ row.status === 0 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新建会员等级' : '编辑会员等级'"
      width="620px"
      destroy-on-close
    >
      <el-form label-width="110px">
        <el-form-item label="等级编码" required>
          <el-input v-model="form.levelCode" maxlength="16" placeholder="如 V1" />
        </el-form-item>
        <el-form-item label="等级名称" required>
          <el-input v-model="form.levelName" maxlength="32" placeholder="如 黑铁" />
        </el-form-item>
        <el-form-item label="等级号" required>
          <el-input-number v-model="form.levelNo" :min="1" :max="6" />
          <span class="field-hint">1~6，与六档体系对应</span>
        </el-form-item>
        <el-form-item label="成长值门槛" required>
          <el-input-number v-model="form.growthThreshold" :min="0" :step="100" />
          <span class="field-hint">近 365 天成长值达到该值即进入本档</span>
        </el-form-item>
        <el-form-item label="星级" required>
          <el-input-number v-model="form.star" :min="1" :max="3" />
          <span class="field-hint">仅展示用，1~3 星</span>
        </el-form-item>
        <el-form-item label="权益 JSON">
          <el-input
            v-model="form.benefitsJson"
            type="textarea"
            :rows="4"
            placeholder='{"couponCount":4,"freeDeliveryTimes":3,"exclusiveService":true}'
          />
        </el-form-item>
        <el-form-item label="图标地址">
          <el-input v-model="form.iconUrl" maxlength="255" placeholder="可选" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option :value="0" label="启用" />
            <el-option :value="1" label="停用" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.star {
  color: var(--el-color-warning);
  letter-spacing: 2px;
}

.field-hint {
  margin-left: 10px;
  color: var(--app-muted);
  font-size: 12px;
}
</style>
