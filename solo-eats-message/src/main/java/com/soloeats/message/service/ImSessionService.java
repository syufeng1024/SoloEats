package com.soloeats.message.service;

import com.soloeats.message.service.view.ImMessageView;
import com.soloeats.message.service.view.ImSessionView;

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
     * 发送消息：落消息 → 更新会话摘要与时间 → 对方未读 +1，事务提交后 WS 推送对方。
     * content 最长 2000 字符；status=1/2 抛 90002；非参与者抛 90003。
     */
    ImMessageView send(Long sessionId, Integer subjectType, Long subjectId,
                       Integer msgType, String content, String extraJson);

    /** 清零当前主体一侧的未读数。 */
    void markRead(Long sessionId, Integer subjectType, Long subjectId);

    /** 仅隐藏当前主体一侧的会话，不物理删除，对方不受影响。 */
    void hide(Long sessionId, Integer subjectType, Long subjectId);
}
