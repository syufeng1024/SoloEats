-- ============================================================
-- 数创星光外卖 T02 阶段B：操作日志 / 登录日志 / 邮件 / 公告
-- Flyway V6 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. 本脚本可重复执行（CREATE TABLE IF NOT EXISTS + 种子 INSERT ... ON DUPLICATE KEY UPDATE id = id）。
--   2. 6 张表均带 tenant_id，由 TenantLineInnerInterceptor 自动读写；
--      SQL 与 Application 层都不手写 tenant_id 条件（异步落日志除外，切面显式透传）。
--   3. 操作日志的 request_params 为 json 列；切面保证写入前脱敏、截断后仍是合法 JSON。
--   4. 邮件 SMTP 密码只允许写入 password_cipher（AES-GCM），禁止明文。
--   5. 公告已读 P0 只维护 read_count，用户级明细表 t_sys_notice_read 留待 P1。
--   6. 时间为 Asia/Shanghai。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. 操作日志
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sys_oper_log (
  id             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  tenant_id      bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  trace_id       varchar(64) NULL COMMENT '链路ID',
  module         varchar(32) NOT NULL COMMENT '模块，如 用户管理',
  action         varchar(32) NOT NULL COMMENT '动作，如 新增',
  method         varchar(255) NULL COMMENT '类#方法',
  request_uri    varchar(255) NULL COMMENT '请求路径',
  request_method varchar(16) NULL COMMENT 'HTTP 方法',
  request_params json NULL COMMENT '脱敏并截断后的请求参数摘要',
  operator_id    bigint unsigned NULL COMMENT '操作人ID',
  operator_name  varchar(64) NULL COMMENT '操作人账号',
  operator_type  varchar(16) NULL COMMENT 'ADMIN/MERCHANT/RIDER/C_USER/ANONYMOUS',
  ip             varchar(64) NULL COMMENT '来源IP',
  ua             varchar(512) NULL COMMENT 'User-Agent',
  result_code    int NOT NULL DEFAULT 0 COMMENT '业务结果码，0 表示成功',
  error_msg      varchar(512) NULL COMMENT '失败原因',
  cost_ms        int NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
  created_at     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_operator_time (operator_id, created_at),
  KEY idx_module_time (module, created_at),
  KEY idx_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志';

-- ------------------------------------------------------------
-- 2. 登录日志（成功与失败都落库）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sys_login_log (
  id          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  tenant_id   bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  user_id     bigint unsigned NULL COMMENT '账号ID，失败时可为空',
  username    varchar(64) NULL COMMENT '登录名（失败时为提交值，不做手机号脱敏存储）',
  login_type  tinyint NOT NULL DEFAULT 1 COMMENT '1密码 2验证码 3mock',
  success     tinyint NOT NULL DEFAULT 0 COMMENT '1成功 0失败',
  fail_reason varchar(128) NULL COMMENT '失败原因',
  ip          varchar(64) NULL COMMENT '来源IP',
  ua          varchar(512) NULL COMMENT 'User-Agent',
  created_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_user_time (user_id, created_at),
  KEY idx_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志';

-- ------------------------------------------------------------
-- 3. 邮件账号（SMTP），password_cipher 为 AES-GCM 密文
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sys_mail_account (
  id              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '账号ID',
  tenant_id       bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  account_name    varchar(64) NOT NULL COMMENT '账号名称',
  host            varchar(128) NOT NULL COMMENT 'SMTP 主机',
  port            int NOT NULL DEFAULT 465 COMMENT 'SMTP 端口',
  username        varchar(128) NOT NULL COMMENT 'SMTP 用户名',
  password_cipher varchar(512) NOT NULL COMMENT 'SMTP 密码密文（AES-GCM，禁止明文）',
  from_addr       varchar(128) NOT NULL COMMENT '发件人地址',
  ssl_enabled     tinyint NOT NULL DEFAULT 1 COMMENT '0 明文/STARTTLS 1 SSL（ssl 为 MySQL 保留字）',
  status          tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at      datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted         tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_account (tenant_id, account_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件账号';

-- ------------------------------------------------------------
-- 4. 邮件模板
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sys_mail_template (
  id            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '模板ID',
  tenant_id     bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  template_code varchar(64) NOT NULL COMMENT '模板编码',
  template_name varchar(64) NOT NULL COMMENT '模板名称',
  subject       varchar(255) NOT NULL COMMENT '邮件主题，支持 {{key}} 占位符',
  content       text NOT NULL COMMENT '邮件正文，支持 {{key}} 占位符',
  status        tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  created_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted       tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_tpl (tenant_id, template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件模板';

-- ------------------------------------------------------------
-- 5. 邮件发送日志
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sys_mail_log (
  id            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  tenant_id     bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  template_code varchar(64) NULL COMMENT '模板编码，测试邮件为 TEST',
  to_addr       varchar(128) NOT NULL COMMENT '收件人',
  subject       varchar(255) NULL COMMENT '主题快照',
  content       text NULL COMMENT '正文快照',
  status        tinyint NOT NULL DEFAULT 0 COMMENT '0待发 1成功 2失败',
  error_msg     varchar(512) NULL COMMENT '失败原因',
  sent_at       datetime(3) NULL COMMENT '发送成功时间',
  created_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_status_time (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件日志';

-- ------------------------------------------------------------
-- 6. 公告
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sys_notice (
  id          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  tenant_id   bigint unsigned NOT NULL DEFAULT 1 COMMENT '租户ID',
  title       varchar(128) NOT NULL COMMENT '标题',
  content     longtext NOT NULL COMMENT '富文本正文',
  notice_type tinyint NOT NULL DEFAULT 1 COMMENT '1公告 2通知',
  target_type tinyint NOT NULL DEFAULT 1 COMMENT '1全部 2平台 3商家 4骑手',
  status      tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布 2已撤回',
  publish_at  datetime(3) NULL COMMENT '发布时间；为空表示发布动作发生时立即生效',
  expire_at   datetime(3) NULL COMMENT '过期时间；为空表示不过期',
  read_count  int NOT NULL DEFAULT 0 COMMENT '已读次数（P0 只做计数，不做用户级明细）',
  version     int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  created_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted     tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  KEY idx_status_publish (status, publish_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公告';

-- ------------------------------------------------------------
-- 7. 系统管理子菜单：日志中心 / 邮件管理 / 公告管理
-- ------------------------------------------------------------
INSERT INTO t_sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (48, 1, 12, '日志中心', 2, '/system/log',    'system/log/index',    'system:log:oper:list', 'log',    9,  0, 0),
  (49, 1, 12, '邮件管理', 2, '/system/mail',   'system/mail/index',   'system:mail:list',     'mail',   10, 0, 0),
  (50, 1, 12, '公告管理', 2, '/system/notice', 'system/notice/index', 'system:notice:list',   'notice', 11, 0, 0)
ON DUPLICATE KEY UPDATE id = id;

-- ------------------------------------------------------------
-- 8. 新菜单对应的按钮权限
-- ------------------------------------------------------------
INSERT INTO t_sys_menu (id, tenant_id, parent_id, menu_name, menu_type, perms, sort, visible, status)
VALUES
  (51, 1, 48, '登录日志查询', 3, 'system:log:login:list', 1, 1, 0),
  (52, 1, 49, '邮件配置',     3, 'system:mail:update',    1, 1, 0),
  (53, 1, 49, '邮件测试',     3, 'system:mail:test',      2, 1, 0),
  (54, 1, 50, '公告新增',     3, 'system:notice:create',  1, 1, 0),
  (55, 1, 50, '公告修改',     3, 'system:notice:update',  2, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 超管默认拥有新菜单与按钮
INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 48 AND 55
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
