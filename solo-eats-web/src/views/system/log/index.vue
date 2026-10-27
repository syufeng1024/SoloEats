<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import {
  listAdminLoginLogs,
  listAdminOperLogs,
  type AdminLoginLogView,
  type AdminOperLogView,
} from '@/api/system-admin'
import {
  LOGIN_SUCCESS,
  LOGIN_SUCCESS_OPTIONS,
  LOGIN_TYPE,
  labelOf,
  tagOf,
} from '@/constants/systemStatus'
import { formatDateTime } from '@/utils/datetime'
import { errorMessage } from '@/views/report/helpers'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const activeTab = ref('oper')
const operLoaded = ref(false)
const loginLoaded = ref(false)

const operLoading = ref(false)
const operError = ref('')
const operLogs = ref<AdminOperLogView[]>([])
const operTotal = ref(0)
const operPageNum = ref(1)
const operPageSize = ref(20)
const operFilters = reactive({
  module: '',
  operatorId: undefined as number | undefined,
  resultCodeText: '',
  dateRange: null as string[] | null,
})

const loginLoading = ref(false)
const loginError = ref('')
const loginLogs = ref<AdminLoginLogView[]>([])
const loginTotal = ref(0)
const loginPageNum = ref(1)
const loginPageSize = ref(20)
const loginFilters = reactive({
  username: '',
  success: undefined as number | undefined,
  dateRange: null as string[] | null,
})

function parseResultCode(): number | undefined | null {
  const text = operFilters.resultCodeText.trim()
  if (text === '') {
    return undefined
  }
  if (!/^\d+$/.test(text)) {
    return null
  }
  return Number(text)
}

async function loadOper(): Promise<void> {
  const resultCode = parseResultCode()
  if (resultCode === null) {
    operError.value = '结果码必须是非负整数'
    return
  }

  operLoading.value = true
  operError.value = ''
  try {
    const result = await listAdminOperLogs({
      module: operFilters.module.trim() || undefined,
      operatorId: operFilters.operatorId,
      resultCode,
      dateFrom: operFilters.dateRange?.[0],
      dateTo: operFilters.dateRange?.[1],
      pageNum: operPageNum.value,
      pageSize: operPageSize.value,
    })
    operLogs.value = result.list
    operTotal.value = result.total
    operLoaded.value = true
  } catch (error) {
    operLogs.value = []
    operTotal.value = 0
    operError.value = errorMessage(error, '操作日志加载失败')
  } finally {
    operLoading.value = false
  }
}

async function loadLogin(): Promise<void> {
  loginLoading.value = true
  loginError.value = ''
  try {
    const result = await listAdminLoginLogs({
      username: loginFilters.username.trim() || undefined,
      success:
        typeof loginFilters.success === 'number'
          ? loginFilters.success
          : undefined,
      dateFrom: loginFilters.dateRange?.[0],
      dateTo: loginFilters.dateRange?.[1],
      pageNum: loginPageNum.value,
      pageSize: loginPageSize.value,
    })
    loginLogs.value = result.list
    loginTotal.value = result.total
    loginLoaded.value = true
  } catch (error) {
    loginLogs.value = []
    loginTotal.value = 0
    loginError.value = errorMessage(error, '登录日志加载失败')
  } finally {
    loginLoading.value = false
  }
}

function handleTabChange(name: string | number): void {
  if (name === 'login') {
    activeTab.value = 'login'
    if (!loginLoaded.value) {
      void loadLogin()
    }
    return
  }
  activeTab.value = 'oper'
  if (!operLoaded.value) {
    void loadOper()
  }
}

function refreshActive(): void {
  if (activeTab.value === 'login') {
    void loadLogin()
  } else {
    void loadOper()
  }
}

function searchOper(): void {
  operPageNum.value = 1
  void loadOper()
}

function onlySuccess(): void {
  operFilters.resultCodeText = '0'
  searchOper()
}

function resetOperFilters(): void {
  operFilters.module = ''
  operFilters.operatorId = undefined
  operFilters.resultCodeText = ''
  operFilters.dateRange = null
  operPageNum.value = 1
  void loadOper()
}

function handleOperPageChange(page: number): void {
  operPageNum.value = page
  void loadOper()
}

function handleOperSizeChange(size: number): void {
  operPageSize.value = size
  operPageNum.value = 1
  void loadOper()
}

function searchLogin(): void {
  loginPageNum.value = 1
  void loadLogin()
}

function resetLoginFilters(): void {
  loginFilters.username = ''
  loginFilters.success = undefined
  loginFilters.dateRange = null
  loginPageNum.value = 1
  void loadLogin()
}

function handleLoginPageChange(page: number): void {
  loginPageNum.value = page
  void loadLogin()
}

function handleLoginSizeChange(size: number): void {
  loginPageSize.value = size
  loginPageNum.value = 1
  void loadLogin()
}

function prettyJson(value: string | null): string {
  if (!value) {
    return '—'
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function operResultLabel(code: number | null | undefined): string {
  return code === 0 ? '成功' : `失败（${code ?? '—'}）`
}

function operResultTag(code: number | null | undefined): 'success' | 'danger' {
  return code === 0 ? 'success' : 'danger'
}

function operatorText(row: AdminOperLogView): string {
  if (!row.operatorName && row.operatorId === null) {
    return '—'
  }
  const name = row.operatorName ?? '—'
  return row.operatorId === null ? name : `${name} #${row.operatorId}`
}

function requestText(row: AdminOperLogView): string {
  return `${row.requestMethod ?? '—'} ${row.requestUri ?? '—'}`
}

onMounted(() => {
  void loadOper()
})
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>日志中心</h2>
        <p>
          操作日志 <span class="mono">GET /api/v1/admin/logs/oper</span> 与登录日志
          <span class="mono">GET /api/v1/admin/logs/login</span> 均为只读。
          操作日志不记录返回体，模块名需精确匹配，操作人只能按 ID 筛选；
          失败码各不相同，无法一键筛选“全部失败”。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="operLoading || loginLoading" @click="refreshActive">
          刷新当前 Tab
        </el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="操作日志" name="oper">
          <el-alert
            v-if="operError"
            type="error"
            :closable="false"
            show-icon
            :title="operError"
            class="log-alert"
          />

          <div class="toolbar filter-bar">
            <el-input
              v-model="operFilters.module"
              clearable
              placeholder="模块（精确匹配）"
              style="width: 170px"
              @keyup.enter="searchOper"
            />
            <el-input-number
              v-model="operFilters.operatorId"
              :min="1"
              :precision="0"
              :controls="false"
              placeholder="操作人 ID"
              style="width: 130px"
            />
            <el-input
              v-model="operFilters.resultCodeText"
              clearable
              placeholder="结果码，0=成功"
              style="width: 150px"
              @keyup.enter="searchOper"
            />
            <el-button @click="onlySuccess">仅成功</el-button>
            <el-date-picker
              v-model="operFilters.dateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 240px"
            />
            <el-button type="primary" @click="searchOper">查询</el-button>
            <el-button @click="resetOperFilters">重置</el-button>
          </div>

          <el-table
            v-loading="operLoading"
            :data="operLogs"
            border
            stripe
            row-key="id"
          >
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="expand-wrap">
                  <el-collapse>
                    <el-collapse-item title="请求参数（已脱敏、超长截断）" name="params">
                      <pre class="code-block">{{ prettyJson(row.requestParams) }}</pre>
                    </el-collapse-item>
                    <el-collapse-item title="错误与链路信息" name="meta">
                      <el-descriptions :column="2" border size="small">
                        <el-descriptions-item label="HTTP 方法">
                          {{ row.requestMethod || '—' }}
                        </el-descriptions-item>
                        <el-descriptions-item label="URL">
                          {{ row.requestUri || '—' }}
                        </el-descriptions-item>
                        <el-descriptions-item label="方法">
                          {{ row.method || '—' }}
                        </el-descriptions-item>
                        <el-descriptions-item label="操作人类型">
                          {{ row.operatorType || '—' }}
                        </el-descriptions-item>
                        <el-descriptions-item label="Trace ID">
                          {{ row.traceId || '—' }}
                        </el-descriptions-item>
                        <el-descriptions-item label="User-Agent">
                          {{ row.ua || '—' }}
                        </el-descriptions-item>
                        <el-descriptions-item label="错误信息" :span="2">
                          {{ row.errorMsg || '—' }}
                        </el-descriptions-item>
                      </el-descriptions>
                    </el-collapse-item>
                  </el-collapse>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="165">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column prop="module" label="模块" min-width="110" />
            <el-table-column prop="action" label="动作" min-width="110" />
            <el-table-column label="操作人" min-width="150">
              <template #default="{ row }">{{ operatorText(row) }}</template>
            </el-table-column>
            <el-table-column label="请求" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="mono">{{ requestText(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="IP" width="130">
              <template #default="{ row }">{{ row.ip || '—' }}</template>
            </el-table-column>
            <el-table-column label="结果" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="operResultTag(row.resultCode)" size="small">
                  {{ operResultLabel(row.resultCode) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="耗时" width="85" align="right">
              <template #default="{ row }">{{ row.costMs }} ms</template>
            </el-table-column>
            <el-table-column label="Trace ID" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="mono">{{ row.traceId || '—' }}</span>
              </template>
            </el-table-column>

            <template #empty>
              <el-empty description="当前筛选条件下没有操作日志。" :image-size="80" />
            </template>
          </el-table>

          <el-pagination
            class="pagination"
            layout="total, sizes, prev, pager, next"
            :total="operTotal"
            :page-size="operPageSize"
            :page-sizes="PAGE_SIZE_OPTIONS"
            :current-page="operPageNum"
            @current-change="handleOperPageChange"
            @size-change="handleOperSizeChange"
          />
        </el-tab-pane>

        <el-tab-pane label="登录日志" name="login">
          <el-alert
            v-if="loginError"
            type="error"
            :closable="false"
            show-icon
            :title="loginError"
            class="log-alert"
          />

          <div class="toolbar filter-bar">
            <el-input
              v-model="loginFilters.username"
              clearable
              placeholder="用户名"
              style="width: 180px"
              @keyup.enter="searchLogin"
            />
            <el-select
              v-model="loginFilters.success"
              clearable
              placeholder="全部结果"
              style="width: 120px"
            >
              <el-option
                v-for="option in LOGIN_SUCCESS_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-date-picker
              v-model="loginFilters.dateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 240px"
            />
            <el-button type="primary" @click="searchLogin">查询</el-button>
            <el-button @click="resetLoginFilters">重置</el-button>
          </div>

          <el-table
            v-loading="loginLoading"
            :data="loginLogs"
            border
            stripe
            row-key="id"
          >
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="expand-wrap">
                  <el-collapse>
                    <el-collapse-item title="失败原因" name="fail">
                      <pre class="code-block">{{ row.failReason || '—' }}</pre>
                    </el-collapse-item>
                    <el-collapse-item title="User-Agent" name="ua">
                      <pre class="code-block">{{ row.ua || '—' }}</pre>
                    </el-collapse-item>
                  </el-collapse>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="165">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="用户名" min-width="150">
              <template #default="{ row }">{{ row.username || '—' }}</template>
            </el-table-column>
            <el-table-column label="用户 ID" width="90">
              <template #default="{ row }">{{ row.userId ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="登录方式" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="tagOf(LOGIN_TYPE, row.loginType)" size="small">
                  {{ labelOf(LOGIN_TYPE, row.loginType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="结果" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="tagOf(LOGIN_SUCCESS, row.success)" size="small">
                  {{ labelOf(LOGIN_SUCCESS, row.success) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="失败原因" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ row.failReason || '—' }}</template>
            </el-table-column>
            <el-table-column label="IP" width="130">
              <template #default="{ row }">{{ row.ip || '—' }}</template>
            </el-table-column>

            <template #empty>
              <el-empty description="当前筛选条件下没有登录日志。" :image-size="80" />
            </template>
          </el-table>

          <el-pagination
            class="pagination"
            layout="total, sizes, prev, pager, next"
            :total="loginTotal"
            :page-size="loginPageSize"
            :page-sizes="PAGE_SIZE_OPTIONS"
            :current-page="loginPageNum"
            @current-change="handleLoginPageChange"
            @size-change="handleLoginSizeChange"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>
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

.log-alert {
  margin-bottom: 12px;
}

.expand-wrap {
  padding: 8px 16px 16px;
}

.code-block {
  margin: 0;
  padding: 10px;
  max-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
}
</style>
