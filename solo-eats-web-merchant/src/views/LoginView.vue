<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, Shop } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useShopStore } from '@/stores/shop'
import { ROLES, matchesRole } from '@/constants/roles'
import type { LoginPayload } from '@/api/types'
import { homeForRole, isRedirectAllowed } from '@/router/home'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const shop = useShopStore()

const mode = ref<'password' | 'mock'>('password')
const submitting = ref(false)

const passwordForm = reactive({
  username: '',
  password: '',
})

const mockForm = reactive({
  merchantId: '1',
  shopId: '1',
})

function positiveInt(value: string): number | null {
  const raw = value.trim()
  if (!/^[1-9]\d*$/.test(raw)) {
    return null
  }
  const parsed = Number(raw)
  return Number.isSafeInteger(parsed) ? parsed : null
}

function buildPayload(): LoginPayload | null {
  if (mode.value === 'password') {
    const username = passwordForm.username.trim()
    if (!username) {
      ElMessage.warning('请输入商家账号')
      return null
    }
    if (!passwordForm.password) {
      ElMessage.warning('请输入登录密码')
      return null
    }
    return { username, password: passwordForm.password }
  }

  const merchantId = positiveInt(mockForm.merchantId)
  if (merchantId === null) {
    ElMessage.warning('商家 ID 必须是正整数')
    return null
  }
  const payload: LoginPayload = { role: ROLES.MERCHANT, merchantId }
  if (mockForm.shopId.trim()) {
    const shopId = positiveInt(mockForm.shopId)
    if (shopId === null) {
      ElMessage.warning('门店 ID 必须是正整数，或留空')
      return null
    }
    payload.shopId = shopId
  }
  return payload
}

async function handleSubmit(): Promise<void> {
  const payload = buildPayload()
  if (!payload) {
    return
  }

  submitting.value = true
  try {
    shop.reset()
    const account = await auth.login(payload)
    if (!account.roles.some((role) => matchesRole(role, ROLES.MERCHANT))) {
      auth.logout()
      ElMessage.error('该账号不是商家账号，请联系平台开通')
      return
    }

    // 门店列表失败不阻断进入工作台，布局顶部会显示错误并提供刷新
    await shop.loadShops(true).catch(() => undefined)

    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const target =
      redirect && isRedirectAllowed(redirect, account.roles)
        ? redirect
        : homeForRole(account.roles)
    await router.replace(target)
  } catch {
    // 错误提示由 Axios 拦截器统一处理
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
        <span class="brand-name">数创星光外卖 · 商家中心</span>
      </div>

      <h1>接单、备餐、库存与对账</h1>
      <p class="brand-desc">
        独立前端工程，部署在 <span class="mono">/merchant/</span>；与平台运营后台共用
        <span class="mono">/api</span> 后端与 RBAC。登录令牌中的
        <span class="mono">merchantId / shopId</span> 决定可见数据范围。
      </p>

      <ul class="brand-list">
        <li><span class="dot" />订单状态流转由后端状态机校验，前端只按状态展示可执行动作。</li>
        <li><span class="dot" />金额按分存储，页面统一通过共享层 <span class="mono">money.ts</span> 换算。</li>
        <li><span class="dot" />多门店账号可在顶栏切换门店，请求头注入 <span class="mono">X-Shop-Id</span>。</li>
      </ul>
    </section>

    <section class="login-panel">
      <el-card shadow="never" class="login-card">
        <h2>商家登录</h2>
        <p class="login-sub">使用平台分配的商家账号，或本地环境用 ID 快捷登录。</p>

        <el-tabs v-model="mode">
          <el-tab-pane label="账号密码" name="password">
            <el-form label-position="top" @submit.prevent="handleSubmit">
              <el-form-item label="商家账号">
                <el-input
                  v-model="passwordForm.username"
                  placeholder="请输入商家账号"
                  maxlength="64"
                  clearable
                  autocomplete="username"
                />
              </el-form-item>
              <el-form-item label="登录密码">
                <el-input
                  v-model="passwordForm.password"
                  type="password"
                  show-password
                  placeholder="请输入登录密码"
                  maxlength="64"
                  autocomplete="current-password"
                  @keyup.enter="handleSubmit"
                />
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="ID 快捷登录" name="mock">
            <el-form label-position="top" @submit.prevent="handleSubmit">
              <el-form-item label="商家 ID">
                <el-input v-model="mockForm.merchantId" inputmode="numeric" placeholder="例如 1" />
              </el-form-item>
              <el-form-item label="门店 ID（可留空）">
                <el-input v-model="mockForm.shopId" inputmode="numeric" placeholder="例如 1" />
              </el-form-item>
            </el-form>
            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="仅本地环境可用"
              description="该模式调用 POST /api/v1/auth/login 的 role 分支，不校验密码；生产环境由 app.auth.mock-login-enabled=false 关闭。"
            />
          </el-tab-pane>
        </el-tabs>

        <el-button
          type="primary"
          :icon="mode === 'password' ? Lock : Shop"
          :loading="submitting"
          class="submit-btn"
          @click="handleSubmit"
        >
          登录
        </el-button>
      </el-card>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(400px, 520px);
  background: #f2f7f6;
}

.login-brand {
  padding: 64px 8vw;
  background:
    radial-gradient(circle at 16% 18%, rgba(15, 118, 110, 0.2), transparent 44%),
    radial-gradient(circle at 84% 78%, rgba(11, 59, 53, 0.14), transparent 48%),
    #f2f7f6;
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
  max-width: 580px;
  color: #3f5753;
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
  color: #3f5753;
  font-size: 13px;
  line-height: 1.7;
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
  margin-bottom: 12px;
  color: var(--app-muted);
  font-size: 13px;
  line-height: 1.6;
}

.submit-btn {
  width: 100%;
  height: 42px;
  font-size: 15px;
  margin-top: 18px;
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
