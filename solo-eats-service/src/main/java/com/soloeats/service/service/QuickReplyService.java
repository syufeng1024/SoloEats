package com.soloeats.service.service;

import com.soloeats.service.service.command.QuickReplyCommand;
import com.soloeats.service.service.view.QuickReplyView;

import java.util.List;

public interface QuickReplyService {

    /**
     * 后台话术列表：只返回未删除记录，按 sort 升序、id 倒序；category 非空时精确匹配。
     * 停用（status=1）的也返回，便于后台维护，坐席发送时由前端按 status 过滤。
     */
    List<QuickReplyView> list(String category);

    Long create(QuickReplyCommand command);

    void update(Long id, QuickReplyCommand command);

    /** 逻辑删除（deleted = 1），记录保留以便追溯。 */
    void delete(Long id);
}
