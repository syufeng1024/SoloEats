-- ============================================================
-- T12 阶段B 第一批：会员等级 / 成长值 / 权益 / 积分 建表 + 菜单
-- Flyway V33 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. 库实测最大为 V32，本脚本使用 V33。
--   2. 阶段B 共 9 张表一次性建齐：本批只写会员等级、成长值、权益、积分代码，
--      签到、邀请、红包三组表先建空表，后续批次不再追加建表脚本。
--   3. 只新建表，不 ALTER 任何既有表；t_member_user.member_level_id /
--      growth_value 由 V11 预留，本脚本不重复建列。
--   4. 成长值与积分是两套独立账户：t_member_growth_log 只记录成长值，
--      t_point_account / t_point_log 只记录积分。
--   5. 菜单 id 从 159 起（V31 后全局最大为 158），挂在「营销中心」(parent_id = 6) 下，
--      授平台超管 role_id = 1。
--   6. 等级种子只在 t_member_level 为空时插入，避免覆盖后台已维护的配置。
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. 会员等级配置（六档，后台可增删改）
--    status：0启用 1停用；growth_threshold 为进入该等级所需的最小近 365 天成长值
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_member_level (
  id               bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id        bigint unsigned NOT NULL DEFAULT 1,
  level_code       varchar(16) NOT NULL COMMENT '等级编码，如 V1',
  level_name       varchar(32) NOT NULL COMMENT '等级名称，如 黑铁',
  level_no         tinyint NOT NULL COMMENT '等级序号 1~6',
  growth_threshold int NOT NULL DEFAULT 0 COMMENT '进入门槛（近365天成长值）',
  star             tinyint NOT NULL DEFAULT 1 COMMENT '星级 1~3',
  benefits_json    json NULL COMMENT '{"couponCount":4,"freeDeliveryTimes":3,"exclusiveService":true}',
  icon_url         varchar(255) NULL,
  sort             int NOT NULL DEFAULT 0,
  status           tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  version          int unsigned NOT NULL DEFAULT 0,
  created_at       datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted          tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_level (tenant_id, level_code),
  KEY idx_no (level_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员等级与权益配置';

-- ------------------------------------------------------------
-- 2. 成长值流水
--    source：1消费 2签到 3评价 4邀请 5手工调整；growth 可负
--    effective_at 参与近 365 天滚动计算，expire_at 仅作审计留存
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_member_growth_log (
  id           bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id    bigint unsigned NOT NULL DEFAULT 1,
  user_id      bigint unsigned NOT NULL COMMENT 't_member_user.id',
  source       tinyint NOT NULL COMMENT '1消费 2签到 3评价 4邀请 5手工调整',
  biz_id       bigint unsigned NULL COMMENT '来源业务主键',
  growth       int NOT NULL DEFAULT 0 COMMENT '成长值，可负',
  before_value int NOT NULL DEFAULT 0 COMMENT '变更前近365天成长值',
  after_value  int NOT NULL DEFAULT 0 COMMENT '变更后近365天成长值',
  effective_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '生效时间',
  expire_at    datetime(3) NULL COMMENT '失效时间，effective_at + 365 天',
  remark       varchar(255) NULL,
  created_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_user_time (user_id, created_at),
  KEY idx_user_effective (user_id, effective_at),
  KEY idx_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员成长值流水';

-- ------------------------------------------------------------
-- 3. 权益发放记录（按周期发放，如 2026-10）
--    benefit_type：1神券 2免配送费 3专属客服 4生日礼遇
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_member_benefit_grant (
  id           bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id    bigint unsigned NOT NULL DEFAULT 1,
  user_id      bigint unsigned NOT NULL,
  level_id     bigint unsigned NOT NULL COMMENT 't_member_level.id',
  benefit_type tinyint NOT NULL COMMENT '1神券 2免配送费 3专属客服 4生日礼遇',
  period       varchar(8) NOT NULL COMMENT '发放周期，如 2026-10',
  grant_qty    int NOT NULL DEFAULT 0,
  used_qty     int NOT NULL DEFAULT 0,
  created_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_benefit (user_id, benefit_type, period),
  KEY idx_level_period (level_id, period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员权益发放记录';

-- ------------------------------------------------------------
-- 4. 积分账户（与成长值账户完全分离）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_point_account (
  id             bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id      bigint unsigned NOT NULL DEFAULT 1,
  user_id        bigint unsigned NOT NULL,
  balance        int NOT NULL DEFAULT 0 COMMENT '当前可用积分',
  total_earned   bigint NOT NULL DEFAULT 0 COMMENT '累计获得',
  total_consumed bigint NOT NULL DEFAULT 0 COMMENT '累计消耗（含过期）',
  version        int unsigned NOT NULL DEFAULT 0,
  created_at     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员积分账户';

-- ------------------------------------------------------------
-- 5. 积分流水
--    source：1下单 2签到 3评价 4邀请 5兑换 6过期 7手工；point 可负
--    uk_biz(source, biz_id) 是幂等兜底：同一业务事件只产生一条流水
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_point_log (
  id             bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id      bigint unsigned NOT NULL DEFAULT 1,
  user_id        bigint unsigned NOT NULL,
  source         tinyint NOT NULL COMMENT '1下单 2签到 3评价 4邀请 5兑换 6过期 7手工',
  biz_id         bigint unsigned NULL COMMENT '来源业务主键；过期流水存被过期积分流水的 id',
  point          int NOT NULL COMMENT '积分变动，可负',
  before_balance int NOT NULL DEFAULT 0,
  after_balance  int NOT NULL DEFAULT 0,
  expire_at      datetime(3) NULL COMMENT '该笔积分的过期时间',
  remark         varchar(255) NULL,
  created_at     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz (source, biz_id),
  KEY idx_user_time (user_id, created_at),
  KEY idx_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员积分流水';

-- ------------------------------------------------------------
-- 6. 签到记录（后续批次使用）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_checkin_record (
  id              bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id       bigint unsigned NOT NULL DEFAULT 1,
  user_id         bigint unsigned NOT NULL,
  checkin_date    date NOT NULL,
  continuous_days int NOT NULL DEFAULT 1,
  point_award     int NOT NULL DEFAULT 0,
  created_at      datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_date (user_id, checkin_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员签到记录';

-- ------------------------------------------------------------
-- 7. 邀请记录（后续批次使用）
--    status：0已注册 1首单已完成 2无效
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_invite_record (
  id            bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id     bigint unsigned NOT NULL DEFAULT 1,
  inviter_id    bigint unsigned NOT NULL,
  invitee_id    bigint unsigned NOT NULL,
  invite_code   varchar(16) NOT NULL,
  status        tinyint NOT NULL DEFAULT 0 COMMENT '0已注册 1首单已完成 2无效',
  reward_point  int NOT NULL DEFAULT 0,
  reward_growth int NOT NULL DEFAULT 0,
  created_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_invitee (invitee_id),
  KEY idx_inviter (inviter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邀请有礼记录';

-- ------------------------------------------------------------
-- 8. 红包活动（后续批次使用）
--    packet_type：1拼手气 2新人 3分享；金额单位分
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_red_packet (
  id           bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id    bigint unsigned NOT NULL DEFAULT 1,
  packet_type  tinyint NOT NULL DEFAULT 1 COMMENT '1拼手气 2新人 3分享',
  total_amount bigint NOT NULL DEFAULT 0 COMMENT '红包总额，分',
  total_qty    int NOT NULL DEFAULT 0,
  remain_qty   int NOT NULL DEFAULT 0,
  start_at     datetime(3) NOT NULL,
  end_at       datetime(3) NOT NULL,
  status       tinyint NOT NULL DEFAULT 0 COMMENT '0未开始 1进行中 2已结束 3停用',
  version      int unsigned NOT NULL DEFAULT 0,
  created_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted      tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_status_time (status, start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='红包活动';

-- ------------------------------------------------------------
-- 9. 用户红包（后续批次使用）
--    status：0未用 1已用 2过期
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_user_red_packet (
  id         bigint unsigned NOT NULL AUTO_INCREMENT,
  tenant_id  bigint unsigned NOT NULL DEFAULT 1,
  packet_id  bigint unsigned NOT NULL,
  user_id    bigint unsigned NOT NULL,
  amount     bigint NOT NULL DEFAULT 0 COMMENT '分',
  status     tinyint NOT NULL DEFAULT 0 COMMENT '0未用 1已用 2过期',
  order_id   bigint unsigned NULL,
  expire_at  datetime(3) NULL,
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_user_status (user_id, status, expire_at),
  KEY idx_packet (packet_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户红包';

-- ------------------------------------------------------------
-- 10. 六档等级种子：仅当 t_member_level 为空时插入一次
-- ------------------------------------------------------------
SET @member_level_empty = (SELECT COUNT(*) FROM t_member_level);

INSERT INTO t_member_level
  (tenant_id, level_code, level_name, level_no, growth_threshold, star, benefits_json, sort, status)
SELECT seed.* FROM (
  SELECT 1 AS tenant_id, 'V1' AS level_code, '黑铁' AS level_name, 1 AS level_no,
         0 AS growth_threshold, 1 AS star,
         CAST('{"couponCount":0,"freeDeliveryTimes":0,"exclusiveService":false}' AS JSON) AS benefits_json,
         1 AS sort, 0 AS status
  UNION ALL SELECT 1, 'V2', '青铜', 2, 500, 2,
         CAST('{"couponCount":1,"freeDeliveryTimes":1,"exclusiveService":false}' AS JSON), 2, 0
  UNION ALL SELECT 1, 'V3', '白银', 3, 2000, 3,
         CAST('{"couponCount":2,"freeDeliveryTimes":2,"exclusiveService":false}' AS JSON), 3, 0
  UNION ALL SELECT 1, 'V4', '黄金', 4, 5000, 1,
         CAST('{"couponCount":3,"freeDeliveryTimes":3,"exclusiveService":false}' AS JSON), 4, 0
  UNION ALL SELECT 1, 'V5', '铂金', 5, 10000, 2,
         CAST('{"couponCount":4,"freeDeliveryTimes":5,"exclusiveService":true}' AS JSON), 5, 0
  UNION ALL SELECT 1, 'V6', '钻石', 6, 20000, 3,
         CAST('{"couponCount":6,"freeDeliveryTimes":8,"exclusiveService":true}' AS JSON), 6, 0
) AS seed
WHERE @member_level_empty = 0;

-- ------------------------------------------------------------
-- 11. 菜单与授权（营销中心 parent_id = 6）
-- ------------------------------------------------------------
INSERT INTO t_sys_menu
  (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (159, 1, 6,   '会员等级', 2, '/marketing/member-level', 'marketing/memberLevel/index', 'marketing:member:list',   'user', 3, 0, 0),
  (160, 1, 159, '等级维护', 3, NULL,                     NULL,                          'marketing:member:update', NULL,   1, 1, 0)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO t_sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, 1, id FROM t_sys_menu WHERE tenant_id = 1 AND id BETWEEN 159 AND 160
ON DUPLICATE KEY UPDATE t_sys_role_menu.id = t_sys_role_menu.id;
