-- ============================================================
-- 数创星光外卖 T11 阶段A：报表模块骨架与 6 张报表表
-- Flyway V23 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. V17 已被 T08 消息模块占用，按实测版本使用 V23（任务卡原写 V17）。
--   2. 一次建齐 6 张表：
--      t_report_daily_platform / t_report_daily_shop / t_report_daily_spu /
--      t_report_daily_rider / t_track_event / t_report_funnel_daily。
--      阶段A 只填充前两张，其余四张建表不写代码。
--   3. 金额一律 bigint 分；费率/评分 decimal；统计日期 date。
--   4. 报表模块只读业务表，本脚本不修改任何既有列（Expand-Contract），
--      只为聚合查询幂等追加 4 个源表索引。
--   5. 全部种子使用 ON DUPLICATE KEY UPDATE id=id，空库重建与重复执行均安全。
--   6. 菜单权限 id 从 115 起（V22 已用到 114），挂在「数据报表」(id=11) 下，
--      授平台超管 role_id = 1。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. 平台经营日报
--    GMV = 已完成订单 pay_amount 之和（剔除全额退款单）
--    客单价 = gmv / order_count，分母为 0 时写 0
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_report_daily_platform (
  id                  bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id           bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  stat_date           date NOT NULL COMMENT '统计日期，Asia/Shanghai 自然日',
  gmv                 bigint NOT NULL DEFAULT 0 COMMENT 'GMV，分',
  order_count         int NOT NULL DEFAULT 0 COMMENT '已完成订单数',
  paid_order_count    int NOT NULL DEFAULT 0 COMMENT '支付成功单数',
  refund_amount       bigint NOT NULL DEFAULT 0 COMMENT '退款金额，分',
  refund_order_count  int NOT NULL DEFAULT 0 COMMENT '退款成功单数',
  new_user_count      int NOT NULL DEFAULT 0 COMMENT '新增用户数',
  active_user_count   int NOT NULL DEFAULT 0 COMMENT '下单用户数（去重）',
  avg_order_amount    bigint NOT NULL DEFAULT 0 COMMENT '客单价，分',
  commission_income   bigint NOT NULL DEFAULT 0 COMMENT '佣金收入，分',
  settlement_expense  bigint NOT NULL DEFAULT 0 COMMENT '结算支出，分',
  subsidy_cost        bigint NOT NULL DEFAULT 0 COMMENT '平台补贴成本，分',
  gross_profit        bigint NOT NULL DEFAULT 0 COMMENT '毛利润，分；可为负',
  created_at          datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_date (tenant_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台经营日报';

-- ------------------------------------------------------------
-- 2. 门店经营日报
--    cancel_rate 为百分比数值（如 3.25 表示 3.25%）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_report_daily_shop (
  id                  bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id           bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  stat_date           date NOT NULL COMMENT '统计日期',
  shop_id             bigint unsigned NOT NULL COMMENT '门店ID',
  merchant_id         bigint unsigned NOT NULL COMMENT '商家ID',
  order_count         int NOT NULL DEFAULT 0 COMMENT '已完成订单数',
  gmv                 bigint NOT NULL DEFAULT 0 COMMENT 'GMV，分',
  refund_amount       bigint NOT NULL DEFAULT 0 COMMENT '退款金额，分',
  avg_prep_minutes    int NOT NULL DEFAULT 0 COMMENT '平均出餐时长（分钟）',
  cancel_rate         decimal(5,2) NOT NULL DEFAULT 0.00 COMMENT '取消率（%）',
  rating              decimal(3,2) NOT NULL DEFAULT 0.00 COMMENT '当日评价均分',
  pickup_order_count  int NOT NULL DEFAULT 0 COMMENT '自取完成单数',
  delivery_order_count int NOT NULL DEFAULT 0 COMMENT '配送完成单数',
  created_at          datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_shop_date (shop_id, stat_date),
  KEY idx_merchant_date (merchant_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店经营日报';

-- ------------------------------------------------------------
-- 3. 商品销售日报（阶段B 填充）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_report_daily_spu (
  id           bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id    bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  stat_date    date NOT NULL COMMENT '统计日期',
  shop_id      bigint unsigned NOT NULL COMMENT '门店ID',
  spu_id       bigint unsigned NOT NULL COMMENT 'SPU ID',
  category_id  bigint unsigned NULL COMMENT '品类ID',
  sale_qty     int NOT NULL DEFAULT 0 COMMENT '销售数量',
  sale_amount  bigint NOT NULL DEFAULT 0 COMMENT '销售金额，分',
  refund_qty   int NOT NULL DEFAULT 0 COMMENT '退款数量',
  created_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_shop_spu_date (shop_id, spu_id, stat_date),
  KEY idx_spu_date (spu_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品销售日报';

-- ------------------------------------------------------------
-- 4. 骑手配送日报（阶段B 填充）
--    on_time_rate 为百分比数值
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_report_daily_rider (
  id                    bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id             bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  stat_date             date NOT NULL COMMENT '统计日期',
  rider_id              bigint unsigned NOT NULL COMMENT '骑手ID',
  delivery_count        int NOT NULL DEFAULT 0 COMMENT '送达单数',
  on_time_count         int NOT NULL DEFAULT 0 COMMENT '准时单数',
  on_time_rate          decimal(5,2) NOT NULL DEFAULT 0.00 COMMENT '准时率（%）',
  online_minutes        int NOT NULL DEFAULT 0 COMMENT '在线时长（分钟）',
  income_amount         bigint NOT NULL DEFAULT 0 COMMENT '收入，分',
  penalty_amount        bigint NOT NULL DEFAULT 0 COMMENT '罚款，分',
  avg_delivery_minutes  int NOT NULL DEFAULT 0 COMMENT '平均配送时长（分钟）',
  rating                decimal(3,2) NOT NULL DEFAULT 0.00 COMMENT '骑手评分快照',
  created_at            datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at            datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_rider_date (rider_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='骑手配送日报';

-- ------------------------------------------------------------
-- 5. 埋点事件（阶段B 填充；只存业务 ID，不存手机号/身份证）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_track_event (
  id          bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id   bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  event_type  varchar(32) NOT NULL COMMENT 'HOME_EXPOSE/SHOP_CLICK/ADD_CART/ORDER_SUBMIT/PAY_SUCCESS',
  user_id     bigint unsigned NULL COMMENT '用户ID，匿名事件为空',
  device_id   varchar(64) NULL COMMENT '设备ID',
  biz_type    varchar(32) NULL COMMENT '业务类型',
  biz_id      bigint unsigned NULL COMMENT '业务ID',
  source      varchar(32) NULL COMMENT '来源渠道',
  extra_json  json NULL COMMENT '扩展参数，禁止写入个人信息',
  event_at    datetime(3) NOT NULL COMMENT '事件发生时间',
  created_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_type_time (event_type, event_at),
  KEY idx_user_time (user_id, event_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='埋点事件';

-- ------------------------------------------------------------
-- 6. 流量转化漏斗日报（阶段B 填充）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_report_funnel_daily (
  id            bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id     bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  stat_date     date NOT NULL COMMENT '统计日期',
  expose_count  int NOT NULL DEFAULT 0 COMMENT '首页曝光',
  click_count   int NOT NULL DEFAULT 0 COMMENT '店铺/商品点击',
  cart_count    int NOT NULL DEFAULT 0 COMMENT '加购',
  order_count   int NOT NULL DEFAULT 0 COMMENT '提交订单',
  pay_count     int NOT NULL DEFAULT 0 COMMENT '支付成功',
  created_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_date (tenant_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流量转化漏斗日报';

-- ------------------------------------------------------------
-- 7. 源表索引：日报与工作台按「状态 + 时间」聚合，既有索引均以
--    shop_id / merchant_id / user_id 开头，无法覆盖平台级全量查询。
--    MySQL 不支持 ADD INDEX IF NOT EXISTS，用 information_schema 判定后
--    动态执行，脚本可重复运行。
-- ------------------------------------------------------------
SET @has_idx := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 't_order'
    AND index_name = 'idx_status_completed'
);
SET @ddl := IF(@has_idx = 0,
  'ALTER TABLE t_order ADD INDEX idx_status_completed (status, completed_at)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 't_order'
    AND index_name = 'idx_status_created'
);
SET @ddl := IF(@has_idx = 0,
  'ALTER TABLE t_order ADD INDEX idx_status_created (status, created_at)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 't_order'
    AND index_name = 'idx_created_at'
);
SET @ddl := IF(@has_idx = 0,
  'ALTER TABLE t_order ADD INDEX idx_created_at (created_at)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 't_payment_order'
    AND index_name = 'idx_status_paid'
);
SET @ddl := IF(@has_idx = 0,
  'ALTER TABLE t_payment_order ADD INDEX idx_status_paid (status, paid_at)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 't_refund_order'
    AND index_name = 'idx_status_success'
);
SET @ddl := IF(@has_idx = 0,
  'ALTER TABLE t_refund_order ADD INDEX idx_status_success (status, success_at)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 8. 后台菜单与权限：挂在「数据报表」(id = 11) 下
-- ------------------------------------------------------------
INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (115, 1, 11, '经营总览',   2, '/report/overview', 'report/overview/index', 'report:overview:view',  'chart', 1, 0, 0),
  (116, 1, 11, '工作台数据卡', 3, NULL,               NULL,                    'report:dashboard:view', NULL,    2, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 115 AND 116
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
