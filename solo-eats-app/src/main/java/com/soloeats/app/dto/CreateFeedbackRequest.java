package com.soloeats.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateFeedbackRequest(
        @NotBlank(message = "content 不能为空")
        @Size(max = 1000, message = "content 最长 1000 个字符")
        String content,
        @Size(max = 9, message = "图片最多 9 张")
        List<@Size(max = 512, message = "图片地址最长 512 个字符") String> images,
        @Size(max = 64, message = "contact 最长 64 个字符")
        String contact
) {
}
