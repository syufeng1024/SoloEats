<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createAdminMailAccount,
  listAdminMailAccounts,
  listAdminMailLogs,
  sendAdminTestMail,
  updateAdminMailAccount,
  type AdminMailAccountView,
  type AdminMailLogView,
  type SaveMailAccountPayload,
} from '@/api/system-admin'
import {
  MAIL_LOG_STATUS,
  MAIL_LOG_STATUS_OPTIONS,
  MAIL_SSL,
  MAIL_SSL_OPTIONS,
  SYSTEM_STATUS,
  SYSTEM_STATUS_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/systemStatus'
import { formatDateTime } from '@/utils/datetime'
import { errorMessage } from '@/views/report/helpers'

const PAGE_SIZE_OPTIONS = [10, 20, 50]
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const activeTab = ref('logs')
const accountsLoaded = ref(false)
const logsLoaded = ref(false)

const accounts = ref<AdminMailAccountView[]>([])
const accountsLoading = ref(false)
const accountsError = ref('')
const accountsActionError = ref('')
const accountKeyword = ref('')

const filteredAccounts = computed(() => {
  const needle = accountKeyword.value.trim().toLowerCase()
  if (needle === '') {
    return accounts.value
  }
  return accounts.value.filter((item) =>
    [item.accountName, item.host, item.username, item.fromAddr]
      .join(' ')
      .toLowerCase()
      .includes(needle),
  )
})

const accountDialogVisible = ref(false)
const accountEditingId = ref<number | null>(null)
const accountSubmitting = ref(false)
const accountForm = reactive({
  accountName: '',
  host: '',
  port: 465,
  username: '',
  password: '',
  fromAddr: '',
  ssl: 1,
  status: 0,
})

const testDialogVisible = ref(false)
const testSubmitting = ref(false)
const testError = ref('')
const testForm = reactive({
  accountId: undefined as number | undefined,
  toAddr: '',
})

const logs = ref<AdminMailLogView[]>([])
const logsLoading = ref(false)
const logsError = ref('')
const logsTotal = ref(0)
const logsPageNum = ref(1)
const logsPageSize = ref(20)
const logFilters = reactive({
  status: undefined as number | undefined,
})

async function loadAccounts(): Promise<void> {
  accountsLoading.value = true
  accountsError.value = ''
  try {
    accounts.value = await listAdminMailAccounts()
    accountsLoaded.value = true
  } catch (error) {
    accounts.value = []
    accountsError.value = errorMessage(error, '邮件账号加载失败')
  } finally {
    accountsLoading.value = false
  }
}

async function loadLogs(): Promise<void> {
  logsLoading.value = true
  logsError.value = ''
  try {
    const result = await listAdminMailLogs({
      status: typeof logFilters.status === 'number' ? logFilters.status : undefined,
      pageNum: logsPageNum.value,
      pageSize: logsPageSize.value,
    })
    logs.value = result.list
    logsTotal.value = result.total
    logsLoaded.value = true
  } catch (error) {
    logs.value = []
    logsTotal.value = 0
    logsError.value = errorMessage(error, '邮件日志加载失败')
  } finally {
    logsLoading.value = false
  }
}

function handleTabChange(name: string | number): void {
  if (name === 'accounts') {
    activeTab.value = 'accounts'
    if (!accountsLoaded.value) {
      void loadAccounts()
    }
    return
  }
  activeTab.value = 'logs'
  if (!logsLoaded.value) {
    void loadLogs()
  }
}

function refreshActive(): void {
  if (activeTab.value === 'accounts') {
    void loadAccounts()
  } else {
    void loadLogs()
  }
}

function resetAccountForm(): void {
  Object.assign(accountForm, {
    accountName: '',
    host: '',
    port: 465,
    username: '',
    password: '',
    fromAddr: '',
    ssl: 1,
    status: 0,
  })
}

function openCreateAccount(): void {
  accountEditingId.value = null
  resetAccountForm()
  accountDialogVisible.value = true
}

function openEditAccount(row: AdminMailAccountView): void {
  accountEditingId.value = row.id
  Object.assign(accountForm, {
    accountName: row.accountName,
    host: row.host,
    port: row.port,
    username: row.username,
    password: '',
    fromAddr: row.fromAddr,
    ssl: row.ssl,
    status: row.status,
  })
  accountDialogVisible.value = true
}

function validateAccountForm(): string | null {
  const accountName = accountForm.accountName.trim()
  if (accountName === '' || accountName.length > 64) {
    return '账号名称不能为空且不能超过 64 个字符'
  }
  const host = accountForm.host.trim()
  if (host === '' || host.length > 128) {
    return 'SMTP 主机不能为空且不能超过 128 个字符'
  }
  if (
    !Number.isInteger(accountForm.port) ||
    accountForm.port < 1 ||
    accountForm.port > 65535
  ) {
    return '端口必须是 1~65535 的整数'
  }
  const username = accountForm.username.trim()
  if (username === '' || username.length > 128) {
    return 'SMTP 用户名不能为空且不能超过 128 个字符'
  }
  if (accountForm.password.length > 256) {
    return '密码不能超过 256 个字符'
  }
  if (accountEditingId.value === null && accountForm.password === '') {
    return '新增邮件账号必须填写密码'
  }
  const fromAddr = accountForm.fromAddr.trim()
  if (fromAddr === '' || fromAddr.length > 128 || !EMAIL_PATTERN.test(fromAddr)) {
    return '发件人地址格式不正确'
  }
  if (accountForm.ssl !== 0 && accountForm.ssl !== 1) {
    return 'SSL 取值不正确'
  }
  if (accountForm.status !== 0 && accountForm.status !== 1) {
    return '状态不正确'
  }
  return null
}

function buildAccountPayload(): SaveMailAccountPayload {
  return {
    accountName: accountForm.accountName.trim(),
    host: accountForm.host.trim(),
    port: accountForm.port,
    username: accountForm.username.trim(),
    password:
      accountEditingId.value !== null && accountForm.password === ''
        ? null
        : accountForm.password,
    fromAddr: accountForm.fromAddr.trim(),
    ssl: accountForm.ssl,
    status: accountForm.status,
  }
}

async function submitAccount(): Promise<void> {
  const invalid = validateAccountForm()
  if (invalid !== null) {
    ElMessage.warning(invalid)
    return
  }

  const payload = buildAccountPayload()
  accountSubmitting.value = true
  accountsActionError.value = ''
  try {
    if (accountEditingId.value === null) {
      await createAdminMailAccount(payload)
      ElMessage.success('邮件账号已创建')
    } else {
      await updateAdminMailAccount(accountEditingId.value, payload)
      ElMessage.success('邮件账号已更新')
    }
    accountDialogVisible.value = false
    await loadAccounts()
  } catch (error) {
    accountsActionError.value = errorMessage(error, '邮件账号保存失败')
  } finally {
    accountSubmitting.value = false
  }
}

async function openTest(row?: AdminMailAccountView): Promise<void> {
  if (!accountsLoaded.value) {
    await loadAccounts()
  }
  if (accountsError.value !== '') {
    ElMessage.warning('邮件账号加载失败，暂时无法发送测试邮件')
    return
  }

  const candidate = row ?? accounts.value.find((item) => item.status === 0)
  if (!candidate) {
    ElMessage.warning('没有可用的邮件账号，请先在“邮件账号”中新增并启用账号')
    return
  }
  if (candidate.status !== 0) {
    ElMessage.warning('该邮件账号已停用，不能发送测试邮件')
    return
  }

  testForm.accountId = candidate.id
  testForm.toAddr = ''
  testError.value = ''
  testDialogVisible.value = true
}

async function submitTest(): Promise<void> {
  const account = accounts.value.find((item) => item.id === testForm.accountId)
  if (!account) {
    testError.value = '请选择邮件账号'
    return
  }
  if (account.status !== 0) {
    testError.value = '该邮件账号已停用，不能发送测试邮件'
    return
  }
  const toAddr = testForm.toAddr.trim()
  if (!EMAIL_PATTERN.test(toAddr)) {
    testError.value = '收件人邮箱格式不正确'
    return
  }

  testSubmitting.value = true
  testError.value = ''
  try {
    await sendAdminTestMail({ accountId: account.id, toAddr })
    ElMessage.success('测试邮件已提交')
    testDialogVisible.value = false
    activeTab.value = 'logs'
    logsLoaded.value = true
    await loadLogs()
  } catch (error) {
    testError.value = errorMessage(error, '测试邮件发送失败')
    // 发送失败后端也会落一条 status=2 的日志，刷新但不关闭弹框
    activeTab.value = 'logs'
    logsLoaded.value = true
    void loadLogs()
  } finally {
    testSubmitting.value = false
  }
}

function searchLogs(): void {
  logsPageNum.value = 1
  void loadLogs()
}

function resetLogFilters(): void {
  logFilters.status = undefined
  logsPageNum.value = 1
  void loadLogs()
}

function handleLogPageChange(page: number): void {
  logsPageNum.value = page
  void loadLogs()
}

function handleLogSizeChange(size: number): void {
  logsPageSize.value = size
  logsPageNum.value = 1
  void loadLogs()
}

onMounted(() => {
  void loadLogs()
})
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>邮件管理</h2>
        <p>
          发送日志：<span class="mono">GET /api/v1/admin/mails/logs</span>；
          邮件账号：<span class="mono">GET /api/v1/admin/mails/accounts</span>。
          后端只提供测试发送 <span class="mono">POST /api/v1/admin/mails/test</span>，
          主题和正文固定；日志接口不支持时间范围筛选，也不返回正文。账号无删除接口。
        </p>
      </div>
      <div class="toolbar">
        <el-button
          :icon="Refresh"
          :loading="logsLoading || accountsLoading"
          @click="refreshActive"
        >
          刷新当前 Tab
        </el-button>
        <el-button type="primary" :icon="Plus" @click="openTest()">测试发送</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="发送日志" name="logs">
          <el-alert
            v-if="logsError"
            type="error"
            :closable="false"
            show-icon
            :title="logsError"
            class="tab-alert"
          />

          <div class="toolbar filter-bar">
            <el-select
              v-model="logFilters.status"
              clearable
              placeholder="全部状态"
              style="width: 130px"
            >
              <el-option
                v-for="option in MAIL_LOG_STATUS_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-button type="primary" @click="searchLogs">查询</el-button>
            <el-button @click="resetLogFilters">重置</el-button>
            <span class="muted">接口不支持日期范围筛选</span>
          </div>

          <el-table v-loading="logsLoading" :data="logs" border stripe row-key="id">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column label="收件人" min-width="180">
              <template #default="{ row }">{{ row.toAddr }}</template>
            </el-table-column>
            <el-table-column label="主题" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ row.subject || '—' }}</template>
            </el-table-column>
            <el-table-column label="模板" width="100">
              <template #default="{ row }">
                <span class="mono">{{ row.templateCode || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="85" align="center">
              <template #default="{ row }">
                <el-tag :type="tagOf(MAIL_LOG_STATUS, row.status)" size="small">
                  {{ labelOf(MAIL_LOG_STATUS, row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="失败原因" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ row.errorMsg || '—' }}</template>
            </el-table-column>
            <el-table-column label="发送时间" min-width="165">
              <template #default="{ row }">{{ formatDateTime(row.sentAt) }}</template>
            </el-table-column>
            <el-table-column label="创建时间" min-width="165">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>

            <template #empty>
              <el-empty description="当前筛选条件下没有邮件日志。" :image-size="80" />
            </template>
          </el-table>

          <el-pagination
            class="pagination"
            layout="total, sizes, prev, pager, next"
            :total="logsTotal"
            :page-size="logsPageSize"
            :page-sizes="PAGE_SIZE_OPTIONS"
            :current-page="logsPageNum"
            @current-change="handleLogPageChange"
            @size-change="handleLogSizeChange"
          />
        </el-tab-pane>

        <el-tab-pane label="邮件账号" name="accounts">
          <el-alert
            v-if="accountsError"
            type="error"
            :closable="false"
            show-icon
            :title="accountsError"
            class="tab-alert"
          />
          <el-alert
            v-if="accountsActionError"
            type="error"
            :closable="false"
            show-icon
            :title="accountsActionError"
            class="tab-alert"
          />

          <div class="toolbar filter-bar">
            <el-input
              v-model="accountKeyword"
              clearable
              placeholder="账号/主机/用户名/发件人（本地过滤）"
              style="width: 280px"
            />
            <el-button type="primary" :icon="Plus" @click="openCreateAccount">
              新建账号
            </el-button>
            <span class="muted">共 {{ filteredAccounts.length }} 个账号</span>
          </div>

          <el-table
            v-loading="accountsLoading"
            :data="filteredAccounts"
            border
            stripe
            row-key="id"
          >
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="accountName" label="账号名称" min-width="140" />
            <el-table-column prop="host" label="SMTP 主机" min-width="160" />
            <el-table-column prop="port" label="端口" width="70" align="right" />
            <el-table-column prop="username" label="用户名" min-width="150" />
            <el-table-column prop="fromAddr" label="发件人" min-width="180" />
            <el-table-column label="SSL" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="tagOf(MAIL_SSL, row.ssl)" size="small">
                  {{ labelOf(MAIL_SSL, row.ssl) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="tagOf(SYSTEM_STATUS, row.status)" size="small">
                  {{ labelOf(SYSTEM_STATUS, row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" min-width="165">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEditAccount(row)">
                  编辑
                </el-button>
                <el-button
                  link
                  type="primary"
                  :disabled="row.status !== 0"
                  @click="openTest(row)"
                >
                  测试发送
                </el-button>
              </template>
            </el-table-column>

            <template #empty>
              <el-empty description="没有邮件账号，请先新建。" :image-size="80" />
            </template>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog
      v-model="accountDialogVisible"
      :title="accountEditingId === null ? '新建邮件账号' : '编辑邮件账号'"
      width="620px"
      :close-on-click-modal="false"
    >
      <el-form label-width="110px">
        <el-form-item label="账号名称" required>
          <el-input v-model="accountForm.accountName" maxlength="64" />
        </el-form-item>
        <el-form-item label="SMTP 主机" required>
          <el-input v-model="accountForm.host" maxlength="128" placeholder="例如 smtp.example.com" />
        </el-form-item>
        <el-form-item label="端口" required>
          <el-input-number
            v-model="accountForm.port"
            :min="1"
            :max="65535"
            :precision="0"
            :controls="false"
          />
        </el-form-item>
        <el-form-item label="SMTP 用户名" required>
          <el-input v-model="accountForm.username" maxlength="128" />
        </el-form-item>
        <el-form-item label="SMTP 密码" :required="accountEditingId === null">
          <el-input
            v-model="accountForm.password"
            type="password"
            show-password
            maxlength="256"
            :placeholder="accountEditingId === null ? '必填' : '留空保持原密码'"
          />
        </el-form-item>
        <el-form-item label="发件人地址" required>
          <el-input v-model="accountForm.fromAddr" maxlength="128" />
        </el-form-item>
        <el-form-item label="SSL">
          <el-select v-model="accountForm.ssl" class="full-width">
            <el-option
              v-for="option in MAIL_SSL_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="accountForm.status" class="full-width">
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
        <el-button @click="accountDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="accountSubmitting"
          @click="submitAccount"
        >
          {{ accountEditingId === null ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="testDialogVisible"
      title="发送测试邮件"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="后端只提供测试发送接口，主题和正文固定为 SoloEats SMTP 测试邮件，不能自定义。"
        class="tab-alert"
      />
      <el-alert
        v-if="testError"
        type="error"
        :closable="false"
        show-icon
        :title="testError"
        class="tab-alert"
      />
      <el-form label-width="90px">
        <el-form-item label="邮件账号" required>
          <el-select
            v-model="testForm.accountId"
            class="full-width"
            placeholder="选择启用账号"
          >
            <el-option
              v-for="account in accounts"
              :key="account.id"
              :label="`${account.accountName} · ${account.fromAddr}${account.status === 0 ? '' : '（已停用）'}`"
              :value="account.id"
              :disabled="account.status !== 0"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="收件人" required>
          <el-input
            v-model="testForm.toAddr"
            maxlength="128"
            placeholder="name@example.com"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="testDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="testSubmitting" @click="submitTest">
          发送测试邮件
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filter-bar {
  margin-bottom: 14px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.full-width {
  width: 100%;
}

.tab-alert {
  margin-bottom: 12px;
}
</style>
