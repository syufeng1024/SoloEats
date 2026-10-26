package com.soloeats.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.service.domain.entity.Complaint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ComplaintMapper extends BaseMapper<Complaint> {

    @Select("SELECT * FROM t_complaint WHERE ticket_id = #{ticketId} ORDER BY id DESC LIMIT 1")
    Complaint selectByTicketId(@Param("ticketId") Long ticketId);

    /** 责任判定只允许一次：liability IS NULL 兜住重复提交。 */
    @Update("UPDATE t_complaint "
            + "SET liability = #{liability}, result = #{result}, punish_action = #{punishAction}, "
            + "    updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND liability IS NULL")
    int judge(@Param("id") Long id,
              @Param("liability") Integer liability,
              @Param("result") String result,
              @Param("punishAction") Integer punishAction);

    /** 工单进入回访时，同步投诉单的回访时间与结果。 */
    @Update("UPDATE t_complaint "
            + "SET callback_at = #{callbackAt}, callback_result = #{callbackResult}, "
            + "    updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE ticket_id = #{ticketId}")
    int updateCallbackByTicket(@Param("ticketId") Long ticketId,
                               @Param("callbackAt") LocalDateTime callbackAt,
                               @Param("callbackResult") String callbackResult);
}
