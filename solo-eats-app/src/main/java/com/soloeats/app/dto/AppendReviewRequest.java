package com.soloeats.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AppendReviewRequest(
        @NotBlank(message = "content 不能为空")
        @Size(max = 500, message = "content 长度不能超过 500")
        String content,
        List<String> images
) {
}
