-- ============================================================
-- 数创星光外卖 T16 阶段A：结算单审核/模拟打款 + 商家/骑手提现
-- Flyway V19 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. 库已到 V18，本脚本按实测版本使用 V19（任务卡原写 V20）。
--   2. t_payout_order 由 V1 建表，已有 fail_reason；本脚本只幂等追加 5 列，
--      不修改任何既有列（Expand-Contract）。t_settlement_bill 已有
--      status/reviewed_by/reviewed_at/paid_at，本脚本不动该表。
--   3. MySQL 不支持 ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS，
--      统一用 information_schema 判定后动态执行，脚本可重复执行。
--   4. 提现单状态：0待审核 10审核通过 20打款中 30已到账 40已驳回。
--      只有 0→10/40、10→20、20→30 合法，应用层条件更新兜底。
--   5. 菜单权限 id 从 108 起（V17 已用到 107），授平台超管 role_id = 1。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. t_payout_order 幂等补列
-- ------------------------------------------------------------
SET @has_audit_by = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_payout_order'
    AND COLUMN_NAME = 'audit_by'
);
SET @ddl = IF(@has_audit_by = 0,
  'ALTER TABLE t_payout_order ADD COLUMN audit_by bigint unsigned NULL COMMENT ''审核人 t_sys_user.id'' AFTER fail_reason',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_audit_at = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_payout_order'
    AND COLUMN_NAME = 'audit_at'
);
SET @ddl = IF(@has_audit_at = 0,
  'ALTER TABLE t_payout_order ADD COLUMN audit_at datetime(3) NULL COMMENT ''审核时间'' AFTER audit_by',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_audit_remark = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_payout_order'
    AND COLUMN_NAME = 'audit_remark'
);
SET @ddl = IF(@has_audit_remark = 0,
  'ALTER TABLE t_payout_order ADD COLUMN audit_remark varchar(255) NULL COMMENT ''审核意见，驳回原因'' AFTER audit_at',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_paid_at = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_payout_order'
    AND COLUMN_NAME = 'paid_at'
);
SET @ddl = IF(@has_paid_at = 0,
  'ALTER TABLE t_payout_order ADD COLUMN paid_at datetime(3) NULL COMMENT ''模拟打款时间'' AFTER audit_remark',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_account_snapshot = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_payout_order'
    AND COLUMN_NAME = 'account_snapshot_json'
);
SET @ddl = IF(@has_account_snapshot = 0,
  'ALTER TABLE t_payout_order ADD COLUMN account_snapshot_json json NULL COMMENT ''申请时收款账户快照'' AFTER paid_at',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. 索引：V1 的 idx_subject 已覆盖 (subject_type, subject_id, status)，
--    按列组合判定，存在同列索引时不再创建重复索引 idx_payee。
-- ------------------------------------------------------------
SET @has_payee_idx = (
  SELECT COUNT(*) FROM (
    SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS idx_cols
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_payout_order'
    GROUP BY INDEX_NAME
  ) idx
  WHERE idx.idx_cols = 'subject_type,subject_id,status'
);
SET @ddl = IF(@has_payee_idx = 0,
  'ALTER TABLE t_payout_order ADD INDEX idx_payee (subject_type, subject_id, status)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_bill_idx = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_payout_order'
    AND INDEX_NAME = 'idx_bill'
);
SET @ddl = IF(@has_bill_idx = 0,
  'ALTER TABLE t_payout_order ADD INDEX idx_bill (bill_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 3. 后台菜单与权限：挂在既有「资金中心」目录（id = 8）下
--    菜单 id 段：V14 用到 92~97、V15 用到 98~100、V16 用到 101~103、
--    V17 用到 104~107，本脚本从 108 起。
-- ------------------------------------------------------------
INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (108, 1, 8,   '结算单审核', 3, NULL, NULL, 'settlement:bill:audit',  NULL,   1, 1, 0),
  (109, 1, 8,   '结算单打款', 3, NULL, NULL, 'settlement:bill:pay',    NULL,   2, 1, 0),
  (110, 1, 8,   '提现管理',   2, '/finance/payout', 'finance/payout/index', 'settlement:payout:list', 'money', 3, 0, 0),
  (111, 1, 110, '提现审核',   3, NULL, NULL, 'settlement:payout:audit', NULL,   1, 1, 0),
  (112, 1, 110, '提现打款',   3, NULL, NULL, 'settlement:payout:pay',   NULL,   2, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 平台超管（role_id = 1）授予结算审核/打款与提现管理权限
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 108 AND 112
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
