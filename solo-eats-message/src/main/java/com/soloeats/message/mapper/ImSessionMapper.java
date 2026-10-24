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
}
