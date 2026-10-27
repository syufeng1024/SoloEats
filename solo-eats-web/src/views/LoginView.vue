<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, Shop, UserFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { ROLES, type ConsoleRole } from '@/constants/roles'
import type { LoginPayload } from '@/api/types'
import { homeForRole, isRedirectAllowed } from '@/router/home'

interface LoginForm {
  role: ConsoleRole
  merchantId: string
  shopId: string
  userId: string
}

const formRef = ref<FormInstance>()
const submitting = ref(false)
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const form = reactive<LoginForm>({
  role: ROLES.MERCHANT,
  merchantId: '1',
  shopId: '1',
  userId: '1',
})

function intValidator(label: string, required: boolean, allowZero: boolean) {
  const pattern = allowZero ? /^(0|[1-9]\d*)$/ : /^[1-9]\d*$/
  return (_rule: unknown, value: string, callback: (error?: Error) => void): void => {
    const raw = (value ?? '').trim()
    if (!raw) {
      if (required) {
        callback(new Error(`${label}不能为空`))
      } else {
        callback()
      }
      return
    }
    if (!pattern.test(raw)) {
      callback(new Error(allowZero ? `${label}必须是非负整数` : `${label}必须是正整数`))
      return
    }
    callback()
  }
}

const rules: FormRules = {
  merchantId: [{ validator: intValidator('商家 ID', true, false), trigger: 'blur' }],
  shopId: [{ validator: intValidator('门店 ID', false, false), trigger: 'blur' }],
  userId: [{ validator: intValidator('管理员用户 ID', true, false), trigger: 'blur' }],
}

async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  const payload: LoginPayload = { role: form.role }
  if (form.role === ROLES.MERCHANT) {
    payload.merchantId = Number(form.merchantId.trim())
    if (form.shopId.trim()) {
      payload.shopId = Number(form.shopId.trim())
    }
  } else {
    // 管理员/骑手必须带真实 userId：后端菜单接口按 userId 查角色菜单，
    // 传 0 或省略会让 /api/v1/auth/menus 返回空、侧边栏退回硬编码菜单。
    payload.userId = Number(form.userId.trim())
  }

  submitting.value = true
  try {
    const account = await auth.login(payload)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const target =
      redirect && isRedirectAllowed(redirect, account.roles)
        ? redirect
        : homeForRole(account.roles)
    await router.replace(target)
  } catch {
    // 错误提示已由 Axios 拦截器统一处理
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="login-brand">
      <div class="brand-row">
        <div class="brand-mark">SE</div>
        <span class="brand-name">SoloEats</span>
      </div>

      <h1>商家履约与平台运营控制台</h1>
      <p class="brand-desc">
        商家侧覆盖接单、备餐、拒单与结算对账；平台侧覆盖结算账单生成与运营监控入口。
        所有请求通过 <span class="mono">/api</span> 同源代理转发至
        <span class="mono">http://localhost:8080</span>。
      </p>

      <ul class="brand-list">
        <li>
          <span class="dot" />
          <span class="li-text">
            订单操作直接对接订单状态机：接单
            <span class="mono">WAIT_ACCEPT → PREPARING</span>，备餐完成
            <span class="mono">PREPARING → WAIT_RIDER_PICKUP</span>。
          </span>
        </li>
        <li>
          <span class="dot" />
          <span class="li-text">
            结算列表读取 <span class="mono">GET /api/v1/merchant/settlements</span>，
            金额字段单位为分，前端统一换算为元展示。
          </span>
        </li>
        <li>
          <span class="dot" />
          <span class="li-text">
            JWT 由登录接口签发，Axios 请求拦截器自动附加
            <span class="mono">Authorization: Bearer &lt;token&gt;</span>。
          </span>
        </li>
      </ul>
    </section>

    <section class="login-panel">
      <el-card shadow="never" class="login-card">
        <h2>登录</h2>
        <p class="login-sub">选择角色并填写身份 ID，后端签发有效期 120 分钟的 JWT。</p>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="handleSubmit"
        >
          <el-form-item label="登录角色" prop="role">
            <el-radio-group v-model="form.role" class="role-group">
              <el-radio-button :value="ROLES.MERCHANT">
                <el-icon><Shop /></el-icon>
                商家
              </el-radio-button>
              <el-radio-button :value="ROLES.ADMIN">
                <el-icon><UserFilled /></el-icon>
                平台管理员
              </el-radio-button>
            </el-radio-group>
          </el-form-item>

          <template v-if="form.role === ROLES.MERCHANT">
            <el-form-item label="商家 ID" prop="merchantId">
              <el-input
                v-model="form.merchantId"
                placeholder="例如 1"
                inputmode="numeric"
                clearable
              />
            </el-form-item>
            <el-form-item label="门店 ID（可选）" prop="shopId">
              <el-input
                v-model="form.shopId"
                placeholder="例如 1"
                inputmode="numeric"
                clearable
              />
            </el-form-item>
          </template>

          <el-form-item v-else label="管理员用户 ID（可选，留空按 0 处理）" prop="userId">
            <el-input v-model="form.userId" placeholder="例如 0" inputmode="numeric" clearable />
          </el-form-item>

          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="登录接口仅在 local profile 生效"
            description="POST /api/v1/auth/login 由 AuthController 上的 @Profile(&quot;local&quot;) 提供，不校验密码；接入生产账号体系时需替换。"
            class="login-alert"
          />

          <el-button
            type="primary"
            native-type="submit"
            :loading="submitting"
            :icon="Lock"
            class="submit-btn"
          >
            登录
          </el-button>
        </el-form>

        <p class="demo-hint">
          联调示例：商家 ID = 1、门店 ID = 1，对应
          <span class="mono">V2__sku_and_demo_data.sql</span> 中的演示 SKU。
        </p>
      </el-card>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(400px, 520px);
  background: #f6f4f1;
}

.login-brand {
  padding: 64px 8vw;
  background:
    radial-gradient(circle at 18% 20%, rgba(232, 89, 12, 0.16), transparent 42%),
    radial-gradient(circle at 82% 76%, rgba(24, 33, 29, 0.12), transparent 46%),
    #f6f4f1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.brand-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 40px;
}

.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: var(--el-color-primary);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 15px;
}

.brand-name {
  font-weight: 600;
  letter-spacing: 0.4px;
}

.login-brand h1 {
  font-size: clamp(30px, 3.6vw, 44px);
  line-height: 1.18;
  letter-spacing: -0.6px;
  max-width: 620px;
}

.brand-desc {
  margin-top: 20px;
  max-width: 560px;
  color: #4b5563;
  font-size: 14px;
  line-height: 1.8;
}

.brand-list {
  margin: 32px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 600px;
}

.brand-list li {
  display: flex;
  gap: 10px;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.7;
}

.li-text {
  display: block;
  flex: 1;
  min-width: 0;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-color-primary);
  margin-top: 8px;
  flex: 0 0 6px;
}

.login-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 32px;
  background: #fff;
  border-left: 1px solid var(--app-border);
}

.login-card {
  width: 100%;
  max-width: 420px;
  border: none;
}

.login-card :deep(.el-card__body) {
  padding: 0;
}

.login-card h2 {
  font-size: 24px;
  font-weight: 600;
}

.login-sub {
  margin-top: 8px;
  margin-bottom: 26px;
  color: var(--app-muted);
  font-size: 13px;
  line-height: 1.6;
}

.role-group {
  width: 100%;
}

.role-group :deep(.el-radio-button) {
  flex: 1;
}

.role-group :deep(.el-radio-button__inner) {
  width: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.login-alert {
  margin: 6px 0 18px;
}

.login-alert :deep(.el-alert__description) {
  line-height: 1.6;
}

.submit-btn {
  width: 100%;
  height: 42px;
  font-size: 15px;
}

.demo-hint {
  margin-top: 18px;
  font-size: 12px;
  color: var(--app-muted);
  line-height: 1.6;
}

@media (max-width: 940px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-brand {
    padding: 40px 24px 8px;
  }

  .login-brand h1 {
    font-size: 28px;
  }

  .brand-list {
    display: none;
  }

  .login-panel {
    border-left: none;
    padding: 24px;
    align-items: flex-start;
  }
}
</style>
