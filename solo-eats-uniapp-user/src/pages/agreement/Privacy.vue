<template>
  <view class="agreement">
    <view class="agreement__head">
      <text class="agreement__updated">更新日期：2026-10-27</text>
      <text class="agreement__hint">{{ doc.summary }}</text>
    </view>

    <view class="se-card agreement__body">
      <view v-for="section in doc.sections" :key="section.title" class="agreement__section">
        <text class="agreement__section-title">{{ section.title }}</text>
        <text
          v-for="(paragraph, index) in section.paragraphs"
          :key="`${section.title}-${index}`"
          class="agreement__paragraph"
        >
          {{ paragraph }}
        </text>
      </view>
    </view>

    <view class="se-fixed-bottom agreement__actions">
      <button class="se-btn se-btn-primary" @click="back">我已知晓</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

/**
 * 协议页：纯静态文本，不请求接口。
 * type=user 展示《用户协议》，其余（含缺省）展示《隐私政策》。
 * 入口在 pages/login/Login.vue 的「登录即表示同意」一行。
 */

interface Section {
  title: string
  paragraphs: string[]
}

interface AgreementDoc {
  title: string
  summary: string
  sections: Section[]
}

const PRIVACY_DOC: AgreementDoc = {
  title: '隐私政策',
  summary: '本政策说明「数创星光外卖」收集哪些信息、用于什么目的，以及你可以如何管理这些信息。',
  sections: [
    {
      title: '一、我们收集的信息',
      paragraphs: [
        '1. 手机号：使用短信验证码登录时收集，用于账号识别与登录校验。手机号在数据库中加密存储，接口返回时为脱敏值（如 138****0002）。',
        '2. 收货地址：你主动填写的收件人姓名、手机号、省市区、详细地址、门牌号与经纬度，用于配送范围校验和骑手配送。',
        '3. 订单与交易记录：门店、商品、规格、数量、金额（以分为单位）、配送或自取方式、订单状态流转记录、退款与售后记录。',
        '4. 钱包信息：余额、冻结金额、累计充值与消费、收支流水。',
        '5. 设备与日志：接口调用日志会记录 Trace ID、请求路径与耗时，用于排查故障；日志不记录完整手机号、身份证号与银行卡号。',
      ],
    },
    {
      title: '二、信息的使用目的',
      paragraphs: [
        '1. 完成下单、支付、备餐、配送或到店自取、取餐码核销等履约流程。',
        '2. 校验配送范围与起送价、计算配送费与预计送达时间。',
        '3. 处理售后、退款、投诉工单与客服回访。',
        '4. 识别重复提交与异常交易：下单、支付、退款、充值接口使用幂等键防止重复扣款。',
      ],
    },
    {
      title: '三、信息的共享范围',
      paragraphs: [
        '1. 承接你订单的门店可查看订单商品、备注与取餐信息；配送骑手可查看收货地址与脱敏联系电话。',
        '2. 本期支付走平台 Mock 网关，不向第三方支付机构传输支付信息。',
        '3. 除法律法规要求外，我们不会向第三方出售你的个人信息。',
      ],
    },
    {
      title: '四、本地存储',
      paragraphs: [
        '1. 为保持登录态，浏览器或 App 本地会保存 token、tokenExpireAt、userId 与昵称。',
        '2. 购物车按门店保存在本地（cart:{shopId}），不随账号同步；更换设备后购物车不会保留。',
        '3. 首页定位结果保存在 location，最近一次收货地址保存在 address:last，搜索记录保存在本地搜索历史。',
        '4. 下单、支付、充值会在本地暂存幂等键（idem:*），成功后清除。清除本地缓存会同时退出登录。',
      ],
    },
    {
      title: '五、你的权利',
      paragraphs: [
        '1. 在「我的 → 收货地址」中新增、修改、删除地址与设置默认地址。',
        '2. 在「我的 → 我的钱包」查看余额与全部收支流水。',
        '3. 在「我的 → 设置」中清除本地缓存；在「我的 → 意见反馈」或「我的工单」提交账号与数据相关请求。',
      ],
    },
    {
      title: '六、政策更新',
      paragraphs: [
        '政策内容变更时会在本页更新「更新日期」。涉及信息收集范围变化时，会在登录页或消息中心提示。',
      ],
    },
  ],
}

const USER_DOC: AgreementDoc = {
  title: '用户协议',
  summary: '本协议约定「数创星光外卖」账号、下单、支付、钱包与评价的使用规则。',
  sections: [
    {
      title: '一、账号与登录',
      paragraphs: [
        '1. 账号以手机号为准，使用短信验证码登录；未注册的手机号首次登录时自动注册。',
        '2. 登录凭证为 JWT，过期后接口返回 10003，需要重新登录；平台不提供静默续期。',
        '3. 请勿将账号交由他人使用，由此产生的订单与费用由账号持有人承担。',
      ],
    },
    {
      title: '二、下单与履约',
      paragraphs: [
        '1. 商品金额、包装费、配送费与优惠减免以确认订单页展示为准，金额以分为最小单位计算。',
        '2. 外卖配送需满足门店起送价与配送范围；不满足时「去结算」不可提交。到店自取不收取配送费。',
        '3. 订单提交后按状态机流转：待付款、待接单、备餐中、待取餐或配送中、已完成、已取消。已支付订单的取消受当前状态限制，可能转入退款流程。',
        '4. 自取订单凭取餐码到店取餐，取餐码在门店配置的有效时间内有效。',
      ],
    },
    {
      title: '三、支付与退款',
      paragraphs: [
        '1. 本期支付使用平台 Mock 网关完成，不产生真实扣款。',
        '2. 退款按订单状态与售后规则发起，退款金额原路退回；处理进度可在「订单详情 → 售后」查看。',
      ],
    },
    {
      title: '四、钱包与充值',
      paragraphs: [
        '1. 单次模拟充值金额为 0.01 元至 1000.00 元，充值后余额计入「累计充值」。',
        '2. 余额仅用于平台内订单消费，不可转账给其他账号。',
        '3. 同一幂等键重复提交不会重复入账；充值记录可在钱包流水中核对。',
      ],
    },
    {
      title: '五、评价与内容',
      paragraphs: [
        '1. 仅已完成订单可提交评价，每个订单商品只能评价一次，可追加一次追评。',
        '2. 评价需经平台审核后展示；选择匿名后，评价列表不展示用户身份。',
        '3. 不得发布违法、辱骂、广告或与订单无关的内容，平台可对违规评价隐藏处理。',
      ],
    },
    {
      title: '六、协议变更',
      paragraphs: ['协议变更时会在本页更新「更新日期」，继续使用平台服务视为接受变更后的条款。'],
    },
  ],
}

const type = ref<'privacy' | 'user'>('privacy')
const doc = computed<AgreementDoc>(() => (type.value === 'user' ? USER_DOC : PRIVACY_DOC))

onLoad((options) => {
  type.value = String(options?.type || '') === 'user' ? 'user' : 'privacy'
  uni.setNavigationBarTitle({ title: doc.value.title })
})

function back(): void {
  if (getCurrentPages().length > 1) {
    uni.navigateBack()
    return
  }
  // 直接打开链接等场景没有上一页，回首页兜底
  uni.switchTab({ url: '/pages/index/Index' })
}
</script>

<style scoped>
.agreement {
  min-height: 100vh;
  padding: 24rpx 24rpx 200rpx;
  background-color: #f5f5f7;
}

.agreement__head {
  padding: 8rpx 8rpx 24rpx;
}

.agreement__updated {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #a0a0a0;
}

.agreement__hint {
  display: block;
  margin-top: 16rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #6f6f6f;
}

.agreement__body {
  padding: 8rpx 28rpx 28rpx;
}

.agreement__section {
  padding-top: 24rpx;
}

.agreement__section-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #1f1f1f;
}

.agreement__paragraph {
  display: block;
  margin-top: 12rpx;
  font-size: 25rpx;
  line-height: 1.8;
  color: #444444;
  word-break: break-all;
}

.agreement__actions {
  padding-top: 16rpx;
}
</style>
