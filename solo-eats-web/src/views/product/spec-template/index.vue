<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createAddonTemplate,
  createSpecTemplate,
  deleteAddonTemplate,
  deleteSpecTemplate,
  listAddonTemplates,
  listSpecTemplates,
  updateAddonTemplate,
  updateSpecTemplate,
  type AddonGroupView,
  type AddonItemPayload,
  type AddonTemplatePayload,
  type SpecGroupView,
  type SpecOptionPayload,
  type SpecTemplatePayload,
} from '@/api/product-admin'
import {
  ENABLE_STATUS_OPTIONS,
  SPEC_GROUP_TYPE,
  SPEC_GROUP_TYPE_OPTIONS,
  labelOf,
  tagOf,
} from '@/constants/productStatus'
import { errorMessage } from '@/views/report/helpers'
import { centsToYuan, formatYuan, yuanToFen } from '@/utils/money'

const activeTab = ref<'spec' | 'addon'>('spec')

const specLoading = ref(false)
const specError = ref('')
const specs = ref<SpecGroupView[]>([])

const addonLoading = ref(false)
const addonError = ref('')
const addons = ref<AddonGroupView[]>([])

interface SpecOptionDraft {
  optionName: string
  /** 表单输入单位元，提交时转分 */
  priceDeltaYuan: number
  sort: number
  status: number
}

interface AddonItemDraft {
  itemName: string
  /** 表单输入单位元，提交时转分 */
  priceYuan: number
  stockUnlimited: boolean
  stock: number
  sort: number
  status: number
}

const specDialogVisible = ref(false)
const specEditingId = ref<number | null>(null)
const specSubmitting = ref(false)
const specForm = reactive({
  groupName: '',
  groupType: 1,
  isRequired: 1,
  sort: 0,
  options: [] as SpecOptionDraft[],
})

const addonDialogVisible = ref(false)
const addonEditingId = ref<number | null>(null)
const addonSubmitting = ref(false)
const addonForm = reactive({
  groupName: '',
  minSelect: 0,
  maxSelect: 0,
  sort: 0,
  items: [] as AddonItemDraft[],
})

function newSpecOption(index: number): SpecOptionDraft {
  return { optionName: '', priceDeltaYuan: 0, sort: index + 1, status: 0 }
}

function newAddonItem(index: number): AddonItemDraft {
  return {
    itemName: '',
    priceYuan: 0,
    stockUnlimited: true,
    stock: 0,
    sort: index + 1,
    status: 0,
  }
}

/** 加价分转展示：0 不显示，负数显示 −¥2.00。 */
function priceDeltaText(cents: number): string {
  if (!cents) {
    return ''
  }
  const sign = cents > 0 ? '+' : '−'
  return `${sign}${formatYuan(Math.abs(cents))}`
}

function specOptionText(row: SpecGroupView): string {
  if (!row.options.length) {
    return '—'
  }
  return row.options
    .map((option) => {
      const delta = priceDeltaText(option.priceDelta)
      return delta ? `${option.optionName}（${delta}）` : option.optionName
    })
    .join('、')
}

function addonItemText(row: AddonGroupView): string {
  if (!row.items.length) {
    return '—'
  }
  return row.items
    .map((item) => `${item.itemName}（${formatYuan(item.price)}）`)
    .join('、')
}

async function loadSpecs(): Promise<void> {
  specLoading.value = true
  specError.value = ''
  try {
    specs.value = await listSpecTemplates()
  } catch (error) {
    specs.value = []
    specError.value = errorMessage(error, '规格模板加载失败')
  } finally {
    specLoading.value = false
  }
}

async function loadAddons(): Promise<void> {
  addonLoading.value = true
  addonError.value = ''
  try {
    addons.value = await listAddonTemplates()
  } catch (error) {
    addons.value = []
    addonError.value = errorMessage(error, '加料模板加载失败')
  } finally {
    addonLoading.value = false
  }
}

function refreshActive(): void {
  if (activeTab.value === 'spec') {
    void loadSpecs()
  } else {
    void loadAddons()
  }
}

function openCreateSpec(): void {
  specEditingId.value = null
  Object.assign(specForm, {
    groupName: '',
    groupType: 1,
    isRequired: 1,
    sort: 0,
    options: [newSpecOption(0)],
  })
  specDialogVisible.value = true
}

function openEditSpec(row: SpecGroupView): void {
  specEditingId.value = row.id
  Object.assign(specForm, {
    groupName: row.groupName,
    groupType: row.groupType,
    isRequired: row.isRequired,
    sort: row.sort,
    options: row.options.length
      ? row.options.map((option) => ({
          optionName: option.optionName,
          priceDeltaYuan: centsToYuan(option.priceDelta),
          sort: option.sort,
          status: option.status,
        }))
      : [newSpecOption(0)],
  })
  specDialogVisible.value = true
}

function addSpecOption(): void {
  specForm.options.push(newSpecOption(specForm.options.length))
}

function removeSpecOption(index: number): void {
  if (specForm.options.length <= 1) {
    ElMessage.warning('规格模板至少保留一个选项')
    return
  }
  specForm.options.splice(index, 1)
}

async function submitSpec(): Promise<void> {
  const groupName = specForm.groupName.trim()
  if (!groupName) {
    ElMessage.warning('模板名称不能为空')
    return
  }
  const options: SpecOptionPayload[] = []
  for (let index = 0; index < specForm.options.length; index += 1) {
    const option = specForm.options[index]
    const optionName = option.optionName.trim()
    if (!optionName) {
      ElMessage.warning(`第 ${index + 1} 个规格选项名称不能为空`)
      return
    }
    options.push({
      optionName,
      priceDelta: yuanToFen(option.priceDeltaYuan),
      sort: option.sort,
      status: option.status,
    })
  }

  const payload: SpecTemplatePayload = {
    groupName,
    groupType: specForm.groupType,
    isRequired: specForm.isRequired,
    sort: specForm.sort,
    options,
  }

  specSubmitting.value = true
  try {
    if (specEditingId.value === null) {
      await createSpecTemplate(payload)
      ElMessage.success('规格模板已创建')
    } else {
      await updateSpecTemplate(specEditingId.value, payload)
      ElMessage.success('规格模板已更新')
    }
    specDialogVisible.value = false
    await loadSpecs()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    specSubmitting.value = false
  }
}

async function confirmDeleteSpec(row: SpecGroupView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认删除规格模板「${row.groupName}」？已被商品引用的模板后端会拒绝删除。`,
      '删除规格模板',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  try {
    await deleteSpecTemplate(row.id)
    ElMessage.success('规格模板已删除')
    await loadSpecs()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  }
}

function openCreateAddon(): void {
  addonEditingId.value = null
  Object.assign(addonForm, {
    groupName: '',
    minSelect: 0,
    maxSelect: 0,
    sort: 0,
    items: [newAddonItem(0)],
  })
  addonDialogVisible.value = true
}

function openEditAddon(row: AddonGroupView): void {
  addonEditingId.value = row.id
  Object.assign(addonForm, {
    groupName: row.groupName,
    minSelect: row.minSelect,
    maxSelect: row.maxSelect,
    sort: row.sort,
    items: row.items.length
      ? row.items.map((item) => ({
          itemName: item.itemName,
          priceYuan: centsToYuan(item.price),
          stockUnlimited: item.stock === null,
          stock: item.stock ?? 0,
          sort: item.sort,
          status: item.status,
        }))
      : [newAddonItem(0)],
  })
  addonDialogVisible.value = true
}

function addAddonItem(): void {
  addonForm.items.push(newAddonItem(addonForm.items.length))
}

function removeAddonItem(index: number): void {
  if (addonForm.items.length <= 1) {
    ElMessage.warning('加料模板至少保留一个加料项')
    return
  }
  addonForm.items.splice(index, 1)
}

async function submitAddon(): Promise<void> {
  const groupName = addonForm.groupName.trim()
  if (!groupName) {
    ElMessage.warning('模板名称不能为空')
    return
  }
  if (addonForm.maxSelect > 0 && addonForm.maxSelect < addonForm.minSelect) {
    ElMessage.warning('最多选择数不能小于最少选择数')
    return
  }

  const items: AddonItemPayload[] = []
  for (let index = 0; index < addonForm.items.length; index += 1) {
    const item = addonForm.items[index]
    const itemName = item.itemName.trim()
    if (!itemName) {
      ElMessage.warning(`第 ${index + 1} 个加料项名称不能为空`)
      return
    }
    items.push({
      itemName,
      price: yuanToFen(item.priceYuan),
      stock: item.stockUnlimited ? null : item.stock,
      sort: item.sort,
      status: item.status,
    })
  }

  const payload: AddonTemplatePayload = {
    groupName,
    minSelect: addonForm.minSelect,
    maxSelect: addonForm.maxSelect,
    sort: addonForm.sort,
    items,
  }

  addonSubmitting.value = true
  try {
    if (addonEditingId.value === null) {
      await createAddonTemplate(payload)
      ElMessage.success('加料模板已创建')
    } else {
      await updateAddonTemplate(addonEditingId.value, payload)
      ElMessage.success('加料模板已更新')
    }
    addonDialogVisible.value = false
    await loadAddons()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  } finally {
    addonSubmitting.value = false
  }
}

async function confirmDeleteAddon(row: AddonGroupView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认删除加料模板「${row.groupName}」？已被商品引用的模板后端会拒绝删除。`,
      '删除加料模板',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  try {
    await deleteAddonTemplate(row.id)
    ElMessage.success('加料模板已删除')
    await loadAddons()
  } catch {
    // 错误提示由 http 响应拦截器统一输出，此处不重复弹
  }
}

onMounted(() => {
  void loadSpecs()
  void loadAddons()
})
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>规格模板</h2>
        <p>
          数据源：<span class="mono">/api/v1/admin/spec-templates</span> 与
          <span class="mono">/api/v1/admin/addon-templates</span>。模板提交时整组覆盖选项 / 加料项；
          加料模板列表只返回 <span class="mono">status=0</span> 的加料项。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="specLoading || addonLoading" @click="refreshActive">
          刷新
        </el-button>
        <el-button
          v-if="activeTab === 'spec'"
          type="primary"
          :icon="Plus"
          @click="openCreateSpec"
        >
          新建规格模板
        </el-button>
        <el-button v-else type="primary" :icon="Plus" @click="openCreateAddon">
          新建加料模板
        </el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="tabs">
      <el-tab-pane label="规格模板" name="spec">
        <el-alert
          v-if="specError"
          type="error"
          :closable="false"
          show-icon
          :title="specError"
        />

        <el-card shadow="never">
          <el-table v-loading="specLoading" :data="specs" border stripe row-key="id">
            <el-table-column prop="groupName" label="模板名称" width="160" />
            <el-table-column label="类型" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="tagOf(SPEC_GROUP_TYPE, row.groupType)" size="small">
                  {{ labelOf(SPEC_GROUP_TYPE, row.groupType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="必选" width="80" align="center">
              <template #default="{ row }">
                {{ row.isRequired === 1 ? '是' : '否' }}
              </template>
            </el-table-column>
            <el-table-column label="排序" width="80" align="right">
              <template #default="{ row }">{{ row.sort }}</template>
            </el-table-column>
            <el-table-column label="选项数" width="80" align="right">
              <template #default="{ row }">{{ row.options.length }}</template>
            </el-table-column>
            <el-table-column label="选项（加价单位元）" min-width="320">
              <template #default="{ row }">{{ specOptionText(row) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEditSpec(row)">编辑</el-button>
                <el-button link type="danger" @click="confirmDeleteSpec(row)">删除</el-button>
              </template>
            </el-table-column>

            <template #empty>
              <el-empty description="暂无规格模板。" :image-size="80" />
            </template>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="加料模板" name="addon">
        <el-alert
          v-if="addonError"
          type="error"
          :closable="false"
          show-icon
          :title="addonError"
        />

        <el-card shadow="never">
          <el-table v-loading="addonLoading" :data="addons" border stripe row-key="id">
            <el-table-column prop="groupName" label="模板名称" width="160" />
            <el-table-column label="最少选择" width="90" align="right">
              <template #default="{ row }">{{ row.minSelect }}</template>
            </el-table-column>
            <el-table-column label="最多选择" width="90" align="right">
              <template #default="{ row }">
                {{ row.maxSelect === 0 ? '不限' : row.maxSelect }}
              </template>
            </el-table-column>
            <el-table-column label="排序" width="80" align="right">
              <template #default="{ row }">{{ row.sort }}</template>
            </el-table-column>
            <el-table-column label="加料项数" width="90" align="right">
              <template #default="{ row }">{{ row.items.length }}</template>
            </el-table-column>
            <el-table-column label="加料项（价格单位元）" min-width="320">
              <template #default="{ row }">{{ addonItemText(row) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEditAddon(row)">编辑</el-button>
                <el-button link type="danger" @click="confirmDeleteAddon(row)">删除</el-button>
              </template>
            </el-table-column>

            <template #empty>
              <el-empty description="暂无加料模板。" :image-size="80" />
            </template>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="specDialogVisible"
      :title="specEditingId === null ? '新建规格模板' : '编辑规格模板'"
      width="760px"
      :close-on-click-modal="false"
    >
      <el-form label-width="88px">
        <el-form-item label="模板名称" required>
          <el-input
            v-model="specForm.groupName"
            maxlength="64"
            show-word-limit
            placeholder="例如：杯型"
          />
        </el-form-item>
        <el-form-item label="选择类型">
          <el-radio-group v-model="specForm.groupType">
            <el-radio
              v-for="option in SPEC_GROUP_TYPE_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="是否必选">
          <el-radio-group v-model="specForm.isRequired">
            <el-radio :value="1">必选</el-radio>
            <el-radio :value="0">可选</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="specForm.sort" :min="0" :precision="0" :controls="false" />
        </el-form-item>
        <el-form-item label="规格选项" required>
          <div class="option-editor">
            <div
              v-for="(option, index) in specForm.options"
              :key="index"
              class="option-row"
            >
              <el-input
                v-model="option.optionName"
                maxlength="64"
                placeholder="选项名称，如大杯"
                class="option-name"
              />
              <el-input-number
                v-model="option.priceDeltaYuan"
                :precision="2"
                :step="0.1"
                :controls="false"
                placeholder="加价（元）"
                class="option-number"
              />
              <el-input-number
                v-model="option.sort"
                :min="0"
                :precision="0"
                :controls="false"
                placeholder="排序"
                class="option-sort"
              />
              <el-select v-model="option.status" class="option-status">
                <el-option
                  v-for="status in ENABLE_STATUS_OPTIONS"
                  :key="status.value"
                  :value="status.value"
                  :label="status.label"
                />
              </el-select>
              <el-button link type="danger" @click="removeSpecOption(index)">删除</el-button>
            </div>
            <el-button link type="primary" :icon="Plus" @click="addSpecOption">
              增加选项
            </el-button>
            <div class="form-hint">
              加价单位元，可为负数（如小杯 −2.00）；后端按分存储。
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="specDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="specSubmitting" @click="submitSpec">
          {{ specEditingId === null ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="addonDialogVisible"
      :title="addonEditingId === null ? '新建加料模板' : '编辑加料模板'"
      width="820px"
      :close-on-click-modal="false"
    >
      <el-form label-width="88px">
        <el-form-item label="模板名称" required>
          <el-input
            v-model="addonForm.groupName"
            maxlength="64"
            show-word-limit
            placeholder="例如：加料"
          />
        </el-form-item>
        <el-form-item label="选择数量">
          <div class="toolbar">
            <span class="muted">最少</span>
            <el-input-number
              v-model="addonForm.minSelect"
              :min="0"
              :precision="0"
              :controls="false"
              class="select-number"
            />
            <span class="muted">最多（0 表示不限）</span>
            <el-input-number
              v-model="addonForm.maxSelect"
              :min="0"
              :precision="0"
              :controls="false"
              class="select-number"
            />
          </div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="addonForm.sort" :min="0" :precision="0" :controls="false" />
        </el-form-item>
        <el-form-item label="加料项" required>
          <div class="option-editor">
            <div
              v-for="(item, index) in addonForm.items"
              :key="index"
              class="option-row"
            >
              <el-input
                v-model="item.itemName"
                maxlength="64"
                placeholder="加料名称，如珍珠"
                class="option-name"
              />
              <el-input-number
                v-model="item.priceYuan"
                :min="0"
                :precision="2"
                :step="0.1"
                :controls="false"
                placeholder="价格（元）"
                class="option-number"
              />
              <el-checkbox v-model="item.stockUnlimited">库存不限</el-checkbox>
              <el-input-number
                v-model="item.stock"
                :min="0"
                :precision="0"
                :controls="false"
                :disabled="item.stockUnlimited"
                placeholder="库存"
                class="option-sort"
              />
              <el-input-number
                v-model="item.sort"
                :min="0"
                :precision="0"
                :controls="false"
                placeholder="排序"
                class="option-sort"
              />
              <el-select v-model="item.status" class="option-status">
                <el-option
                  v-for="status in ENABLE_STATUS_OPTIONS"
                  :key="status.value"
                  :value="status.value"
                  :label="status.label"
                />
              </el-select>
              <el-button link type="danger" @click="removeAddonItem(index)">删除</el-button>
            </div>
            <el-button link type="primary" :icon="Plus" @click="addAddonItem">
              增加加料项
            </el-button>
            <div class="form-hint">
              价格单位元，后端按分存储；停用的加料项不会出现在列表回显中。
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addonDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="addonSubmitting" @click="submitAddon">
          {{ addonEditingId === null ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tabs :deep(.el-tabs__header) {
  margin-bottom: 14px;
}

.option-editor {
  width: 100%;
}

.option-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.option-name {
  width: 180px;
}

.option-number {
  width: 120px;
}

.option-sort {
  width: 88px;
}

.option-status {
  width: 96px;
}

.select-number {
  width: 90px;
}

.form-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
</style>
