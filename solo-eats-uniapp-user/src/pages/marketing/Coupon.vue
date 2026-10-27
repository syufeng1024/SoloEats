<template>
  <view class="coupon">
    <view class="coupon__banner">
      <text class="coupon__banner-title">优惠券</text>
      <text class="coupon__banner-desc">
        用户端优惠券查询接口 /c/coupons 尚未提供，本页不展示券数据，只说明已有的表结构与状态含义。
      </text>
    </view>

    <view class="se-card coupon__status-card">
      <text class="coupon__section-title">当前状态</text>
      <view class="coupon__status-row">
        <text class="coupon__status-label">后端表结构</text>
        <text class="coupon__status-value is-ok">已存在</text>
      </view>
      <text class="coupon__status-desc">
        t_coupon_template（券模板）、t_user_coupon（用户券）、t_user_coupon_log（状态流水）
        已由 V1__init_schema.sql 建表，后台券模板管理也有对应接口。
      </text>

      <view class="coupon__status-row">
        <text class="coupon__status-label">订单链路券操作</text>
        <text class="coupon__status-value is-ok">已实现</text>
      </view>
      <text class="coupon__status-desc">
        下单时按模板校验并锁定用户券，支付成功后核销，取消或退款时回滚，均有状态流水。
      </text>

      <view class="coupon__status-row">
        <text class="coupon__status-label">C 端查询 / 领取</text>
        <text class="coupon__status-value is-pending">待开放</text>
      </view>
      <text class="coupon__status-desc">
        没有 GET /c/coupons 之类的端点，用户当前无法在小程序内查看自己的券或领券。
      </text>
    </view>

    <view class="se-card coupon__fields">
      <text class="coupon__section-title">t_user_coupon 字段含义</text>

      <view class="coupon__field">
        <text class="coupon__field-name">id</text>
        <text class="coupon__field-desc">用户券主键，bigint unsigned</text>
      </view>
      <view class="coupon__field">
        <text class="coupon__field-name">user_id</text>
        <text class="coupon__field-desc">归属用户，对应 t_member_user.id</text>
      </view>
      <view class="coupon__field">
        <text class="coupon__field-name">template_id</text>
        <text class="coupon__field-desc">券模板 ID，对应 t_coupon_template.id</text>
      </view>
      <view class="coupon__field">
        <text class="coupon__field-name">status</text>
        <text class="coupon__field-desc">券状态，取值见下方状态表</text>
      </view>
      <view class="coupon__field">
        <text class="coupon__field-name">order_id</text>
        <text class="coupon__field-desc">核销订单 ID；未使用时为 NULL</text>
      </view>
      <view class="coupon__field">
        <text class="coupon__field-name">locked_at</text>
        <text class="coupon__field-desc">下单锁券时间；未锁定为 NULL</text>
      </view>
      <view class="coupon__field">
        <text class="coupon__field-name">used_at</text>
        <text class="coupon__field-desc">实际核销时间；未使用为 NULL</text>
      </view>
      <view class="coupon__field">
        <text class="coupon__field-name">expire_at</text>
        <text class="coupon__field-desc">券到期时间，NOT NULL；过期后状态由任务或读取逻辑置为 3</text>
      </view>
      <view class="coupon__field">
        <text class="coupon__field-name">version</text>
        <text class="coupon__field-desc">乐观锁版本号，锁券/核销/回滚时递增</text>
      </view>
      <view class="coupon__field">
        <text class="coupon__field-name">created_at / updated_at</text>
        <text class="coupon__field-desc">创建与更新时间</text>
      </view>
    </view>

    <view class="se-card coupon__statuses">
      <text class="coupon__section-title">status 取值</text>

      <view v-for="row in STATUS_ROWS" :key="row.value" class="coupon__status-item">
        <text class="coupon__status-code">{{ row.value }}</text>
        <text class="coupon__status-name">{{ row.label }}</text>
        <text class="coupon__status-note">{{ row.note }}</text>
      </view>
    </view>

    <view class="se-card coupon__statuses">
      <text class="coupon__section-title">t_user_coupon_log.op 取值</text>

      <view v-for="row in LOG_ROWS" :key="row.value" class="coupon__status-item">
        <text class="coupon__status-code">{{ row.value }}</text>
        <text class="coupon__status-name">{{ row.label }}</text>
        <text class="coupon__status-note">{{ row.note }}</text>
      </view>
    </view>

    <view class="coupon__note">
      本页不发起任何网络请求，也不构造示例券，避免把后端尚未提供的数据展示成真实券包。
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 优惠券说明页。
 *
 * 后端没有 C 端优惠券接口（无 /c/coupons），因此这里只如实说明已有表结构与
 * 状态含义，不展示任何用户券数据。字段定义来自 V1__init_schema.sql。
 */

const STATUS_ROWS = [
  { value: '0', label: '未使用', note: '可参与下单锁券' },
  { value: '1', label: '已锁定', note: '订单待支付期间占用，超时或取消会释放' },
  { value: '2', label: '已使用', note: '订单支付成功后核销' },
  { value: '3', label: '已过期', note: '超过 expire_at 后不可用' },
  { value: '4', label: '已作废', note: '后台或规则作废' },
  { value: '5', label: '已退款', note: '退款链路回滚后的状态' },
]

const LOG_ROWS = [
  { value: 'LOCK', label: '锁定', note: '下单时从未使用 → 已锁定' },
  { value: 'CONSUME', label: '核销', note: '支付成功后从已锁定 → 已使用' },
  { value: 'RELEASE', label: '释放', note: '取消订单时从已锁定 → 未使用' },
  { value: 'ROLLBACK', label: '回滚', note: '退款链路把已使用券回滚' },
]
</script>

<style scoped>
.coupon {
  min-height: 100vh;
  padding: 24rpx;
  background-color: #f5f5f7;
}

.coupon__banner {
  padding: 28rpx 32rpx;
  border-radius: 12rpx;
  background: linear-gradient(120deg, #b8865a 0%, #8a6134 100%);
  color: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(138, 97, 52, 0.22);
}

.coupon__banner-title {
  display: block;
  font-size: 36rpx;
  font-weight: 600;
}

.coupon__banner-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  line-height: 1.6;
  opacity: 0.94;
}

.coupon__status-card,
.coupon__fields,
.coupon__statuses {
  margin-top: 20rpx;
  padding: 24rpx;
}

.coupon__section-title {
  display: block;
  margin-bottom: 8rpx;
  font-size: 30rpx;
  font-weight: 600;
}

.coupon__status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20rpx;
}

.coupon__status-label {
  font-size: 26rpx;
  color: #333333;
  font-weight: 500;
}

.coupon__status-value {
  padding: 2rpx 12rpx;
  border-radius: 6rpx;
  font-size: 20rpx;
}

.coupon__status-value.is-ok {
  background-color: #f6ffed;
  color: #52c41a;
}

.coupon__status-value.is-pending {
  background-color: #fff2e6;
  color: #ff7a00;
}

.coupon__status-desc {
  display: block;
  margin-top: 8rpx;
  color: #8a8a8a;
  font-size: 22rpx;
  line-height: 1.6;
}

.coupon__field {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  padding: 14rpx 0;
  border-bottom: 1rpx solid #f7f7f7;
}

.coupon__field:last-child {
  border-bottom: none;
}

.coupon__field-name {
  width: 220rpx;
  flex-shrink: 0;
  color: #333333;
  font-size: 23rpx;
  font-family: Consolas, Menlo, monospace;
  word-break: break-all;
}

.coupon__field-desc {
  flex: 1;
  color: #6f6f6f;
  font-size: 22rpx;
  line-height: 1.6;
}

.coupon__status-item {
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
  padding: 14rpx 0;
  border-bottom: 1rpx solid #f7f7f7;
}

.coupon__status-item:last-child {
  border-bottom: none;
}

.coupon__status-code {
  width: 110rpx;
  flex-shrink: 0;
  color: #ff7a00;
  font-size: 22rpx;
  font-family: Consolas, Menlo, monospace;
}

.coupon__status-name {
  width: 100rpx;
  flex-shrink: 0;
  color: #333333;
  font-size: 22rpx;
  font-weight: 500;
}

.coupon__status-note {
  flex: 1;
  color: #8a8a8a;
  font-size: 21rpx;
  line-height: 1.6;
}

.coupon__note {
  padding: 24rpx 8rpx;
  color: #a0a0a0;
  font-size: 21rpx;
  line-height: 1.6;
}
</style>
