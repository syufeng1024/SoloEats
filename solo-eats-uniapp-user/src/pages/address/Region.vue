<template>
  <view class="help">
    <view class="se-card help__intro">
      <text class="help__title">地址填写帮助</text>
      <text class="help__desc">
        省市区选择放在「新增 / 编辑地址」页内完成，本页说明填写规则与常见问题。
      </text>
    </view>

    <view v-for="item in FAQS" :key="item.title" class="se-card help__item">
      <text class="help__q">{{ item.title }}</text>
      <text class="help__a">{{ item.answer }}</text>
    </view>

    <view class="se-card help__item">
      <text class="help__q">地址字段对应关系</text>
      <view class="help__table">
        <view v-for="row in FIELDS" :key="row.field" class="help__table-row">
          <text class="help__table-field">{{ row.field }}</text>
          <text class="help__table-value">{{ row.value }}</text>
        </view>
      </view>
    </view>

    <button class="se-btn se-btn-primary help__back" @click="goBack">返回继续填写</button>
  </view>
</template>

<script setup lang="ts">
/**
 * 地址填写帮助。
 *
 * 原计划的独立「区域选择」页会与编辑页内的 picker 功能重复，
 * 这里改为不依赖接口的说明页，避免为凑页数保留一个空壳页面。
 * 内容与后端 SaveAddressRequest、AddressView 的实际行为一致。
 */

interface HelpItem {
  title: string
  answer: string
}

const FAQS: HelpItem[] = [
  {
    title: '为什么手机号显示为 138****8000？',
    answer:
      '地址接口只返回脱敏手机号，目的是避免完整号码在列表和编辑页反复展示。编辑地址时需要重新输入完整号码，后端会校验 11 位手机号格式。下单页也会单独填写联系电话。',
  },
  {
    title: '省市区里找不到我所在的城市或区县',
    answer:
      '省市区选择使用内置的精简区域表，只覆盖常用省市。未收录的城市或区县请直接写在「详细地址」里，例如“浙江省湖州市德清县”，后端只保存文本，不做行政区划编码校验。',
  },
  {
    title: '详细地址和门牌号怎么填？',
    answer:
      '详细地址填街道、小区、楼栋，例如“星光路 18 号星光花园 3 栋”；门牌号单独填“3 单元 502 室”。两个字段会按顺序拼接展示，门牌号最多 64 个字符。',
  },
  {
    title: '能不能在地图上选点？',
    answer:
      '地址接口支持经度、纬度字段，但当前用户端未接入地图选点，新增地址不会写入坐标。配送范围与运费以下单页的定位和门店围栏校验结果为准。',
  },
  {
    title: '删除默认地址后怎么办？',
    answer:
      '删除后列表会重新加载。你可以在剩余地址里点「设为默认」，下单时会优先选中默认地址；如果没有任何地址，下单页需要先新增。',
  },
  {
    title: '地址填错了但订单已提交',
    answer:
      '订单提交后地址无法在 App 内修改。待接单、备餐中的订单可在订单详情申请退款；已进入配送的订单请联系客服或提交工单处理。',
  },
]

const FIELDS = [
  { field: '接收人', value: '收货人姓名，必填，最长 64 字符' },
  { field: '手机号', value: '11 位手机号，必填，格式为 1[3-9] 开头' },
  { field: '省 / 市 / 区', value: '选填，最长 64 字符，来自内置精简区域表' },
  { field: '详细地址', value: '必填，最长 255 字符' },
  { field: '门牌号', value: '选填，最长 64 字符' },
  { field: '标签', value: '选填，家 / 公司 / 学校，最长 16 字符' },
]

function goBack(): void {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.redirectTo({ url: '/pages/address/Edit' })
}
</script>

<style scoped>
.help {
  min-height: 100vh;
  padding: 20rpx 24rpx 60rpx;
  background-color: #f5f5f7;
}

.help__intro {
  padding: 28rpx 24rpx;
  margin-bottom: 16rpx;
}

.help__title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  margin-bottom: 10rpx;
}

.help__desc {
  font-size: 24rpx;
  line-height: 1.7;
  color: #666666;
}

.help__item {
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.help__q {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #1f1f1f;
  margin-bottom: 10rpx;
}

.help__a {
  font-size: 24rpx;
  line-height: 1.8;
  color: #555555;
}

.help__table {
  margin-top: 8rpx;
}

.help__table-row {
  display: flex;
  align-items: flex-start;
  padding: 12rpx 0;
  border-bottom: 1rpx solid #f5f5f5;
}

.help__table-row:last-child {
  border-bottom: none;
}

.help__table-field {
  width: 180rpx;
  flex-shrink: 0;
  font-size: 24rpx;
  color: #8a8a8a;
}

.help__table-value {
  flex: 1;
  font-size: 24rpx;
  line-height: 1.6;
  color: #444444;
}

.help__back {
  width: 100%;
  margin-top: 16rpx;
}
</style>
