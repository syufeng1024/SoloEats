package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 门店账号创建。accountRole：1店长 2店员；密码明文只在传输层出现。
 */
public record CreateShopAccountRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_.@-]{3,64}$", message = "用户名格式不正确")
        String username,
        @NotBlank @Size(min = 8, max = 64) String password,
        @Size(max = 64) String nickname,
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确") String phone,
        @NotNull @Min(1) @Max(2) Integer accountRole
) {
}
