package com.soloeats.member.service;

import com.soloeats.member.service.command.ChangePhoneCommand;
import com.soloeats.member.service.command.UpdateProfileCommand;
import com.soloeats.member.service.view.MemberView;

/**
 * C 端个人资料：查询、基础资料修改、手机号换绑、注销申请与冷静期撤销。
 */
public interface MemberProfileService {

    /** 资料查询，phone 固定返回掩码；用户不存在抛 80001。 */
    MemberView getProfile(Long userId);

    /** 只允许修改 nickname / avatarUrl / gender / birthday，其余字段为 null 表示不改。 */
    MemberView updateProfile(Long userId, UpdateProfileCommand command);

    /** 换绑手机号：校验新手机号 scene=3 的验证码后更新密文/哈希/掩码三列。 */
    MemberView changePhone(Long userId, ChangePhoneCommand command);

    /** 注销申请：status 置 2 并记录 cancel_apply_at，不物理删除。 */
    void applyCancel(Long userId);

    /** 冷静期内撤回注销申请：status 恢复 0 并清空 cancel_apply_at。 */
    void cancelCancel(Long userId);
}
