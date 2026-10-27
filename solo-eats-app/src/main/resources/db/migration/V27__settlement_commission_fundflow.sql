-- ============================================================
-- 数创星光外卖 T16 阶段B：三级抽佣规则引擎 + 资金流水
-- Flyway V27 / MySQL 8.0 / utf8mb4
--
-- 说明：
--   1. 任务卡原写 V21，已被 V21__seed_demo_shop.sql 占用；落地前实际最大为
--      V26__content.sql，故本脚本用 V27。
--   2. 三张新表全部带 tenant_id，由 TenantLineInnerInterceptor 自动读写，
--      因此不需要加入 MybatisPlusConfig.IGNORE_TABLES。
--   3. 不建 t_payout_apply：提现单复用既有 t_payout_order（V19 已补列）。
--   4. 不改 t_promotion：活动级费率走 t_commission_rule.rule_scope = 4
--      + ref_id = t_promotion.id，避免给营销主表加列。
--   5. MySQL 不支持 CREATE INDEX IF NOT EXISTS，索引统一用 information_schema
--      判定后动态执行；建表用 CREATE TABLE IF NOT EXISTS，脚本可重复执行。
--   6. 菜单 id 从 142 起：V19 占 108~112、V22 占 113~114、V23 占 115~116、
--      V24 占 117~119、V25 占 120~130、V26 占 133~141。131/132 虽然空缺，
--      但为避免与本地手工建过的数据冲突，统一从 142 起向后排。
--      全部挂在既有「资金中心」目录（id = 8）下，并授予平台超管 role_id = 1。
--   7. 枚举口径：
--      rule_scope 1全局 2品类 3商家 4活动；
--      flow_type  1佣金收入 2结算支出 3补贴支出 4优惠成本 5提现支出 6退款冲抵；
--      direction  1入 2出。
--   8. 不种入任何抽佣规则：无规则命中时应用层回落
--      app.settlement.default-commission-rate（默认 0.0600）且 rule_id 记 NULL，
--      与既有账单一套口径，保证历史可比。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. 抽佣规则：活动级 > 商家级 > 品类级 > 全局默认
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_commission_rule (
  id              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '规则ID',
  tenant_id       bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  rule_scope      tinyint NOT NULL COMMENT '1全局 2品类 3商家 4活动',
  ref_id          bigint unsigned NULL COMMENT '品类ID/商家ID/活动ID；rule_scope=1 时为 NULL',
  commission_rate decimal(6,4) NOT NULL COMMENT '佣金比例，如 0.0600',
  min_amount      bigint NULL COMMENT '订单金额下限，分；NULL 表示不限',
  max_amount      bigint NULL COMMENT '订单金额上限，分；NULL 表示不限',
  effective_from  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '生效开始时间',
  effective_to    datetime(3) NULL COMMENT '生效结束时间；NULL 表示长期有效',
  priority        int NOT NULL DEFAULT 0 COMMENT '同 scope 内排序，越大越优先',
  status          tinyint NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
  remark          varchar(255) NULL COMMENT '备注',
  created_at      datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted         tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_scope_ref (rule_scope, ref_id, status),
  KEY idx_effective (status, effective_from, effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽佣规则（全局/品类/商家/活动）';

-- ------------------------------------------------------------
-- 2. 抽佣明细：一单一条，uk_order 保证重复结算幂等
--    金额口径：base_amount = 商品金额 + 打包费 − 商家承担优惠（含打包费，
--    与既有账单一套口径）；commission_amount = HALF_UP(base_amount * rate)。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_commission_record (
  id                bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  tenant_id         bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  order_id          bigint unsigned NOT NULL COMMENT '订单ID t_order.id',
  merchant_id       bigint unsigned NULL COMMENT '商家ID',
  shop_id           bigint unsigned NULL COMMENT '门店ID',
  category_id       bigint unsigned NULL COMMENT '品类ID（订单首件商品所属品类）',
  base_amount       bigint NOT NULL DEFAULT 0 COMMENT '抽佣基数，分',
  rule_id           bigint unsigned NULL COMMENT '命中规则ID；内置默认费率时为 NULL',
  rule_scope        tinyint NULL COMMENT '命中规则的 scope；内置默认费率时为 NULL',
  commission_rate   decimal(6,4) NOT NULL COMMENT '实际使用的佣金比例',
  commission_amount bigint NOT NULL DEFAULT 0 COMMENT '抽佣金额，分',
  settlement_bill_id bigint unsigned NULL COMMENT '归集的结算单ID',
  created_at        datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_order (order_id),
  KEY idx_merchant_time (merchant_id, created_at),
  KEY idx_bill (settlement_bill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽佣计算明细';

-- ------------------------------------------------------------
-- 3. 平台资金流水：uk_biz 保证同一业务单据同一流水类型只记一次
--    平台无统一账户余额，balance_after 一律留 NULL。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_fund_flow (
  id            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  tenant_id     bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  flow_type     tinyint NOT NULL COMMENT '1佣金收入 2结算支出 3补贴支出 4优惠成本 5提现支出 6退款冲抵',
  biz_type      varchar(32) NOT NULL COMMENT '业务类型，如 commission_record/payout_order',
  biz_id        bigint unsigned NOT NULL COMMENT '业务单据ID',
  direction     tinyint NOT NULL COMMENT '1入 2出',
  amount        bigint NOT NULL DEFAULT 0 COMMENT '流水金额，分',
  balance_after bigint NULL COMMENT '余额快照；平台无统一账户，留 NULL',
  remark        varchar(255) NULL COMMENT '备注',
  created_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz (flow_type, biz_type, biz_id),
  KEY idx_type_time (flow_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台资金流水';

-- ------------------------------------------------------------
-- 4. 索引兜底：本地库可能已手工建过表，CREATE TABLE IF NOT EXISTS 不会补索引，
--    这里按 information_schema 判定后补建。
-- ------------------------------------------------------------
SET @has_idx = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_commission_rule'
    AND INDEX_NAME = 'idx_scope_ref'
);
SET @ddl = IF(@has_idx = 0,
  'ALTER TABLE t_commission_rule ADD INDEX idx_scope_ref (rule_scope, ref_id, status)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_commission_rule'
    AND INDEX_NAME = 'idx_effective'
);
SET @ddl = IF(@has_idx = 0,
  'ALTER TABLE t_commission_rule ADD INDEX idx_effective (status, effective_from, effective_to)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_commission_record'
    AND INDEX_NAME = 'idx_merchant_time'
);
SET @ddl = IF(@has_idx = 0,
  'ALTER TABLE t_commission_record ADD INDEX idx_merchant_time (merchant_id, created_at)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_commission_record'
    AND INDEX_NAME = 'idx_bill'
);
SET @ddl = IF(@has_idx = 0,
  'ALTER TABLE t_commission_record ADD INDEX idx_bill (settlement_bill_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_fund_flow'
    AND INDEX_NAME = 'idx_type_time'
);
SET @ddl = IF(@has_idx = 0,
  'ALTER TABLE t_fund_flow ADD INDEX idx_type_time (flow_type, created_at)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 5. 后台菜单与权限：挂在「资金中心」（parent_id = 8）下
-- ------------------------------------------------------------
INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (142, 1, 8,   '佣金规则', 2, '/finance/commission-rule',   'finance/commissionRule/index',   'settlement:commission:list', 'money', 4, 0, 0),
  (143, 1, 142, '规则维护', 3, NULL,                          NULL,                             'settlement:commission:save', NULL,    1, 1, 0),
  (144, 1, 8,   '抽佣明细', 2, '/finance/commission-record', 'finance/commissionRecord/index', 'settlement:commission:list', 'list',  5, 0, 0),
  (145, 1, 8,   '资金流水', 2, '/finance/fund-flow',         'finance/fundFlow/index',         'settlement:fund:list',       'money', 6, 0, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 平台超管（role_id = 1）授予佣金规则/抽佣明细/资金流水权限
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 142 AND 145
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
