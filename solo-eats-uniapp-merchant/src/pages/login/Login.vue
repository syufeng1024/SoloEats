<template>
  <view class="login">
    <view class="login__hero">
      <view class="login__logo">商</view>
      <text class="login__brand">数创星光外卖 · 商家端</text>
      <text class="login__slogan">接单 → 备餐 → 出餐 → 自取核销</text>
    </view>

    <view class="mt-card login__form">
      <view class="login__field">
        <text class="login__label">账号</text>
        <input
          v-model="username"
          class="login__input"
          type="text"
          maxlength="32"
          placeholder="请输入商家账号"
          placeholder-class="mt-placeholder"
        />
      </view>
      <view class="mt-divider" />
      <view class="login__field">
        <text class="login__label">密码</text>
        <input
          v-model="password"
          class="login__input"
          type="text"
          :password="!showPassword"
          maxlength="32"
          placeholder="请输入登录密码"
          placeholder-class="mt-placeholder"
          @confirm="handleLogin"
        />
        <text class="login__eye" @click="showPassword = !showPassword">
          {{ showPassword ? '隐藏' : '显示' }}
        </text>
      </view>
    </view>

    <view class="login__demo" @click="fillDemoAccount">
      测试账号 merchant001 / Merchant@123，点此填入
    </view>

    <view v-if="loadError" class="login__error">
      <text class="login__error-text">{{ loadError }}</text>
      <text class="login__error-retry" @click="reloadShops">重新加载门店</text>
    </view>

    <button class="mt-btn mt-btn--primary login__submit" :disabled="submitting" @click="handleLogin">
      {{ submitting ? '登录中…' : '登 录' }}
    </button>

    <view class="login__tip">
      仅限商家经营人员使用。登录后按账号绑定的门店展示订单与商品数据。
    </view>

    <!-- 多门店选择 -->
    <view v-if="shopPickerVisible" class="mt-mask" @click="closePicker" />
    <view v-if="shopPickerVisible" class="mt-sheet">
      <view class="mt-between">
        <text class="mt-sheet__title">选择要经营的门店</text>
        <text class="login__sheet-close" @click="closePicker">关闭</text>
      </view>
      <view class="mt-sheet__tip">
        共 {{ shop.shops.length }} 家门店，选中后可随时在工作台右上角切换。
      </view>

      <scroll-view scroll-y class="login__shop-list">
        <view
          v-for="item in shop.shops"
          :key="item.id"
          class="login__shop"
          :class="{ 'is-active': item.id === pendingShopId }"
          @click="pendingShopId = item.id"
        >
          <view class="login__shop-main">
            <text class="login__shop-name">{{ item.shopName }}</text>
            <text class="login__shop-addr">{{ item.address || '地址待维护' }}</text>
          </view>
          <view class="login__shop-meta">
            <text class="mt-tag" :class="item.status === 1 ? 'mt-tag--ok' : 'mt-tag--off'">
              {{ item.statusText || '状态未知' }}
            </text>
            <text v-if="item.id === auth.shopId" class="mt-tag mt-tag--info">账号绑定</text>
          </view>
        </view>
      </scroll-view>

      <view v-if="hasOtherShop" class="login__sheet-warn">
        该账号令牌绑定的是「{{ boundShopName || '未绑定门店' }}」；切换到其它门店后，
        后端仍按令牌门店校验，若提示「无权访问」请改用该门店账号登录。
      </view>

      <view class="mt-sheet__actions">
        <button class="mt-btn mt-btn--plain" @click="closePicker">取消</button>
        <button
          class="mt-btn mt-btn--primary"
          :disabled="pendingShopId === null"
          @click="confirmShop"
        >
          进入工作台
        </button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { ApiError } from '@/api/http'
import { useAuthStore, hasRoleFamily } from '@/stores/auth'
import { useShopStore } from '@/stores/shop'
import type { LoginVO } from '@/api/types'

/**
 * 商家登录。
 * 登录成功 → 拉门店列表：
 * - 仅 1 家：直接进工作台
 * - 多家：弹门店选择（选中写 store，后续请求带 shopId / X-Shop-Id）
 * - 0 家：停留在登录页并提示联系运营
 */

const TAB_PAGES = ['/pages/home/Home', '/pages/order/List', '/pages/product/List']

const auth = useAuthStore()
const shop = useShopStore()

const username = ref('')
const password = ref('')
const showPassword = ref(false)
const submitting = ref(false)
const shopLoading = ref(false)
const shopPickerVisible = ref(false)
const pendingShopId = ref<number | null>(null)
const loadError = ref('')

let navigating = false
let redirect = ''

const hasOtherShop = computed(() => {
  if (!auth.shopId) {
    return shop.shops.length > 1
  }
  return shop.shops.some((item) => item.id !== auth.shopId)
})

const boundShopName = computed(() => {
  if (!auth.shopId) {
    return ''
  }
  return shop.shops.find((item) => item.id === auth.shopId)?.shopName || ''
})

onLoad((options) => {
  const raw = options?.redirect
  redirect = raw ? decodeURIComponent(String(raw)) : ''
})

onShow(() => {
  // 首屏：已登录且有当前门店直接放行，否则补拉门店
  if (!auth.isLogin || submitting.value || navigating || shopPickerVisible.value) {
    return
  }
  if (shop.currentShopId) {
    navigateAfterLogin()
    return
  }
  void prepareShops(false)
})

function toast(message: string): void {
  uni.showToast({ title: message, icon: 'none', duration: 2400 })
}

function fillDemoAccount(): void {
  username.value = 'merchant001'
  password.value = 'Merchant@123'
  showPassword.value = true
}

function isMerchantAccount(result: LoginVO): boolean {
  return hasRoleFamily(result.roles, 'MERCHANT')
}

async function handleLogin(): Promise<void> {
  if (submitting.value || navigating) {
    return
  }
  if (!username.value.trim()) {
    toast('请输入商家账号')
    return
  }
  if (!password.value) {
    toast('请输入登录密码')
    return
  }

  submitting.value = true
  loadError.value = ''
  try {
    const result = await auth.login(username.value, password.value)
    if (!isMerchantAccount(result)) {
      auth.logout()
      shop.reset()
      toast('该账号不是商家账号，请联系平台开通')
      return
    }
    // 新登录不沿用上次选择，强制按本次门店列表规则处理
    shop.selectShop(null)
    await prepareShops(true)
  } catch (error) {
    // http 层已统一提示
  } finally {
    submitting.value = false
  }
}

/**
 * @param freshLogin true 表示刚完成登录：多门店时弹选择；
 *                   false 表示页面恢复登录态：有店铺就直接进，不重复打扰
 */
async function prepareShops(freshLogin: boolean): Promise<void> {
  if (shopLoading.value) {
    return
  }
  shopLoading.value = true
  loadError.value = ''
  try {
    const list = await shop.loadShops(true)
    if (list.length === 0) {
      loadError.value = '当前账号未关联任何门店，请联系平台运营绑定门店后再登录'
      return
    }
    if (freshLogin && list.length > 1) {
      pendingShopId.value = shop.currentShopId ?? list[0].id
      shopPickerVisible.value = true
      return
    }
    if (shop.currentShopId === null) {
      const preferred = list.find((item) => item.id === auth.shopId) || list[0]
      shop.selectShop(preferred.id)
    }
    navigateAfterLogin()
  } catch (error) {
    // 旧 token 仍有效但不属于商家（或缺少 merchantId）：清掉登录态，避免停在「无权访问」
    if (error instanceof ApiError && (error.code === 10004 || error.code === 10003)) {
      auth.logout()
      shop.reset()
      loadError.value = '当前登录账号无权访问商家门店，请重新输入商家账号'
      return
    }
    loadError.value = (error as Error)?.message || '门店列表加载失败，请重试'
  } finally {
    shopLoading.value = false
  }
}

function reloadShops(): void {
  void prepareShops(true)
}

function confirmShop(): void {
  if (pendingShopId.value === null) {
    return
  }
  shop.selectShop(pendingShopId.value)
  shopPickerVisible.value = false
  navigateAfterLogin()
}

function closePicker(): void {
  // 未选择门店时关闭：保留 applyList 默认选中的门店，直接进工作台
  shopPickerVisible.value = false
  if (shop.currentShopId !== null) {
    navigateAfterLogin()
    return
  }
  toast('请选择门店后再进入')
}

function navigateAfterLogin(): void {
  if (navigating) {
    return
  }
  navigating = true
  const target = redirect
  redirect = ''
  if (target && !target.startsWith('/pages/login')) {
    const path = target.split('?')[0]
    if (TAB_PAGES.includes(path)) {
      uni.switchTab({ url: path })
    } else {
      uni.reLaunch({ url: target })
    }
    return
  }
  uni.switchTab({ url: '/pages/home/Home' })
}
</script>

<style scoped>
.login {
  min-height: 100vh;
  padding: 0 32rpx 60rpx;
  background: linear-gradient(180deg, #23384d 0%, #f2f4f7 42%);
}

.login__hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 92rpx 0 44rpx;
}

.login__logo {
  width: 120rpx;
  height: 120rpx;
  line-height: 120rpx;
  text-align: center;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #ff9a3c, #ff7a00);
  color: #ffffff;
  font-size: 56rpx;
  font-weight: 700;
}

.login__brand {
  margin-top: 24rpx;
  font-size: 34rpx;
  font-weight: 700;
  color: #ffffff;
}

.login__slogan {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.72);
}

.login__form {
  padding: 4rpx 24rpx;
}

.login__field {
  display: flex;
  align-items: center;
  height: 96rpx;
}

.login__label {
  width: 96rpx;
  font-size: 26rpx;
  font-weight: 600;
  color: #33455f;
}

.login__input {
  flex: 1;
  height: 96rpx;
  font-size: 28rpx;
  color: #1b2432;
}

.login__eye {
  padding-left: 18rpx;
  border-left: 1rpx solid #eef1f5;
  color: #e85d04;
  font-size: 24rpx;
}

.login__demo {
  margin: 20rpx 6rpx 0;
  padding: 14rpx 18rpx;
  border-radius: 8rpx;
  background-color: #fff6ea;
  color: #a2520a;
  font-size: 22rpx;
}

.login__error {
  margin-top: 18rpx;
  padding: 16rpx 18rpx;
  border-radius: 8rpx;
  background-color: #fee4e2;
  font-size: 22rpx;
  color: #b42318;
  line-height: 1.6;
}

.login__error-text {
  display: block;
}

.login__error-retry {
  display: inline-block;
  margin-top: 8rpx;
  color: #d93a2b;
  font-weight: 600;
  text-decoration: underline;
}

.login__submit {
  margin-top: 32rpx;
}

.login__tip {
  margin-top: 26rpx;
  text-align: center;
  font-size: 21rpx;
  color: #8d99aa;
  line-height: 1.7;
}

.login__sheet-close {
  font-size: 24rpx;
  color: #7a8699;
}

.login__shop-list {
  max-height: 44vh;
  margin-top: 16rpx;
}

.login__shop {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 20rpx 16rpx;
  margin-bottom: 12rpx;
  border-radius: 10rpx;
  background-color: #f7f9fc;
  border: 2rpx solid transparent;
}

.login__shop.is-active {
  background-color: #fff6ea;
  border-color: #ff9a3c;
}

.login__shop-main {
  flex: 1;
  min-width: 0;
}

.login__shop-name {
  display: block;
  font-size: 27rpx;
  font-weight: 600;
  color: #1b2432;
}

.login__shop-addr {
  display: block;
  margin-top: 4rpx;
  font-size: 21rpx;
  color: #7a8699;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.login__shop-meta {
  display: flex;
  flex: none;
  gap: 8rpx;
}

.login__sheet-warn {
  margin-top: 6rpx;
  padding: 12rpx 14rpx;
  border-radius: 8rpx;
  background-color: #fff6ea;
  color: #a2520a;
  font-size: 20rpx;
  line-height: 1.6;
}
</style>
