-- ============================================================
-- 数创星光外卖 T02 阶段A：租户 / 套餐 / 部门 / 岗位 / 数据字典
-- Flyway V5 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. 本脚本可重复执行（CREATE TABLE IF NOT EXISTS + 种子 INSERT ... ON DUPLICATE KEY UPDATE id = id）。
--   2. t_sys_tenant / t_sys_package 为平台级表，不带 tenant_id；
--      t_sys_dept / t_sys_post / t_sys_dict_type / t_sys_dict_item 带 tenant_id，
--      由 TenantLineInnerInterceptor 自动读写，SQL 中不手写租户条件。
--   3. 既有 21 张业务表不补 tenant_id（README Q1），隔离继续靠 merchant_id / shop_id。
--   4. 金额单位一律为分（bigint）；时间为 Asia/Shanghai。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. 套餐（平台级，菜单范围 + 有效期 + 门店/账号上限）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sys_package (
  id           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '套餐ID',
  package_code varchar(64) NOT NULL COMMENT '套餐编码',
  package_name varchar(64) NOT NULL COMMENT '套餐名称',
  menu_ids     json NULL COMMENT '可访问菜单ID数组，如 [1,3,4,8]；NULL 表示不限制',
  max_shop     int NOT NULL DEFAULT 0 COMMENT '门店上限，0 表示不限制',
  max_user     int NOT NULL DEFAULT 0 COMMENT '账号上限，0 表示不限制',
  price        bigint NOT NULL DEFAULT 0 COMMENT '套餐价格，单位：分',
  valid_days   int NOT NULL DEFAULT 0 COMMENT '有效期天数，0 表示长期有效',
  status       tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  remark       varchar(255) NULL COMMENT '备注',
  created_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted      tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_package_code (package_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户套餐';

-- ------------------------------------------------------------
-- 2. 租户（平台 / 商家）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sys_tenant (
  id            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '租户ID',
  tenant_code   varchar(64) NOT NULL COMMENT '租户编码',
  tenant_name   varchar(128) NOT NULL COMMENT '租户名称',
  tenant_type   tinyint NOT NULL COMMENT '1平台 2商家',
  merchant_id   bigint unsigned NULL COMMENT '绑定商家（tenant_type=2 必填）',
  package_id    bigint unsigned NULL COMMENT '套餐ID',
  status        tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用 2过期（2 由 expire_at 派生，不回写）',
  expire_at     datetime(3) NULL COMMENT '到期时间，NULL 表示长期有效',
  contact_name  varchar(64) NULL COMMENT '联系人',
  contact_phone varchar(128) NULL COMMENT '联系电话（密文，禁止明文落库）',
  version       int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  created_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted       tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_code (tenant_code),
  KEY idx_type_status (tenant_type, status),
  KEY idx_package (package_id),
  KEY idx_merchant (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户';

-- ------------------------------------------------------------
-- 3. 部门（组织树，ancestors 形如 0,1,3）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sys_dept (
  id         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '部门ID',
  tenant_id  bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  parent_id  bigint unsigned NOT NULL DEFAULT 0 COMMENT '父部门，0为根',
  ancestors  varchar(512) NOT NULL DEFAULT '0' COMMENT '祖级路径，如 0,1,3',
  dept_name  varchar(64) NOT NULL COMMENT '部门名称',
  leader     varchar(64) NULL COMMENT '负责人',
  phone      varchar(128) NULL COMMENT '联系电话',
  sort       int NOT NULL DEFAULT 0 COMMENT '排序',
  status     tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted    tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_parent (tenant_id, parent_id),
  KEY idx_ancestors (ancestors)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门';

-- ------------------------------------------------------------
-- 4. 岗位
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sys_post (
  id         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
  tenant_id  bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  post_code  varchar(64) NOT NULL COMMENT '岗位编码',
  post_name  varchar(64) NOT NULL COMMENT '岗位名称',
  sort       int NOT NULL DEFAULT 0 COMMENT '排序',
  status     tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  remark     varchar(255) NULL COMMENT '备注',
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted    tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_post (tenant_id, post_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位';

-- ------------------------------------------------------------
-- 5. 字典类型
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sys_dict_type (
  id         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '字典类型ID',
  tenant_id  bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  dict_type  varchar(64) NOT NULL COMMENT '字典类型编码，如 order_status',
  dict_name  varchar(64) NOT NULL COMMENT '字典类型名称',
  status     tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  remark     varchar(255) NULL COMMENT '备注',
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted    tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_dict_type (tenant_id, dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典类型';

-- ------------------------------------------------------------
-- 6. 字典项
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sys_dict_item (
  id         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '字典项ID',
  tenant_id  bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  dict_type  varchar(64) NOT NULL COMMENT '字典类型编码',
  dict_label varchar(128) NOT NULL COMMENT '显示标签',
  dict_value varchar(128) NOT NULL COMMENT '数据值',
  dict_sort  int NOT NULL DEFAULT 0 COMMENT '排序',
  css_class  varchar(64) NULL COMMENT '样式类',
  is_default tinyint NOT NULL DEFAULT 0 COMMENT '1默认项；同一 dict_type 最多 1 条（应用层保证）',
  status     tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  remark     varchar(255) NULL COMMENT '备注',
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted    tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_type_sort (tenant_id, dict_type, dict_sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典项';

-- ------------------------------------------------------------
-- 7. 系统管理子菜单：租户 / 套餐 / 部门 / 岗位 / 字典
-- ------------------------------------------------------------
INSERT INTO t_sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (30, 1, 12, '租户管理', 2, '/system/tenant',  'system/tenant/index',  'system:tenant:list',  'tenant', 4, 0, 0),
  (31, 1, 12, '套餐管理', 2, '/system/package', 'system/package/index', 'system:package:list', 'package', 5, 0, 0),
  (32, 1, 12, '部门管理', 2, '/system/dept',    'system/dept/index',    'system:dept:list',    'dept',   6, 0, 0),
  (33, 1, 12, '岗位管理', 2, '/system/post',    'system/post/index',    'system:post:list',    'post',   7, 0, 0),
  (34, 1, 12, '字典管理', 2, '/system/dict',    'system/dict/index',    'system:dict:list',    'dict',   8, 0, 0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 8. 新菜单对应的按钮权限
-- ------------------------------------------------------------
INSERT INTO t_sys_menu (id, tenant_id, parent_id, menu_name, menu_type, perms, sort, visible, status)
VALUES
  (35, 1, 30, '租户新增', 3, 'system:tenant:create',  1, 1, 0),
  (36, 1, 30, '租户修改', 3, 'system:tenant:update',  2, 1, 0),
  (37, 1, 31, '套餐新增', 3, 'system:package:create', 1, 1, 0),
  (38, 1, 31, '套餐修改', 3, 'system:package:update', 2, 1, 0),
  (39, 1, 32, '部门新增', 3, 'system:dept:create',    1, 1, 0),
  (40, 1, 32, '部门修改', 3, 'system:dept:update',    2, 1, 0),
  (41, 1, 32, '部门删除', 3, 'system:dept:delete',    3, 1, 0),
  (42, 1, 33, '岗位新增', 3, 'system:post:create',    1, 1, 0),
  (43, 1, 33, '岗位修改', 3, 'system:post:update',    2, 1, 0),
  (44, 1, 33, '岗位删除', 3, 'system:post:delete',    3, 1, 0),
  (45, 1, 34, '字典新增', 3, 'system:dict:create',    1, 1, 0),
  (46, 1, 34, '字典修改', 3, 'system:dict:update',    2, 1, 0),
  (47, 1, 34, '字典删除', 3, 'system:dict:delete',    3, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 超管默认拥有新菜单与按钮
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 30 AND 47
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;

-- ------------------------------------------------------------
-- 9. 种子套餐
--    基础版只放开 工作台/商品/订单/资金；专业版放开除系统管理外的全部一级目录
-- ------------------------------------------------------------
INSERT INTO t_sys_package
  (id, package_code, package_name, menu_ids, max_shop, max_user, price, valid_days, status, remark)
VALUES
  (1, 'BASIC',    '基础版', JSON_ARRAY(1, 3, 4, 8), 1, 20, 0, 365, 0, '单门店基础经营套餐'),
  (2, 'STANDARD', '标准版', JSON_ARRAY(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), 10, 100, 299900, 365, 0, '多门店连锁套餐')
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 10. 种子租户：平台租户（固定 ID 1，全站默认租户）
-- ------------------------------------------------------------
INSERT INTO t_sys_tenant
  (id, tenant_code, tenant_name, tenant_type, merchant_id, package_id, status, expire_at, contact_name, contact_phone)
VALUES
  (1, 'PLATFORM', '数创星光外卖平台', 1, NULL, NULL, 0, NULL, '平台管理员', NULL)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 11. 种子部门（ancestors 必须与路径一致）
-- ------------------------------------------------------------
INSERT INTO t_sys_dept (id, tenant_id, parent_id, ancestors, dept_name, leader, sort, status)
VALUES
  (1, 1, 0, '0',   '运营中心', '平台管理员', 1, 0),
  (2, 1, 1, '0,1', '技术部',   NULL,         2, 0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 12. 种子岗位
-- ------------------------------------------------------------
INSERT INTO t_sys_post (id, tenant_id, post_code, post_name, sort, status, remark)
VALUES
  (1, 1, 'PLATFORM_OPS', '平台运营', 1, 0, '招商与运营'),
  (2, 1, 'FINANCE',      '财务',     2, 0, '结算与对账')
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 13. 种子字典类型
-- ------------------------------------------------------------
INSERT INTO t_sys_dict_type (id, tenant_id, dict_type, dict_name, status, remark)
VALUES
  (1, 1, 'order_status',  '订单状态', 0, '对齐规格书 9.9'),
  (2, 1, 'order_type',    '订单类型', 0, NULL),
  (3, 1, 'delivery_type', '配送方式', 0, NULL),
  (4, 1, 'yes_no',        '是否',     0, NULL)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 14. 种子字典项：订单状态 13 项
-- ------------------------------------------------------------
INSERT INTO t_sys_dict_item
  (id, tenant_id, dict_type, dict_label, dict_value, dict_sort, is_default, status)
VALUES
  (1,  1, 'order_status', '待支付',    '0',  1,  1, 0),
  (2,  1, 'order_status', '待接单',    '10', 2,  0, 0),
  (3,  1, 'order_status', '备餐中',    '20', 3,  0, 0),
  (4,  1, 'order_status', '待取货',    '30', 4,  0, 0),
  (5,  1, 'order_status', '待取餐',    '31', 5,  0, 0),
  (6,  1, 'order_status', '配送中',    '40', 6,  0, 0),
  (7,  1, 'order_status', '已送达',    '50', 7,  0, 0),
  (8,  1, 'order_status', '已取餐',    '51', 8,  0, 0),
  (9,  1, 'order_status', '已完成',    '60', 9,  0, 0),
  (10, 1, 'order_status', '已取消',    '70', 10, 0, 0),
  (11, 1, 'order_status', '已退款',    '80', 11, 0, 0),
  (12, 1, 'order_status', '部分退款',  '81', 12, 0, 0),
  (13, 1, 'order_status', '异常',      '90', 13, 0, 0)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO t_sys_dict_item
  (id, tenant_id, dict_type, dict_label, dict_value, dict_sort, is_default, status)
VALUES
  (21, 1, 'order_type',    '平台配送', '1', 1, 1, 0),
  (22, 1, 'order_type',    '到店自取', '2', 2, 0, 0),
  (31, 1, 'delivery_type', '平台配送', '1', 1, 1, 0),
  (32, 1, 'delivery_type', '商家自配', '2', 2, 0, 0),
  (33, 1, 'delivery_type', '到店自取', '3', 3, 0, 0),
  (41, 1, 'yes_no',        '是',       '1', 1, 0, 0),
  (42, 1, 'yes_no',        '否',       '0', 2, 1, 0)
ON DUPLICATE KEY UPDATE id = id;
