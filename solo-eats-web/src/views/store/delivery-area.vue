<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  getDeliveryArea,
  getShopDetail,
  listShops,
  saveDeliveryArea,
  type DeliveryAreaPoint,
  type DeliveryAreaView,
  type SaveDeliveryAreaPayload,
} from '@/api/store'
import { ApiError } from '@/api/http'
import type { ShopDetailView, ShopView } from '@/api/types'
import { SHOP_STATUS_MAP, statusMeta } from '@/constants/businessStatus'
import { debounce } from '@/utils/debounce'

const route = useRoute()

const shopKeyword = ref('')
const directShopId = ref<number | undefined>(undefined)
const shopLoading = ref(false)
const shopOptions = ref<ShopView[]>([])
const shopId = ref<number | undefined>(undefined)
const shopDetail = ref<ShopDetailView | null>(null)

const areaLoading = ref(false)
const areaLoaded = ref(false)
const areaExists = ref(false)
const loadError = ref('')
const saveError = ref('')
const saving = ref(false)

const form = reactive({
  areaType: 1,
  centerLng: 116.397128,
  centerLat: 39.916527,
  radiusM: 3000,
  polygon: [] as DeliveryAreaPoint[],
})

const selectedShop = computed<ShopView | undefined>(() =>
  shopOptions.value.find((item) => item.id === shopId.value),
)

/** 直接按 ID 查询时下拉里没有选项，退回用详情接口返回的门店信息。 */
const shopInfo = computed<ShopView | ShopDetailView | undefined>(
  () => selectedShop.value ?? shopDetail.value ?? undefined,
)

const areaTypeText = computed(() => (form.areaType === 2 ? '多边形围栏' : '圆形围栏'))

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

function positiveShopId(): number | null {
  const value = shopId.value
  return typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : null
}

function roundCoord(value: number): number {
  return Number(value.toFixed(6))
}

async function loadShopOptions(keyword = ''): Promise<void> {
  shopLoading.value = true
  try {
    const result = await listShops({
      keyword: keyword.trim() || undefined,
      pageNum: 1,
      pageSize: 50,
    })
    shopOptions.value = result.list
  } catch {
    // 拦截器已提示错误；保留上一次可选列表
  } finally {
    shopLoading.value = false
  }
}

const remoteSearchShops = debounce((keyword: string) => {
  void loadShopOptions(keyword)
}, 300)

function defaultPolygon(lng: number, lat: number): DeliveryAreaPoint[] {
  return [
    { lng: roundCoord(lng - 0.002), lat: roundCoord(lat - 0.002) },
    { lng: roundCoord(lng + 0.002), lat: roundCoord(lat - 0.002) },
    { lng: roundCoord(lng + 0.002), lat: roundCoord(lat + 0.002) },
  ]
}

function applyArea(area: DeliveryAreaView): void {
  form.areaType = area.areaType === 2 ? 2 : 1
  form.centerLng = area.centerLng === null ? 0 : Number(area.centerLng)
  form.centerLat = area.centerLat === null ? 0 : Number(area.centerLat)
  form.radiusM = area.radiusM === null ? 3000 : Number(area.radiusM)
  form.polygon = (area.polygon ?? []).map((point) => ({
    lng: Number(point.lng),
    lat: Number(point.lat),
  }))
}

/** 未配置围栏时用门店坐标初始化，避免用户从 0,0 开始填。 */
function resetAreaFromShop(): void {
  const fallbackLng = shopDetail.value?.lng ?? 116.397128
  const fallbackLat = shopDetail.value?.lat ?? 39.916527
  form.areaType = 1
  form.centerLng = Number(fallbackLng)
  form.centerLat = Number(fallbackLat)
  form.radiusM = shopDetail.value?.deliveryRadiusM || 3000
  form.polygon = []
}

async function loadArea(): Promise<void> {
  const id = positiveShopId()
  if (id === null) {
    ElMessage.warning('请先选择门店')
    return
  }
  areaLoading.value = true
  areaLoaded.value = false
  areaExists.value = false
  loadError.value = ''
  saveError.value = ''
  try {
    shopDetail.value = await getShopDetail(id)
  } catch {
    shopDetail.value = null
  }
  try {
    const area = await getDeliveryArea(id)
    applyArea(area)
    areaExists.value = true
  } catch (error) {
    if (error instanceof ApiError && error.code === 10002) {
      // 后端语义：没有启用的配送范围，按未配置处理，不当作页面错误。
      areaExists.value = false
      resetAreaFromShop()
    } else {
      loadError.value = errorMessage(error, '配送范围加载失败')
      resetAreaFromShop()
    }
  } finally {
    areaLoading.value = false
    areaLoaded.value = true
  }
}

function loadByShopId(): void {
  const id = Number(directShopId.value)
  if (!Number.isInteger(id) || id <= 0) {
    ElMessage.warning('请输入正整数门店 ID')
    return
  }
  shopId.value = id
  void loadArea()
}

function handleAreaTypeChange(value: string | number | boolean | undefined): void {
  const next = Number(value) === 2 ? 2 : 1
  form.areaType = next
  if (next === 2 && form.polygon.length < 3) {
    form.polygon = defaultPolygon(form.centerLng, form.centerLat)
  }
}

function addPoint(): void {
  if (form.polygon.length >= 50) {
    ElMessage.warning('多边形最多 50 个顶点')
    return
  }
  const last = form.polygon[form.polygon.length - 1]
  form.polygon.push({
    lng: last ? roundCoord(last.lng + 0.0005) : roundCoord(form.centerLng),
    lat: last ? last.lat : roundCoord(form.centerLat),
  })
}

function removePoint(index: number): void {
  form.polygon.splice(index, 1)
}

function validCoord(lng: number, lat: number): boolean {
  return (
    Number.isFinite(lng) &&
    Number.isFinite(lat) &&
    lng >= -180 &&
    lng <= 180 &&
    lat >= -90 &&
    lat <= 90
  )
}

function buildPayload(): SaveDeliveryAreaPayload | null {
  if (form.areaType === 2) {
    if (form.polygon.length < 3 || form.polygon.length > 50) {
      saveError.value = '多边形围栏需要 3 ~ 50 个顶点'
      return null
    }
    const polygon: DeliveryAreaPoint[] = []
    for (let index = 0; index < form.polygon.length; index += 1) {
      const point = form.polygon[index]
      if (!validCoord(point.lng, point.lat)) {
        saveError.value = `第 ${index + 1} 个顶点坐标超出经纬度范围`
        return null
      }
      polygon.push({ lng: roundCoord(point.lng), lat: roundCoord(point.lat) })
    }
    return { areaType: 2, polygon }
  }

  if (!validCoord(form.centerLng, form.centerLat)) {
    saveError.value = '圆心经纬度超出合法范围'
    return null
  }
  const radius = Number(form.radiusM)
  if (!Number.isInteger(radius) || radius < 100 || radius > 50000) {
    saveError.value = '圆形半径必须是 100 ~ 50000 之间的整数米'
    return null
  }
  return {
    areaType: 1,
    centerLng: roundCoord(form.centerLng),
    centerLat: roundCoord(form.centerLat),
    radiusM: radius,
  }
}

async function submit(): Promise<void> {
  const id = positiveShopId()
  if (id === null) {
    ElMessage.warning('请先选择门店')
    return
  }
  saveError.value = ''
  const payload = buildPayload()
  if (payload === null) {
    return
  }
  saving.value = true
  try {
    const area = await saveDeliveryArea(id, payload)
    applyArea(area)
    areaExists.value = true
    ElMessage.success('配送范围已保存')
  } catch (error) {
    saveError.value = errorMessage(error, '配送范围保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadShopOptions()
  const queryShopId = Number(route.query.shopId)
  if (Number.isInteger(queryShopId) && queryShopId > 0) {
    shopId.value = queryShopId
    await loadArea()
  }
})
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>配送区域</h2>
        <p>
          数据源：<span class="mono">GET / PUT /api/v1/admin/shops/{id}/delivery-area</span>。
          圆形围栏半径 <span class="mono">100 ~ 50000</span> 米；多边形
          <span class="mono">3 ~ 50</span> 个顶点，坐标顺序为 <span class="mono">lng, lat</span>。
          未配置时后端返回 <span class="mono">10002</span>，页面按空围栏处理。
        </p>
      </div>
    </div>

    <el-card shadow="never">
      <div class="toolbar filter-bar">
        <el-select
          v-model="shopId"
          filterable
          remote
          clearable
          reserve-keyword
          :remote-method="remoteSearchShops"
          :loading="shopLoading"
          placeholder="按门店名 / 编号检索门店"
          class="shop-select"
          @change="loadArea"
        >
          <el-option
            v-for="shop in shopOptions"
            :key="shop.id"
            :value="shop.id"
            :label="`${shop.shopName}（#${shop.id} ${shop.shopNo}）`"
          />
        </el-select>
        <el-input
          v-model="shopKeyword"
          placeholder="输入门店关键字后由下拉检索（300ms 防抖）"
          clearable
          class="keyword-input"
          :prefix-icon="Search"
          @input="remoteSearchShops"
          @keyup.enter="loadShopOptions(shopKeyword)"
        />
        <el-input-number
          v-model="directShopId"
          :min="1"
          :controls="false"
          placeholder="门店 ID"
          class="id-input"
          @keyup.enter="loadByShopId"
        />
        <el-button @click="loadByShopId">按 ID 读取</el-button>
        <el-button type="primary" :disabled="positiveShopId() === null" @click="loadArea">
          读取围栏
        </el-button>
      </div>

      <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

      <el-empty
        v-if="positiveShopId() === null"
        description="选择门店后读取并编辑配送范围。"
        :image-size="80"
      />

      <div v-else v-loading="areaLoading">
        <el-alert
          v-if="areaLoaded && !areaExists"
          type="info"
          :closable="false"
          show-icon
          title="该门店尚未配置配送范围，当前表单使用门店坐标与配送半径作为初始值，保存后生效。"
          class="area-alert"
        />

        <el-descriptions v-if="shopInfo" :column="3" border size="small" class="shop-info">
          <el-descriptions-item label="门店">
            {{ shopInfo.shopName }}
          </el-descriptions-item>
          <el-descriptions-item label="门店编号">
            <span class="mono">{{ shopInfo.shopNo }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusMeta(SHOP_STATUS_MAP, shopInfo.status).tag" size="small">
              {{ statusMeta(SHOP_STATUS_MAP, shopInfo.status).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="地址" :span="2">
            {{ shopInfo.address }}
          </el-descriptions-item>
          <el-descriptions-item label="门店坐标">
            <span class="mono">{{ shopInfo.lng }}, {{ shopInfo.lat }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-form label-width="120px" class="area-form">
          <el-form-item label="围栏类型">
            <el-radio-group :model-value="form.areaType" @change="handleAreaTypeChange">
              <el-radio :value="1">圆形围栏</el-radio>
              <el-radio :value="2">多边形围栏</el-radio>
            </el-radio-group>
            <span class="muted form-hint">当前：{{ areaTypeText }}</span>
          </el-form-item>

          <template v-if="form.areaType === 1">
            <el-form-item label="圆心经度">
              <el-input-number
                v-model="form.centerLng"
                :min="-180"
                :max="180"
                :precision="6"
                :step="0.0001"
                class="coord-input"
              />
            </el-form-item>
            <el-form-item label="圆心纬度">
              <el-input-number
                v-model="form.centerLat"
                :min="-90"
                :max="90"
                :precision="6"
                :step="0.0001"
                class="coord-input"
              />
            </el-form-item>
            <el-form-item label="半径（米）">
              <el-input-number
                v-model="form.radiusM"
                :min="100"
                :max="50000"
                :step="100"
                :precision="0"
                class="coord-input"
              />
              <span class="muted form-hint">后端只接受 100 ~ 50000 的整数米。</span>
            </el-form-item>
          </template>

          <template v-else>
            <el-form-item label="顶点">
              <div class="polygon-table">
                <el-table :data="form.polygon" border size="small">
                  <el-table-column label="#" width="56">
                    <template #default="{ $index }">{{ $index + 1 }}</template>
                  </el-table-column>
                  <el-table-column label="经度 lng" min-width="170">
                    <template #default="{ row }">
                      <el-input-number
                        v-model="row.lng"
                        :min="-180"
                        :max="180"
                        :precision="6"
                        :step="0.0001"
                        :controls="false"
                        class="poly-input"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="纬度 lat" min-width="170">
                    <template #default="{ row }">
                      <el-input-number
                        v-model="row.lat"
                        :min="-90"
                        :max="90"
                        :precision="6"
                        :step="0.0001"
                        :controls="false"
                        class="poly-input"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="90">
                    <template #default="{ $index }">
                      <el-button
                        link
                        type="danger"
                        :disabled="form.polygon.length <= 3"
                        @click="removePoint($index)"
                      >
                        删除
                      </el-button>
                    </template>
                  </el-table-column>
                  <template #empty>
                    <el-empty description="至少需要 3 个顶点。" :image-size="60" />
                  </template>
                </el-table>
                <el-button class="add-point" :disabled="form.polygon.length >= 50" @click="addPoint">
                  追加顶点（{{ form.polygon.length }} / 50）
                </el-button>
              </div>
            </el-form-item>
          </template>

          <el-alert
            v-if="saveError"
            type="error"
            :closable="false"
            show-icon
            :title="saveError"
            class="area-alert"
          />

          <el-form-item>
            <el-button type="primary" :loading="saving" @click="submit">保存配送范围</el-button>
            <el-button :disabled="areaLoading" @click="loadArea">重新读取</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.filter-bar {
  margin-bottom: 14px;
}

.shop-select {
  width: 360px;
}

.keyword-input {
  width: 300px;
}

.id-input {
  width: 120px;
}

.area-alert {
  margin-bottom: 14px;
}

.shop-info {
  margin-bottom: 18px;
}

.area-form {
  max-width: 880px;
}

.form-hint {
  margin-left: 10px;
  font-size: 12px;
}

.coord-input {
  width: 220px;
}

.polygon-table {
  width: 100%;
}

.poly-input {
  width: 100%;
}

.add-point {
  margin-top: 8px;
}

@media (max-width: 900px) {
  .shop-select,
  .keyword-input {
    width: 100%;
  }
}
</style>
