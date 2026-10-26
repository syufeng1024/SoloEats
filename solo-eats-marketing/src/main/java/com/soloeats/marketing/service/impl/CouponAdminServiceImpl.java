package com.soloeats.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.domain.entity.CouponTemplate;
import com.soloeats.marketing.domain.entity.UserCoupon;
import com.soloeats.marketing.mapper.CouponTemplateMapper;
import com.soloeats.marketing.mapper.UserCouponMapper;
import com.soloeats.marketing.service.CouponAdminService;
import com.soloeats.marketing.service.command.CouponTemplateSaveCommand;
import com.soloeats.marketing.service.view.CouponGrantRecordView;
import com.soloeats.marketing.service.view.CouponTemplateView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 券模板后台管理。t_coupon_template 是平台级全局表（无 tenant_id），
 * 不手写租户条件，也不给实体补 tenantId。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponAdminServiceImpl implements CouponAdminService {

    private static final int COUPON_TYPE_RATE = 2;
    private static final int STATUS_ENABLED = 0;
    private static final int STATUS_DISABLED = 1;
    private static final int STATUS_UNUSED = 0;
    private static final int MAX_GRANT_USERS = 500;
    private static final int NAME_MAX_LENGTH = 64;
    private static final int RATE_SCALE = 4;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private static final Set<Integer> TEMPLATE_STATUS = Set.of(STATUS_ENABLED, STATUS_DISABLED);
    private static final Set<Integer> COUPON_TYPES = Set.of(1, 2, 3);
    private static final Set<Integer> BEARERS = Set.of(1, 2);
    private static final Set<Integer> USER_COUPON_STATUS = Set.of(0, 1, 2, 3, 4, 5);

    private final CouponTemplateMapper couponTemplateMapper;
    private final UserCouponMapper userCouponMapper;

    @Override
    public PageResult<CouponTemplateView> page(String name, Integer status, Integer couponType,
                                               Integer bearer, long pageNum, long pageSize) {
        if (status != null && !TEMPLATE_STATUS.contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0 启用 / 1 停用");
        }
        if (couponType != null && !COUPON_TYPES.contains(couponType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "couponType 只能是 1 满减 / 2 折扣 / 3 无门槛");
        }
        if (bearer != null && !BEARERS.contains(bearer)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bearer 只能是 1 平台 / 2 商家");
        }

        long normalizedPageNum = normalizePageNum(pageNum);
        long normalizedPageSize = normalizePageSize(pageSize);

        LambdaQueryWrapper<CouponTemplate> wrapper = new LambdaQueryWrapper<CouponTemplate>()
                .eq(status != null, CouponTemplate::getStatus, status)
                .eq(couponType != null, CouponTemplate::getCouponType, couponType)
                .eq(bearer != null, CouponTemplate::getBearer, bearer)
                .like(StringUtils.hasText(name), CouponTemplate::getName, trimToNull(name))
                .orderByDesc(CouponTemplate::getId);

        Page<CouponTemplate> page = couponTemplateMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<CouponTemplateView> views = page.getRecords().stream()
                .map(CouponTemplateView::from)
                .toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public CouponTemplateView detail(Long id) {
        return CouponTemplateView.from(requireTemplate(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponTemplateView create(CouponTemplateSaveCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求参数不能为空");
        }
        String name = normalizeName(command.name());
        int couponType = requireCouponType(command.couponType());
        int bearer = normalizeBearer(command.bearer());
        long thresholdAmount = normalizeAmount(command.thresholdAmount(), "thresholdAmount");
        long discountAmount = normalizeAmount(command.discountAmount(), "discountAmount");
        int totalQty = normalizeTotalQty(command.totalQty());
        BigDecimal discountRate = normalizeRate(command.discountRate());
        Integer validDays = normalizeValidDays(command.validDays());
        validateValidity(command.validStart(), command.validEnd());
        validateDiscount(couponType, discountAmount, discountRate);

        CouponTemplate template = new CouponTemplate();
        template.setName(name);
        template.setCouponType(couponType);
        template.setThresholdAmount(thresholdAmount);
        template.setDiscountAmount(discountAmount);
        template.setDiscountRate(discountRate);
        template.setBearer(bearer);
        template.setTotalQty(totalQty);
        template.setIssuedQty(0);
        template.setValidStart(command.validStart());
        template.setValidEnd(command.validEnd());
        template.setValidDays(validDays);
        template.setStatus(STATUS_ENABLED);
        couponTemplateMapper.insert(template);

        log.info("coupon template created templateId={} totalQty={}", template.getId(), totalQty);
        return CouponTemplateView.from(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponTemplateView update(Long id, CouponTemplateSaveCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求参数不能为空");
        }
        CouponTemplate existing = requireTemplate(id);
        int couponType = requireCouponType(existing.getCouponType());

        String name = normalizeName(command.name());
        long thresholdAmount = normalizeAmount(command.thresholdAmount(), "thresholdAmount");
        long discountAmount = normalizeAmount(command.discountAmount(), "discountAmount");
        int totalQty = normalizeTotalQty(command.totalQty());
        BigDecimal discountRate = normalizeRate(command.discountRate());
        Integer validDays = normalizeValidDays(command.validDays());
        validateValidity(command.validStart(), command.validEnd());
        validateDiscount(couponType, discountAmount, discountRate);

        int issuedQty = existing.getIssuedQty() == null ? 0 : existing.getIssuedQty();
        if (totalQty < issuedQty) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "totalQty 不能小于已发放数量 " + issuedQty);
        }

        // 条件更新兜住并发发券：读取后到写入前 issued_qty 可能被推高。
        LambdaUpdateWrapper<CouponTemplate> wrapper = new LambdaUpdateWrapper<CouponTemplate>()
                .eq(CouponTemplate::getId, id)
                .le(CouponTemplate::getIssuedQty, totalQty)
                .set(CouponTemplate::getName, name)
                .set(CouponTemplate::getThresholdAmount, thresholdAmount)
                .set(CouponTemplate::getDiscountAmount, discountAmount)
                .set(CouponTemplate::getDiscountRate, discountRate)
                .set(CouponTemplate::getTotalQty, totalQty)
                .set(CouponTemplate::getValidStart, command.validStart())
                .set(CouponTemplate::getValidEnd, command.validEnd())
                .set(CouponTemplate::getValidDays, validDays);
        int rows = couponTemplateMapper.update(null, wrapper);
        if (rows == 0) {
            if (couponTemplateMapper.selectById(id) == null) {
                throw new BizException(ErrorCode.COUPON_TEMPLATE_NOT_FOUND);
            }
            throw new BizException(ErrorCode.PARAM_INVALID, "totalQty 不能小于已发放数量，请刷新后重试");
        }

        log.info("coupon template updated templateId={} totalQty={}", id, totalQty);
        return CouponTemplateView.from(requireTemplate(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        if (status == null || !TEMPLATE_STATUS.contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0 启用 / 1 停用");
        }
        requireTemplate(id);

        LambdaUpdateWrapper<CouponTemplate> wrapper = new LambdaUpdateWrapper<CouponTemplate>()
                .eq(CouponTemplate::getId, id)
                .set(CouponTemplate::getStatus, status);
        if (couponTemplateMapper.update(null, wrapper) == 0) {
            throw new BizException(ErrorCode.COUPON_TEMPLATE_NOT_FOUND);
        }
        log.info("coupon template status updated templateId={} status={}", id, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int grant(Long templateId, List<Long> userIds) {
        List<Long> targets = normalizeUserIds(userIds);
        CouponTemplate template = requireTemplate(templateId);
        if (!Objects.equals(template.getStatus(), STATUS_ENABLED)) {
            throw new BizException(ErrorCode.COUPON_TEMPLATE_STATUS_INVALID, "优惠券模板已停用");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = resolveExpireAt(template, now);

        // 单条 SQL 原子占用额度：影响行数 0 即余量不足，整批随事务回滚。
        if (couponTemplateMapper.deductIssueQty(templateId, targets.size()) != 1) {
            throw new BizException(ErrorCode.COUPON_STOCK_NOT_ENOUGH);
        }

        for (Long userId : targets) {
            UserCoupon coupon = new UserCoupon();
            coupon.setUserId(userId);
            coupon.setTemplateId(templateId);
            coupon.setStatus(STATUS_UNUSED);
            coupon.setExpireAt(expireAt);
            coupon.setVersion(0);
            userCouponMapper.insert(coupon);
        }

        log.info("coupon granted templateId={} qty={} expireAt={}", templateId, targets.size(), expireAt);
        return targets.size();
    }

    @Override
    public PageResult<CouponGrantRecordView> records(Long templateId, Integer status,
                                                     long pageNum, long pageSize) {
        requireTemplate(templateId);
        if (status != null && !USER_COUPON_STATUS.contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0~5");
        }

        long normalizedPageNum = normalizePageNum(pageNum);
        long normalizedPageSize = normalizePageSize(pageSize);

        LambdaQueryWrapper<UserCoupon> wrapper = new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getTemplateId, templateId)
                .eq(status != null, UserCoupon::getStatus, status)
                .orderByDesc(UserCoupon::getId);

        Page<UserCoupon> page = userCouponMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<CouponGrantRecordView> views = page.getRecords().stream()
                .map(CouponGrantRecordView::from)
                .toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    /**
     * 过期时间优先级：valid_days → now + days；否则回落模板 valid_end；
     * 两者皆空或 valid_end 已过期时拒绝发券，避免发出生来即失效的券。
     */
    private LocalDateTime resolveExpireAt(CouponTemplate template, LocalDateTime now) {
        Integer validDays = template.getValidDays();
        if (validDays != null && validDays > 0) {
            return now.plusDays(validDays);
        }
        LocalDateTime validEnd = template.getValidEnd();
        if (validEnd == null) {
            throw new BizException(ErrorCode.COUPON_TEMPLATE_STATUS_INVALID,
                    "模板未配置 validDays 与 validEnd，无法计算过期时间");
        }
        if (!validEnd.isAfter(now)) {
            throw new BizException(ErrorCode.COUPON_TEMPLATE_STATUS_INVALID, "模板有效期已结束，无法发券");
        }
        return validEnd;
    }

    private CouponTemplate requireTemplate(Long id) {
        CouponTemplate template = id == null ? null : couponTemplateMapper.selectById(id);
        if (template == null) {
            throw new BizException(ErrorCode.COUPON_TEMPLATE_NOT_FOUND);
        }
        return template;
    }

    private List<Long> normalizeUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userIds 不能为空");
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId == null || userId <= 0L) {
                throw new BizException(ErrorCode.PARAM_INVALID, "userIds 只能包含正整数");
            }
            distinct.add(userId);
        }
        if (distinct.size() > MAX_GRANT_USERS) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "单次发券不能超过 " + MAX_GRANT_USERS + " 人");
        }
        return List.copyOf(distinct);
    }

    private String normalizeName(String name) {
        String trimmed = trimToNull(name);
        if (trimmed == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "name 不能为空");
        }
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "name 不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }
        return trimmed;
    }

    private int requireCouponType(Integer couponType) {
        if (couponType == null || !COUPON_TYPES.contains(couponType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "couponType 只能是 1 满减 / 2 折扣 / 3 无门槛");
        }
        return couponType;
    }

    private int normalizeBearer(Integer bearer) {
        if (bearer == null) {
            return 1;
        }
        if (!BEARERS.contains(bearer)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bearer 只能是 1 平台 / 2 商家");
        }
        return bearer;
    }

    private long normalizeAmount(Long amount, String field) {
        if (amount == null) {
            return 0L;
        }
        if (amount < 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为负数");
        }
        return amount;
    }

    private int normalizeTotalQty(Integer totalQty) {
        if (totalQty == null) {
            return 0;
        }
        if (totalQty < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "totalQty 不能为负数");
        }
        return totalQty;
    }

    private BigDecimal normalizeRate(BigDecimal rate) {
        return rate == null ? null : rate.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    private Integer normalizeValidDays(Integer validDays) {
        if (validDays != null && validDays <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "validDays 必须大于 0");
        }
        return validDays;
    }

    private void validateValidity(LocalDateTime validStart, LocalDateTime validEnd) {
        if (validStart != null && validEnd != null && !validEnd.isAfter(validStart)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "validEnd 必须晚于 validStart");
        }
    }

    private void validateDiscount(int couponType, long discountAmount, BigDecimal discountRate) {
        if (couponType == COUPON_TYPE_RATE) {
            if (discountRate == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "折扣券必须配置 discountRate");
            }
            if (discountRate.compareTo(BigDecimal.ZERO) <= 0
                    || discountRate.compareTo(BigDecimal.ONE) > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "discountRate 必须在 (0,1] 区间");
            }
            return;
        }
        if (discountAmount <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "满减券/无门槛券必须配置大于 0 的 discountAmount");
        }
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static long normalizePageNum(long pageNum) {
        return pageNum < 1L ? 1L : pageNum;
    }

    private static long normalizePageSize(long pageSize) {
        return pageSize < 1L ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
