<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

interface MissingCapability {
  capability: string
  detail: string
  owner: string
}

const riderId = ref<number | null | undefined>(undefined)

const missingCapabilities: MissingCapability[] = [
  {
    capability: '收入列表（按骑手 / 日期）',
    detail:
      '需要平台端按 riderId、dateFrom、dateTo 分页查询，当前只有骑手端按登录身份查询自己的明细。',
    owner: 'T07 骑手结算',
  },
  {
    capability: '罚款扣款明细',
    detail:
      't_rider_income 已记录 penalty_amount / compensation_amount，但未提供平台端按骑手筛选的明细接口。',
    owner: 'T07 骑手结算',
  },
  {
    capability: '结算周期配置',
    detail:
      '缺少平台端读取与修改骑手结算周期、结算规则的接口与页面。',
    owner: 'T07 骑手结算',
  },
]

function queryIncome(): void {
  const value = riderId.value
  if (typeof value !== 'number' || !Number.isInteger(value) || value <= 0) {
    ElMessage.warning('请先填写正整数骑手 ID')
    return
  }
  ElMessage.warning('接口未开放：后台暂无骑手收入查询接口')
}
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>骑手收入</h2>
        <p>
          数据源：无。后台未提供骑手收入查询接口，本页不发起任何请求，也不展示任何模拟金额。
        </p>
      </div>
    </div>

    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="后台暂未提供骑手收入查询接口，当前仅骑手端可查个人收入。"
      class="block-alert"
    >
      <div class="alert-body">
        菜单权限码 <span class="mono">delivery:income:list</span> 已由
        <span class="mono">V16__delivery_freight_income.sql</span> 种入，但后端没有对应的
        <span class="mono">/api/v1/admin/**</span> income controller；现有接口仅
        <span class="mono">GET /api/v1/rider/income/overview</span> 与
        <span class="mono">GET /api/v1/rider/income/details</span>，且骑手身份取自登录令牌，不接受骑手 ID 参数。
      </div>
    </el-alert>

    <el-card shadow="never" class="query-card">
      <div class="toolbar">
        <span class="query-label">骑手 ID</span>
        <el-input-number
          v-model="riderId"
          :min="1"
          :precision="0"
          :controls="false"
          placeholder="请输入正整数"
          class="id-input"
        />
        <el-button type="primary" @click="queryIncome">查询收入</el-button>
      </div>
      <div class="form-hint">
        按钮不会发起网络请求；接口开放前仅提示「接口未开放」。
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <span>待补能力</span>
      </template>
      <el-table :data="missingCapabilities" border stripe>
        <el-table-column prop="capability" label="能力" width="230" />
        <el-table-column prop="detail" label="说明" min-width="420" />
        <el-table-column prop="owner" label="归属任务卡" width="150" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.block-alert {
  margin-bottom: 14px;
}

.alert-body {
  line-height: 1.8;
  margin-top: 4px;
}

.query-card {
  margin-bottom: 14px;
}

.query-label {
  font-weight: 500;
}

.id-input {
  width: 180px;
}

.form-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
  margin-top: 8px;
}
</style>
