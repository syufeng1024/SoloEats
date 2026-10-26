<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import {
  createShop,
  getShopDetail,
  updateShop,
  updateShopStatus,
  type SaveShopPayload,
} from '@/api/store'
import { DELIVERY_TYPE_OPTIONS, SHOP_STATUS_OPTIONS } from '@/constants/businessStatus'
import { centsToYuan, yuanToFen } from '@/utils/money'

const route = useRoute()
const router = useRouter()

const rawId = route.params.id
const shopId = Number(rawId)
const isEdit = Number.isInteger(shopId) && shopId > 0 && rawId !== undefined

const loading = ref(false)
const submitting = ref(false)
const loadError = ref('')
const originalStatus = ref<number | null>(null)
const currentStatus = ref(0)
const statusText = ref('')

const deliveryTypes = ref<number[]>([1])

const form = reactive({
  merchantId: undefined as number | undefined,
  shopName: '',
  brandId: undefined as number | undefined,
  categoryId: undefined as number | undefined,
  province: '',
  city: '',
  district: '',
  address: '',
  lng: 116.47,
  lat: 39.99,
  contactName: '',
  contactPhone: '',
  logoUrl: '',
  coverUrl: '',
  imageUrlsText: '',
  announcement: '',
  minOrderYuan: 0,
  deliveryFeeYuan: 0,
  packagingFeeYuan: 0,
  prepMinutes: 15,
  deliveryRadiusM: 3000,
  sortWeight: 0,
  settleAccountId: undefined as number | undefined,
})

const pageTitle = computed(() => (isEdit ? '编辑门店' : '新增门店'))

function applyDetail(detail: Awaited<ReturnType<typeof getShopDetail>>): void {
  form.merchantId = detail.merchantId
  form.shopName = detail.shopName
  form.brandId = detail.brandId ?? undefined
  form.categoryId = detail.categoryId ?? undefined
  form.province = detail.province ?? ''
  form.city = detail.city ?? ''
  form.district = detail.district ?? ''
  form.address = detail.address
  form.lng = Number(detail.lng)
  form.lat = Number(detail.lat)
  form.contactName = detail.contactName ?? ''
  form.contactPhone = detail.contactPhone ?? ''
  form.logoUrl = detail.logoUrl ?? ''
  form.coverUrl = detail.coverUrl ?? ''
  form.imageUrlsText = (detail.imageUrls ?? []).join('\n')
  form.announcement = detail.announcement ?? ''
  form.minOrderYuan = centsToYuan(detail.minOrderAmount)
  form.deliveryFeeYuan = centsToYuan(detail.deliveryFee)
  form.packagingFeeYuan = centsToYuan(detail.packagingFee)
  form.prepMinutes = detail.prepMinutes
  form.deliveryRadiusM = detail.deliveryRadiusM
  // ShopDetailView 没有 sortWeight 字段，编辑时无法回显；后端 update 也未处理该字段
  form.sortWeight = 0
  form.settleAccountId = detail.settleAccountId ?? undefined

  deliveryTypes.value = []
  if ((detail.deliveryTypes & 1) === 1) {
    deliveryTypes.value.push(1)
  }
  if ((detail.deliveryTypes & 2) === 2) {
    deliveryTypes.value.push(2)
  }

  originalStatus.value = detail.status
  currentStatus.value = detail.status
  statusText.value = detail.statusText
}

async function load(): Promise<void> {
  if (!isEdit) {
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    applyDetail(await getShopDetail(shopId))
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '门店详情加载失败'
  } finally {
    loading.value = false
  }
}

function parseImageUrls(): string[] {
  return form.imageUrlsText
    .split(/[\n,]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function validate(): string | null {
  if (!form.merchantId || !Number.isInteger(form.merchantId) || form.merchantId <= 0) {
    return '商家 ID 必须是正整数'
  }
  if (!form.shopName.trim()) {
    return '门店名称不能为空'
  }
  if (!form.address.trim()) {
    return '详细地址不能为空'
  }
  if (!Number.isFinite(form.lng) || form.lng < -180 || form.lng > 180) {
    return '经度必须在 -180 ~ 180 之间'
  }
  if (!Number.isFinite(form.lat) || form.lat < -90 || form.lat > 90) {
    return '纬度必须在 -90 ~ 90 之间'
  }
  if (deliveryTypes.value.length === 0) {
    return '至少选择一种履约方式'
  }
  if (form.contactPhone.trim() && !/^1[3-9]\d{9}$/.test(form.contactPhone.trim())) {
    return '联系电话必须是 11 位手机号'
  }
  if (form.minOrderYuan < 0 || form.deliveryFeeYuan < 0 || form.packagingFeeYuan < 0) {
    return '金额字段不能为负数'
  }
  if (!Number.isInteger(form.prepMinutes) || form.prepMinutes < 1 || form.prepMinutes > 180) {
    return '出餐时长必须是 1~180 分钟的整数'
  }
  if (!Number.isInteger(form.deliveryRadiusM)
    || form.deliveryRadiusM < 100
    || form.deliveryRadiusM > 50000) {
    return '配送半径必须是 100~50000 米'
  }
  const images = parseImageUrls()
  if (images.length > 9) {
    return '门店图片最多 9 张'
  }
  if (images.some((url) => url.length > 512)) {
    return '单张图片 URL 不能超过 512 字符'
  }
  if (form.announcement.length > 500) {
    return '门店公告不能超过 500 字'
  }
  return null
}

function toPayload(): SaveShopPayload {
  return {
    merchantId: form.merchantId,
    shopName: form.shopName.trim(),
    brandId: form.brandId,
    categoryId: form.categoryId,
    province: form.province.trim() || undefined,
    city: form.city.trim() || undefined,
    district: form.district.trim() || undefined,
    address: form.address.trim(),
    lng: form.lng,
    lat: form.lat,
    contactName: form.contactName.trim() || undefined,
    contactPhone: form.contactPhone.trim() || undefined,
    logoUrl: form.logoUrl.trim() || undefined,
    coverUrl: form.coverUrl.trim() || undefined,
    imageUrls: parseImageUrls(),
    announcement: form.announcement.trim() || undefined,
    deliveryTypes: deliveryTypes.value.reduce((mask, bit) => mask | bit, 0),
    minOrderAmount: yuanToFen(form.minOrderYuan),
    deliveryFee: yuanToFen(form.deliveryFeeYuan),
    packagingFee: yuanToFen(form.packagingFeeYuan),
    prepMinutes: form.prepMinutes,
    deliveryRadiusM: form.deliveryRadiusM,
    sortWeight: form.sortWeight,
    settleAccountId: form.settleAccountId,
  }
}

async function submit(): Promise<void> {
  const invalid = validate()
  if (invalid) {
    ElMessage.warning(invalid)
    return
  }

  submitting.value = true
  try {
    const payload = toPayload()
    if (isEdit) {
      await updateShop(shopId, payload)
      if (originalStatus.value !== null && currentStatus.value !== originalStatus.value) {
        await updateShopStatus(shopId, currentStatus.value)
      }
      ElMessage.success('门店已更新')
    } else {
      await createShop(payload)
      ElMessage.success('门店已创建，初始状态为营业')
    }
    await router.push('/store/shop')
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

function back(): void {
  void router.push('/store/shop')
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div class="lead-title">
        <el-button :icon="ArrowLeft" text @click="back">返回列表</el-button>
        <div>
          <h2>{{ pageTitle }}</h2>
          <p>
            <template v-if="isEdit">
              数据源：<span class="mono">GET /api/v1/c/shops/{{ shopId }}</span>（管理端无详情端点，
              复用用户端公开详情）；保存调用 <span class="mono">PUT /api/v1/admin/shops/{{ shopId }}</span>。
            </template>
            <template v-else>
              保存调用 <span class="mono">POST /api/v1/admin/shops</span>，权限码
              <span class="mono">store:shop:create</span>。金额表单填元，提交转分。
            </template>
          </p>
        </div>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-card v-loading="loading" shadow="never">
      <el-form label-width="120px" class="shop-form">
        <div class="section-title">基本信息</div>
        <el-row :gutter="20">
          <el-col :xs="24" :md="12">
            <el-form-item label="商家 ID" required>
              <el-input-number
                v-model="form.merchantId"
                :min="1"
                :precision="0"
                :controls="false"
                class="full-width"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="门店名称" required>
              <el-input v-model="form.shopName" maxlength="128" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="品牌 ID">
              <el-input-number
                v-model="form.brandId"
                :min="1"
                :precision="0"
                :controls="false"
                class="full-width"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="平台品类 ID">
              <el-input-number
                v-model="form.categoryId"
                :min="1"
                :precision="0"
                :controls="false"
                class="full-width"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="联系人">
              <el-input v-model="form.contactName" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="联系电话">
              <el-input
                v-model="form.contactPhone"
                maxlength="11"
                placeholder="11 位手机号，可留空"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="section-title">地址与坐标</div>
        <el-row :gutter="20">
          <el-col :xs="24" :md="8">
            <el-form-item label="省">
              <el-input v-model="form.province" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="市">
              <el-input v-model="form.city" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="区/县">
              <el-input v-model="form.district" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="详细地址" required>
              <el-input v-model="form.address" maxlength="255" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="经度" required>
              <el-input-number
                v-model="form.lng"
                :min="-180"
                :max="180"
                :step="0.000001"
                :precision="6"
                :controls="false"
                class="full-width"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="纬度" required>
              <el-input-number
                v-model="form.lat"
                :min="-90"
                :max="90"
                :step="0.000001"
                :precision="6"
                :controls="false"
                class="full-width"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <p class="form-note">
              本轮未接入地图选点组件，经纬度需手工填写。演示坐标：望京 SOHO
              <span class="mono">lng=116.470000 / lat=39.990000</span>。
            </p>
          </el-col>
        </el-row>

        <div class="section-title">履约与费用</div>
        <el-row :gutter="20">
          <el-col :xs="24" :md="12">
            <el-form-item label="履约方式" required>
              <el-checkbox-group v-model="deliveryTypes">
                <el-checkbox
                  v-for="option in DELIVERY_TYPE_OPTIONS"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="出餐时长">
              <el-input-number v-model="form.prepMinutes" :min="1" :max="180" :precision="0" />
              <span class="field-hint">分钟</span>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="起送价（元）">
              <el-input-number
                v-model="form.minOrderYuan"
                :min="0"
                :precision="2"
                :step="1"
                class="full-width"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="配送费（元）">
              <el-input-number
                v-model="form.deliveryFeeYuan"
                :min="0"
                :precision="2"
                :step="1"
                class="full-width"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="打包费（元）">
              <el-input-number
                v-model="form.packagingFeeYuan"
                :min="0"
                :precision="2"
                :step="1"
                class="full-width"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="默认配送半径">
              <el-input-number
                v-model="form.deliveryRadiusM"
                :min="100"
                :max="50000"
                :step="100"
                :precision="0"
              />
              <span class="field-hint">米，无围栏时兜底</span>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="结算账户 ID">
              <el-input-number
                v-model="form.settleAccountId"
                :min="1"
                :precision="0"
                :controls="false"
                class="full-width"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="section-title">展示与运营</div>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="Logo URL">
              <el-input v-model="form.logoUrl" maxlength="512" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="头图 URL">
              <el-input v-model="form.coverUrl" maxlength="512" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="门店图片">
              <el-input
                v-model="form.imageUrlsText"
                type="textarea"
                :rows="3"
                placeholder="每行一个 URL，最多 9 张"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="门店公告">
              <el-input
                v-model="form.announcement"
                type="textarea"
                :rows="2"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="排序权重">
              <el-input-number v-model="form.sortWeight" :precision="0" :disabled="isEdit" />
            </el-form-item>
          </el-col>
          <el-col v-if="isEdit" :xs="24" :md="12">
            <el-form-item label="营业状态">
              <el-select v-model="currentStatus" class="full-width">
                <el-option
                  v-for="option in SHOP_STATUS_OPTIONS"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
              <span class="field-hint">当前：{{ statusText || '—' }}</span>
            </el-form-item>
          </el-col>
        </el-row>

        <div class="form-actions">
          <el-button @click="back">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submit">
            {{ isEdit ? '保存修改' : '创建门店' }}
          </el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.lead-title {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.shop-form {
  max-width: 1080px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  margin: 6px 0 18px;
  padding-left: 8px;
  border-left: 3px solid var(--el-color-primary);
}

.full-width {
  width: 100%;
}

.field-hint {
  margin-left: 10px;
  color: var(--app-muted);
  font-size: 12px;
}

.form-note {
  margin: -6px 0 16px 120px;
  color: var(--app-muted);
  font-size: 12px;
  line-height: 1.6;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 12px;
  padding-top: 16px;
  border-top: 1px solid var(--app-border);
}

@media (max-width: 900px) {
  .form-note {
    margin-left: 0;
  }
}
</style>
