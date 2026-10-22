package com.soloeats.app.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveAddonTemplateRequest(
        @NotBlank(message = "groupName 不能为空")
        @Size(max = 64, message = "groupName 长度不能超过 64")
        String groupName,
        Integer minSelect,
        Integer maxSelect,
        Integer sort,
        @NotEmpty(message = "items 不能为空")
        @Valid
        List<Item> items
) {

    public record Item(
            @NotBlank(message = "itemName 不能为空")
            @Size(max = 64, message = "itemName 长度不能超过 64")
            String itemName,
            Long price,
            Integer stock,
            Integer sort,
            Integer status
    ) {
    }
}
