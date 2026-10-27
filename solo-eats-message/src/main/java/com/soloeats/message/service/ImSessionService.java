package com.soloeats.message.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.message.service.view.AgentSessionView;
import com.soloeats.message.service.view.ImMessageView;
import com.soloeats.message.service.view.ImSessionView;

import java.time.LocalDate;
import java.util.List;

/**
 * 用户 ↔ 商家/骑手/客服 的 IM 会话。所有方法的 subjectType/subjectId 必须是
 * 登录态解析出的当前主体，禁止使用请求体里的 userId。
 *
 * <p>subjectType 取值与 {@code ReceiverType} 一致：1用户 2商家 3骑手 4平台（客服）。
 * 存储侧 {@code t_im_session.user_id} 永远是消费者，{@code peer_type/peer_id} 永远是被咨询方。
 */
public interface ImSessionService {

    /**
     * 创建或复用会话（uk_order_peer 幂等）。
     *
     * @param subjectType 当前主体类型 1用户 2商家 3骑手 4平台
     * @param subjectId   当前主体 ID（取 JWT userId，不是 merchantId/riderId）
     * @param peerType    对端类型：当前是用户时为 2/3/4；当前是商家/骑手/客服时必须为 1（对端是消费者）
     * @param peerId      对端 ID；当前是商家/骑手/客服时该值即落库的 user_id
     */
    ImSessionView getOrCreate(Long orderId, Integer subjectType, Long subjectId,
                              Integer peerType, Long peerId);

    /** 当前主体可见的会话（本人已隐藏的不返回），按最近消息时间倒序。 */
    List<ImSessionView> listMine(Integer subjectType, Long subjectId);

    /** 游标分页，id 倒序取一页后反转成正序；pageSize 默认 20、上限 50。 */
    List<ImMessageView> messages(Long sessionId, Integer subjectType, Long subjectId,
                                 Long beforeMessageId, Integer pageSize);

    /**
     * 坐席端会话记录：按 agent_id 判权，不走 peer_id（take/transfer 之后两者不再相等）。
     * 会话不存在或非客服会话抛 90001；已被其他坐席接待抛 90003；
     * agent_id 为空的待接待会话允许任意坐席先读内容再决定是否接待。
     * 分页、排序与 pageSize 归一化与 {@link #messages} 完全一致。
     */
    List<ImMessageView> agentMessages(Long sessionId, Long agentId,
                                      Long beforeMessageId, Integer pageSize);

    /**
     * 发送消息：落消息 → 更新会话摘要与时间 → 对方未读 +1，事务提交后 WS 推送对方。
     * content 最长 2000 字符；status=1/2 抛 90002；非参与者抛 90003。
     */
    ImMessageView send(Long sessionId, Integer subjectType, Long subjectId,
                       Integer msgType, String content, String extraJson);

    /** 清零当前主体一侧的未读数。 */
    void markRead(Long sessionId, Integer subjectType, Long subjectId);

    /** 仅隐藏当前主体一侧的会话，不物理删除，对方不受影响。 */
    void hide(Long sessionId, Integer subjectType, Long subjectId);

    // ------------------------------------------------------------
    // T09 阶段B 客服工作台坐席端（peer_type = 4）
    // 以上 6 个方法签名保持不变；以下为新增能力。
    // ------------------------------------------------------------

    /**
     * 坐席端会话列表：只查 peer_type=4，按 status 升序、最近消息时间倒序。
     *
     * @param status  null 查全部；0进行中 1已结束 2只读
     * @param agentId null 查全部（含未接待）；传值只查该坐席接待的会话
     */
    PageResult<AgentSessionView> listAgentSessions(Integer status, Long agentId,
                                                   long pageNum, long pageSize);

    /**
     * 接待会话：条件更新 {@code agent_id NULL → agentId}，并发抢接只成功一次。
     * 不存在或非客服会话抛 90001；已结束/只读抛 90002；已被他人接待抛 90005。
     */
    void takeSession(Long sessionId, Long agentId);

    /**
     * 转接会话：只改 agent_id，不校验当前接待人（管理员可强制转）。
     * 不存在或非客服会话抛 90001；已结束/只读抛 90002。
     */
    void transferSession(Long sessionId, Long toAgentId, Long operatorId);

    /**
     * 坐席结束会话：status 0→1。不存在或非客服会话抛 90001；已结束/只读抛 90002。
     */
    void closeSessionByAgent(Long sessionId, Long operatorId);

    /**
     * 坐席已结束会话数，dateFrom/dateTo 非空时按 updated_at 过滤（含当天）。
     * agentId 为空时统计全部坐席。
     */
    long countServedSessions(Long agentId, LocalDate dateFrom, LocalDate dateTo);

    /** 进行中会话数（status=0），agentId 为空时统计全部。 */
    long countActiveSessions(Long agentId);

    /** 待接待队列长度（status=0 且 agent_id IS NULL）。 */
    long countWaitingSessions();
}
