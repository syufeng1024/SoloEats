package com.soloeats.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 绑定邀请关系请求。邀请码长度上限与 t_invite_record.invite_code 一致。
 */
public record InviteBindRequest(
        @NotBlank(message = "inviteCode 不能为空")
        @Size(max = 16, message = "inviteCode 不能超过 16 个字符")
        String inviteCode
) {
}
