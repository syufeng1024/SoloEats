package com.soloeats.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.content.domain.entity.Banner;
import com.soloeats.content.domain.enums.JumpType;
import com.soloeats.content.mapper.BannerMapper;
import com.soloeats.content.service.BannerService;
import com.soloeats.content.service.command.SaveBannerCommand;
import com.soloeats.content.service.view.BannerView;
import com.soloeats.content.support.ContentTexts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private static final Set<String> SLOTS = Set.of("HOME_TOP", "HOME_POPUP", "SPLASH", "SHOP_TOP");
    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ENABLED = 1;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    /** Redis 曝光计数键；值在定时任务刷回后删除。 */
    static final String VIEW_KEY_PREFIX = "content:banner:view:";
    static final String VIEW_ID_SET = "content:banner:view:ids";

    private final BannerMapper bannerMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public PageResult<BannerView> page(String slotCode, Integer status, long pageNum, long pageSize) {
        validateStatus(status);
        long normalizedPageNum = normalizePageNum(pageNum);
        long normalizedPageSize = normalizePageSize(pageSize);

        LambdaQueryWrapper<Banner> wrapper = new LambdaQueryWrapper<Banner>()
                .eq(StringUtils.hasText(slotCode), Banner::getSlotCode, normalizeSlot(slotCode, false))
                .eq(status != null, Banner::getStatus, status)
                .orderByAsc(Banner::getSort)
                .orderByDesc(Banner::getId);

        Page<Banner> page = bannerMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        return PageResult.of(page.getRecords().stream().map(BannerView::from).toList(),
                page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public BannerView detail(Long id) {
        return BannerView.from(requireBanner(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SaveBannerCommand command) {
        Banner banner = new Banner();
        applyCommand(banner, command);
        banner.setClickCount(0L);
        banner.setViewCount(0L);
        bannerMapper.insert(banner);
        log.info("banner created id={} slot={}", banner.getId(), banner.getSlotCode());
        return banner.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SaveBannerCommand command) {
        Banner banner = requireBanner(id);
        applyCommand(banner, command);
        bannerMapper.updateById(banner);
        log.info("banner updated id={} slot={}", id, banner.getSlotCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireBanner(id);
        if (bannerMapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "广告不存在");
        }
        redisTemplate.delete(VIEW_KEY_PREFIX + id);
        redisTemplate.opsForSet().remove(VIEW_ID_SET, String.valueOf(id));
        log.info("banner deleted id={}", id);
    }

    @Override
    public List<BannerView> listClient(String slotCode) {
        String slot = normalizeSlot(slotCode, true);
        List<Banner> banners = bannerMapper.selectActiveBySlot(slot, LocalDateTime.now());
        banners.forEach(this::recordView);
        return banners.stream().map(BannerView::from).toList();
    }

    @Override
    public void flushViewCounts() {
        try {
            Set<String> ids = redisTemplate.opsForSet().members(VIEW_ID_SET);
            if (ids == null || ids.isEmpty()) {
                return;
            }
            for (String idText : ids) {
                long bannerId;
                try {
                    bannerId = Long.parseLong(idText);
                } catch (NumberFormatException ex) {
                    redisTemplate.opsForSet().remove(VIEW_ID_SET, idText);
                    continue;
                }
                String value = redisTemplate.opsForValue().getAndDelete(VIEW_KEY_PREFIX + bannerId);
                long delta = parseDelta(value);
                if (delta > 0) {
                    bannerMapper.increaseViewCount(bannerId, delta);
                }
                // 不把 id 移出集合：刷回期间并发 +1 会重建计数键，移出会导致该键再也不会被扫到。
                // 集合大小以「有过曝光的 Banner 数」为上限，下一轮 getAndDelete 为空即为 no-op；
                // Banner 删除时由 delete() 清理集合成员与计数键。
            }
        } catch (RuntimeException ex) {
            log.error("flush banner view counts failed", ex);
        }
    }

    /** 曝光只进 Redis，接口不等待数据库写入；Redis 故障不影响 Banner 下发。 */
    private void recordView(Banner banner) {
        if (banner.getId() == null) {
            return;
        }
        try {
            String id = String.valueOf(banner.getId());
            redisTemplate.opsForValue().increment(VIEW_KEY_PREFIX + id);
            redisTemplate.opsForSet().add(VIEW_ID_SET, id);
        } catch (RuntimeException ex) {
            log.warn("record banner view failed bannerId={} reason={}",
                    banner.getId(), ex.getMessage());
        }
    }

    private void applyCommand(Banner banner, SaveBannerCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求参数不能为空");
        }
        banner.setSlotCode(normalizeSlot(command.slotCode(), true));
        banner.setTitle(ContentTexts.requirePlainText(command.title(), "title", 128));
        banner.setImageUrl(ContentTexts.requireImageUrl(command.imageUrl(), "imageUrl"));

        JumpType jumpType = JumpType.fromCode(command.jumpType());
        if (jumpType == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "jumpType 只能是 1~5");
        }
        banner.setJumpType(jumpType.getCode());
        if (jumpType == JumpType.NONE) {
            banner.setJumpValue(null);
        } else {
            banner.setJumpValue(ContentTexts.requirePlainText(command.jumpValue(), "jumpValue", 255));
        }

        banner.setSort(command.sort() == null ? 0 : command.sort());
        LocalDateTime from = command.effectiveFrom() == null
                ? LocalDateTime.now() : command.effectiveFrom();
        if (command.effectiveTo() != null && !command.effectiveTo().isAfter(from)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "effectiveTo 必须晚于 effectiveFrom");
        }
        banner.setEffectiveFrom(from);
        banner.setEffectiveTo(command.effectiveTo());

        int status = command.status() == null ? STATUS_ENABLED : command.status();
        validateStatus(status);
        banner.setStatus(status);
    }

    private Banner requireBanner(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "广告 ID 不能为空");
        }
        Banner banner = bannerMapper.selectById(id);
        if (banner == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "广告不存在");
        }
        return banner;
    }

    private static String normalizeSlot(String slotCode, boolean required) {
        if (!StringUtils.hasText(slotCode)) {
            if (required) {
                throw new BizException(ErrorCode.PARAM_INVALID, "slotCode 不能为空");
            }
            return null;
        }
        String slot = slotCode.strip().toUpperCase(Locale.ROOT);
        if (!SLOTS.contains(slot)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "slotCode 只能是 HOME_TOP/HOME_POPUP/SPLASH/SHOP_TOP");
        }
        return slot;
    }

    private static void validateStatus(Integer status) {
        if (status != null && status != STATUS_DISABLED && status != STATUS_ENABLED) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0停用 / 1启用");
        }
    }

    private static long parseDelta(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
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
