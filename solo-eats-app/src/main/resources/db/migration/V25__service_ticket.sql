-- ============================================================
-- 数创星光外卖 T09 阶段A：工单主干 / 投诉 / FAQ / 意见反馈 / 快捷话术
-- Flyway V25 / MySQL 8.0 / utf8mb4
--
-- 说明：
--   1. 任务卡原写 V15，已被 V15__delivery_track_anomaly.sql 占用；库实际到 V24，故用 V25。
--   2. 六张表一次建齐。t_quick_reply 阶段A 只建表，阶段B 的 QuickReplyService 使用。
--   3. 六张表均带 tenant_id，由 TenantLineInnerInterceptor 自动读写；业务 SQL 不手写租户条件，
--      因此不需要加入 MybatisPlusConfig.IGNORE_TABLES。
--   4. 菜单 id 120~130，t_sys_menu 现最大 119；全部挂在「客服与工单」(id = 10) 下。
--   5. 种子一律 ON DUPLICATE KEY UPDATE id = id，可重复执行。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. 服务工单：状态 0待受理 1处理中 2待回访 3已办结 4已关闭
--    状态只通过条件更新迁移，version 供后续乐观锁扩展，本阶段不读不写。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_service_ticket (
  id                  bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '工单ID',
  tenant_id           bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  ticket_no           varchar(32) NOT NULL COMMENT '工单编号',
  ticket_type         tinyint NOT NULL COMMENT '1订单问题 2退款 3配送 4投诉 5建议',
  source              tinyint NOT NULL DEFAULT 1 COMMENT '1用户 2商家 3骑手 4平台',
  submitter_type      tinyint NOT NULL DEFAULT 1 COMMENT '1用户 2商家 3骑手 4平台',
  submitter_id        bigint unsigned NOT NULL COMMENT '提交者业务ID',
  order_id            bigint unsigned NULL COMMENT '关联订单ID；不反查订单表',
  subject             varchar(200) NOT NULL COMMENT '工单标题（纯文本）',
  content             varchar(2000) NOT NULL COMMENT '工单内容（纯文本，禁止渲染 HTML）',
  images_json         json NULL COMMENT '凭证图片 URL 数组',
  priority            tinyint NOT NULL DEFAULT 0 COMMENT '0普通 1紧急 2特急',
  status              tinyint NOT NULL DEFAULT 0 COMMENT '0待受理 1处理中 2待回访 3已办结 4已关闭',
  assignee_id         bigint unsigned NULL COMMENT '受理客服（t_sys_user.id）',
  accept_at           datetime(3) NULL COMMENT '受理时间',
  deadline_at         datetime(3) NULL COMMENT '处理时限：普通24h 紧急4h 特急1h',
  finish_at           datetime(3) NULL COMMENT '办结时间',
  satisfaction        tinyint NULL COMMENT '满意度 1~5，仅办结后可评一次',
  satisfaction_remark varchar(500) NULL COMMENT '满意度备注',
  version             int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  created_at          datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted             tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ticket_no (ticket_no),
  KEY idx_status_deadline (status, deadline_at),
  KEY idx_submitter (submitter_type, submitter_id, created_at),
  KEY idx_order (order_id),
  KEY idx_assignee (assignee_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务工单';

-- ------------------------------------------------------------
-- 2. 工单流转记录：每次状态变化一条，只增不改（审计流水）
--    from_status 允许 NULL，仅创建记录使用。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_ticket_record (
  id            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  tenant_id     bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  ticket_id     bigint unsigned NOT NULL COMMENT '工单ID',
  from_status   tinyint NULL COMMENT '变更前状态；创建记录为空',
  to_status     tinyint NOT NULL COMMENT '变更后状态',
  action        varchar(32) NOT NULL COMMENT 'CREATED/ACCEPT/ASSIGN/HANDLE/CALLBACK/FINISH/CLOSE/...',
  operator_id   bigint unsigned NOT NULL COMMENT '操作人ID',
  operator_name varchar(64) NOT NULL COMMENT '操作人名称快照',
  content       varchar(1000) NULL COMMENT '处理说明（纯文本）',
  created_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_ticket_time (ticket_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单流转记录';

-- ------------------------------------------------------------
-- 3. 投诉单：ticket_id 指向自动创建的投诉类工单
--    封禁/扣款只登记 punish_action，实际执行由 T07/T04 端口落地。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_complaint (
  id               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '投诉ID',
  tenant_id        bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  ticket_id        bigint unsigned NOT NULL COMMENT '关联工单ID',
  order_id         bigint unsigned NOT NULL COMMENT '关联订单ID',
  complainant_type tinyint NOT NULL DEFAULT 1 COMMENT '1用户 2商家 3骑手 4平台',
  complainant_id   bigint unsigned NOT NULL COMMENT '投诉人业务ID',
  target_type      tinyint NOT NULL COMMENT '2商家 3骑手 4平台',
  target_id        bigint unsigned NOT NULL COMMENT '被投诉方业务ID',
  reason           varchar(500) NOT NULL COMMENT '投诉原因（纯文本）',
  liability        tinyint NULL COMMENT '1商家 2骑手 3平台 4用户',
  result           varchar(500) NULL COMMENT '处理结果',
  punish_action    tinyint NOT NULL DEFAULT 0 COMMENT '0无 1警告 2扣款 3封禁',
  callback_at      datetime(3) NULL COMMENT '回访时间',
  callback_result  varchar(500) NULL COMMENT '回访结果',
  created_at       datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_ticket (ticket_id),
  KEY idx_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投诉单';

-- ------------------------------------------------------------
-- 4. FAQ：用户端帮助中心只读 status = 0
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_faq (
  id         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'FAQ ID',
  tenant_id  bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  category   varchar(64) NOT NULL COMMENT '分类',
  question   varchar(200) NOT NULL COMMENT '问题',
  answer     text NOT NULL COMMENT '答案（纯文本）',
  sort       int NOT NULL DEFAULT 0 COMMENT '排序，越小越前',
  view_count int NOT NULL DEFAULT 0 COMMENT '浏览次数',
  status     tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted    tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_category_sort (category, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='常见问题';

-- ------------------------------------------------------------
-- 5. 意见反馈：0待处理 1已回复；同一反馈只允许回复一次
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_feedback (
  id          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '反馈ID',
  tenant_id   bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  user_id     bigint unsigned NOT NULL COMMENT '用户ID（t_member_user.id）',
  content     varchar(1000) NOT NULL COMMENT '反馈内容（纯文本）',
  images_json json NULL COMMENT '图片 URL 数组',
  contact     varchar(64) NULL COMMENT '联系方式',
  status      tinyint NOT NULL DEFAULT 0 COMMENT '0待处理 1已回复',
  reply       varchar(1000) NULL COMMENT '回复内容',
  reply_at    datetime(3) NULL COMMENT '回复时间',
  created_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_status_time (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='意见反馈';

-- ------------------------------------------------------------
-- 6. 快捷话术：阶段A 只建表，阶段B 的坐席工作台读取
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_quick_reply (
  id         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '话术ID',
  tenant_id  bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  category   varchar(64) NOT NULL COMMENT '分类',
  content    varchar(500) NOT NULL COMMENT '话术内容',
  sort       int NOT NULL DEFAULT 0 COMMENT '排序',
  status     tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted    tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_category_sort (category, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客服快捷话术';

-- ------------------------------------------------------------
-- 7. 后台菜单与权限：挂在「客服与工单」(id = 10) 下
--    V4 一级目录 1~12；V17 菜单到 107；V24 到 119；本脚本 120~130。
-- ------------------------------------------------------------
INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (120, 1, 10,  '工单管理',   2, '/service/ticket',     'service/ticket/index',     'service:ticket:list',     'service', 1, 0, 0),
  (121, 1, 120, '工单详情',   3, NULL,                  NULL,                       'service:ticket:detail',   NULL,      1, 1, 0),
  (122, 1, 120, '工单处理',   3, NULL,                  NULL,                       'service:ticket:handle',   NULL,      2, 1, 0),
  (123, 1, 120, '工单指派',   3, NULL,                  NULL,                       'service:ticket:assign',   NULL,      3, 1, 0),
  (124, 1, 120, '工单关闭',   3, NULL,                  NULL,                       'service:ticket:close',    NULL,      4, 1, 0),
  (125, 1, 10,  '投诉处理',   2, '/service/complaint',  'service/complaint/index',  'service:complaint:list',  'warning', 2, 0, 0),
  (126, 1, 125, '责任判定',   3, NULL,                  NULL,                       'service:complaint:judge', NULL,      1, 1, 0),
  (127, 1, 10,  'FAQ 维护',   2, '/service/faq',        'service/faq/index',        'service:faq:list',        'question',3, 0, 0),
  (128, 1, 127, 'FAQ 编辑',   3, NULL,                  NULL,                       'service:faq:save',        NULL,      1, 1, 0),
  (129, 1, 10,  '意见反馈',   2, '/service/feedback',   'service/feedback/index',   'service:feedback:list',   'edit',    4, 0, 0),
  (130, 1, 129, '反馈回复',   3, NULL,                  NULL,                       'service:feedback:reply',  NULL,      1, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 平台超管（role_id = 1）授予客服工单全部菜单与按钮权限
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 120 AND 130
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
