-- ============================================================
-- 数创星光外卖 T06 阶段C-2：后台会员标签操作权限
-- Flyway V13 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. V12 只种了 member:tag:list（菜单 id = 88），本脚本补三个按钮权限。
--   2. 菜单 id 段：V12 用到 83~88，本脚本从 89 起。
--   3. 全部 INSERT ... ON DUPLICATE KEY UPDATE，可重复执行。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 标签分群按钮权限
--    挂在会员管理菜单（V12 id = 83）下，与 V4/V9 的按钮写法一致：
--    menu_type = 3、path/component 为空、visible = 1（按钮不显示在导航）。
-- ------------------------------------------------------------
INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (89, 1, 83, '新建标签', 3, NULL, NULL, 'member:tag:create', NULL, 3, 1, 0),
  (90, 1, 83, '编辑标签', 3, NULL, NULL, 'member:tag:update', NULL, 4, 1, 0),
  (91, 1, 83, '用户打标', 3, NULL, NULL, 'member:tag:assign', NULL, 5, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 2. 平台超管（role_id = 1）授予标签操作权限
-- ------------------------------------------------------------
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 89 AND 91
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
