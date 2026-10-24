package com.soloeats.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.message.domain.entity.SiteMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SiteMessageMapper extends BaseMapper<SiteMessage> {

    /** 未读数按 msg_type 聚合。走 idx_receiver，不逐条 COUNT。 */
    @Select("SELECT msg_type, COUNT(*) AS cnt FROM t_site_message "
            + "WHERE receiver_type = #{receiverType} AND receiver_id = #{receiverId} "
            + "AND read_flag = 0 GROUP BY msg_type")
    @Results({
            @Result(column = "msg_type", property = "msgType"),
            @Result(column = "cnt", property = "cnt")
    })
    List<UnreadCountRow> countUnreadByType(@Param("receiverType") Integer receiverType,
                                           @Param("receiverId") Long receiverId);

    /** 条件更新：归属与未读状态都进 WHERE，避免先查后改的竞态。 */
    @Update("UPDATE t_site_message SET read_flag = 1, read_at = #{readAt} "
            + "WHERE id = #{id} AND receiver_type = #{receiverType} AND receiver_id = #{receiverId} "
            + "AND read_flag = 0")
    int markRead(@Param("id") Long id,
                 @Param("receiverType") Integer receiverType,
                 @Param("receiverId") Long receiverId,
                 @Param("readAt") LocalDateTime readAt);

    /** msgType 为 null 时全部已读。 */
    @Update("<script>UPDATE t_site_message SET read_flag = 1, read_at = #{readAt} "
            + "WHERE receiver_type = #{receiverType} AND receiver_id = #{receiverId} "
            + "AND read_flag = 0"
            + "<if test='msgType != null'> AND msg_type = #{msgType}</if>"
            + "</script>")
    int markAllRead(@Param("receiverType") Integer receiverType,
                    @Param("receiverId") Long receiverId,
                    @Param("msgType") Integer msgType,
                    @Param("readAt") LocalDateTime readAt);
}
