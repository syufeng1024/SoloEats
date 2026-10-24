package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SubmitCertificationRequest(
        @NotBlank(message = "真实姓名不能为空")
        @Size(max = 64, message = "真实姓名不能超过 64 个字符")
        String realName,

        @NotBlank(message = "身份证号不能为空")
        String idCard,

        Long idCardFrontFileId,

        Long idCardBackFileId,

        Long healthCertFileId,

        @NotNull(message = "健康证到期日不能为空")
        LocalDate healthCertExpireAt,

        @NotNull(message = "车型不能为空")
        @Min(value = 1, message = "车型只能是 1~4")
        @Max(value = 4, message = "车型只能是 1~4")
        Integer vehicleType
) {
}
