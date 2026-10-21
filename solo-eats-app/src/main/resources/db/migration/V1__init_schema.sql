-- ============================================================
-- 数创星光外卖 P0 Schema
-- Flyway V1 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. 先连接 solo_eats 数据库，再执行本脚本。
--   2. 本脚本只适用于空库；重复执行会因表已存在而失败。
--   3. 金额字段统一为 bigint，单位分。
--   4. 不建外键，应用层保证一致性。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. 订单域
-- ------------------------------------------------------------
CREATE TABLE t_order (
  id                  bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  order_no            varchar(32) NOT NULL COMMENT '业务单号',
  user_id             bigint unsigned NOT NULL COMMENT '用户ID',
  merchant_id         bigint unsigned NOT NULL COMMENT '商家ID',
  shop_id             bigint unsigned NOT NULL COMMENT '门店ID',
  fulfillment_type    tinyint NOT NULL COMMENT '1配送 2自取',
  status              tinyint NOT NULL DEFAULT 0 COMMENT '订单状态',
  payment_status      tinyint NOT NULL DEFAULT 0 COMMENT '0未支付 1已支付 2已退款 3部分退款',
  refund_status       tinyint NOT NULL DEFAULT 0 COMMENT '0无 1部分 2全额',
  exception_type      tinyint NOT NULL DEFAULT 0 COMMENT '异常类型',
  settlement_status   tinyint NOT NULL DEFAULT 0 COMMENT '0待结算 1已结算 2冻结',
  goods_amount        bigint NOT NULL DEFAULT 0 COMMENT '商品金额，分',
  packaging_fee       bigint NOT NULL DEFAULT 0 COMMENT '打包费，分',
  delivery_fee        bigint NOT NULL DEFAULT 0 COMMENT '配送费，分；自取为0',
  discount_amount     bigint NOT NULL DEFAULT 0 COMMENT '优惠总额，分',
  platform_discount   bigint NOT NULL DEFAULT 0 COMMENT '平台承担优惠，分',
  merchant_discount   bigint NOT NULL DEFAULT 0 COMMENT '商家承担优惠，分',
  pay_amount          bigint NOT NULL DEFAULT 0 COMMENT '用户实付，分',
  refunded_amount     bigint NOT NULL DEFAULT 0 COMMENT '已退金额，分',
  address_id          bigint unsigned NULL COMMENT '收货地址ID',
  receiver_name       varchar(32) NULL COMMENT '收货人',
  receiver_phone      varchar(20) NULL COMMENT '收货手机号',
  receiver_address    varchar(255) NULL COMMENT '收货地址快照',
  receiver_lng        decimal(10,6) NULL COMMENT '收货经度',
  receiver_lat        decimal(10,6) NULL COMMENT '收货纬度',
  pickup_phone        varchar(20) NULL COMMENT '自取人手机号',
  pickup_code         varchar(24) NULL COMMENT '取餐码',
  pickup_code_date    date NULL COMMENT '取餐码日期',
  pickup_code_status  tinyint NOT NULL DEFAULT 0 COMMENT '0未用 1已用 2过期',
  pickup_ready_at     datetime(3) NULL COMMENT '备餐完成时间',
  picked_up_at        datetime(3) NULL COMMENT '取餐时间',
  pickup_expire_at    datetime(3) NULL COMMENT '自取超时时间',
  expected_delivery_at datetime(3) NULL COMMENT '承诺送达时间',
  completed_at        datetime(3) NULL COMMENT '完成时间',
  settle_eligible_at  datetime(3) NULL COMMENT '可结算时间',
  cancelled_at        datetime(3) NULL COMMENT '取消时间',
  cancel_reason       varchar(128) NULL COMMENT '取消原因',
  remark              varchar(255) NULL COMMENT '用户备注',
  version             int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  created_at          datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  UNIQUE KEY uk_pickup_code (shop_id, pickup_code_date, pickup_code),
  KEY idx_user_status (user_id, status, created_at),
  KEY idx_shop_status (shop_id, status, created_at),
  KEY idx_merchant_settle (merchant_id, settlement_status, completed_at),
  KEY idx_settle_eligible (settlement_status, settle_eligible_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单主表';

CREATE TABLE t_order_item (
  id                bigint unsigned NOT NULL AUTO_INCREMENT,
  order_id          bigint unsigned NOT NULL,
  shop_id           bigint unsigned NOT NULL,
  spu_id            bigint unsigned NOT NULL,
  sku_id            bigint unsigned NOT NULL,
  sku_name          varchar(128) NOT NULL COMMENT '商品名快照',
  spec_json         json NULL COMMENT '规格快照',
  image_url         varchar(512) NULL,
  original_unit_price bigint NOT NULL COMMENT '原单价，分',
  quantity          int NOT NULL COMMENT '购买数量',
  original_amount   bigint NOT NULL COMMENT '原价小计，分',
  discount_amount   bigint NOT NULL DEFAULT 0 COMMENT '优惠分摊，分',
  merchant_discount bigint NOT NULL DEFAULT 0 COMMENT '商家承担优惠，分',
  platform_discount bigint NOT NULL DEFAULT 0 COMMENT '平台承担优惠，分',
  payable_amount    bigint NOT NULL COMMENT '分摊后应付，分',
  refunded_qty      int NOT NULL DEFAULT 0 COMMENT '已退数量',
  refunded_amount   bigint NOT NULL DEFAULT 0 COMMENT '已退金额，分',
  restock_flag      tinyint NOT NULL DEFAULT 0 COMMENT '退款是否回补库存',
  created_at        datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_order (order_id),
  KEY idx_sku (shop_id, sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单明细';

CREATE TABLE t_order_discount (
  id              bigint unsigned NOT NULL AUTO_INCREMENT,
  order_id        bigint unsigned NOT NULL,
  discount_type   tinyint NOT NULL COMMENT '1券 2满减 3会员 4自取立减',
  source_id       bigint unsigned NOT NULL COMMENT '券ID或活动ID',
  bearer          tinyint NOT NULL COMMENT '1平台 2商家',
  amount          bigint NOT NULL COMMENT '优惠金额，分',
  apportion_json  json NOT NULL COMMENT '按明细分摊结果',
  created_at      datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_order (order_id),
  KEY idx_source (discount_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单优惠快照';

CREATE TABLE t_order_status_log (
  id             bigint unsigned NOT NULL AUTO_INCREMENT,
  order_id       bigint unsigned NOT NULL,
  from_status    tinyint NOT NULL,
  to_status      tinyint NOT NULL,
  event          varchar(32) NOT NULL,
  operator_type  tinyint NOT NULL COMMENT '1用户 2商家 3骑手 4平台 5系统',
  operator_id    bigint unsigned NULL,
  request_id     varchar(128) NOT NULL COMMENT '幂等键或业务唯一键',
  remark         varchar(255) NULL,
  created_at     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_request (order_id, request_id),
  KEY idx_order_time (order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单状态流水';

-- ------------------------------------------------------------
-- 2. 库存域
-- ------------------------------------------------------------
CREATE TABLE t_sku_stock (
  id            bigint unsigned NOT NULL AUTO_INCREMENT,
  shop_id       bigint unsigned NOT NULL,
  sku_id        bigint unsigned NOT NULL,
  available_qty int unsigned NOT NULL DEFAULT 0 COMMENT '可售库存',
  locked_qty    int unsigned NOT NULL DEFAULT 0 COMMENT '锁定库存',
  sold_qty      int unsigned NOT NULL DEFAULT 0 COMMENT '已售库存',
  sold_out      tinyint(1) NOT NULL DEFAULT 0 COMMENT '估清标记',
  version       int unsigned NOT NULL DEFAULT 0,
  updated_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_shop_sku (shop_id, sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店SKU库存';

CREATE TABLE t_stock_lock (
  id         bigint unsigned NOT NULL AUTO_INCREMENT,
  order_id   bigint unsigned NOT NULL,
  shop_id    bigint unsigned NOT NULL,
  sku_id     bigint unsigned NOT NULL,
  qty        int unsigned NOT NULL,
  status     tinyint NOT NULL DEFAULT 0 COMMENT '0锁定 1确认 2释放 3退款回补',
  expire_at  datetime(3) NOT NULL,
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_sku (order_id, sku_id),
  KEY idx_status_expire (status, expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存锁定记录';

CREATE TABLE t_stock_log (
  id               bigint unsigned NOT NULL AUTO_INCREMENT,
  shop_id          bigint unsigned NOT NULL,
  sku_id           bigint unsigned NOT NULL,
  biz_type         varchar(16) NOT NULL COMMENT 'ORDER/REFUND/SECKILL',
  biz_id           bigint unsigned NOT NULL,
  op               varchar(16) NOT NULL COMMENT 'LOCK/CONFIRM/RELEASE/REFUND',
  qty              int NOT NULL,
  before_available int NOT NULL,
  after_available  int NOT NULL,
  created_at       datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz_op (biz_type, biz_id, sku_id, op),
  KEY idx_sku_time (shop_id, sku_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存流水';

-- ------------------------------------------------------------
-- 3. 营销域
-- ------------------------------------------------------------
CREATE TABLE t_coupon_template (
  id               bigint unsigned NOT NULL AUTO_INCREMENT,
  name             varchar(64) NOT NULL,
  coupon_type      tinyint NOT NULL COMMENT '1满减 2折扣 3无门槛',
  threshold_amount bigint NOT NULL DEFAULT 0 COMMENT '门槛，分',
  discount_amount  bigint NOT NULL DEFAULT 0 COMMENT '满减金额，分',
  discount_rate    decimal(6,4) NULL COMMENT '折扣率',
  bearer           tinyint NOT NULL DEFAULT 1 COMMENT '1平台 2商家',
  total_qty        int NOT NULL DEFAULT 0,
  issued_qty       int NOT NULL DEFAULT 0,
  valid_start      datetime(3) NULL,
  valid_end        datetime(3) NULL,
  valid_days       int NULL COMMENT '领取后有效天数',
  status           tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at       datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_status_time (status, valid_start, valid_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠券模板';

CREATE TABLE t_user_coupon (
  id          bigint unsigned NOT NULL AUTO_INCREMENT,
  user_id     bigint unsigned NOT NULL,
  template_id bigint unsigned NOT NULL,
  status      tinyint NOT NULL DEFAULT 0 COMMENT '0未用 1锁定 2已用 3过期 4作废 5已退款',
  order_id    bigint unsigned NULL,
  locked_at   datetime(3) NULL,
  used_at     datetime(3) NULL,
  expire_at   datetime(3) NOT NULL,
  version     int unsigned NOT NULL DEFAULT 0,
  created_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_user_status (user_id, status, expire_at),
  KEY idx_order (order_id),
  KEY idx_template (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户优惠券';

CREATE TABLE t_user_coupon_log (
  id             bigint unsigned NOT NULL AUTO_INCREMENT,
  user_coupon_id bigint unsigned NOT NULL,
  order_id       bigint unsigned NULL,
  from_status    tinyint NOT NULL,
  to_status      tinyint NOT NULL,
  op             varchar(16) NOT NULL COMMENT 'LOCK/CONSUME/RELEASE/ROLLBACK',
  created_at     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_coupon_time (user_coupon_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠券状态流水';

-- ------------------------------------------------------------
-- 4. 支付与退款域
-- ------------------------------------------------------------
CREATE TABLE t_payment_order (
  id               bigint unsigned NOT NULL AUTO_INCREMENT,
  payment_no       varchar(32) NOT NULL COMMENT '支付单号',
  order_id         bigint unsigned NOT NULL,
  channel          tinyint NOT NULL COMMENT '1支付宝沙箱 2余额 3模拟微信',
  amount           bigint NOT NULL COMMENT '支付金额，分',
  status           tinyint NOT NULL DEFAULT 0 COMMENT '0INIT 10PAYING 20SUCCESS 30FAIL 40CLOSED 50REFUNDING 60REFUNDED 61PARTIAL_REFUNDED',
  channel_trade_no varchar(64) NULL COMMENT '渠道交易号',
  idempotency_key  varchar(128) NOT NULL COMMENT '客户端幂等键或服务端组合键',
  paid_at          datetime(3) NULL,
  created_at       datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_payment_no (payment_no),
  UNIQUE KEY uk_channel_trade (channel, channel_trade_no),
  UNIQUE KEY uk_idem (idempotency_key),
  KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付单';

CREATE TABLE t_refund_order (
  id                bigint unsigned NOT NULL AUTO_INCREMENT,
  refund_no         varchar(32) NOT NULL COMMENT '退款单号',
  order_id          bigint unsigned NOT NULL,
  refund_type       tinyint NOT NULL COMMENT '1全额 2部分 3迟到支付',
  amount            bigint NOT NULL COMMENT '退款金额，分',
  reason            varchar(64) NOT NULL,
  evidence_json     json NULL COMMENT '凭证URL列表',
  status            tinyint NOT NULL DEFAULT 0 COMMENT '0申请 10商家同意 11商家拒绝 12平台仲裁 20退款中 30成功 31失败 40取消 50自动同意',
  channel_refund_no varchar(64) NULL COMMENT '渠道退款号',
  idempotency_key   varchar(128) NOT NULL COMMENT '客户端幂等键或服务端组合键',
  merchant_review_at datetime(3) NULL,
  success_at        datetime(3) NULL,
  created_at        datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_refund_no (refund_no),
  UNIQUE KEY uk_idem (idempotency_key),
  KEY idx_order (order_id),
  KEY idx_status_time (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退款单';

CREATE TABLE t_refund_item (
  id            bigint unsigned NOT NULL AUTO_INCREMENT,
  refund_id     bigint unsigned NOT NULL,
  order_item_id bigint unsigned NOT NULL,
  sku_id        bigint unsigned NOT NULL,
  qty           int NOT NULL,
  refund_amount bigint NOT NULL COMMENT '退款金额，分',
  restock_flag  tinyint NOT NULL DEFAULT 0 COMMENT '是否回补库存',
  created_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_refund_item (refund_id, order_item_id),
  KEY idx_order_item (order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退款明细';

-- ------------------------------------------------------------
-- 5. 配送域
-- ------------------------------------------------------------
CREATE TABLE t_delivery_order (
  id                 bigint unsigned NOT NULL AUTO_INCREMENT,
  delivery_no        varchar(32) NOT NULL,
  order_id           bigint unsigned NOT NULL,
  shop_id            bigint unsigned NOT NULL,
  rider_id           bigint unsigned NULL,
  status             tinyint NOT NULL DEFAULT 0 COMMENT '0待派单 10已推送 20已接单 30已取货 40配送中 50已送达 70已取消',
  pickup_address     varchar(255) NOT NULL,
  pickup_lng         decimal(10,6) NULL,
  pickup_lat         decimal(10,6) NULL,
  receiver_address   varchar(255) NOT NULL,
  receiver_lng       decimal(10,6) NULL,
  receiver_lat       decimal(10,6) NULL,
  distance_m         int NULL COMMENT '距离，米',
  delivery_fee       bigint NOT NULL DEFAULT 0 COMMENT '骑手配送费，分',
  dispatch_count     int NOT NULL DEFAULT 0,
  dispatch_expire_at datetime(3) NULL,
  accepted_at        datetime(3) NULL,
  picked_up_at       datetime(3) NULL,
  delivered_at       datetime(3) NULL,
  cancel_reason      varchar(128) NULL,
  version            int unsigned NOT NULL DEFAULT 0,
  created_at         datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at         datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_delivery_no (delivery_no),
  UNIQUE KEY uk_order (order_id),
  KEY idx_rider_status (rider_id, status),
  KEY idx_status_expire (status, dispatch_expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配送单';

CREATE TABLE t_dispatch_record (
  id                bigint unsigned NOT NULL AUTO_INCREMENT,
  delivery_order_id bigint unsigned NOT NULL,
  rider_id          bigint unsigned NOT NULL,
  dispatch_type     tinyint NOT NULL COMMENT '1自动 2抢单 3手动',
  status            tinyint NOT NULL COMMENT '0推送 1接受 2拒绝 3超时 4取消',
  score             int NULL COMMENT '派单评分',
  expire_at         datetime(3) NULL,
  responded_at      datetime(3) NULL,
  created_at        datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_delivery (delivery_order_id),
  KEY idx_rider_status (rider_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='派单记录';

CREATE TABLE t_order_exception (
  id                bigint unsigned NOT NULL AUTO_INCREMENT,
  order_id          bigint unsigned NOT NULL,
  delivery_order_id bigint unsigned NULL,
  exception_type    tinyint NOT NULL COMMENT '1无骑手 2联系不上 3地址异常 4餐品破损 5商家超时 6自取超时 7其他',
  source_type       tinyint NOT NULL COMMENT '1用户 2商家 3骑手 4系统 5平台',
  source_id         bigint unsigned NULL,
  description       varchar(255) NULL,
  status            tinyint NOT NULL DEFAULT 0 COMMENT '0待处理 1处理中 2已解决 3已关闭',
  handler_id        bigint unsigned NULL,
  resolved_at       datetime(3) NULL,
  created_at        datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_order (order_id),
  KEY idx_status_time (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='异常订单池';

-- ------------------------------------------------------------
-- 6. 结算域
-- ------------------------------------------------------------
CREATE TABLE t_settlement_bill (
  id                  bigint unsigned NOT NULL AUTO_INCREMENT,
  bill_no             varchar(32) NOT NULL,
  merchant_id         bigint unsigned NOT NULL,
  shop_id             bigint unsigned NULL COMMENT '按商家出账时为空',
  bill_date           date NOT NULL,
  period_start        datetime(3) NOT NULL,
  period_end          datetime(3) NOT NULL,
  gross_amount        bigint NOT NULL DEFAULT 0 COMMENT '商品+打包，分',
  merchant_discount   bigint NOT NULL DEFAULT 0 COMMENT '商家承担优惠，分',
  platform_discount   bigint NOT NULL DEFAULT 0 COMMENT '平台承担优惠，分',
  commission_base     bigint NOT NULL DEFAULT 0 COMMENT '佣金基数，分',
  commission_amount   bigint NOT NULL DEFAULT 0 COMMENT '技术服务费，分',
  performance_fee     bigint NOT NULL DEFAULT 0 COMMENT '履约服务费，P0为0',
  refund_amount       bigint NOT NULL DEFAULT 0 COMMENT '退款冲抵，分',
  carry_forward_amount bigint NOT NULL DEFAULT 0 COMMENT '上期结转负项，分',
  payable_amount      bigint NOT NULL DEFAULT 0 COMMENT '应结金额，分',
  status              tinyint NOT NULL DEFAULT 0 COMMENT '0已生成 10待审核 20已审核 30已打款 40已驳回',
  reviewed_by         bigint unsigned NULL,
  reviewed_at         datetime(3) NULL,
  paid_at             datetime(3) NULL,
  created_at          datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_bill_no (bill_no),
  UNIQUE KEY uk_merchant_date (merchant_id, bill_date),
  KEY idx_status_date (status, bill_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商家结算账单';

CREATE TABLE t_settlement_item (
  id         bigint unsigned NOT NULL AUTO_INCREMENT,
  bill_id    bigint unsigned NOT NULL,
  order_id   bigint unsigned NULL,
  item_type  tinyint NOT NULL COMMENT '1商品 2佣金 3履约费 4退款 5调整',
  source_id  bigint unsigned NULL COMMENT '退款单ID或调整单ID',
  amount     bigint NOT NULL COMMENT '正负均可，分',
  remark     varchar(255) NULL,
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_bill (bill_id),
  KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算明细';

CREATE TABLE t_rider_income (
  id                  bigint unsigned NOT NULL AUTO_INCREMENT,
  rider_id            bigint unsigned NOT NULL,
  delivery_order_id   bigint unsigned NOT NULL,
  order_id            bigint unsigned NOT NULL,
  base_fee            bigint NOT NULL DEFAULT 0,
  distance_fee        bigint NOT NULL DEFAULT 0,
  time_subsidy        bigint NOT NULL DEFAULT 0,
  weather_subsidy     bigint NOT NULL DEFAULT 0,
  tip_amount          bigint NOT NULL DEFAULT 0,
  penalty_amount      bigint NOT NULL DEFAULT 0,
  compensation_amount bigint NOT NULL DEFAULT 0 COMMENT '取消/异常补偿，分',
  total_amount        bigint NOT NULL DEFAULT 0,
  settlement_date     date NOT NULL,
  status              tinyint NOT NULL DEFAULT 0 COMMENT '0待结算 1已结算',
  created_at          datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_delivery (delivery_order_id),
  KEY idx_rider_date (rider_id, settlement_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='骑手收入明细';

CREATE TABLE t_payout_order (
  id           bigint unsigned NOT NULL AUTO_INCREMENT,
  payout_no    varchar(32) NOT NULL,
  subject_type tinyint NOT NULL COMMENT '1商家 2骑手',
  subject_id   bigint unsigned NOT NULL,
  bill_id      bigint unsigned NULL,
  amount       bigint NOT NULL,
  channel      tinyint NOT NULL COMMENT '1支付宝 2银行卡',
  status       tinyint NOT NULL DEFAULT 0 COMMENT '0待审核 10已审核 20打款中 30已到账 40已驳回',
  fail_reason  varchar(255) NULL,
  created_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_payout_no (payout_no),
  KEY idx_subject (subject_type, subject_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提现单';

-- ------------------------------------------------------------
-- 7. 基础设施
-- ------------------------------------------------------------
CREATE TABLE t_idempotency (
  id              bigint unsigned NOT NULL AUTO_INCREMENT,
  idempotency_key varchar(128) NOT NULL COMMENT 'biz_type:client_key',
  biz_type        varchar(32) NOT NULL,
  biz_id          bigint unsigned NULL,
  request_hash    varchar(64) NOT NULL COMMENT 'SHA-256',
  response_code   int NULL,
  response_body   json NULL,
  status          tinyint NOT NULL DEFAULT 0 COMMENT '0处理中 1成功 2失败',
  expire_at       datetime(3) NOT NULL,
  created_at      datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_key (idempotency_key),
  KEY idx_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='幂等记录';

CREATE TABLE t_outbox_event (
  id             bigint unsigned NOT NULL AUTO_INCREMENT,
  aggregate_type varchar(32) NOT NULL,
  aggregate_id   bigint unsigned NOT NULL,
  event_type     varchar(64) NOT NULL,
  payload        json NOT NULL,
  status         tinyint NOT NULL DEFAULT 0 COMMENT '0待发 1已发 2失败',
  retry_count    int NOT NULL DEFAULT 0,
  next_retry_at  datetime(3) NULL,
  created_at     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_status_retry (status, next_retry_at),
  KEY idx_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事务消息Outbox';

-- 初始化完成
