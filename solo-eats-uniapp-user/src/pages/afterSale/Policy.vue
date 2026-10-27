<template>
  <view class="policy">
    <view class="se-card policy__intro">
      <text class="policy__title">退款规则说明</text>
      <text class="policy__desc">
        以下规则对齐当前后端 RefundServiceImpl 的实际校验逻辑，不是通用平台文案。
      </text>
    </view>

    <view class="se-card policy__block">
      <text class="policy__block-title">一、哪些订单可以申请退款</text>
      <view class="policy__table">
        <view v-for="row in STATUS_ROWS" :key="row.status" class="policy__table-row">
          <text class="policy__table-status">{{ row.status }}</text>
          <text class="policy__table-value" :class="{ 'is-blocked': !row.allowed }">
            {{ row.result }}
          </text>
        </view>
      </view>
      <text class="policy__note">
        用户端退款接口只接受「待接单（10）」「备餐中（20）」两种状态；
        其他状态调用会返回 ORDER_STATUS_INVALID。
      </text>
    </view>

    <view class="se-card policy__block">
      <text class="policy__block-title">二、退款金额与范围</text>
      <text class="policy__paragraph">
        当前只支持整单退款，金额取订单实付金额 payAmount，不支持按商品部分退款。
      </text>
      <text class="policy__paragraph">
        退款成功后，后端会自动回滚订单占用的商品库存，并回滚该订单核销的优惠券。
      </text>
      <text class="policy__paragraph">
        退款原因在接口里只有一个 reason 字段（必填，@NotBlank）；
        申请页填写的退款说明会拼接在原因后面一起提交。
      </text>
    </view>

    <view class="se-card policy__block">
      <text class="policy__block-title">三、退款单状态（申请接口返回）</text>
      <view class="policy__table">
        <view v-for="row in REFUND_ROWS" :key="row.code" class="policy__table-row">
          <text class="policy__table-status">{{ row.code }}</text>
          <text class="policy__table-value">{{ row.label }}：{{ row.desc }}</text>
        </view>
      </view>
    </view>

    <view class="se-card policy__block">
      <text class="policy__block-title">四、到账时间</text>
      <text class="policy__paragraph">
        本项目支付渠道为 Mock 网关，退款在接口请求内同步返回结果，
        成功后退款单状态直接变为「退款成功（30）」。
      </text>
      <text class="policy__paragraph">
        真实支付渠道尚未接入，因此真实渠道的到账时效无法在本项目内给出；
        接入后需以渠道返回的退款结果为准。
      </text>
    </view>

    <view class="se-card policy__block">
      <text class="policy__block-title">五、退款进度怎么查</text>
      <text class="policy__paragraph">
        售后单列表、退款单详情接口后端尚未提供，无法按退款单号查询进度。
      </text>
      <text class="policy__paragraph">
        可在订单详情查看 refundStatus 与 refundedAmount（已退款金额）。
        注意订单详情的 refundStatus 是订单级标识：0 无退款、1 部分退款、2 全额退款，
        不是上面的退款单状态码。
      </text>
      <text class="policy__paragraph">
        退款申请成功页会展示 refundNo，请先截图保存。
      </text>
    </view>

    <view class="se-card policy__block">
      <text class="policy__block-title">六、凭证与人工处理</text>
      <text class="policy__paragraph">
        文件上传接口后端尚未提供，申请页选择的凭证图片只在本机预览，不会提交。
      </text>
      <text class="policy__paragraph">
        订单已进入配送或已完成、需要人工判责时，可通过帮助中心提交工单或意见反馈，
        在内容里描述订单号与问题。
      </text>
    </view>

    <view class="policy__actions">
      <button class="se-btn se-btn-ghost policy__btn" @click="goOrders">去订单列表</button>
      <button class="se-btn se-btn-plain policy__btn" @click="goHelp">帮助中心</button>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 退款规则静态说明页，不请求接口。
 * 内容来源：solo-eats-order/RefundServiceImpl、RefundStatus、
 * 以及后端 SaveAddressRequest / RefundRequest 的字段约束。
 */

interface StatusRow {
  status: string
  result: string
  allowed: boolean
}

const STATUS_ROWS: StatusRow[] = [
  { status: '待付款（0）', result: '无需退款，直接在订单详情取消订单', allowed: false },
  { status: '待接单（10）', result: '可申请整单退款', allowed: true },
  { status: '备餐中（20）', result: '可申请整单退款', allowed: true },
  { status: '待骑手取货（30）', result: '不支持自助退款，请联系客服', allowed: false },
  { status: '待用户取餐（31）', result: '不支持自助退款，请联系客服', allowed: false },
  { status: '配送中（40）', result: '不支持自助退款，请联系客服', allowed: false },
  { status: '已送达（50）', result: '不支持自助退款，可提交工单', allowed: false },
  { status: '已取餐（51）', result: '不支持自助退款，可提交工单', allowed: false },
  { status: '已完成（60）', result: '不支持自助退款，可提交工单', allowed: false },
  { status: '已取消（70）', result: '订单已取消，无需重复申请', allowed: false },
]

const REFUND_ROWS = [
  { code: '0', label: '已申请', desc: '退款单已创建，尚未调用渠道' },
  { code: '20', label: '退款中', desc: '已提交渠道，等待结果' },
  { code: '30', label: '退款成功', desc: '渠道返回成功，库存与券已回滚' },
  { code: '31', label: '退款失败', desc: '渠道退款失败，可联系客服' },
  { code: '40', label: '退款已取消', desc: '退款单被取消，未发生退款' },
]

function goOrders(): void {
  uni.switchTab({ url: '/pages/order/List' })
}

function goHelp(): void {
  uni.navigateTo({ url: '/pages/mine/Help' })
}
</script>

<style scoped>
.policy {
  min-height: 100vh;
  padding: 20rpx 24rpx 60rpx;
  background-color: #f5f5f7;
}

.policy__intro {
  padding: 28rpx 24rpx;
  margin-bottom: 16rpx;
}

.policy__title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  margin-bottom: 10rpx;
}

.policy__desc {
  font-size: 24rpx;
  line-height: 1.7;
  color: #666666;
}

.policy__block {
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.policy__block-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #1f1f1f;
  margin-bottom: 14rpx;
}

.policy__paragraph {
  display: block;
  font-size: 25rpx;
  line-height: 1.8;
  color: #444444;
  margin-bottom: 10rpx;
}

.policy__paragraph:last-child {
  margin-bottom: 0;
}

.policy__table {
  border-top: 1rpx solid #f5f5f5;
}

.policy__table-row {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  padding: 12rpx 0;
  border-bottom: 1rpx solid #f5f5f5;
}

.policy__table-status {
  width: 200rpx;
  flex-shrink: 0;
  font-size: 24rpx;
  color: #8a8a8a;
}

.policy__table-value {
  flex: 1;
  font-size: 24rpx;
  line-height: 1.6;
  color: #444444;
}

.policy__table-value.is-blocked {
  color: #a26a2a;
}

.policy__note {
  display: block;
  margin-top: 14rpx;
  padding: 12rpx 14rpx;
  border-radius: 6rpx;
  background-color: #fff8ef;
  color: #a26a2a;
  font-size: 22rpx;
  line-height: 1.7;
}

.policy__actions {
  display: flex;
  gap: 16rpx;
  margin-top: 12rpx;
}

.policy__btn {
  flex: 1;
}
</style>
