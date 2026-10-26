package com.soloeats.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.content.domain.entity.AppVersion;
import com.soloeats.content.domain.enums.AppType;
import com.soloeats.content.mapper.AppVersionMapper;
import com.soloeats.content.service.AppVersionService;
import com.soloeats.content.service.view.AppVersionCheckResult;
import com.soloeats.content.service.view.AppVersionView;
import com.soloeats.content.support.ContentTexts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppVersionServiceImpl implements AppVersionService {

    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ENABLED = 1;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private final AppVersionMapper versionMapper;

    @Override
    public PageResult<AppVersionView> page(Integer appType, long pageNum, long pageSize) {
        validateAppType(appType, false);
        long normalizedPageNum = normalizePageNum(pageNum);
        long normalizedPageSize = normalizePageSize(pageSize);

        LambdaQueryWrapper<AppVersion> wrapper = new LambdaQueryWrapper<AppVersion>()
                .eq(appType != null, AppVersion::getAppType, appType)
                .orderByDesc(AppVersion::getVersionCode)
                .orderByDesc(AppVersion::getId);

        Page<AppVersion> page = versionMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<AppVersionView> views = page.getRecords().stream()
                .map(item -> AppVersionView.from(item, appTypeName(item.getAppType())))
                .toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SaveVersionCommand command) {
        AppVersion entity = new AppVersion();
        applyCommand(entity, command, null);
        try {
            versionMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.CONTENT_VERSION_EXISTS);
        }
        log.info("app version created id={} appType={} code={}",
                entity.getId(), entity.getAppType(), entity.getVersionCode());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SaveVersionCommand command) {
        AppVersion entity = requireVersion(id);
        applyCommand(entity, command, id);
        try {
            versionMapper.updateById(entity);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.CONTENT_VERSION_EXISTS);
        }
        log.info("app version updated id={} appType={} code={}",
                id, entity.getAppType(), entity.getVersionCode());
    }

    @Override
    public AppVersionCheckResult check(Integer appType, Integer currentCode,
                                       Long userId, String deviceId) {
        validateAppType(appType, true);
        if (currentCode == null || currentCode < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "currentCode 不能为空且不能为负数");
        }

        AppVersion latest = versionMapper.selectLatest(appType, LocalDateTime.now());
        if (latest == null) {
            return AppVersionCheckResult.upToDate(appType);
        }

        // 只信库内 version_code：前端 currentCode 仅作为比较基准。
        boolean versionAhead = latest.getVersionCode() != null
                && latest.getVersionCode() > currentCode;
        boolean hasUpdate = versionAhead
                && grayHit(latest.getGrayPercent(), userId, deviceId);
        boolean forceUpdate = hasUpdate && Objects.equals(latest.getForceUpdate(), 1);

        return new AppVersionCheckResult(
                latest.getId(),
                latest.getAppType(),
                latest.getVersionName(),
                latest.getVersionCode(),
                latest.getUpdateDesc(),
                latest.getDownloadUrl(),
                forceUpdate,
                hasUpdate,
                latest.getGrayPercent()
        );
    }

    /**
     * 灰度判定：
     * 0 不发、100 全量；中间值 userId % 100 &lt; grayPercent。
     * 未登录使用 deviceId 的稳定 hash 取模；两者都没有时不投放（避免匿名流量被误放大）。
     */
    static boolean grayHit(Integer grayPercent, Long userId, String deviceId) {
        int percent = grayPercent == null ? 0 : grayPercent;
        if (percent <= 0) {
            return false;
        }
        if (percent >= 100) {
            return true;
        }
        int bucket;
        if (userId != null && userId > 0L) {
            bucket = (int) Math.floorMod(userId, 100L);
        } else if (StringUtils.hasText(deviceId)) {
            bucket = Math.floorMod(stableHash(deviceId.strip()), 100);
        } else {
            return false;
        }
        return bucket < percent;
    }

    /** FNV-1a，跨 JVM 稳定，避免 String.hashCode 被误认为可变的随机值。 */
    static int stableHash(String value) {
        int hash = 0x811c9dc5;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x01000193;
        }
        return hash;
    }

    private void applyCommand(AppVersion entity, SaveVersionCommand command, Long excludeId) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求参数不能为空");
        }
        validateAppType(command.appType(), true);
        String versionName = ContentTexts.requirePlainText(
                command.versionName(), "versionName", 32);
        if (command.versionCode() == null || command.versionCode() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "versionCode 必须是正整数");
        }
        if (exists(command.appType(), command.versionCode(), excludeId)) {
            throw new BizException(ErrorCode.CONTENT_VERSION_EXISTS);
        }

        entity.setAppType(command.appType());
        entity.setVersionName(versionName);
        entity.setVersionCode(command.versionCode());
        entity.setUpdateDesc(ContentTexts.optionalMultilineText(
                command.updateDesc(), "updateDesc", 1000));
        entity.setDownloadUrl(ContentTexts.requireUrl(
                command.downloadUrl(), "downloadUrl", 512));

        int forceUpdate = command.forceUpdate() == null ? 0 : command.forceUpdate();
        if (forceUpdate != 0 && forceUpdate != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "forceUpdate 只能是 0 或 1");
        }
        entity.setForceUpdate(forceUpdate);

        int grayPercent = command.grayPercent() == null ? 0 : command.grayPercent();
        if (grayPercent < 0 || grayPercent > 100) {
            throw new BizException(ErrorCode.PARAM_INVALID, "grayPercent 必须在 0~100 之间");
        }
        entity.setGrayPercent(grayPercent);

        int status = command.status() == null ? STATUS_ENABLED : command.status();
        if (status != STATUS_DISABLED && status != STATUS_ENABLED) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0停用 / 1启用");
        }
        entity.setStatus(status);
        entity.setPublishAt(command.publishAt());
    }

    private boolean exists(Integer appType, Integer versionCode, Long excludeId) {
        LambdaQueryWrapper<AppVersion> wrapper = new LambdaQueryWrapper<AppVersion>()
                .eq(AppVersion::getAppType, appType)
                .eq(AppVersion::getVersionCode, versionCode)
                .ne(excludeId != null, AppVersion::getId, excludeId);
        Long count = versionMapper.selectCount(wrapper);
        return count != null && count > 0L;
    }

    private AppVersion requireVersion(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "版本 ID 不能为空");
        }
        AppVersion version = versionMapper.selectById(id);
        if (version == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "版本不存在");
        }
        return version;
    }

    private static void validateAppType(Integer appType, boolean required) {
        if (appType == null) {
            if (required) {
                throw new BizException(ErrorCode.PARAM_INVALID, "appType 不能为空");
            }
            return;
        }
        if (!AppType.isValid(appType)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "appType 只能是 1用户端 / 2骑手端 / 3商家端");
        }
    }

    private static String appTypeName(Integer code) {
        AppType type = AppType.fromCode(code);
        return type == null ? null : type.getLabel();
    }

    private static long normalizePageNum(long pageNum) {
        return pageNum < 1L ? 1L : pageNum;
    }

    private static long normalizePageSize(long pageSize) {
        if (pageSize < 1L) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
