package com.soloeats.system.service.command;

import java.time.LocalDateTime;

/**
 * 公告保存命令。status 不在此处传入，草稿 → 发布/撤回统一走 publish 接口的条件更新。
 */
public record SaveNoticeCommand(
        String title,
        String content,
        Integer noticeType,
        Integer targetType,
        LocalDateTime publishAt,
        LocalDateTime expireAt
) {
}
