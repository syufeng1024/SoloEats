package com.soloeats.service.service.view;

/**
 * 客服工作台坐席统计。
 *
 * <p>口径：
 * <ul>
 *   <li>servedSessions —— status=1 且 agent_id 命中的会话数，可按日期范围过滤；</li>
 *   <li>activeSessions —— status=0 的会话数，agentId 为空时统计全平台；</li>
 *   <li>waitingSessions —— status=0 且 agent_id IS NULL 的待接待队列，与具体坐席无关。</li>
 * </ul>
 */
public record CsStatsView(
        long servedSessions,
        long activeSessions,
        long waitingSessions
) {
}
