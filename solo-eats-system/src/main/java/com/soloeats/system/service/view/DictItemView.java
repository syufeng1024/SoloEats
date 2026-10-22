package com.soloeats.system.service.view;

import java.time.LocalDateTime;

public record DictItemView(
        Long id,
        String dictType,
        String dictLabel,
        String dictValue,
        Integer dictSort,
        String cssClass,
        Integer isDefault,
        Integer status,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
