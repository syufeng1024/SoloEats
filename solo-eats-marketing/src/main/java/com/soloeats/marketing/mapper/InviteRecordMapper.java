package com.soloeats.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.marketing.domain.entity.InviteRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface InviteRecordMapper extends BaseMapper<InviteRecord> {

    @Select("""
            SELECT *
            FROM t_invite_record
            WHERE invitee_id = #{inviteeId}
            LIMIT 1
            """)
    InviteRecord selectByInvitee(@Param("inviteeId") Long inviteeId);

    /**
     * 同一邀请人 24 小时内成功绑定次数（status 0已注册 / 1首单已完成）。
     * 没有设备/IP 上下文时的降级防刷口径。
     */
    @Select("""
            SELECT COUNT(*)
            FROM t_invite_record
            WHERE inviter_id = #{inviterId}
              AND created_at >= #{since}
              AND status IN (0, 1)
            """)
    int countBindsSince(@Param("inviterId") Long inviterId,
                        @Param("since") LocalDateTime since);

    /**
     * 首单完成结算：条件更新把 status 从 0 推到 1，0 行表示已结算或记录不存在。
     */
    @Update("""
            UPDATE t_invite_record
            SET status = 1, updated_at = #{now}
            WHERE invitee_id = #{inviteeId}
              AND status = 0
            """)
    int markCompleted(@Param("inviteeId") Long inviteeId,
                      @Param("now") LocalDateTime now);
}
