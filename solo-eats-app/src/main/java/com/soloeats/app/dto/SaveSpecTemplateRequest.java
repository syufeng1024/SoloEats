package com.soloeats.app.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveSpecTemplateRequest(
        @NotBlank(message = "groupName 不能为空")
        @Size(max = 64, message = "groupName 长度不能超过 64")
        String groupName,
        Integer groupType,
        Integer isRequired,
        Integer sort,
        @NotEmpty(message = "options 不能为空")
        @Valid
        List<Option> options
) {

    public record Option(
            @NotBlank(message = "optionName 不能为空")
            @Size(max = 64, message = "optionName 长度不能超过 64")
            String optionName,
            Long priceDelta,
            Integer sort,
            Integer status
    ) {
    }
}
