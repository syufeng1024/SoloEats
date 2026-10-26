package com.soloeats.service.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.service.service.command.CreateTicketCommand;
import com.soloeats.service.service.query.TicketQuery;
import com.soloeats.service.service.view.TicketCreatedView;
import com.soloeats.service.service.view.TicketDetailView;
import com.soloeats.service.service.view.TicketStatsView;
import com.soloeats.service.service.view.TicketView;

import java.time.LocalDate;

/**
 * 工单主干。状态迁移：0待受理 → 1处理中 → 2待回访 → 3已办结；0/1/2 → 4已关闭。
 *
 * <p>所有迁移使用 {@code WHERE id = ? AND status = ?} 条件更新，影响行数为 0 时
 * 工单不存在抛 90010，状态不允许抛 90011；受理抢锁失败且已处理中抛 90013。
 */
public interface TicketService {

    TicketCreatedView createTicket(CreateTicketCommand command);

    /** C 端我的工单，只返回 submitter_type = 1 且 submitter_id = userId 的记录。 */
    PageResult<TicketView> pageMine(Long userId, Integer status, long pageNum, long pageSize);

    /** 越权校验：submitter_id 与当前 userId 不一致抛 90012。 */
    TicketDetailView detailForConsumer(Long ticketId, Long userId);

    /** 用户撤单：仅待受理（status=0）。 */
    void closeByConsumer(Long ticketId, Long userId);

    /** 满意度：仅已办结（status=3）可评一次，重复抛 90011。 */
    void rateByConsumer(Long ticketId, Long userId, Integer satisfaction, String remark);

    PageResult<TicketView> pageAdmin(TicketQuery query, long pageNum, long pageSize);

    TicketDetailView detailForAdmin(Long ticketId);

    /** 受理 0→1，按 priority 设置 deadline_at，并发只有一个请求成功。 */
    void accept(Long ticketId, Long operatorId, String operatorName);

    /** 指派/转派，终态工单拒绝。 */
    void assign(Long ticketId, Long assigneeId, Long operatorId, String operatorName);

    /** 处理记录，状态保持 1→1。 */
    void handle(Long ticketId, String content, Long operatorId, String operatorName);

    /** 进入待回访 1→2。 */
    void callback(Long ticketId, String content, String result,
                  Long operatorId, String operatorName);

    /** 办结 2→3。 */
    void finish(Long ticketId, Long operatorId, String operatorName);

    /** 关闭 0/1/2→4。 */
    void close(Long ticketId, String content, Long operatorId, String operatorName);

    /** 日期闭区间统计；dateFrom/dateTo 必填。 */
    TicketStatsView stats(LocalDate dateFrom, LocalDate dateTo);
}
