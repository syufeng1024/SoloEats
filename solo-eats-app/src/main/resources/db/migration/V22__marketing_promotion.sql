-- ============================================================
-- 数创星光外卖 T12 阶段A-0：平台级单档满减 + 试算/分摊
-- Flyway V22 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. 库已到 V21，本脚本按实测版本使用 V22（任务卡原写 V18）。
--   2. 一次建齐 6 张表：满减三张（t_promotion / t_promotion_rule /
--      t_promotion_scope）+ 秒杀三张（t_seckill_activity / t_seckill_item /
--      t_seckill_order）。本阶段只读写满减三张，秒杀表建表不写代码。
--   3. 金额一律 bigint 分；折扣率 decimal(4,2)（如 0.85）。
--   4. 只新建表，不修改任何既有表结构（Expand-Contract）。
--   5. 全部种子使用 ON DUPLICATE KEY UPDATE id=id，空库重建与重复执行均安全。
--   6. 菜单权限 id 从 113 起（V19 已用到 112），挂在「营销中心」(id=6) 下，
--      授平台超管 role_id = 1。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. 满减/折扣活动主表
--    promotion_type：1满减 2折扣 3新人立减
--    bearer：1平台 2商家
--    scope_type：1全平台 2指定商家 3指定门店 4指定品类 5指定商品
--    status：0草稿 1进行中 2已结束 3暂停
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_promotion (
  id             bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id      bigint unsigned NOT NULL DEFAULT 1,
  promotion_type tinyint NOT NULL DEFAULT 1 COMMENT '1满减 2折扣 3新人立减',
  promotion_name varchar(64) NOT NULL,
  bearer         tinyint NOT NULL DEFAULT 1 COMMENT '1平台 2商家',
  merchant_id    bigint unsigned NULL COMMENT '商家承担时 t_merchant.id',
  shop_id        bigint unsigned NULL COMMENT '指定门店时 t_shop.id',
  scope_type     tinyint NOT NULL DEFAULT 1 COMMENT '1全平台 2指定商家 3指定门店 4指定品类 5指定商品',
  scope_json     json NULL COMMENT '范围快照，便于前端回显',
  start_at       datetime(3) NOT NULL,
  end_at         datetime(3) NOT NULL,
  stackable      tinyint NOT NULL DEFAULT 0 COMMENT '0不可与券叠加 1可叠加',
  priority       int NOT NULL DEFAULT 0 COMMENT '同单多个活动时取最大',
  status         tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1进行中 2已结束 3暂停',
  total_budget   bigint NULL COMMENT '活动总预算，分；NULL 表示不限',
  used_budget    bigint NOT NULL DEFAULT 0 COMMENT '已用预算，分',
  created_at     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted        tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_status_time (status, start_at, end_at),
  KEY idx_shop (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='营销活动（满减/折扣）';

-- ------------------------------------------------------------
-- 2. 活动档位规则（阶梯满减 / 折扣）
--    threshold_amount <= 折后商品小计时命中；同活动取最大门槛档
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_promotion_rule (
  id               bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id        bigint unsigned NOT NULL DEFAULT 1,
  promotion_id     bigint unsigned NOT NULL,
  threshold_amount bigint NOT NULL DEFAULT 0 COMMENT '门槛，分',
  discount_amount  bigint NULL COMMENT '满减金额，分',
  discount_rate    decimal(4,2) NULL COMMENT '折扣率，如 0.85',
  max_discount     bigint NULL COMMENT '折扣封顶，分',
  sort             int NOT NULL DEFAULT 0,
  created_at       datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_promotion_sort (promotion_id, sort),
  KEY idx_promotion_threshold (promotion_id, threshold_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动档位规则';

-- ------------------------------------------------------------
-- 3. 活动范围（商家/门店/品类/SPU/SKU）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_promotion_scope (
  id           bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id    bigint unsigned NOT NULL DEFAULT 1,
  promotion_id bigint unsigned NOT NULL,
  ref_type     tinyint NOT NULL COMMENT '1商家 2门店 3品类 4SPU 5SKU',
  ref_id       bigint unsigned NOT NULL,
  created_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_promo_ref (promotion_id, ref_type, ref_id),
  KEY idx_ref (ref_type, ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动适用范围';

-- ------------------------------------------------------------
-- 4. 秒杀场次（阶段A 使用，本阶段仅建表）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_seckill_activity (
  id            bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id     bigint unsigned NOT NULL DEFAULT 1,
  activity_name varchar(64) NOT NULL,
  session_start datetime(3) NOT NULL,
  session_end   datetime(3) NOT NULL,
  preheat_at    datetime(3) NULL COMMENT '预热开始时间',
  status        tinyint NOT NULL DEFAULT 0 COMMENT '0待开始 1进行中 2已结束',
  created_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted       tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_time (session_start, session_end),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='秒杀场次';

-- ------------------------------------------------------------
-- 5. 秒杀商品（库存独立，不占用 t_sku_stock）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_seckill_item (
  id             bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id      bigint unsigned NOT NULL DEFAULT 1,
  activity_id    bigint unsigned NOT NULL,
  shop_id        bigint unsigned NOT NULL,
  spu_id         bigint unsigned NOT NULL,
  sku_id         bigint unsigned NOT NULL,
  seckill_price  bigint NOT NULL COMMENT '秒杀价，分',
  seckill_stock  int NOT NULL DEFAULT 0 COMMENT '秒杀总库存',
  sold_qty       int NOT NULL DEFAULT 0 COMMENT '已售/已预占数量',
  limit_per_user int NOT NULL DEFAULT 1 COMMENT '每人限购',
  version        int unsigned NOT NULL DEFAULT 0,
  created_at     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_sku (activity_id, sku_id),
  KEY idx_activity (activity_id, sold_qty),
  KEY idx_sku (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='秒杀商品与独立库存';

-- ------------------------------------------------------------
-- 6. 秒杀预占单（uk_user_item 兜底每人每场限购）
--    status：0预占 1已支付 2已释放
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_seckill_order (
  id          bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id   bigint unsigned NOT NULL DEFAULT 1,
  activity_id bigint unsigned NOT NULL,
  item_id     bigint unsigned NOT NULL,
  user_id     bigint unsigned NOT NULL,
  order_id    bigint unsigned NULL,
  qty         int NOT NULL DEFAULT 1,
  status      tinyint NOT NULL DEFAULT 0 COMMENT '0预占 1已支付 2已释放',
  created_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_item (user_id, item_id),
  KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='秒杀预占单';

-- ------------------------------------------------------------
-- 7. 种子：平台级单档满减「满 30 元减 5 元」
--    start_at/end_at 覆盖联调期（2026-01-01 ~ 2030-12-31）。
-- ------------------------------------------------------------
INSERT INTO t_promotion
  (id, tenant_id, promotion_type, promotion_name, bearer, merchant_id, shop_id,
   scope_type, scope_json, start_at, end_at, stackable, priority, status,
   total_budget, used_budget, deleted)
VALUES
  (1, 1, 1, '平台满30减5', 1, NULL, NULL,
   1, NULL, '2026-01-01 00:00:00.000', '2030-12-31 23:59:59.000', 1, 100, 1,
   NULL, 0, 0)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO t_promotion_rule
  (id, tenant_id, promotion_id, threshold_amount, discount_amount,
   discount_rate, max_discount, sort)
VALUES
  (1, 1, 1, 3000, 500, NULL, NULL, 1)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 8. 后台菜单与权限：挂在「营销中心」(id = 6) 下
-- ------------------------------------------------------------
INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (113, 1, 6,   '满减活动', 2, '/marketing/promotion', 'marketing/promotion/index', 'marketing:promotion:list',   'marketing', 1, 0, 0),
  (114, 1, 113, '活动维护', 3, NULL,                   NULL,                        'marketing:promotion:update', NULL,        1, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 113 AND 114
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
