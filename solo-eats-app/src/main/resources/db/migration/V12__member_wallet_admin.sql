-- ============================================================
-- 数创星光外卖 T06 阶段C-1：会员钱包后台权限 + 地址唯一键缺陷修复
-- Flyway V12 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. 只删索引、不删列；DDL 通过 information_schema 条件执行，可重复运行。
--   2. 菜单 id 段：V4 1~29、V5 30~47、V6 48~55、V7 56~59、V8 60~62、
--      V9 63~71、V10 72~82，本脚本从 83 起。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 收货地址唯一键缺陷修复
--    原 uk_user_phone_addr(user_id, receiver_phone_mask, address) 不含 deleted：
--    地址逻辑删除后，同用户再次添加同手机号 + 同地址会撞唯一键（10001），无法重新新增。
--    索引改为普通查询索引，重复校验改由 MemberAddressServiceImpl.create 只比对 deleted = 0。
-- ------------------------------------------------------------
SET @has_uk := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 't_member_address'
    AND index_name = 'uk_user_phone_addr'
);
SET @ddl := IF(@has_uk > 0,
  'ALTER TABLE t_member_address DROP INDEX uk_user_phone_addr',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 't_member_address'
    AND index_name = 'idx_user_phone'
);
SET @ddl := IF(@has_idx = 0,
  'ALTER TABLE t_member_address ADD INDEX idx_user_phone (user_id, receiver_phone_mask)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. 会员模块后台菜单与权限（阶段C-1 钱包、阶段C-2 会员/标签共用）
--    挂在既有「用户中心」目录（id = 7）下；按钮 visible = 1 与 V4/V9 一致。
-- ------------------------------------------------------------
INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (83, 1, 7,  '会员管理', 2, '/member/user',   'member/user/index',   'member:user:list',    'user',  1, 0, 0),
  (84, 1, 83, '会员详情', 3, NULL, NULL, 'member:user:detail',  NULL, 1, 1, 0),
  (85, 1, 83, '会员编辑', 3, NULL, NULL, 'member:user:update',  NULL, 2, 1, 0),
  (86, 1, 7,  '钱包流水', 2, '/member/wallet', 'member/wallet/index', 'member:wallet:list',  'money', 2, 0, 0),
  (87, 1, 86, '手工调账', 3, NULL, NULL, 'member:wallet:adjust', NULL, 1, 1, 0),
  (88, 1, 7,  '标签分群', 2, '/member/tag',    'member/tag/index',    'member:tag:list',     'user',  3, 0, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 平台超管（role_id = 1）授予全部会员权限
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 83 AND 88
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
