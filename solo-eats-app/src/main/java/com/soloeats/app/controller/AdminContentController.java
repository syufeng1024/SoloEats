package com.soloeats.app.controller;

import com.soloeats.app.dto.PublishHomePageRequest;
import com.soloeats.app.dto.SaveAppVersionRequest;
import com.soloeats.app.dto.SaveBannerRequest;
import com.soloeats.app.dto.SaveHomeFloorRequest;
import com.soloeats.app.dto.SaveSearchKeywordRequest;
import com.soloeats.app.dto.SortHomeFloorsRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.content.service.AppVersionService;
import com.soloeats.content.service.BannerService;
import com.soloeats.content.service.HomePageService;
import com.soloeats.content.service.SearchKeywordService;
import com.soloeats.content.service.command.SaveBannerCommand;
import com.soloeats.content.service.command.SaveFloorCommand;
import com.soloeats.content.service.view.AppVersionView;
import com.soloeats.content.service.view.BannerView;
import com.soloeats.content.service.view.HomePageView;
import com.soloeats.content.service.view.SearchKeywordView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内容运营后台。权限码由 V26 菜单 133~141 种入，role_id=1 已授予。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminContentController {

    private final HomePageService homePageService;
    private final BannerService bannerService;
    private final SearchKeywordService searchKeywordService;
    private final AppVersionService appVersionService;

    // ------------------------------------------------------------ 首页装修

    @RequirePermission("content:home:list")
    @GetMapping("/content/home")
    public ApiResponse<HomePageView> home(
            @RequestParam(defaultValue = "USER_HOME") String pageCode) {
        return ApiResponse.ok(homePageService.adminDetail(pageCode));
    }

    @RequirePermission("content:home:list")
    @GetMapping("/content/home/preview")
    public ApiResponse<HomePageView> preview(
            @RequestParam(defaultValue = "USER_HOME") String pageCode) {
        return ApiResponse.ok(homePageService.preview(pageCode));
    }

    @RequirePermission("content:home:update")
    @Idempotent(ttlSeconds = 60)
    @OperationLog(module = "首页装修", action = "新增楼层")
    @PostMapping("/content/home/floors")
    public ApiResponse<Long> createFloor(@Valid @RequestBody SaveHomeFloorRequest request) {
        return ApiResponse.ok(homePageService.createFloor(toFloorCommand(request)));
    }

    @RequirePermission("content:home:update")
    @OperationLog(module = "首页装修", action = "修改楼层")
    @PutMapping("/content/home/floors/{id}")
    public ApiResponse<Void> updateFloor(
            @PathVariable Long id,
            @Valid @RequestBody SaveHomeFloorRequest request) {
        homePageService.updateFloor(id, toFloorCommand(request));
        return ApiResponse.ok();
    }

    @RequirePermission("content:home:update")
    @OperationLog(module = "首页装修", action = "删除楼层")
    @DeleteMapping("/content/home/floors/{id}")
    public ApiResponse<Void> deleteFloor(@PathVariable Long id) {
        homePageService.deleteFloor(id);
        return ApiResponse.ok();
    }

    @RequirePermission("content:home:update")
    @OperationLog(module = "首页装修", action = "楼层排序")
    @PutMapping("/content/home/floors/sort")
    public ApiResponse<Void> sortFloors(@Valid @RequestBody SortHomeFloorsRequest request) {
        homePageService.sortFloors(request.ids());
        return ApiResponse.ok();
    }

    @RequirePermission("content:home:publish")
    @Idempotent(ttlSeconds = 30)
    @OperationLog(module = "首页装修", action = "发布")
    @PostMapping("/content/home/publish")
    public ApiResponse<Void> publish(@Valid @RequestBody PublishHomePageRequest request) {
        homePageService.publish(request.pageCode());
        return ApiResponse.ok();
    }

    // ------------------------------------------------------------ 广告位

    @RequirePermission("content:banner:list")
    @GetMapping("/banners")
    public ApiResponse<PageResult<BannerView>> banners(
            @RequestParam(required = false) String slotCode,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(bannerService.page(slotCode, status, pageNum, pageSize));
    }

    @RequirePermission("content:banner:list")
    @GetMapping("/banners/{id}")
    public ApiResponse<BannerView> banner(@PathVariable Long id) {
        return ApiResponse.ok(bannerService.detail(id));
    }

    @RequirePermission("content:banner:save")
    @Idempotent(ttlSeconds = 60)
    @OperationLog(module = "广告位管理", action = "新增")
    @PostMapping("/banners")
    public ApiResponse<Long> createBanner(@Valid @RequestBody SaveBannerRequest request) {
        return ApiResponse.ok(bannerService.create(toBannerCommand(request)));
    }

    @RequirePermission("content:banner:save")
    @OperationLog(module = "广告位管理", action = "修改")
    @PutMapping("/banners/{id}")
    public ApiResponse<Void> updateBanner(
            @PathVariable Long id,
            @Valid @RequestBody SaveBannerRequest request) {
        bannerService.update(id, toBannerCommand(request));
        return ApiResponse.ok();
    }

    @RequirePermission("content:banner:save")
    @OperationLog(module = "广告位管理", action = "删除")
    @DeleteMapping("/banners/{id}")
    public ApiResponse<Void> deleteBanner(@PathVariable Long id) {
        bannerService.delete(id);
        return ApiResponse.ok();
    }

    // ------------------------------------------------------------ 搜索运营

    @RequirePermission("content:search:list")
    @GetMapping("/search-keywords")
    public ApiResponse<PageResult<SearchKeywordView>> keywords(
            @RequestParam(required = false) Integer keywordType,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(searchKeywordService.page(
                keywordType, pageNum, pageSize));
    }

    @RequirePermission("content:search:save")
    @Idempotent(ttlSeconds = 60)
    @OperationLog(module = "搜索运营", action = "新增关键词")
    @PostMapping("/search-keywords")
    public ApiResponse<Long> createKeyword(
            @Valid @RequestBody SaveSearchKeywordRequest request) {
        return ApiResponse.ok(searchKeywordService.create(
                request.keyword(), request.keywordType(),
                request.weight(), request.status()));
    }

    @RequirePermission("content:search:save")
    @OperationLog(module = "搜索运营", action = "修改关键词")
    @PutMapping("/search-keywords/{id}")
    public ApiResponse<Void> updateKeyword(
            @PathVariable Long id,
            @Valid @RequestBody SaveSearchKeywordRequest request) {
        searchKeywordService.update(id, request.keyword(), request.keywordType(),
                request.weight(), request.status());
        return ApiResponse.ok();
    }

    @RequirePermission("content:search:save")
    @OperationLog(module = "搜索运营", action = "删除关键词")
    @DeleteMapping("/search-keywords/{id}")
    public ApiResponse<Void> deleteKeyword(@PathVariable Long id) {
        searchKeywordService.delete(id);
        return ApiResponse.ok();
    }

    // ------------------------------------------------------------ App 版本

    @RequirePermission("content:version:list")
    @GetMapping("/app-versions")
    public ApiResponse<PageResult<AppVersionView>> appVersions(
            @RequestParam(required = false) Integer appType,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(appVersionService.page(appType, pageNum, pageSize));
    }

    @RequirePermission("content:version:save")
    @Idempotent(ttlSeconds = 60)
    @OperationLog(module = "App版本", action = "新增")
    @PostMapping("/app-versions")
    public ApiResponse<Long> createAppVersion(
            @Valid @RequestBody SaveAppVersionRequest request) {
        return ApiResponse.ok(appVersionService.create(toVersionCommand(request)));
    }

    @RequirePermission("content:version:save")
    @OperationLog(module = "App版本", action = "修改")
    @PutMapping("/app-versions/{id}")
    public ApiResponse<Void> updateAppVersion(
            @PathVariable Long id,
            @Valid @RequestBody SaveAppVersionRequest request) {
        appVersionService.update(id, toVersionCommand(request));
        return ApiResponse.ok();
    }

    private SaveFloorCommand toFloorCommand(SaveHomeFloorRequest request) {
        return new SaveFloorCommand(
                request.pageCode(),
                request.floorType(),
                request.title(),
                request.config(),
                request.sort(),
                request.effectiveFrom(),
                request.effectiveTo(),
                request.audience(),
                request.audienceConfig(),
                request.status()
        );
    }

    private SaveBannerCommand toBannerCommand(SaveBannerRequest request) {
        return new SaveBannerCommand(
                request.slotCode(),
                request.title(),
                request.imageUrl(),
                request.jumpType(),
                request.jumpValue(),
                request.sort(),
                request.effectiveFrom(),
                request.effectiveTo(),
                request.status()
        );
    }

    private AppVersionService.SaveVersionCommand toVersionCommand(SaveAppVersionRequest request) {
        return new AppVersionService.SaveVersionCommand(
                request.appType(),
                request.versionName(),
                request.versionCode(),
                request.updateDesc(),
                request.downloadUrl(),
                request.forceUpdate(),
                request.grayPercent(),
                request.status(),
                request.publishAt()
        );
    }
}
