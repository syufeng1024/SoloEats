package com.soloeats.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.system.domain.entity.SysNotice;
import com.soloeats.system.mapper.SysNoticeMapper;
import com.soloeats.system.service.SysNoticeService;
import com.soloeats.system.service.command.SaveNoticeCommand;
import com.soloeats.system.service.view.NoticeView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysNoticeServiceImpl implements SysNoticeService {

    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_PUBLISHED = 1;
    private static final int STATUS_WITHDRAWN = 2;

    private static final int TARGET_ALL = 1;
    private static final int TARGET_MERCHANT = 3;
    private static final int TARGET_RIDER = 4;

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final long MAX_CLIENT_PAGE_SIZE = 50L;

    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_CONTENT_LENGTH = 1_000_000;

    private final SysNoticeMapper sysNoticeMapper;

    @Override
    public PageResult<NoticeView> page(Integer status,
                                       Integer noticeType,
                                       Integer targetType,
                                       String keyword,
                                       long pageNum,
                                       long pageSize) {
        validateStatus(status, true);
        validateNoticeType(noticeType);
        validateTargetType(targetType);
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = normalizePageSize(pageSize, MAX_PAGE_SIZE);

        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getDeleted, 0)
                .eq(status != null, SysNotice::getStatus, status)
                .eq(noticeType != null, SysNotice::getNoticeType, noticeType)
                .eq(targetType != null, SysNotice::getTargetType, targetType)
                .like(StringUtils.hasText(keyword), SysNotice::getTitle, trim(keyword))
                .orderByDesc(SysNotice::getCreatedAt)
                .orderByDesc(SysNotice::getId);

        Page<SysNotice> page = sysNoticeMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        return PageResult.of(
                page.getRecords().stream().map(this::toView).toList(),
                page.getTotal(),
                normalizedPageNum,
                normalizedPageSize);
    }

    @Override
    public NoticeView create(SaveNoticeCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        LocalDateTime publishAt = command.publishAt();
        LocalDateTime expireAt = command.expireAt();
        validateTimeRange(publishAt, expireAt);

        SysNotice notice = new SysNotice();
        notice.setTitle(requireTitle(command.title()));
        notice.setContent(requireContent(command.content()));
        notice.setNoticeType(requireNoticeType(command.noticeType()));
        notice.setTargetType(requireTargetType(command.targetType()));
        notice.setStatus(STATUS_DRAFT);
        notice.setPublishAt(publishAt);
        notice.setExpireAt(expireAt);
        notice.setReadCount(0);
        notice.setVersion(0);
        notice.setDeleted(0);
        sysNoticeMapper.insert(notice);
        log.info("notice created noticeId={} status=draft", notice.getId());
        return toView(notice);
    }

    @Override
    public NoticeView update(Long id, SaveNoticeCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        SysNotice notice = requireNotice(id);
        if (notice.getStatus() != null && notice.getStatus() == STATUS_PUBLISHED) {
            throw new BizException(ErrorCode.PARAM_INVALID, "已发布公告请先撤回再修改");
        }

        LocalDateTime publishAt = command.publishAt() == null
                ? notice.getPublishAt() : command.publishAt();
        LocalDateTime expireAt = command.expireAt() == null
                ? notice.getExpireAt() : command.expireAt();
        validateTimeRange(publishAt, expireAt);

        notice.setTitle(command.title() == null ? notice.getTitle() : requireTitle(command.title()));
        notice.setContent(command.content() == null
                ? notice.getContent() : requireContent(command.content()));
        notice.setNoticeType(command.noticeType() == null
                ? notice.getNoticeType() : requireNoticeType(command.noticeType()));
        notice.setTargetType(command.targetType() == null
                ? notice.getTargetType() : requireTargetType(command.targetType()));
        notice.setPublishAt(publishAt);
        notice.setExpireAt(expireAt);

        // entity 带 @Version：并发修改返回 0
        if (sysNoticeMapper.updateById(notice) == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "公告已被修改，请刷新后重试");
        }
        log.info("notice updated noticeId={}", notice.getId());
        return toView(notice);
    }

    @Override
    public void publish(Long id, Integer status) {
        if (status == null || (status != STATUS_PUBLISHED && status != STATUS_WITHDRAWN)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 1发布 2撤回");
        }
        SysNotice notice = requireNotice(id);
        LocalDateTime now = LocalDateTime.now();

        if (status == STATUS_PUBLISHED) {
            if (notice.getExpireAt() != null && !notice.getExpireAt().isAfter(now)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "公告已过期，请先调整 expireAt");
            }
            LocalDateTime publishAt = notice.getPublishAt() == null ? now : notice.getPublishAt();
            int updated = sysNoticeMapper.update(null, new LambdaUpdateWrapper<SysNotice>()
                    .eq(SysNotice::getId, id)
                    .eq(SysNotice::getDeleted, 0)
                    .in(SysNotice::getStatus, STATUS_DRAFT, STATUS_WITHDRAWN)
                    .set(SysNotice::getStatus, STATUS_PUBLISHED)
                    .set(SysNotice::getPublishAt, publishAt)
                    // 状态变更也推进版本号，让并发中的草稿编辑因 @Version 失败而不是覆盖已发布内容
                    .setSql("version = version + 1"));
            if (updated == 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "公告当前状态不允许发布");
            }
        } else {
            int updated = sysNoticeMapper.update(null, new LambdaUpdateWrapper<SysNotice>()
                    .eq(SysNotice::getId, id)
                    .eq(SysNotice::getDeleted, 0)
                    .eq(SysNotice::getStatus, STATUS_PUBLISHED)
                    .set(SysNotice::getStatus, STATUS_WITHDRAWN)
                    .setSql("version = version + 1"));
            if (updated == 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "只有已发布公告才能撤回");
            }
        }
        log.info("notice status changed noticeId={} status={}", id, status);
    }

    @Override
    public PageResult<NoticeView> pageForUser(Set<String> roles, long pageNum, long pageSize) {
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = normalizePageSize(pageSize, MAX_CLIENT_PAGE_SIZE);
        LocalDateTime now = LocalDateTime.now();
        List<Integer> targets = resolveTargetTypes(roles);

        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getStatus, STATUS_PUBLISHED)
                .eq(SysNotice::getDeleted, 0)
                .and(w -> w.isNull(SysNotice::getPublishAt).or().le(SysNotice::getPublishAt, now))
                .and(w -> w.isNull(SysNotice::getExpireAt).or().gt(SysNotice::getExpireAt, now))
                .in(targets != null, SysNotice::getTargetType, targets)
                .orderByDesc(SysNotice::getPublishAt)
                .orderByDesc(SysNotice::getId);

        Page<SysNotice> page = sysNoticeMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        return PageResult.of(
                page.getRecords().stream().map(this::toView).toList(),
                page.getTotal(),
                normalizedPageNum,
                normalizedPageSize);
    }

    @Override
    public void markRead(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "公告 ID 必须是正整数");
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = sysNoticeMapper.update(null, new LambdaUpdateWrapper<SysNotice>()
                .eq(SysNotice::getId, id)
                .eq(SysNotice::getStatus, STATUS_PUBLISHED)
                .eq(SysNotice::getDeleted, 0)
                .and(w -> w.isNull(SysNotice::getPublishAt).or().le(SysNotice::getPublishAt, now))
                .and(w -> w.isNull(SysNotice::getExpireAt).or().gt(SysNotice::getExpireAt, now))
                .setSql("read_count = read_count + 1"));
        if (updated == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "公告不存在或已下线");
        }
    }

    /**
     * 平台细角色（ADMIN_*）可见全部目标类型；商家只看全部+商家；骑手只看全部+骑手；C 端只看全部。
     */
    private List<Integer> resolveTargetTypes(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of(TARGET_ALL);
        }
        if (roles.stream().anyMatch(role -> role != null && role.startsWith("ADMIN"))) {
            return null;
        }
        if (roles.stream().anyMatch(role -> role != null && role.startsWith("MERCHANT"))) {
            return List.of(TARGET_ALL, TARGET_MERCHANT);
        }
        if (roles.contains("RIDER")) {
            return List.of(TARGET_ALL, TARGET_RIDER);
        }
        return List.of(TARGET_ALL);
    }

    private SysNotice requireNotice(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "公告 ID 必须是正整数");
        }
        SysNotice notice = sysNoticeMapper.selectOne(new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getId, id)
                .eq(SysNotice::getDeleted, 0)
                .last("LIMIT 1"));
        if (notice == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "公告不存在");
        }
        return notice;
    }

    private void validateTimeRange(LocalDateTime publishAt, LocalDateTime expireAt) {
        if (publishAt != null && expireAt != null && !expireAt.isAfter(publishAt)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "expireAt 必须晚于 publishAt");
        }
    }

    private void validateStatus(Integer status, boolean allowNull) {
        if (status == null) {
            if (allowNull) {
                return;
            }
            throw new BizException(ErrorCode.PARAM_INVALID, "status 不能为空");
        }
        if (status != STATUS_DRAFT && status != STATUS_PUBLISHED && status != STATUS_WITHDRAWN) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0草稿 1已发布 2已撤回");
        }
    }

    private void validateNoticeType(Integer noticeType) {
        if (noticeType != null && noticeType != 1 && noticeType != 2) {
            throw new BizException(ErrorCode.PARAM_INVALID, "noticeType 只能是 1公告 2通知");
        }
    }

    private void validateTargetType(Integer targetType) {
        if (targetType != null && (targetType < TARGET_ALL || targetType > TARGET_RIDER)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "targetType 只能是 1全部 2平台 3商家 4骑手");
        }
    }

    private int requireNoticeType(Integer noticeType) {
        if (noticeType == null) {
            return 1;
        }
        validateNoticeType(noticeType);
        return noticeType;
    }

    private int requireTargetType(Integer targetType) {
        if (targetType == null) {
            return TARGET_ALL;
        }
        validateTargetType(targetType);
        return targetType;
    }

    private String requireTitle(String title) {
        if (!StringUtils.hasText(title)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "title 不能为空");
        }
        String trimmed = title.trim();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "title 长度不能超过 " + MAX_TITLE_LENGTH);
        }
        return trimmed;
    }

    private String requireContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "content 不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "content 长度不能超过 " + MAX_CONTENT_LENGTH);
        }
        return content;
    }

    private long normalizePageSize(long pageSize, long maxPageSize) {
        if (pageSize < 1L) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, maxPageSize);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private NoticeView toView(SysNotice notice) {
        return new NoticeView(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.getNoticeType(),
                notice.getTargetType(),
                notice.getStatus(),
                notice.getPublishAt(),
                notice.getExpireAt(),
                notice.getReadCount(),
                notice.getCreatedAt(),
                notice.getUpdatedAt());
    }
}
