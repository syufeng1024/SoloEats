package com.soloeats.member.service;

import com.soloeats.member.service.command.SaveTagCommand;
import com.soloeats.member.service.view.TagView;

import java.util.List;

/**
 * 后台用户标签分群：标签定义维护 + 手动打标。
 * 自动打标（高频/沉默/新客）当前只保留 auto_tag 字段，规则执行留 TODO。
 */
public interface MemberTagService {

    /** 标签列表，含每个标签的绑定人数。 */
    List<TagView> list();

    /** 新增标签；名称重复抛 10001。 */
    TagView create(SaveTagCommand command);

    /** 修改标签（含启停）；标签不存在抛 10002。 */
    TagView update(Long id, SaveTagCommand command);

    /** 全量覆盖用户标签：先按 user_id 删关联再批量插入，同事务；用户不存在抛 80001。 */
    void assignTags(Long userId, List<Long> tagIds);
}
