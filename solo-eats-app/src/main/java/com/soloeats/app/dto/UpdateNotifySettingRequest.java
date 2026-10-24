package com.soloeats.app.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateNotifySettingRequest(
        @NotNull(message = "通知类型不能为空") Integer notifyType,
        @NotNull(message = "开关状态不能为空") Boolean enabled
) {
}
