package com.soloeats.content.service.view;

/**
 * 版本检查结果。
 *
 * <p>hasUpdate / forceUpdate 只由服务端比较「库内最新 versionCode」与 currentCode 得出，
 * 前端传参只作为比较基准，不能直接影响判定。
 */
public record AppVersionCheckResult(
        Long id,
        Integer appType,
        String versionName,
        Integer versionCode,
        String updateDesc,
        String downloadUrl,
        boolean forceUpdate,
        boolean hasUpdate,
        Integer grayPercent
) {

    public static AppVersionCheckResult upToDate(Integer appType) {
        return new AppVersionCheckResult(null, appType, null, null, null, null,
                false, false, null);
    }
}
