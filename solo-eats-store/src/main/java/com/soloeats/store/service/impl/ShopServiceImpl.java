package com.soloeats.store.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.merchant.service.MerchantProfileService;
import com.soloeats.store.domain.entity.Shop;
import com.soloeats.store.domain.entity.ShopBusinessHour;
import com.soloeats.store.domain.enums.ShopStatus;
import com.soloeats.store.mapper.ShopBusinessHourMapper;
import com.soloeats.store.mapper.ShopMapper;
import com.soloeats.store.service.ShopService;
import com.soloeats.store.service.command.SaveBusinessHourCommand;
import com.soloeats.store.service.command.SaveShopCommand;
import com.soloeats.store.service.view.ShopDetailView;
import com.soloeats.store.service.view.ShopView;
import com.soloeats.system.service.SysTenantService;
import com.soloeats.system.service.view.PackageView;
import com.soloeats.system.service.view.TenantView;
import com.soloeats.system.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private static final int MERCHANT_STATUS_APPROVED = 2;
    private static final int SHOP_NO_RETRY = 3;
    private static final int MAX_BUSINESS_HOURS = 50;
    private static final int MAX_IMAGE_COUNT = 9;
    private static final int MAX_URL_LENGTH = 512;

    private static final int DEFAULT_PREP_MINUTES = 15;
    private static final int DEFAULT_DELIVERY_RADIUS_M = 3000;
    private static final int DEFAULT_PICKUP_PREPARE_MINUTES = 15;
    private static final int DEFAULT_PICKUP_TIMEOUT_MINUTES = 120;
    private static final int DEFAULT_PICKUP_CODE_LENGTH = 6;
    private static final int DEFAULT_PICKUP_CODE_VALID_MINUTES = 30;

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern PICKUP_CODE_PREFIX_PATTERN = Pattern.compile("^[A-Za-z0-9]{0,8}$");
    private static final String[] WEEK_TEXT = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private final ShopMapper shopMapper;
    private final ShopBusinessHourMapper businessHourMapper;
    private final MerchantProfileService merchantProfileService;
    private final SysTenantService sysTenantService;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<ShopView> adminPage(Long merchantId, Integer status, String city, String keyword,
                                          long pageNum, long pageSize) {
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = pageSize < 1L ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<Shop>()
                .eq(Shop::getDeleted, 0)
                .eq(merchantId != null, Shop::getMerchantId, merchantId)
                .eq(status != null, Shop::getStatus, status)
                .eq(StringUtils.hasText(city), Shop::getCity, city);
        String sanitized = sanitizeKeyword(keyword);
        if (StringUtils.hasText(sanitized)) {
            wrapper.and(w -> w.like(Shop::getShopName, sanitized)
                    .or()
                    .like(Shop::getShopNo, sanitized));
        }
        wrapper.orderByDesc(Shop::getId);

        Page<Shop> page = shopMapper.selectScopedPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<ShopView> views = page.getRecords().stream().map(this::toView).toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public List<ShopView> listByMerchant(Long merchantId, Long operatorShopId) {
        requireMerchant(merchantId);
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<Shop>()
                .eq(Shop::getDeleted, 0)
                .eq(Shop::getMerchantId, merchantId)
                .eq(operatorShopId != null, Shop::getId, operatorShopId)
                .orderByDesc(Shop::getId);
        return shopMapper.selectList(wrapper).stream().map(this::toView).toList();
    }

    @Override
    public ShopDetailView detail(Long shopId) {
        return toDetail(requireShop(shopId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopDetailView createForAdmin(SaveShopCommand command) {
        if (command == null || command.merchantId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "merchantId 不能为空");
        }
        return createInternal(command.merchantId(), command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopDetailView createForMerchant(Long merchantId, SaveShopCommand command) {
        if (merchantId == null) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "令牌缺少 merchantId");
        }
        return createInternal(merchantId, command);
    }

    private ShopDetailView createInternal(Long merchantId, SaveShopCommand command) {
        requireMerchant(merchantId);
        long existing = shopMapper.selectCount(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getMerchantId, merchantId)
                .eq(Shop::getDeleted, 0));
        assertPackageShopLimit(existing);

        Shop shop = new Shop();
        shop.setMerchantId(merchantId);
        applyEditableFields(shop, command, true);
        shop.setStatus(ShopStatus.OPEN.code());
        shop.setMonthSales(0);
        shop.setSortWeight(command.sortWeight() == null ? 0 : command.sortWeight());
        shop.setRating(new BigDecimal("5.00"));
        shop.setRatingTaste(new BigDecimal("5.00"));
        shop.setRatingPackage(new BigDecimal("5.00"));
        shop.setRatingDelivery(new BigDecimal("5.00"));
        applyPickupDefaults(shop);
        shop.setDeleted(0);

        insertWithShopNo(shop, merchantId, existing);
        log.info("shop created shopId={} merchantId={}", shop.getId(), merchantId);
        return toDetail(shop);
    }

    private void insertWithShopNo(Shop shop, Long merchantId, long existing) {
        for (int attempt = 0; attempt < SHOP_NO_RETRY; attempt++) {
            long sequence = existing + attempt + 1;
            shop.setShopNo(String.format("S%04d%04d", merchantId, sequence));
            try {
                shopMapper.insert(shop);
                return;
            } catch (DuplicateKeyException ex) {
                // uk_shop_no 冲突：并发建店时换下一个流水号重试
                log.warn("shop_no conflict shopNo={} attempt={}", shop.getShopNo(), attempt + 1);
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "门店编号生成冲突，请重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopDetailView update(Long shopId, SaveShopCommand command, Long operatorMerchantId) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        Shop shop = requireShop(shopId);
        assertOwnership(shop, operatorMerchantId);

        if (operatorMerchantId == null && command.merchantId() != null
                && !Objects.equals(command.merchantId(), shop.getMerchantId())) {
            requireMerchant(command.merchantId());
            shop.setMerchantId(command.merchantId());
        }
        applyEditableFields(shop, command, false);
        if (shop.getSortWeight() == null) {
            shop.setSortWeight(0);
        }

        if (shopMapper.updateById(shop) == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "门店已被修改，请刷新后重试");
        }
        log.info("shop updated shopId={} operatorMerchantId={}", shopId, operatorMerchantId);
        return toDetail(shop);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long shopId, Integer status, Long operatorMerchantId) {
        Shop shop = requireShop(shopId);
        assertOwnership(shop, operatorMerchantId);
        ShopStatus target = ShopStatus.fromCode(status);
        if (operatorMerchantId != null && !ShopStatus.merchantSwitchable(status)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "商家端不能设置门店停用");
        }
        if (Objects.equals(shop.getStatus(), target.code())) {
            return;
        }
        int updated = shopMapper.update(null, new LambdaUpdateWrapper<Shop>()
                .eq(Shop::getId, shopId)
                .eq(Shop::getStatus, shop.getStatus())
                .eq(Shop::getDeleted, 0)
                .set(Shop::getStatus, target.code()));
        if (updated == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "门店状态已被修改，请刷新后重试");
        }
        log.info("shop status changed shopId={} from={} to={}", shopId, shop.getStatus(), target.code());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePickup(Long shopId, SaveShopCommand.Pickup pickup, Long operatorMerchantId) {
        if (pickup == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        Shop shop = requireShop(shopId);
        assertOwnership(shop, operatorMerchantId);

        Integer prepare = pickup.pickupPrepareMinutes() == null
                ? shop.getPickupPrepareMinutes() : pickup.pickupPrepareMinutes();
        Integer timeout = pickup.pickupTimeoutMinutes() == null
                ? shop.getPickupTimeoutMinutes() : pickup.pickupTimeoutMinutes();
        Integer codeLength = pickup.pickupCodeLength() == null
                ? shop.getPickupCodeLength() : pickup.pickupCodeLength();
        Integer codeValid = pickup.pickupCodeValidMinutes() == null
                ? shop.getPickupCodeValidMinutes() : pickup.pickupCodeValidMinutes();
        Long discount = pickup.pickupDiscount() == null
                ? shop.getPickupDiscount() : pickup.pickupDiscount();
        String prefix = pickup.pickupCodePrefix() == null
                ? (shop.getPickupCodePrefix() == null ? "" : shop.getPickupCodePrefix())
                : pickup.pickupCodePrefix().trim();
        Integer enabled = pickup.pickupEnabled() == null
                ? shop.getPickupEnabled() : (pickup.pickupEnabled() ? 1 : 0);

        if (prepare < 1 || prepare > 120) {
            throw new BizException(ErrorCode.PARAM_INVALID, "pickupPrepareMinutes 取值 1~120");
        }
        if (timeout < 10 || timeout > 1440) {
            throw new BizException(ErrorCode.PARAM_INVALID, "pickupTimeoutMinutes 取值 10~1440");
        }
        if (codeLength < 4 || codeLength > 10) {
            throw new BizException(ErrorCode.PARAM_INVALID, "pickupCodeLength 取值 4~10");
        }
        if (codeValid < 1 || codeValid > 120) {
            throw new BizException(ErrorCode.PARAM_INVALID, "pickupCodeValidMinutes 取值 1~120");
        }
        if (discount < 0 || discount > 100000) {
            throw new BizException(ErrorCode.PARAM_INVALID, "pickupDiscount 取值 0~100000 分");
        }
        if (prefix.length() > 8 || !PICKUP_CODE_PREFIX_PATTERN.matcher(prefix).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "pickupCodePrefix 只能是 0~8 位字母或数字");
        }

        int updated = shopMapper.update(null, new LambdaUpdateWrapper<Shop>()
                .eq(Shop::getId, shopId)
                .eq(Shop::getDeleted, 0)
                .set(Shop::getPickupEnabled, enabled)
                .set(Shop::getPickupPrepareMinutes, prepare)
                .set(Shop::getPickupTimeoutMinutes, timeout)
                .set(Shop::getPickupCodePrefix, prefix.isEmpty() ? null : prefix)
                .set(Shop::getPickupCodeLength, codeLength)
                .set(Shop::getPickupCodeValidMinutes, codeValid)
                .set(Shop::getPickupDiscount, discount));
        if (updated == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "门店已被修改，请刷新后重试");
        }
        log.info("shop pickup updated shopId={} enabled={}", shopId, enabled);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBusinessHours(Long shopId, List<SaveBusinessHourCommand> hours,
                                  Long operatorMerchantId) {
        Shop shop = requireShop(shopId);
        assertOwnership(shop, operatorMerchantId);
        if (hours == null || hours.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "hours 不能为空");
        }
        if (hours.size() > MAX_BUSINESS_HOURS) {
            throw new BizException(ErrorCode.PARAM_INVALID, "营业时段最多 " + MAX_BUSINESS_HOURS + " 条");
        }

        List<ShopBusinessHour> entities = new ArrayList<>(hours.size());
        for (SaveBusinessHourCommand hour : hours) {
            entities.add(toBusinessHour(shopId, hour));
        }

        // 全量替换：先删后插，同一事务内完成
        businessHourMapper.delete(new LambdaQueryWrapper<ShopBusinessHour>()
                .eq(ShopBusinessHour::getShopId, shopId));
        for (ShopBusinessHour entity : entities) {
            entity.setTenantId(shop.getTenantId());
            businessHourMapper.insert(entity);
        }
        log.info("shop business hours replaced shopId={} count={}", shopId, entities.size());
    }

    @Override
    public void assertMerchantShop(Long shopId, Long merchantId, Long operatorShopId) {
        Shop shop = requireShop(shopId);
        assertOwnership(shop, merchantId);
        if (operatorShopId != null && !Objects.equals(shop.getId(), operatorShopId)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无权操作其他门店");
        }
    }

    @Override
    public BusinessStatus businessStatus(Long shopId) {
        Shop shop = requireShop(shopId);
        List<ShopBusinessHour> hours = businessHourMapper.selectList(
                new LambdaQueryWrapper<ShopBusinessHour>()
                        .eq(ShopBusinessHour::getShopId, shopId)
                        .orderByAsc(ShopBusinessHour::getDayOfWeek)
                        .orderByAsc(ShopBusinessHour::getStartTime));

        LocalDateTime now = LocalDateTime.now();
        // 未配置营业时段视为不限制（新店可直接接单）；配置后严格按规则判定
        boolean inWindow = hours.isEmpty() || hours.stream().anyMatch(hour -> matches(hour, now));
        ShopBusinessHour next = nextPeriod(hours, now);

        boolean open = Objects.equals(shop.getStatus(), ShopStatus.OPEN.code());
        return new BusinessStatus(
                open && inWindow,
                shop.getStatus(),
                ShopStatus.textOf(shop.getStatus()),
                open,
                inWindow,
                next == null ? null : formatPeriod(next, true),
                next == null ? null : formatPeriod(next, false));
    }

    private String formatPeriod(ShopBusinessHour hour, boolean start) {
        return WEEK_TEXT[hour.getDayOfWeek() - 1] + " "
                + (start ? hour.getStartTime() : hour.getEndTime());
    }

    /**
     * 跨天时段 [start, 24:00) ∪ [00:00, end)：前一段落在 dayOfWeek，后一段落在次日。
     */
    private boolean matches(ShopBusinessHour hour, LocalDateTime now) {
        LocalTime time = now.toLocalTime();
        int dow = now.getDayOfWeek().getValue();
        LocalTime start = hour.getStartTime();
        LocalTime end = hour.getEndTime();
        boolean crossDay = Objects.equals(hour.getCrossDay(), 1);

        if (!crossDay) {
            return dow == hour.getDayOfWeek() && !time.isBefore(start) && time.isBefore(end);
        }
        int nextDow = hour.getDayOfWeek() == 7 ? 1 : hour.getDayOfWeek() + 1;
        return (dow == hour.getDayOfWeek() && !time.isBefore(start))
                || (dow == nextDow && time.isBefore(end));
    }

    /**
     * 下一个尚未开始的营业时段，最多向后找 7 天。
     */
    private ShopBusinessHour nextPeriod(List<ShopBusinessHour> hours, LocalDateTime now) {
        ShopBusinessHour best = null;
        LocalDateTime bestStart = null;
        for (int offset = 0; offset <= 7; offset++) {
            LocalDateTime date = now.toLocalDate().plusDays(offset).atStartOfDay();
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            for (ShopBusinessHour hour : hours) {
                if (hour.getDayOfWeek() == null || hour.getDayOfWeek() != dayOfWeek.getValue()) {
                    continue;
                }
                LocalDateTime candidate = date.with(hour.getStartTime());
                if (!candidate.isAfter(now)) {
                    continue;
                }
                if (bestStart == null || candidate.isBefore(bestStart)) {
                    bestStart = candidate;
                    best = hour;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return best;
    }

    private ShopBusinessHour toBusinessHour(Long shopId, SaveBusinessHourCommand command) {
        if (command == null || command.dayOfWeek() == null
                || command.dayOfWeek() < 1 || command.dayOfWeek() > 7) {
            throw new BizException(ErrorCode.PARAM_INVALID, "dayOfWeek 取值 1~7");
        }
        LocalTime start = parseTime(command.startTime(), "startTime");
        LocalTime end = parseTime(command.endTime(), "endTime");
        if (start.equals(end)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "startTime 与 endTime 不能相同");
        }
        ShopBusinessHour entity = new ShopBusinessHour();
        entity.setShopId(shopId);
        entity.setDayOfWeek(command.dayOfWeek());
        entity.setStartTime(start);
        entity.setEndTime(end);
        entity.setCrossDay(end.isBefore(start) ? 1 : 0);
        return entity;
    }

    private LocalTime parseTime(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空");
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 必须为 HH:mm 格式");
        }
    }

    private void applyEditableFields(Shop shop, SaveShopCommand command, boolean creating) {
        if (creating || command.shopName() != null) {
            shop.setShopName(sanitizeHtml(command.shopName(), 128, "shopName", true));
        }
        if (creating || command.address() != null) {
            shop.setAddress(sanitizeHtml(command.address(), 255, "address", true));
        }
        if (command.province() != null) {
            shop.setProvince(sanitizeHtml(command.province(), 64, "province", false));
        }
        if (command.city() != null) {
            shop.setCity(sanitizeHtml(command.city(), 64, "city", false));
        }
        if (command.district() != null) {
            shop.setDistrict(sanitizeHtml(command.district(), 64, "district", false));
        }
        if (command.brandId() != null) {
            shop.setBrandId(command.brandId());
        }
        if (command.categoryId() != null) {
            shop.setCategoryId(command.categoryId());
        }
        if (creating || command.lng() != null) {
            shop.setLng(validateLng(command.lng()));
        }
        if (creating || command.lat() != null) {
            shop.setLat(validateLat(command.lat()));
        }
        if (command.contactName() != null) {
            shop.setContactName(sanitizeHtml(command.contactName(), 64, "contactName", false));
        }
        if (command.contactPhone() != null) {
            String phone = command.contactPhone().trim();
            if (!PHONE_PATTERN.matcher(phone).matches()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "contactPhone 格式不正确");
            }
            shop.setContactPhone(phone);
        }
        if (command.logoUrl() != null) {
            shop.setLogoUrl(validateUrl(command.logoUrl(), "logoUrl"));
        }
        if (command.coverUrl() != null) {
            shop.setCoverUrl(validateUrl(command.coverUrl(), "coverUrl"));
        }
        if (command.imageUrls() != null) {
            shop.setImageUrls(serializeImages(command.imageUrls()));
        } else if (creating) {
            shop.setImageUrls("[]");
        }
        if (command.announcement() != null) {
            shop.setAnnouncement(sanitizeHtml(command.announcement(), 500, "announcement", false));
        }
        if (creating || command.deliveryTypes() != null) {
            Integer types = command.deliveryTypes();
            if (types == null || types < 1 || types > 3) {
                throw new BizException(ErrorCode.PARAM_INVALID, "deliveryTypes 只能是 1、2 或 3");
            }
            shop.setDeliveryTypes(types);
        }
        if (creating || command.minOrderAmount() != null) {
            shop.setMinOrderAmount(nonNegative(command.minOrderAmount(), "minOrderAmount"));
        }
        if (creating || command.deliveryFee() != null) {
            shop.setDeliveryFee(nonNegative(command.deliveryFee(), "deliveryFee"));
        }
        if (creating || command.packagingFee() != null) {
            shop.setPackagingFee(nonNegative(command.packagingFee(), "packagingFee"));
        }
        if (creating || command.prepMinutes() != null) {
            Integer prep = command.prepMinutes();
            if (prep == null || prep < 1 || prep > 180) {
                throw new BizException(ErrorCode.PARAM_INVALID, "prepMinutes 取值 1~180");
            }
            shop.setPrepMinutes(prep);
        }
        if (command.deliveryRadiusM() != null) {
            if (command.deliveryRadiusM() < 100 || command.deliveryRadiusM() > 50000) {
                throw new BizException(ErrorCode.PARAM_INVALID, "deliveryRadiusM 取值 100~50000");
            }
            shop.setDeliveryRadiusM(command.deliveryRadiusM());
        } else if (creating) {
            shop.setDeliveryRadiusM(DEFAULT_DELIVERY_RADIUS_M);
        }
        if (command.settleAccountId() != null) {
            shop.setSettleAccountId(command.settleAccountId());
        }
    }

    private void applyPickupDefaults(Shop shop) {
        shop.setPickupEnabled(0);
        shop.setPickupPrepareMinutes(DEFAULT_PICKUP_PREPARE_MINUTES);
        shop.setPickupTimeoutMinutes(DEFAULT_PICKUP_TIMEOUT_MINUTES);
        shop.setPickupCodeLength(DEFAULT_PICKUP_CODE_LENGTH);
        shop.setPickupCodeValidMinutes(DEFAULT_PICKUP_CODE_VALID_MINUTES);
        shop.setPickupDiscount(0L);
    }

    private long nonNegative(Long value, String field) {
        if (value == null || value < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为负数");
        }
        return value;
    }

    private BigDecimal validateLng(BigDecimal lng) {
        if (lng == null || lng.compareTo(new BigDecimal("-180")) < 0
                || lng.compareTo(new BigDecimal("180")) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "lng 取值范围 -180~180");
        }
        return lng.setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal validateLat(BigDecimal lat) {
        if (lat == null || lat.compareTo(new BigDecimal("-90")) < 0
                || lat.compareTo(new BigDecimal("90")) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "lat 取值范围 -90~90");
        }
        return lat.setScale(6, RoundingMode.HALF_UP);
    }

    private String validateUrl(String value, String field) {
        String url = value.trim();
        if (url.isEmpty()) {
            return null;
        }
        if (url.length() > MAX_URL_LENGTH
                || !(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/"))) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 必须是 http(s) 或站内路径");
        }
        return url;
    }

    private String serializeImages(List<String> imageUrls) {
        if (imageUrls.size() > MAX_IMAGE_COUNT) {
            throw new BizException(ErrorCode.PARAM_INVALID, "imageUrls 最多 " + MAX_IMAGE_COUNT + " 张");
        }
        List<String> normalized = new ArrayList<>(imageUrls.size());
        for (String image : imageUrls) {
            if (!StringUtils.hasText(image)) {
                continue;
            }
            normalized.add(validateUrl(image, "imageUrls"));
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "imageUrls 序列化失败");
        }
    }

    private List<String> parseImages(String imageUrls) {
        if (!StringUtils.hasText(imageUrls)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(imageUrls, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            log.warn("shop image_urls parse failed, fallback empty");
            return List.of();
        }
    }

    /**
     * 去标签防 XSS；不做 HTML 实体转义，避免前端二次转义。
     */
    private String sanitizeHtml(String value, int maxLength, String field, boolean required) {
        if (!StringUtils.hasText(value)) {
            if (required) {
                throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空");
            }
            return null;
        }
        String cleaned = value.replaceAll("(?is)<script.*?</script>", "")
                .replaceAll("<[^>]+>", "")
                .replaceAll("(?i)javascript:", "")
                .trim();
        if (cleaned.isEmpty()) {
            if (required) {
                throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空");
            }
            return null;
        }
        if (cleaned.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    field + " 长度不能超过 " + maxLength + " 字符");
        }
        return cleaned;
    }

    private String sanitizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String cleaned = keyword.replaceAll("<[^>]+>", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }

    private void assertPackageShopLimit(long existing) {
        try {
            Long tenantId = TenantContext.get();
            List<TenantView> tenants = sysTenantService.page(null, null, null, 1, MAX_PAGE_SIZE).list();
            TenantView tenant = tenants.stream()
                    .filter(item -> Objects.equals(item.id(), tenantId))
                    .findFirst()
                    .orElse(null);
            if (tenant == null || tenant.packageId() == null) {
                return;
            }
            Integer maxShop = sysTenantService.listPackages().stream()
                    .filter(item -> Objects.equals(item.id(), tenant.packageId()))
                    .map(PackageView::maxShop)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (maxShop != null && maxShop > 0 && existing >= maxShop) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "门店数量已达套餐上限 " + maxShop + "，请联系平台升级套餐");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // 套餐是软校验：系统表不可用时只告警，不阻断建店
            log.warn("package max_shop check skipped reason={}", ex.getMessage());
        }
    }

    private void requireMerchant(Long merchantId) {
        if (merchantId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "merchantId 不能为空");
        }
        Integer status = merchantProfileService.profile(merchantId).status();
        if (!Objects.equals(status, MERCHANT_STATUS_APPROVED)) {
            throw new BizException(ErrorCode.MERCHANT_STATUS_INVALID, "商家未通过审核，不能创建门店");
        }
    }

    private Shop requireShop(Long shopId) {
        if (shopId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "shopId 不能为空");
        }
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null || !Objects.equals(shop.getDeleted(), 0)) {
            throw new BizException(ErrorCode.SHOP_NOT_FOUND);
        }
        return shop;
    }

    private void assertOwnership(Shop shop, Long operatorMerchantId) {
        if (operatorMerchantId != null && !Objects.equals(shop.getMerchantId(), operatorMerchantId)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无权操作其他商家的门店");
        }
    }

    private ShopView toView(Shop shop) {
        return new ShopView(
                shop.getId(),
                shop.getMerchantId(),
                shop.getShopNo(),
                shop.getShopName(),
                shop.getCity(),
                shop.getDistrict(),
                shop.getAddress(),
                shop.getLng(),
                shop.getLat(),
                shop.getStatus(),
                ShopStatus.textOf(shop.getStatus()),
                shop.getDeliveryTypes(),
                shop.getMinOrderAmount(),
                shop.getDeliveryFee(),
                shop.getPackagingFee(),
                shop.getPrepMinutes(),
                shop.getRating(),
                shop.getMonthSales(),
                shop.getPickupEnabled(),
                shop.getCreatedAt());
    }

    private ShopDetailView toDetail(Shop shop) {
        List<ShopDetailView.BusinessHourView> hours = businessHourMapper.selectList(
                        new LambdaQueryWrapper<ShopBusinessHour>()
                                .eq(ShopBusinessHour::getShopId, shop.getId())
                                .orderByAsc(ShopBusinessHour::getDayOfWeek)
                                .orderByAsc(ShopBusinessHour::getStartTime))
                .stream()
                .sorted(Comparator.comparing(ShopBusinessHour::getDayOfWeek)
                        .thenComparing(ShopBusinessHour::getStartTime))
                .map(hour -> new ShopDetailView.BusinessHourView(
                        hour.getDayOfWeek(),
                        String.valueOf(hour.getStartTime()),
                        String.valueOf(hour.getEndTime()),
                        Objects.equals(hour.getCrossDay(), 1)))
                .toList();

        return new ShopDetailView(
                shop.getId(),
                shop.getMerchantId(),
                shop.getShopNo(),
                shop.getShopName(),
                shop.getBrandId(),
                shop.getCategoryId(),
                shop.getProvince(),
                shop.getCity(),
                shop.getDistrict(),
                shop.getAddress(),
                shop.getLng(),
                shop.getLat(),
                shop.getContactName(),
                shop.getContactPhone(),
                shop.getLogoUrl(),
                shop.getCoverUrl(),
                parseImages(shop.getImageUrls()),
                shop.getAnnouncement(),
                shop.getStatus(),
                ShopStatus.textOf(shop.getStatus()),
                shop.getDeliveryTypes(),
                shop.getMinOrderAmount(),
                shop.getDeliveryFee(),
                shop.getPackagingFee(),
                shop.getPrepMinutes(),
                shop.getDeliveryRadiusM(),
                shop.getRating(),
                shop.getRatingTaste(),
                shop.getRatingPackage(),
                shop.getRatingDelivery(),
                shop.getMonthSales(),
                shop.getPickupEnabled(),
                shop.getPickupPrepareMinutes(),
                shop.getPickupTimeoutMinutes(),
                shop.getPickupCodePrefix(),
                shop.getPickupCodeLength(),
                shop.getPickupCodeValidMinutes(),
                shop.getPickupDiscount(),
                shop.getSettleAccountId(),
                hours,
                shop.getCreatedAt(),
                shop.getUpdatedAt());
    }
}
