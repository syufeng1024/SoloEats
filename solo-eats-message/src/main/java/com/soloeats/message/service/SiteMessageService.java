package com.soloeats.message.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.message.service.command.SendMessageCommand;
import com.soloeats.message.service.query.SiteMessageQuery;
import com.soloeats.message.service.view.SiteMessageView;
import com.soloeats.message.service.view.UnreadCountView;

public interface SiteMessageService {

    /** 渲染模板 → 落 t_site_message → 交 PushService 下发；返回站内信 ID。 */
    Long send(SendMessageCommand command);

    PageResult<SiteMessageView> page(Integer receiverType, Long receiverId,
                                     SiteMessageQuery query, long pageNum, long pageSize);

    /** 单条详情；非本人消息抛 AUTH_FORBIDDEN(10004)，不存在抛 RESOURCE_NOT_FOUND。 */
    SiteMessageView detail(Long id, Integer receiverType, Long receiverId);

    UnreadCountView unreadCount(Integer receiverType, Long receiverId);

    /** 非本人消息抛 AUTH_FORBIDDEN(10004)；已读消息幂等返回。 */
    void markRead(Long id, Integer receiverType, Long receiverId);

    /** msgType 为 null 表示全部已读；返回实际更新条数。 */
    int markAllRead(Integer receiverType, Long receiverId, Integer msgType);
}
