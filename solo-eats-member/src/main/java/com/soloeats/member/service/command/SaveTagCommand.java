package com.soloeats.member.service.command;

/**
 * 新增 / 修改用户标签。autoFlag 0 手动 1 自动，status 0 启用 1 停用。
 */
public record SaveTagCommand(
        String name,
        String ruleDesc,
        Integer autoFlag,
        Integer status
) {
}
