-- ============================================================
-- 后台菜单：补「运营看板」「抽成结算」两个既有页面的入口
-- Flyway V31 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. 背景：V4~V30 已种 150+ 条菜单，但缺 /admin/overview 与
--      /admin/settlements 两条——而这两个页面在 solo-eats-web 里
--      早已存在（AdminOverviewView.vue / AdminSettlementsView.vue）。
--      菜单改由后端驱动后，它们会在侧边栏丢失去入口。
--   2. component 一律写「能精确解析到 src/views 下真实文件」的路径，
--      不含 .vue 后缀，与 router 的 import.meta.glob 规则一致。
--   3. 菜单 id 从 157 起（V30 后全局最大为 156）。
--   4. 「运营看板」是前台首页落点，挂为顶级菜单（parent_id = 0）；
--      「抽成结算」挂既有一级目录「资金中心」（id = 8）。
--   5. 全部 ON DUPLICATE KEY UPDATE id=id，可重复执行。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (157, 1, 0, '运营看板', 2, '/admin/overview',    'admin/AdminOverviewView',    'report:dashboard:view', 'dashboard', 1, 0, 0),
  (158, 1, 8, '抽成结算', 2, '/admin/settlements', 'admin/AdminSettlementsView', 'settlement:bill:audit', 'money',     7, 0, 0)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 157 AND 158
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
