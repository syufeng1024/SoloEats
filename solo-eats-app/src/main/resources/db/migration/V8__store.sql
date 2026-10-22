-- ============================================================
-- 数创星光外卖 T04：门店、营业时段、配送围栏、门店账号、门店装修
-- Flyway V8 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. 全部表带 tenant_id，由 TenantLineInnerInterceptor 自动读写，
--      SQL 与业务代码都不手写 tenant_id 条件。
--   2. 金额字段 min_order_amount / delivery_fee / packaging_fee / pickup_discount
--      一律 bigint（分）；经纬度 decimal(10,6)，禁止 double。
--   3. pickup_code_valid_minutes 是取餐码有效期；
--      pickup_timeout_minutes 是自取订单超时未取时长，两者语义不同。
--   4. 不修改 t_order，也不复用/删除既有 demo 数据（shop_id=1 的 SKU 仍然可用）。
--   5. 可重复执行：CREATE TABLE IF NOT EXISTS + 种子 INSERT ... ON DUPLICATE KEY UPDATE id = id。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. 门店主表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_shop (
  id                       bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '门店ID',
  tenant_id                bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  merchant_id              bigint unsigned NOT NULL COMMENT '商家ID',
  shop_no                  varchar(32) NOT NULL COMMENT '门店编号 S{商家4位}{流水4位}',
  shop_name                varchar(128) NOT NULL COMMENT '门店名称',
  brand_id                 bigint unsigned NULL COMMENT '品牌ID（预留，本期不建 t_brand）',
  category_id              bigint unsigned NULL COMMENT '品类ID',
  province                 varchar(64) NULL COMMENT '省',
  city                     varchar(64) NULL COMMENT '市',
  district                 varchar(64) NULL COMMENT '区/县',
  address                  varchar(255) NOT NULL COMMENT '详细地址',
  lng                      decimal(10,6) NOT NULL COMMENT '经度',
  lat                      decimal(10,6) NOT NULL COMMENT '纬度',
  contact_name             varchar(64) NULL COMMENT '联系人',
  contact_phone            varchar(32) NULL COMMENT '联系电话',
  logo_url                 varchar(512) NULL COMMENT '门店 Logo',
  cover_url                varchar(512) NULL COMMENT '头图',
  image_urls               json NULL COMMENT '门店图片 JSON 数组',
  announcement             varchar(500) NULL COMMENT '门店公告（已过滤 HTML）',
  status                   tinyint NOT NULL DEFAULT 1 COMMENT '0休息 1营业 2打烊 3停用',
  delivery_types           tinyint NOT NULL DEFAULT 1 COMMENT '位掩码：1平台配送 2到店自取',
  min_order_amount         bigint NOT NULL DEFAULT 0 COMMENT '起送价（分），按商品小计判断',
  delivery_fee             bigint NOT NULL DEFAULT 0 COMMENT '配送费（分）',
  packaging_fee            bigint NOT NULL DEFAULT 0 COMMENT '打包费（分）',
  prep_minutes             int NOT NULL DEFAULT 15 COMMENT '预计出餐时长（分钟）',
  delivery_radius_m        int NOT NULL DEFAULT 3000 COMMENT '默认配送半径（米），无围栏时兜底',
  rating                   decimal(3,2) NOT NULL DEFAULT 5.00 COMMENT '综合评分',
  rating_taste             decimal(3,2) NOT NULL DEFAULT 5.00 COMMENT '口味评分',
  rating_package           decimal(3,2) NOT NULL DEFAULT 5.00 COMMENT '包装评分',
  rating_delivery          decimal(3,2) NOT NULL DEFAULT 5.00 COMMENT '配送评分',
  month_sales              int NOT NULL DEFAULT 0 COMMENT '月售',
  sort_weight              int NOT NULL DEFAULT 0 COMMENT '综合排序权重',
  pickup_enabled           tinyint NOT NULL DEFAULT 0 COMMENT '1开启到店自取',
  pickup_prepare_minutes   int NOT NULL DEFAULT 15 COMMENT '自取备餐时长（分钟）',
  pickup_timeout_minutes   int NOT NULL DEFAULT 120 COMMENT '自取订单超时未取时长（分钟）',
  pickup_code_prefix       varchar(8) NULL COMMENT '取餐码前缀',
  pickup_code_length       tinyint NOT NULL DEFAULT 6 COMMENT '取餐码位数',
  pickup_code_valid_minutes int NOT NULL DEFAULT 30 COMMENT '取餐码有效期（分钟）',
  pickup_discount          bigint NOT NULL DEFAULT 0 COMMENT '自取立减（分）',
  settle_account_id        bigint unsigned NULL COMMENT '结算账户ID',
  version                  int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  created_at               datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at               datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted                  tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_shop_no (shop_no),
  KEY idx_merchant (merchant_id),
  KEY idx_city_status (city, status),
  KEY idx_geo (lat, lng),
  KEY idx_sort (status, sort_weight, month_sales)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店';

-- ------------------------------------------------------------
-- 2. 营业时段（支持多时段与跨天）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_shop_business_hour (
  id           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '时段ID',
  tenant_id    bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  shop_id      bigint unsigned NOT NULL COMMENT '门店ID',
  day_of_week  tinyint NOT NULL COMMENT '1周一~7周日',
  start_time   time NOT NULL COMMENT '开始时间',
  end_time     time NOT NULL COMMENT '结束时间',
  cross_day    tinyint NOT NULL DEFAULT 0 COMMENT '1跨天：start 当天至次日 end',
  created_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_shop_day (shop_id, day_of_week)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店营业时段';

-- ------------------------------------------------------------
-- 3. 配送围栏（圆形/多边形）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_shop_delivery_area (
  id           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '围栏ID',
  tenant_id    bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  shop_id      bigint unsigned NOT NULL COMMENT '门店ID',
  area_type    tinyint NOT NULL COMMENT '1圆形 2多边形',
  center_lng   decimal(10,6) NULL COMMENT '圆心经度（圆形）',
  center_lat   decimal(10,6) NULL COMMENT '圆心纬度（圆形）',
  radius_m     int NULL COMMENT '半径（米，圆形）',
  polygon_json json NULL COMMENT '多边形顶点 [[lng,lat],...]，3~50 点',
  status       tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted      tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_shop (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店配送围栏';

-- ------------------------------------------------------------
-- 4. 门店账号关联（解绑不删 t_sys_user）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_shop_account (
  id           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  tenant_id    bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  shop_id      bigint unsigned NOT NULL COMMENT '门店ID',
  merchant_id  bigint unsigned NOT NULL COMMENT '商家ID',
  user_id      bigint unsigned NOT NULL COMMENT 't_sys_user.id',
  account_role tinyint NOT NULL DEFAULT 2 COMMENT '1店长 2店员',
  is_default   tinyint NOT NULL DEFAULT 0 COMMENT '1登录默认门店',
  status       tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted      tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_shop_user (shop_id, user_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店账号';

-- ------------------------------------------------------------
-- 5. 门店装修配置
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_shop_decoration (
  id          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '装修ID',
  tenant_id   bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  shop_id     bigint unsigned NOT NULL COMMENT '门店ID',
  config_json json NULL COMMENT '招牌/橱窗/推荐分类/活动位',
  version     int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  status      tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted     tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_shop (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店装修配置';

-- ------------------------------------------------------------
-- 6. 门店管理菜单与权限码
--    V4 占用 1~29，V5 占用 30~47，V6 占用 48~55，V7 占用 56~59，本卡从 60 起
--    缺种子会导致 @RequirePermission 对超管也返回 10004
-- ------------------------------------------------------------
INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (60, 1, 2,  '门店管理', 2, '/store/list', 'store/list/index', 'store:shop:list',   'shop', 5, 0, 0),
  (61, 1, 60, '门店新增', 3, NULL, NULL, 'store:shop:create', NULL, 1, 1, 0),
  (62, 1, 60, '门店修改', 3, NULL, NULL, 'store:shop:update', NULL, 2, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 超管：全部门店权限
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id IN (60, 61, 62)
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;

-- 招商运营（role_id=2）：查看与新增/修改
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 2, id FROM t_sys_menu WHERE tenant_id = 1 AND id IN (60, 61, 62)
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;

-- 调度（role_id=5）：只读门店，便于排单
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 5, id FROM t_sys_menu WHERE tenant_id = 1 AND id IN (60)
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
