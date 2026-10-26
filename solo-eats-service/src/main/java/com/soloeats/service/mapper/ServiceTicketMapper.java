package com.soloeats.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.service.domain.entity.ServiceTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单状态迁移全部使用条件更新：影响行数为 0 由服务层翻译成 90010 / 90011 / 90013。
 * 手写 SQL 不经过 @TableLogic，故显式带 deleted = 0。
 */
@Mapper
public interface ServiceTicketMapper extends BaseMapper<ServiceTicket> {

    /** 受理抢锁：并发下只有一个请求能把 0 改成 1。 */
    @Update("UPDATE t_service_ticket "
            + "SET status = 1, assignee_id = #{assigneeId}, accept_at = #{acceptAt}, "
            + "    deadline_at = #{deadlineAt} "
            + "WHERE id = #{id} AND status = 0 AND deleted = 0")
    int accept(@Param("id") Long id,
               @Param("assigneeId") Long assigneeId,
               @Param("acceptAt") LocalDateTime acceptAt,
               @Param("deadlineAt") LocalDateTime deadlineAt);

    /** 指派/转派：终态工单不允许再改受理人。 */
    @Update("UPDATE t_service_ticket SET assignee_id = #{assigneeId} "
            + "WHERE id = #{id} AND status IN (0, 1, 2) AND deleted = 0")
    int assign(@Param("id") Long id, @Param("assigneeId") Long assigneeId);

    /** 同状态推进（处理记录 1→1）。状态仍进 WHERE，避免并发下处理已关闭工单。 */
    @Update("UPDATE t_service_ticket SET status = #{toStatus}, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = #{fromStatus} AND deleted = 0")
    int transit(@Param("id") Long id,
                @Param("fromStatus") Integer fromStatus,
                @Param("toStatus") Integer toStatus);

    /** 办结：2→3，同时写 finish_at。 */
    @Update("UPDATE t_service_ticket "
            + "SET status = 3, finish_at = #{finishAt}, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 2 AND deleted = 0")
    int finish(@Param("id") Long id, @Param("finishAt") LocalDateTime finishAt);

    /** 客服关闭：从指定状态迁移到 4。 */
    @Update("UPDATE t_service_ticket SET status = 4, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = #{fromStatus} AND deleted = 0")
    int close(@Param("id") Long id, @Param("fromStatus") Integer fromStatus);

    /** 用户撤单：仅待受理，且归属写进 WHERE，避免越权改单。 */
    @Update("UPDATE t_service_ticket SET status = 4, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 0 AND submitter_type = 1 "
            + "AND submitter_id = #{submitterId} AND deleted = 0")
    int closeBySubmitter(@Param("id") Long id, @Param("submitterId") Long submitterId);

    /** 满意度只写一次：satisfaction IS NULL 兜住重复评价。 */
    @Update("UPDATE t_service_ticket "
            + "SET satisfaction = #{satisfaction}, satisfaction_remark = #{remark}, "
            + "    updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 3 AND submitter_type = 1 "
            + "AND submitter_id = #{submitterId} AND satisfaction IS NULL AND deleted = 0")
    int rate(@Param("id") Long id,
             @Param("submitterId") Long submitterId,
             @Param("satisfaction") Integer satisfaction,
             @Param("remark") String remark);

    /**
     * 工单统计。响应时长按「创建 → 首次受理」计算；办结率按状态 3 计。
     * 会话接待量属阶段B，本查询只统计工单量。
     */
    @Select("""
            SELECT COUNT(*) AS ticketCount,
                   COALESCE(SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END), 0) AS solvedCount,
                   AVG(CASE WHEN accept_at IS NOT NULL
                            THEN TIMESTAMPDIFF(MINUTE, created_at, accept_at) END) AS avgResponseMinutes,
                   AVG(satisfaction) AS satisfactionAvg
            FROM t_service_ticket
            WHERE deleted = 0
              AND created_at >= #{start}
              AND created_at < #{end}
            """)
    TicketStatsRow stats(@Param("start") LocalDateTime start,
                         @Param("end") LocalDateTime end);

    record TicketStatsRow(Long ticketCount,
                          Long solvedCount,
                          BigDecimal avgResponseMinutes,
                          BigDecimal satisfactionAvg) {
    }
}
