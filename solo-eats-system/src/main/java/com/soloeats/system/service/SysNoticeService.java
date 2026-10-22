package com.soloeats.system.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.system.service.command.SaveNoticeCommand;
import com.soloeats.system.service.view.NoticeView;

import java.util.Set;

/**
 * 公告：后台维护 + 发布/撤回 + 用户端可见列表 + 已读计数（P0 只维护 read_count）。
 */
public interface SysNoticeService {

    PageResult<NoticeView> page(Integer status,
                                Integer noticeType,
                                Integer targetType,
                                String keyword,
                                long pageNum,
                                long pageSize);

    NoticeView create(SaveNoticeCommand command);

    NoticeView update(Long id, SaveNoticeCommand command);

    /**
     * @param status 1 发布 2 撤回；用条件更新保证状态流转
     */
    void publish(Long id, Integer status);

    /**
     * 用户端可见：已发布、到达发布时间、未过期，并按角色过滤 target_type。
     */
    PageResult<NoticeView> pageForUser(Set<String> roles, long pageNum, long pageSize);

    /**
     * 标记已读：read_count 自增。P0 没有用户级明细，重复调用会重复计数。
     */
    void markRead(Long id);
}
