-- ============================================================
-- T11 阶段B：报表菜单与权限
-- Flyway V30 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. 沿用 V23 的「数据报表」父菜单（id = 11），菜单 id 从 150 起
--      （V23 已用 115~116，V29 后全局最大为 149）。
--   2. 漏斗/商品/财务/用户四个页面菜单为任务卡指定；
--      154/155 补齐门店排行、骑手分析，否则对应接口的权限码无法授权。
--   3. 156 为「报表重算」按钮权限（menu_type=3），visible=1 不在侧栏展示。
--   4. 全部 ON DUPLICATE KEY UPDATE id=id，可重复执行。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (150, 1, 11, '流量漏斗', 2, '/report/funnel',  'report/funnel/index',  'report:funnel:view',  'chart', 1, 0, 0),
  (151, 1, 11, '商品报表', 2, '/report/product', 'report/product/index', 'report:product:view', 'list',  2, 0, 0),
  (152, 1, 11, '财务报表', 2, '/report/finance', 'report/finance/index', 'report:finance:view', 'money', 3, 0, 0),
  (153, 1, 11, '用户报表', 2, '/report/user',    'report/user/index',    'report:user:view',    'user',  4, 0, 0),
  (154, 1, 11, '门店排行', 2, '/report/shop',    'report/shop/index',    'report:shop:view',    'shop',  5, 0, 0),
  (155, 1, 11, '骑手分析', 2, '/report/rider',   'report/rider/index',   'report:rider:view',   'user',  6, 0, 0),
  (156, 1, 11, '报表重算', 3, NULL,              NULL,                   'report:rebuild',      NULL,    7, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 150 AND 156
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
