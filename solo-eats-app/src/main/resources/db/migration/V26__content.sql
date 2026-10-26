-- ============================================================
-- 数创星光外卖 T10：首页装修 / 广告位 / 搜索运营 / App 版本
-- Flyway V26 / MySQL 8.0 / utf8mb4
--
-- 说明：
--   1. 任务卡原写 V16，已被 V16__delivery_freight_income.sql 占用；
--      落地前实际最大为 V25，故本脚本用 V26。
--   2. 公告复用 T02 的 t_sys_notice，本脚本不建公告表。
--   3. 五张表均带 tenant_id，由 TenantLineInnerInterceptor 自动读写；
--      业务 SQL 不手写租户条件，因此不需要加入 MybatisPlusConfig.IGNORE_TABLES。
--   4. 生效窗口统一为 status = 1 且 now BETWEEN effective_from AND effective_to，
--      effective_to IS NULL 视为长期有效；过滤在 SQL 中完成。
--   5. 菜单 id 133~141，t_sys_menu 现最大 130；全部挂在「内容运营」(id = 9) 下。
--   6. 种子一律 ON DUPLICATE KEY UPDATE id = id，可重复执行。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. 首页页面：page_code=USER_HOME / USER_MINE
--    version 每次发布 +1，作为用户端聚合缓存键的一部分。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_home_page (
  id         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '页面ID',
  tenant_id  bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  page_code  varchar(32) NOT NULL COMMENT '页面编码：USER_HOME / USER_MINE',
  page_name  varchar(64) NOT NULL COMMENT '页面名称',
  status     tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布 2已下线',
  publish_at datetime(3) NULL COMMENT '最近发布时间',
  version    int unsigned NOT NULL DEFAULT 0 COMMENT '发布版本号，每次发布+1',
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted    tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_page (tenant_id, page_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='首页页面';

-- ------------------------------------------------------------
-- 2. 首页楼层：floor_type 1轮播 2金刚区 3秒杀 4神券 5推荐店铺 6猜你喜欢 7公告条
--    config_json 结构由服务层按 floor_type 校验引用；audience_json 供 P1 指定用户使用。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_home_floor (
  id             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '楼层ID',
  tenant_id      bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  page_id        bigint unsigned NOT NULL COMMENT '页面ID',
  floor_type     tinyint NOT NULL COMMENT '1轮播 2金刚区 3秒杀 4神券 5推荐店铺 6猜你喜欢 7公告条',
  title          varchar(64) NOT NULL COMMENT '楼层标题（纯文本）',
  config_json    json NULL COMMENT '楼层配置，含 categoryId/shopId/spuId 等引用',
  sort           int NOT NULL DEFAULT 0 COMMENT '排序，升序',
  effective_from datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '生效开始时间',
  effective_to   datetime(3) NULL COMMENT '生效结束时间；NULL=长期',
  audience       tinyint NOT NULL DEFAULT 0 COMMENT '0全部 1新客 2会员 3指定用户',
  audience_json  json NULL COMMENT '投放人群配置；audience=3 时使用，P0 不生效',
  status         tinyint NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
  created_at     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted        tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_page_sort (page_id, sort),
  KEY idx_effective (status, effective_from, effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='首页楼层';

-- ------------------------------------------------------------
-- 3. 广告位：slot_code=HOME_TOP / HOME_POPUP / SPLASH / SHOP_TOP
--    view_count 由 Redis 累加后每 5 分钟刷回；click_count 本期只保留字段与接口位。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_banner (
  id             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '广告ID',
  tenant_id      bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  slot_code      varchar(32) NOT NULL COMMENT '广告位：HOME_TOP/HOME_POPUP/SPLASH/SHOP_TOP',
  title          varchar(128) NOT NULL COMMENT '标题（纯文本）',
  image_url      varchar(512) NOT NULL COMMENT '图片地址（t_file 代理或 https）',
  jump_type      tinyint NOT NULL DEFAULT 5 COMMENT '1商品 2店铺 3活动 4页面 5无跳转',
  jump_value     varchar(255) NULL COMMENT '跳转目标：商品ID/店铺ID/活动ID/页面路径',
  sort           int NOT NULL DEFAULT 0 COMMENT '排序，升序',
  effective_from datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '生效开始时间',
  effective_to   datetime(3) NULL COMMENT '生效结束时间；NULL=长期',
  click_count    bigint NOT NULL DEFAULT 0 COMMENT '点击次数',
  view_count     bigint NOT NULL DEFAULT 0 COMMENT '曝光次数',
  status         tinyint NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
  created_at     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted        tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_slot_sort (slot_code, sort),
  KEY idx_effective (status, effective_from, effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='广告位';

-- ------------------------------------------------------------
-- 4. 搜索词：1默认词 2热搜词 3联想词 4屏蔽词
--    屏蔽词只在服务端过滤候选，不返回前端。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_search_keyword (
  id           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '搜索词ID',
  tenant_id    bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  keyword      varchar(64) NOT NULL COMMENT '关键词',
  keyword_type tinyint NOT NULL COMMENT '1默认词 2热搜词 3联想词 4屏蔽词',
  weight       int NOT NULL DEFAULT 0 COMMENT '权重，越大越靠前',
  status       tinyint NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
  created_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted      tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_kw (tenant_id, keyword, keyword_type),
  KEY idx_type_weight (keyword_type, weight)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='搜索关键词';

-- ------------------------------------------------------------
-- 5. App 版本：app_type 1用户端 2骑手端 3商家端
--    gray_percent 0=不发，100=全量，其余按 userId/deviceId 取模。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_app_version (
  id           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '版本ID',
  tenant_id    bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  app_type     tinyint NOT NULL COMMENT '1用户端 2骑手端 3商家端',
  version_name varchar(32) NOT NULL COMMENT '版本名称，如 1.2.0',
  version_code int NOT NULL COMMENT '版本号，单调递增整数',
  update_desc  varchar(1000) NULL COMMENT '更新说明（纯文本）',
  download_url varchar(512) NOT NULL COMMENT '下载地址',
  force_update tinyint NOT NULL DEFAULT 0 COMMENT '1强制更新',
  gray_percent tinyint NOT NULL DEFAULT 0 COMMENT '灰度比例 0~100',
  status       tinyint NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
  publish_at   datetime(3) NULL COMMENT '发布时间；NULL 视为立即生效',
  created_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted      tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_type_code (tenant_id, app_type, version_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='App 版本';

-- ------------------------------------------------------------
-- 6. 页面种子：两个固定页面编码，后台首次进入即可编辑
-- ------------------------------------------------------------
INSERT INTO t_home_page (id, tenant_id, page_code, page_name, status)
VALUES
  (1, 1, 'USER_HOME', '用户首页', 0),
  (2, 1, 'USER_MINE', '我的页面', 0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 7. 后台菜单与权限：挂在「内容运营」(id = 9) 下
--    V4 目录 1~12；V8 到 62；V9 到 71；V17 到 107；V24 到 119；V25 到 130。
--    本脚本 133~141（T09 阶段B 预留 131~132）。
-- ------------------------------------------------------------
INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (133, 1, 9,   '首页装修',   2, '/content/home',    'content/home/index',    'content:home:list',    'content', 1, 0, 0),
  (134, 1, 133, '楼层编辑',   3, NULL,               NULL,                    'content:home:update',  NULL,      1, 1, 0),
  (135, 1, 133, '首页发布',   3, NULL,               NULL,                    'content:home:publish', NULL,      2, 1, 0),
  (136, 1, 9,   '广告位管理', 2, '/content/banner',  'content/banner/index',  'content:banner:list',  'picture', 2, 0, 0),
  (137, 1, 136, '广告位编辑', 3, NULL,               NULL,                    'content:banner:save',  NULL,      1, 1, 0),
  (138, 1, 9,   '搜索运营',   2, '/content/search',  'content/search/index',  'content:search:list',  'search',  3, 0, 0),
  (139, 1, 138, '搜索词编辑', 3, NULL,               NULL,                    'content:search:save',  NULL,      1, 1, 0),
  (140, 1, 9,   'App 版本',   2, '/content/version', 'content/version/index', 'content:version:list', 'phone',   4, 0, 0),
  (141, 1, 140, '版本编辑',   3, NULL,               NULL,                    'content:version:save', NULL,      1, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 平台超管（role_id = 1）授予内容运营全部菜单与按钮权限
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 133 AND 141
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
