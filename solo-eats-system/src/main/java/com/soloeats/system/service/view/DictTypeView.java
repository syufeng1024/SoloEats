package com.soloeats.system.service.view;

import java.time.LocalDateTime;

public record DictTypeView(
        Long id,
        String dictType,
        String dictName,
        Integer status,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
