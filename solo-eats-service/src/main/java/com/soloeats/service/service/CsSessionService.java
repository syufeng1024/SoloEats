package com.soloeats.service.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.message.service.view.AgentSessionView;
import com.soloeats.service.service.view.CsStatsView;

import java.time.LocalDate;

/**
 * 客服工作台坐席端编排。会话存储与状态迁移在 solo-eats-message 的 ImSessionService，
 * 本接口只做坐席端口径的组装，不新增第二套会话表。
 */
public interface CsSessionService {

    PageResult<AgentSessionView> listSessions(Integer status, Long agentId,
                                              long pageNum, long pageSize);

    void take(Long sessionId, Long agentId);

    void transfer(Long sessionId, Long toAgentId, Long operatorId);

    void close(Long sessionId, Long operatorId);

    /** agentId 为空时统计全平台；dateFrom/dateTo 为空时不过滤时间。 */
    CsStatsView stats(Long agentId, LocalDate dateFrom, LocalDate dateTo);
}
