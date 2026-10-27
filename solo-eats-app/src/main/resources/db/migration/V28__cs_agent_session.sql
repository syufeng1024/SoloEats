-- ============================================================
-- 数创星光外卖 T09 阶段B：客服工作台坐席端
-- Flyway V28 / MySQL 8.0 / utf8mb4
--
-- 说明：
--   1. 版本号来源：库内实际最大迁移为 V27__settlement_commission_fundflow.sql
--      （任务卡写 V26，已被 T10 内容装修占用），本脚本按实际最大 +1 取 V28。
--   2. t_im_session 由 V17 建表、V18 追加 user_hidden/peer_hidden。本脚本只
--      ADD COLUMN agent_id 与一个复合索引，不 DROP/MODIFY 任何既有列（Expand-Contract）。
--      旧代码不读 agent_id；新代码把 agent_id IS NULL 视为「未接待」，新旧可共存。
--   3. t_quick_reply 已由 V25 建表，本脚本不重建，不补列。
--   4. MySQL 不支持 ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS，统一用
--      information_schema 判定后动态执行，脚本可重复执行。
--   5. 菜单 id 146~147：t_sys_menu 现最大 145（V27 占用 142~145），按实际最大 +1 取号。
--      131/132 虽为空号但跳过不用，避免与后续任务卡抢号。菜单挂在「客服与工单」
--      （parent_id = 10）下，role_id = 1（平台超管）授权。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. t_im_session 追加坐席字段
--    agent_id 为空 = 尚未被任何坐席接待（待接待队列）。
-- ------------------------------------------------------------
SET @has_agent_id = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_im_session'
    AND COLUMN_NAME = 'agent_id'
);
SET @ddl = IF(@has_agent_id = 0,
  'ALTER TABLE t_im_session ADD COLUMN agent_id bigint unsigned NULL COMMENT ''接待坐席 t_sys_user.id'' AFTER peer_id',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. 索引 idx_agent_status(agent_id, status)
--    支撑两个查询：坐席工作台按状态筛本人会话、待接待队列（agent_id IS NULL AND status = 0）。
-- ------------------------------------------------------------
SET @has_agent_status_idx = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_im_session'
    AND INDEX_NAME = 'idx_agent_status'
);
SET @ddl = IF(@has_agent_status_idx = 0,
  'ALTER TABLE t_im_session ADD INDEX idx_agent_status (agent_id, status)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 3. 后台菜单与权限：客服工作台（146）+ 会话接待按钮（147）
--    perms 与 AdminCsController 的 @RequirePermission 一一对应。
-- ------------------------------------------------------------
INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (146, 1, 10,  '客服工作台', 2, '/service/cs', 'service/cs/index', 'service:cs:list',   'service', 11, 0, 0),
  (147, 1, 146, '会话接待',   3, NULL,          NULL,               'service:cs:handle', NULL,       1, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 平台超管（role_id = 1）授予客服工作台与会话接待权限
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 146 AND 147
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
