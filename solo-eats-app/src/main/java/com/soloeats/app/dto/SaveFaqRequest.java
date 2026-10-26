package com.soloeats.app.dto;

import com.soloeats.service.service.command.FaqCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveFaqRequest(
        @NotBlank(message = "category 不能为空")
        @Size(max = 64, message = "category 最长 64 个字符")
        String category,
        @NotBlank(message = "question 不能为空")
        @Size(max = 200, message = "question 最长 200 个字符")
        String question,
        @NotBlank(message = "answer 不能为空")
        String answer,
        Integer sort,
        @Min(value = 0, message = "status 只能是 0启用 / 1停用")
        @Max(value = 1, message = "status 只能是 0启用 / 1停用")
        Integer status
) {

    public FaqCommand toCommand() {
        return new FaqCommand(category, question, answer, sort, status);
    }
}
