package com.soloeats.content.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.content.service.view.AppVersionCheckResult;
import com.soloeats.content.service.view.AppVersionView;

import java.time.LocalDateTime;

public interface AppVersionService {

    PageResult<AppVersionView> page(Integer appType, long pageNum, long pageSize);

    Long create(SaveVersionCommand command);

    void update(Long id, SaveVersionCommand command);

    /**
     * 版本检查。hasUpdate 必须由库内最新 versionCode 与 currentCode 比较得出；
     * 灰度按 userId（未登录按 deviceId hash）取模判定。
     */
    AppVersionCheckResult check(Integer appType, Integer currentCode, Long userId, String deviceId);

    /** 版本维护入参。 */
    record SaveVersionCommand(
            Integer appType,
            String versionName,
            Integer versionCode,
            String updateDesc,
            String downloadUrl,
            Integer forceUpdate,
            Integer grayPercent,
            Integer status,
            LocalDateTime publishAt
    ) {
    }
}
