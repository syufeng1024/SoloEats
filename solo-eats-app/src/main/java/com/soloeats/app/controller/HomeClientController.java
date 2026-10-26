package com.soloeats.app.controller;

import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.content.service.AppVersionService;
import com.soloeats.content.service.BannerService;
import com.soloeats.content.service.HomePageService;
import com.soloeats.content.service.SearchKeywordService;
import com.soloeats.content.service.view.AppVersionCheckResult;
import com.soloeats.content.service.view.BannerView;
import com.soloeats.content.service.view.HomePageView;
import com.soloeats.content.service.view.SearchSuggestView;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端内容白名单接口：带 token 时识别消费者身份用于投放人群，不带 token 也可访问。
 */
@RestController
@RequestMapping("/api/v1/c")
@RequiredArgsConstructor
public class HomeClientController {

    private static final int MAX_DEVICE_ID_LENGTH = 128;

    private final HomePageService homePageService;
    private final BannerService bannerService;
    private final SearchKeywordService searchKeywordService;
    private final AppVersionService appVersionService;

    @GetMapping("/home")
    public ApiResponse<HomePageView> home(
            @RequestParam(defaultValue = "USER_HOME") String pageCode,
            @RequestParam(required = false) String deviceId,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceIdHeader) {
        return ApiResponse.ok(homePageService.clientHome(
                pageCode, consumerUserId(), resolveDeviceId(deviceId, deviceIdHeader)));
    }

    @GetMapping("/banners")
    public ApiResponse<List<BannerView>> banners(
            @RequestParam(defaultValue = "HOME_TOP") String slotCode) {
        return ApiResponse.ok(bannerService.listClient(slotCode));
    }

    @GetMapping("/search/suggest")
    public ApiResponse<SearchSuggestView> searchSuggest(
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(searchKeywordService.suggest(keyword));
    }

    @GetMapping("/app/version")
    public ApiResponse<AppVersionCheckResult> appVersion(
            @RequestParam Integer appType,
            @RequestParam Integer currentCode,
            @RequestParam(required = false) String deviceId,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceIdHeader) {
        return ApiResponse.ok(appVersionService.check(
                appType, currentCode, consumerUserId(),
                resolveDeviceId(deviceId, deviceIdHeader)));
    }

    /** 仅消费者 token 参与人群/灰度判定；后台或商家 token 一律按匿名处理。 */
    private Long consumerUserId() {
        UserPrincipal principal = AuthContext.current();
        if (principal == null || principal.roles() == null) {
            return null;
        }
        boolean consumer = principal.roles().stream().anyMatch(role ->
                Roles.C_USER.equals(role) || role.startsWith(Roles.C_USER + "_"));
        return consumer ? principal.userId() : null;
    }

    private String resolveDeviceId(String queryValue, String headerValue) {
        String value = StringUtils.hasText(queryValue) ? queryValue : headerValue;
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String cleaned = value.strip().replaceAll("[\\p{Cntrl}]", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > MAX_DEVICE_ID_LENGTH
                ? cleaned.substring(0, MAX_DEVICE_ID_LENGTH) : cleaned;
    }
}
