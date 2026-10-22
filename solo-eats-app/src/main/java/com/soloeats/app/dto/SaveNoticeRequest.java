package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record SaveNoticeRequest(
        @NotBlank(message = "title 不能为空")
        @Size(max = 128, message = "title 长度不能超过 128")
        String title,

        @NotBlank(message = "content 不能为空")
        String content,

        @Min(value = 1, message = "noticeType 只能是 1公告 2通知")
        @Max(value = 2, message = "noticeType 只能是 1公告 2通知")
        Integer noticeType,

        @Min(value = 1, message = "targetType 只能是 1全部 2平台 3商家 4骑手")
        @Max(value = 4, message = "targetType 只能是 1全部 2平台 3商家 4骑手")
        Integer targetType,

        LocalDateTime publishAt,

        LocalDateTime expireAt
) {
}
