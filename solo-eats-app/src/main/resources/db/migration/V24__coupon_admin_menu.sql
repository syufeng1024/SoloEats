-- ------------------------------------------------------------
-- T12 阶段A-1：券后台管理菜单与权限
--
-- 本脚本只插 t_sys_menu / t_sys_role_menu 种子。
-- t_coupon_template、t_user_coupon、t_user_coupon_log 三张表由 V1__init_schema.sql
-- 建好且结构够用，这里不建表、不改表。
--
-- 菜单 ID：V22 占用 113~114，V23 占用 115~116，故本阶段从 117 起，
-- 挂在「营销中心」(id = 6) 下。任务卡写的 115~117 与 V23 冲突，
-- ON DUPLICATE KEY UPDATE id = id 会把冲突行静默丢弃，故后移。
-- ------------------------------------------------------------
INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (117, 1, 6,   '基础券',     2, '/marketing/coupon', 'marketing/coupon/index', 'marketing:coupon:list',  'coupon', 2, 0, 0),
  (118, 1, 117, '券模板维护', 3, NULL,                NULL,                     'marketing:coupon:save',  NULL,     1, 1, 0),
  (119, 1, 117, '定向发券',   3, NULL,                NULL,                     'marketing:coupon:grant', NULL,     2, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 117 AND 119
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
