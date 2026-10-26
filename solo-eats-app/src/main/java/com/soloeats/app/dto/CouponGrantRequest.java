package com.soloeats.app.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 定向发券请求。userIds 为 C 端用户业务 ID（t_member_user.id），重复项在服务层去重，
 * 去重后超过 500 人整批拒绝。
 */
public record CouponGrantRequest(
        @NotEmpty(message = "userIds 不能为空")
        @Size(max = 500, message = "单次发券不能超过 500 人")
        List<@NotNull(message = "userId 不能为空") Long> userIds
) {
}
