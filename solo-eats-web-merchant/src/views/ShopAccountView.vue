<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useShopStore } from '@/stores/shop'
import { createShopAccount, listShopAccounts, unbindShopAccount } from '@/api/shop'
import type { ShopAccountView } from '@/api/types'
import { formatDateTime } from '@/utils/datetime'

interface CreateFormState {
  username: string
  password: string
  nickname: string
  phone: string
  accountRole: 1 | 2
}

const auth = useAuthStore()
const shop = useShopStore()

const shopId = computed(() => shop.currentShopId ?? auth.shopId ?? null)
const accounts = ref<ShopAccountView[]>([])
const loading = ref(false)
const loadError = ref('')
const createVisible = ref(false)
const creating = ref(false)
const deletingUserId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<CreateFormState>({
  username: '',
  password: '',
  nickname: '',
  phone: '',
  accountRole: 2,
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入登录用户名', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_.@-]{3,64}$/,
      message: '3~64 位，仅支持字母、数字、下划线、点、@、连字符',
      trigger: 'blur',
    },
  ],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度 8~64 位', trigger: 'blur' },
  ],
  nickname: [{ max: 64, message: '昵称最长 64 字符', trigger: 'blur' }],
  phone: [
    {
      validator: (_rule, value, callback) => {
        const phone = String(value ?? '').trim()
        if (phone && !/^1[3-9]\d{9}$/.test(phone)) {
          callback(new Error('手机号格式不正确'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  accountRole: [{ required: true, message: '请选择账号角色', trigger: 'change' }],
}

async function load(): Promise<void> {
  const id = shopId.value
  if (id === null) {
    accounts.value = []
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const result = await listShopAccounts(id)
    accounts.value = Array.isArray(result) ? result : []
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '门店账号加载失败'
    accounts.value = []
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  form.username = ''
  form.password = ''
  form.nickname = ''
  form.phone = ''
  form.accountRole = 2
  createVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

async function submitCreate(): Promise<void> {
  const id = shopId.value
  if (id === null) {
    return
  }
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  creating.value = true
  try {
    await createShopAccount(id, {
      username: form.username.trim(),
      password: form.password,
      nickname: form.nickname.trim() || null,
      phone: form.phone.trim() || null,
      accountRole: form.accountRole,
    })
    ElMessage.success('门店账号已创建')
    createVisible.value = false
    await load()
  } catch {
    // 拦截器已提示；用户名重复等业务错误由后端返回
  } finally {
    creating.value = false
  }
}

async function unbind(row: ShopAccountView): Promise<void> {
  const id = shopId.value
  if (id === null) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认解绑账号「${row.username ?? row.userId}」？只解除与门店的关联，不删除系统用户，该账号仍可能关联其他门店。`,
      '解绑门店账号',
      { confirmButtonText: '解绑', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  deletingUserId.value = row.userId
  try {
    await unbindShopAccount(id, row.userId)
    ElMessage.success('账号已解绑')
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    deletingUserId.value = null
  }
}

watch(shopId, () => {
  void load()
})

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>门店账号</h2>
        <p>
          门店 ID <span class="mono">{{ shopId ?? '未绑定' }}</span> ·
          {{ shop.currentShop?.shopName ?? '—' }}。数据源
          <span class="mono">GET/POST /api/v1/merchant/shops/{{ shopId ?? '{id}' }}/accounts</span>。
          新建账号统一绑定 MERCHANT_CLERK 角色，门店范围由后端写入 t_sys_user.shop_id。
        </p>
      </div>
      <div class="lead-actions">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" :disabled="shopId === null" @click="openCreate">
          新建账号
        </el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-empty
      v-if="shopId === null && !loading"
      description="当前令牌未绑定门店。请先在右上角选择门店。"
    />

    <el-card v-else shadow="never">
      <el-table v-loading="loading" :data="accounts" border row-key="userId">
        <el-table-column label="用户名" min-width="150">
          <template #default="{ row }">
            <span class="mono">{{ row.username || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="昵称" min-width="130">
          <template #default="{ row }">{{ row.nickname || '—' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.accountRole === 1 ? 'warning' : 'info'" size="small">
              {{ row.accountRoleText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'info'" size="small" effect="plain">
              {{ row.status === 0 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="默认门店" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault === 1" type="primary" size="small" effect="plain">
              默认
            </el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="用户 ID" width="110">
          <template #default="{ row }">
            <span class="mono">{{ row.userId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="danger"
              :loading="deletingUserId === row.userId"
              @click="unbind(row)"
            >
              解绑
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty
            description="该门店还没有子账号。点击右上角“新建账号”创建店长或店员账号。"
            :image-size="80"
          />
        </template>
      </el-table>
    </el-card>

    <el-dialog
      v-model="createVisible"
      title="新建门店账号"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" maxlength="64" placeholder="3~64 位字母/数字" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            maxlength="64"
            placeholder="8~64 位"
          />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" maxlength="64" placeholder="可选" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="11" placeholder="可选，11 位手机号" />
        </el-form-item>
        <el-form-item label="账号角色" prop="accountRole">
          <el-select v-model="form.accountRole" class="role-select">
            <el-option label="店长" :value="1" />
            <el-option label="店员" :value="2" />
          </el-select>
        </el-form-item>
      </el-form>
      <p class="dialog-hint">
        密码只在创建时提交一次，后端加密存储；密码遗忘需重新创建账号。
      </p>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.lead-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.role-select {
  width: 160px;
}

.dialog-hint {
  color: var(--app-muted);
  font-size: 12px;
  line-height: 1.7;
}
</style>
