package com.soloeats.message.service.view;

/**
 * 通知开关项。无数据库记录时 enabled=true（默认开启）。
 */
public record NotifySettingView(
        Integer notifyType,
        String notifyTypeName,
        boolean enabled
) {
}
