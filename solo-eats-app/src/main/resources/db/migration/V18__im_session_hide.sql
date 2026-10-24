-- ============================================================
-- 数创星光外卖 T08 阶段B：IM 会话「仅隐藏本人」标记
-- Flyway V18 / MySQL 8.0 / utf8mb4
-- 说明：
--   1. 只新增两列，不修改 t_im_session 既有列（Expand-Contract）。
--   2. MySQL 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 判定后动态执行，
--      脚本可重复执行；Flyway 校验和固定，重复执行只发生在手工排障场景。
--   3. 隐藏只影响一侧列表，不物理删除会话与消息：
--      user_hidden=1 时用户侧 GET /api/v1/im/sessions 不再返回该会话，
--      peer_hidden=1 时商家/骑手/客服侧不再返回，对方会话与未读计数不受影响。
--   4. IM 接口登录即可，无新增菜单权限。
-- ============================================================

SET NAMES utf8mb4;

-- 用户侧隐藏：t_im_session.user_id 对应一方是否隐藏
SET @has_user_hidden = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_im_session'
    AND COLUMN_NAME = 'user_hidden'
);
SET @ddl = IF(@has_user_hidden = 0,
  'ALTER TABLE t_im_session ADD COLUMN user_hidden tinyint NOT NULL DEFAULT 0 COMMENT ''0显示 1用户侧隐藏'' AFTER status',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 对方侧隐藏：peer_type + peer_id 对应一方是否隐藏
SET @has_peer_hidden = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_im_session'
    AND COLUMN_NAME = 'peer_hidden'
);
SET @ddl = IF(@has_peer_hidden = 0,
  'ALTER TABLE t_im_session ADD COLUMN peer_hidden tinyint NOT NULL DEFAULT 0 COMMENT ''0显示 1对方侧隐藏'' AFTER user_hidden',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
