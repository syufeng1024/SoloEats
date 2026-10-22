package com.soloeats.system.service.view;

import java.time.LocalDateTime;

public record PostView(
        Long id,
        String postCode,
        String postName,
        Integer sort,
        Integer status,
        String remark,
        LocalDateTime createdAt
) {
}
