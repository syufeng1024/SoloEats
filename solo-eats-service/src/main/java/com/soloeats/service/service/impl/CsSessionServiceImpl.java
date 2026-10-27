package com.soloeats.service.service.impl;

import com.soloeats.common.api.PageResult;
import com.soloeats.message.service.ImSessionService;
import com.soloeats.message.service.view.AgentSessionView;
import com.soloeats.service.service.CsSessionService;
import com.soloeats.service.service.view.CsStatsView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 坐席端薄封装：会话状态机与并发控制全部复用 {@link ImSessionService}，
 * 本类只负责把坐席维度需要的三段统计拼成 {@link CsStatsView}。
 */
@Service
@RequiredArgsConstructor
public class CsSessionServiceImpl implements CsSessionService {

    private final ImSessionService imSessionService;

    @Override
    public PageResult<AgentSessionView> listSessions(Integer status, Long agentId,
                                                     long pageNum, long pageSize) {
        return imSessionService.listAgentSessions(status, agentId, pageNum, pageSize);
    }

    @Override
    public void take(Long sessionId, Long agentId) {
        imSessionService.takeSession(sessionId, agentId);
    }

    @Override
    public void transfer(Long sessionId, Long toAgentId, Long operatorId) {
        imSessionService.transferSession(sessionId, toAgentId, operatorId);
    }

    @Override
    public void close(Long sessionId, Long operatorId) {
        imSessionService.closeSessionByAgent(sessionId, operatorId);
    }

    @Override
    public CsStatsView stats(Long agentId, LocalDate dateFrom, LocalDate dateTo) {
        return new CsStatsView(
                imSessionService.countServedSessions(agentId, dateFrom, dateTo),
                imSessionService.countActiveSessions(agentId),
                imSessionService.countWaitingSessions());
    }
}
