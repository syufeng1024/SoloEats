package com.soloeats.content.service.view;

import com.soloeats.content.domain.entity.AppVersion;

import java.time.LocalDateTime;

public record AppVersionView(
        Long id,
        Integer appType,
        String appTypeName,
        String versionName,
        Integer versionCode,
        String updateDesc,
        String downloadUrl,
        Integer forceUpdate,
        Integer grayPercent,
        Integer status,
        LocalDateTime publishAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AppVersionView from(AppVersion version, String appTypeName) {
        return new AppVersionView(
                version.getId(),
                version.getAppType(),
                appTypeName,
                version.getVersionName(),
                version.getVersionCode(),
                version.getUpdateDesc(),
                version.getDownloadUrl(),
                version.getForceUpdate(),
                version.getGrayPercent(),
                version.getStatus(),
                version.getPublishAt(),
                version.getCreatedAt(),
                version.getUpdatedAt()
        );
    }
}
