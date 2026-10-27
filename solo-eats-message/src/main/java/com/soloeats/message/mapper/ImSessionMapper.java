package com.soloeats.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.message.domain.entity.ImSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ImSessionMapper extends BaseMapper<ImSession> {

    /**
     * 会话唯一键查询，对应 uk_order_peer(order_id, user_id, peer_type, peer_id)。
     * orderId 为 null 时必须显式用 IS NULL，否则 SQL 的 = NULL 永远不命中。
     */
    @Select("<script>SELECT * FROM t_im_session "
            + "WHERE user_id = #{userId} AND peer_type = #{peerType} AND peer_id = #{peerId} "
            + "<choose>"
            + "<when test='orderId == null'>AND order_id IS NULL</when>"
            + "<otherwise>AND order_id = #{orderId}</otherwise>"
            + "</choose>"
            + " LIMIT 1</script>")
    ImSession findByUnique(@Param("orderId") Long orderId,
                           @Param("userId") Long userId,
                           @Param("peerType") Integer peerType,
                           @Param("peerId") Long peerId);

    /** 原子自增，避免「先读后写」丢失并发消息的未读数。 */
    @Update("UPDATE t_im_session SET user_unread = user_unread + 1 WHERE id = #{id}")
    int incUserUnread(@Param("id") Long id);

    @Update("UPDATE t_im_session SET peer_unread = peer_unread + 1 WHERE id = #{id}")
    int incPeerUnread(@Param("id") Long id);

    @Update("UPDATE t_im_session SET user_unread = 0 WHERE id = #{id}")
    int clearUserUnread(@Param("id") Long id);

    @Update("UPDATE t_im_session SET peer_unread = 0 WHERE id = #{id}")
    int clearPeerUnread(@Param("id") Long id);

    /** 再次进入被隐藏的会话时只恢复本方可见，不重置对方状态。 */
    @Update("UPDATE t_im_session SET user_hidden = 0 WHERE id = #{id}")
    int clearUserHidden(@Param("id") Long id);

    @Update("UPDATE t_im_session SET peer_hidden = 0 WHERE id = #{id}")
    int clearPeerHidden(@Param("id") Long id);

    /** DELETE 接口的「仅隐藏本人」：不物理删除，对方仍可继续查看历史消息。 */
    @Update("UPDATE t_im_session SET user_hidden = 1 WHERE id = #{id}")
    int hideUserSide(@Param("id") Long id);

    @Update("UPDATE t_im_session SET peer_hidden = 1 WHERE id = #{id}")
    int hidePeerSide(@Param("id") Long id);

    @Update("UPDATE t_im_session SET last_message = #{lastMessage}, last_message_at = #{lastMessageAt} "
            + "WHERE id = #{id}")
    int updateLastMessage(@Param("id") Long id,
                          @Param("lastMessage") String lastMessage,
                          @Param("lastMessageAt") LocalDateTime lastMessageAt);

    /**
     * 当前主体参与的会话，按最近消息时间倒序。
     *
     * <p>receiverType=1 只查 user_id，避免消费者 ID 与商家/骑手 ID 数值相同造成串号；
     * 其余主体只查 (peer_type, peer_id)。MySQL 的 DESC 排序天然把 NULL 排在最后。
     * 隐藏过滤放在 SQL 里，调用方拿到的就是「本人可见」集合。
     */
    @Select("<script>SELECT * FROM t_im_session "
            + "<where>"
            + "<choose>"
            + "<when test='receiverType == 1'>user_id = #{receiverId} AND user_hidden = 0</when>"
            + "<otherwise>peer_type = #{receiverType} AND peer_id = #{receiverId} AND peer_hidden = 0</otherwise>"
            + "</choose>"
            + "</where>"
            + " ORDER BY last_message_at DESC, id DESC</script>")
    List<ImSession> selectMine(@Param("receiverType") Integer receiverType,
                               @Param("receiverId") Long receiverId);

    // ------------------------------------------------------------
    // T09 阶段B 客服工作台坐席端：只处理 peer_type = 4 的会话
    // ------------------------------------------------------------

    /**
     * 接待：条件更新把 agent_id 从 NULL 改成当前坐席。
     * 条件里同时带 peer_type=4、status=0、agent_id IS NULL，并发抢接只有一行更新成功；
     * 影响 0 行时由调用方 selectById 区分「不存在 / 已结束 / 已被他人接待」。
     */
    @Update("UPDATE t_im_session SET agent_id = #{agentId} "
            + "WHERE id = #{id} AND peer_type = 4 AND status = 0 AND agent_id IS NULL")
    int takeSession(@Param("id") Long id, @Param("agentId") Long agentId);

    /**
     * 转接：管理员可强制改接待人，条件只校验 peer_type=4 且 status=0。
     * 不校验当前 agent_id，是否本人接待由接口权限（service:cs:handle）控制。
     */
    @Update("UPDATE t_im_session SET agent_id = #{agentId} "
            + "WHERE id = #{id} AND peer_type = 4 AND status = 0")
    int transferAgent(@Param("id") Long id, @Param("agentId") Long agentId);

    /** 结束会话：status 0→1，用户侧随之只读（send 对 status != 0 抛 90002）。 */
    @Update("UPDATE t_im_session SET status = 1 "
            + "WHERE id = #{id} AND peer_type = 4 AND status = 0")
    int closeSession(@Param("id") Long id);

    /** 坐席已接待完成量：status=1 且 agent_id 命中；agentId 为空时统计全部坐席。 */
    @Select("<script>SELECT COUNT(*) FROM t_im_session "
            + "WHERE peer_type = 4 AND status = 1 "
            + "<if test='agentId != null'>AND agent_id = #{agentId}</if>"
            + "<if test='start != null'> AND updated_at &gt;= #{start}</if>"
            + "<if test='end != null'> AND updated_at &lt; #{end}</if>"
            + "</script>")
    long countServedSessions(@Param("agentId") Long agentId,
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    /** 进行中会话数：status=0，agentId 为空时统计全部坐席（含未接待）。 */
    @Select("<script>SELECT COUNT(*) FROM t_im_session "
            + "WHERE peer_type = 4 AND status = 0 "
            + "<if test='agentId != null'>AND agent_id = #{agentId}</if>"
            + "</script>")
    long countActiveSessions(@Param("agentId") Long agentId);

    /** 待接待队列：status=0 且尚未分配坐席，与具体坐席无关。 */
    @Select("SELECT COUNT(*) FROM t_im_session "
            + "WHERE peer_type = 4 AND status = 0 AND agent_id IS NULL")
    long countWaitingSessions();
}
