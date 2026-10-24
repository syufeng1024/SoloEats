-- ============================================================
-- 数创星光外卖 T08 阶段A：站内信 / 消息模板 / WebSocket 下发日志 / 通知开关
-- Flyway V17 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. 库已到 V16，本脚本从 V17 起（任务卡原写 V14，已按实际库版本顺延）。
--   2. 六张表一次建全；阶段A 只用前四张（template / site_message / push_log / notify_setting），
--      t_im_session、t_im_message 由阶段B（IM 会话）使用，本阶段不写读写代码。
--   3. 六张表均带 tenant_id，由 TenantLineInnerInterceptor 自动读写；
--      业务 SQL 不手写 tenant_id 条件，因此不需要加入 MybatisPlusConfig.IGNORE_TABLES。
--   4. 菜单权限 id 从 104 起（V16 已用到 103），授平台超管 role_id = 1。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. 消息模板：{orderNo}、{amount} 等占位符由应用层字符串替换，不引入模板引擎
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_message_template (
  id           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '模板ID',
  tenant_id    bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  template_code varchar(64) NOT NULL COMMENT '模板编码，全局唯一（租户内）',
  template_name varchar(100) NOT NULL COMMENT '模板名称',
  template_type tinyint NOT NULL COMMENT '1订单 2营销 3系统 4客服',
  title_tpl    varchar(200) NOT NULL COMMENT '标题模板，支持 {key} 占位',
  content_tpl  varchar(1000) NOT NULL COMMENT '内容模板，支持 {key} 占位',
  channels     varchar(64) NOT NULL DEFAULT 'SITE,WS' COMMENT '下发通道，逗号分隔 SITE/WS/PUSH',
  status       tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted      tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_code (tenant_id, template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息模板';

-- ------------------------------------------------------------
-- 2. 站内信：WS 不作为唯一通道，每条通知同时落此表兜底
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_site_message (
  id            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '站内信ID',
  tenant_id     bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  receiver_type tinyint NOT NULL COMMENT '1用户 2商家 3骑手 4平台',
  receiver_id   bigint unsigned NOT NULL COMMENT '接收者ID',
  template_code varchar(64) NOT NULL COMMENT '模板编码',
  msg_type      tinyint NOT NULL COMMENT '1订单 2营销 3系统',
  title         varchar(200) NOT NULL COMMENT '渲染后标题',
  content       varchar(1000) NOT NULL COMMENT '渲染后内容（纯文本）',
  biz_type      varchar(32) NULL COMMENT '业务类型，如 ORDER',
  biz_id        bigint unsigned NULL COMMENT '业务单ID，如订单ID',
  read_flag     tinyint NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  read_at       datetime(3) NULL COMMENT '已读时间',
  created_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_receiver (receiver_type, receiver_id, read_flag, created_at),
  KEY idx_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内信';

-- ------------------------------------------------------------
-- 3. 下发日志：status 0待发/跳过 1成功 2失败
--    enabled=0 的通知开关、WS 离线都记此处，不阻断站内信
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_push_log (
  id            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  tenant_id     bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  message_id    bigint unsigned NULL COMMENT '关联站内信ID；纯 WS 通知为空',
  channel       varchar(16) NOT NULL COMMENT 'WS/SITE/PUSH',
  receiver_type tinyint NOT NULL COMMENT '1用户 2商家 3骑手 4平台',
  receiver_id   bigint unsigned NOT NULL COMMENT '接收者ID',
  status        tinyint NOT NULL DEFAULT 0 COMMENT '0待发 1成功 2失败',
  error_msg     varchar(255) NULL COMMENT '失败原因，如 offline',
  sent_at       datetime(3) NULL COMMENT '下发成功时间',
  created_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_status_time (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息下发日志';

-- ------------------------------------------------------------
-- 4. IM 会话（阶段B 使用，本阶段仅建表）
--    uk_order_peer 的 order_id 允许 NULL：MySQL 唯一索引允许多行 NULL，
--    即无订单关联的会话不受该约束。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_im_session (
  id              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  tenant_id       bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  session_no      varchar(32) NOT NULL COMMENT '会话编号',
  order_id        bigint unsigned NULL COMMENT '关联订单ID',
  user_id         bigint unsigned NOT NULL COMMENT '用户ID',
  peer_type       tinyint NOT NULL COMMENT '2商家 3骑手 4客服',
  peer_id         bigint unsigned NOT NULL COMMENT '对方ID',
  last_message    varchar(200) NULL COMMENT '最近一条消息摘要',
  last_message_at datetime(3) NULL COMMENT '最近消息时间',
  user_unread     int NOT NULL DEFAULT 0 COMMENT '用户未读数',
  peer_unread     int NOT NULL DEFAULT 0 COMMENT '对方未读数',
  status          tinyint NOT NULL DEFAULT 0 COMMENT '0进行中 1已结束 2只读',
  created_at      datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_peer (order_id, user_id, peer_type, peer_id),
  KEY idx_user (user_id, last_message_at),
  KEY idx_peer (peer_type, peer_id, last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM会话';

-- ------------------------------------------------------------
-- 5. IM 消息（阶段B 使用，本阶段仅建表）
--    content 纯文本；订单卡片/位置等结构化内容放 extra_json，禁止渲染 HTML
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_im_message (
  id          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  tenant_id   bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  session_id  bigint unsigned NOT NULL COMMENT '会话ID',
  sender_type tinyint NOT NULL COMMENT '1用户 2商家 3骑手 4客服 5系统',
  sender_id   bigint unsigned NOT NULL COMMENT '发送者ID',
  msg_type    tinyint NOT NULL COMMENT '1文字 2图片 3订单卡片 4位置 5常用语',
  content     varchar(2000) NOT NULL DEFAULT '' COMMENT '文字内容',
  extra_json  json NULL COMMENT '结构化扩展（fileId / 订单卡片 / 坐标）',
  read_flag   tinyint NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  created_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_session_time (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM消息';

-- ------------------------------------------------------------
-- 6. 通知开关：无记录视为开启，enabled=0 时只写下发日志不推 WS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_notify_setting (
  id            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '开关ID',
  tenant_id     bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  receiver_type tinyint NOT NULL COMMENT '1用户 2商家 3骑手',
  receiver_id   bigint unsigned NOT NULL COMMENT '接收者ID',
  notify_type   tinyint NOT NULL COMMENT '1订单 2营销 3系统 4会话',
  enabled       tinyint NOT NULL DEFAULT 1 COMMENT '0关闭 1开启',
  created_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_receiver_type (receiver_type, receiver_id, notify_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知开关';

-- ------------------------------------------------------------
-- 7. 种子模板：订单节点三类，文案含 {orderNo}
-- ------------------------------------------------------------
INSERT INTO t_message_template
  (id, tenant_id, template_code, template_name, template_type, title_tpl, content_tpl, channels, status)
VALUES
  (1, 1, 'ORDER_PAID',      '订单已支付',   1, '订单 {orderNo} 已支付',
   '您的订单 {orderNo} 已支付成功，商家正在确认接单。', 'SITE,WS', 0),
  (2, 1, 'ORDER_ACCEPTED',  '商家已接单',   1, '订单 {orderNo} 商家已接单',
   '商家已接单，订单 {orderNo} 正在备餐中。', 'SITE,WS', 0),
  (3, 1, 'ORDER_DELIVERED', '订单已送达',   1, '订单 {orderNo} 已送达',
   '您的订单 {orderNo} 已送达，请确认收货。', 'SITE,WS', 0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 8. 后台菜单与权限：挂在既有「内容运营」目录（id = 9）下
--    菜单 id 段：V14 用到 92~97、V15 用到 98~100、V16 用到 101~103，本脚本从 104 起。
-- ------------------------------------------------------------
INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (104, 1, 9,   '消息模板', 2, '/content/message-template', 'content/message-template/index', 'message:template:list',   'email', 1, 0, 0),
  (105, 1, 104, '模板新增', 3, NULL, NULL, 'message:template:create', NULL,    1, 1, 0),
  (106, 1, 104, '模板修改', 3, NULL, NULL, 'message:template:update', NULL,    2, 1, 0),
  (107, 1, 104, '消息推送', 3, NULL, NULL, 'message:send',            NULL,    3, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 平台超管（role_id = 1）授予消息模板与定向发送权限
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 104 AND 107
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
