package com.soloeats.store.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.store.domain.entity.Shop;
import com.soloeats.store.domain.entity.ShopDeliveryArea;
import com.soloeats.store.domain.enums.DeliveryType;
import com.soloeats.store.mapper.ShopDeliveryAreaMapper;
import com.soloeats.store.mapper.ShopMapper;
import com.soloeats.store.service.ShopGeoService;
import com.soloeats.store.service.command.DeliveryCheckCommand;
import com.soloeats.store.service.command.SaveDeliveryAreaCommand;
import com.soloeats.store.service.view.DeliveryCheckResult;
import com.soloeats.store.service.view.NearbyShopView;
import com.soloeats.store.support.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopGeoServiceImpl implements ShopGeoService {

    private static final int AREA_TYPE_CIRCLE = 1;
    private static final int AREA_TYPE_POLYGON = 2;
    private static final int MIN_POLYGON_POINTS = 3;
    private static final int MAX_POLYGON_POINTS = 50;

    private static final int MIN_RADIUS_M = 100;
    private static final int MAX_RADIUS_M = 50_000;
    private static final int DEFAULT_RADIUS_M = 5_000;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private static final int FULFILLMENT_DELIVERY = 1;
    private static final int FULFILLMENT_PICKUP = 2;

    /** 配送时速 15km/h ≈ 250m/min，用于预估送达时间。 */
    private static final double DELIVERY_METERS_PER_MINUTE = 250D;
    private static final double METERS_PER_LAT_DEGREE = 111_320D;

    private final ShopMapper shopMapper;
    private final ShopDeliveryAreaMapper deliveryAreaMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryAreaView saveDeliveryArea(Long shopId, SaveDeliveryAreaCommand command) {
        Shop shop = requireShop(shopId);
        if (command == null || command.areaType() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "areaType 不能为空");
        }

        ShopDeliveryArea area = deliveryAreaMapper.selectOne(new LambdaQueryWrapper<ShopDeliveryArea>()
                .eq(ShopDeliveryArea::getShopId, shopId)
                .eq(ShopDeliveryArea::getDeleted, 0)
                .eq(ShopDeliveryArea::getStatus, 0)
                .orderByDesc(ShopDeliveryArea::getId)
                .last("LIMIT 1"));
        boolean creating = area == null;
        if (creating) {
            area = new ShopDeliveryArea();
            area.setShopId(shopId);
            area.setTenantId(shop.getTenantId());
            area.setStatus(0);
            area.setDeleted(0);
        }

        if (Objects.equals(command.areaType(), AREA_TYPE_CIRCLE)) {
            area.setAreaType(AREA_TYPE_CIRCLE);
            area.setCenterLng(validateLng(command.centerLng(), "centerLng"));
            area.setCenterLat(validateLat(command.centerLat(), "centerLat"));
            area.setRadiusM(validateRadius(command.radiusM()));
            area.setPolygonJson(null);
        } else if (Objects.equals(command.areaType(), AREA_TYPE_POLYGON)) {
            area.setAreaType(AREA_TYPE_POLYGON);
            area.setPolygonJson(serializePolygon(command.polygon()));
            area.setCenterLng(null);
            area.setCenterLat(null);
            area.setRadiusM(null);
        } else {
            throw new BizException(ErrorCode.PARAM_INVALID, "areaType 只能是 1圆形 或 2多边形");
        }

        if (creating) {
            deliveryAreaMapper.insert(area);
        } else if (deliveryAreaMapper.updateById(area) == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "配送围栏已被修改，请刷新后重试");
        }
        log.info("shop delivery area saved shopId={} areaType={}", shopId, area.getAreaType());
        return toAreaView(area);
    }

    @Override
    public DeliveryAreaView getDeliveryArea(Long shopId) {
        requireShop(shopId);
        ShopDeliveryArea area = deliveryAreaMapper.selectOne(new LambdaQueryWrapper<ShopDeliveryArea>()
                .eq(ShopDeliveryArea::getShopId, shopId)
                .eq(ShopDeliveryArea::getDeleted, 0)
                .eq(ShopDeliveryArea::getStatus, 0)
                .orderByDesc(ShopDeliveryArea::getId)
                .last("LIMIT 1"));
        if (area == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "门店未配置配送范围");
        }
        return toAreaView(area);
    }

    @Override
    public DeliveryCheckResult check(Long shopId, DeliveryCheckCommand command) {
        Shop shop = requireShop(shopId);
        if (command == null || command.goodsAmount() == null || command.goodsAmount() < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "goodsAmount 不能为空且不能为负数");
        }
        if (command.fulfillmentType() == null
                || (command.fulfillmentType() != FULFILLMENT_DELIVERY
                && command.fulfillmentType() != FULFILLMENT_PICKUP)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "fulfillmentType 只能是 1平台配送 或 2到店自取");
        }

        long minOrderAmount = shop.getMinOrderAmount() == null ? 0L : shop.getMinOrderAmount();
        int prepMinutes = shop.getPrepMinutes() == null ? 0 : shop.getPrepMinutes();

        // 自取：跳过围栏、免配送费，但起送价校验仍生效
        if (command.fulfillmentType() == FULFILLMENT_PICKUP) {
            if (!Objects.equals(shop.getPickupEnabled(), 1)) {
                throw new BizException(ErrorCode.SHOP_PICKUP_DISABLED);
            }
            assertMinOrder(minOrderAmount, command.goodsAmount());
            int pickupPrep = shop.getPickupPrepareMinutes() == null ? prepMinutes : shop.getPickupPrepareMinutes();
            return new DeliveryCheckResult(
                    true,
                    null,
                    minOrderAmount,
                    0L,
                    LocalDateTime.now().plusMinutes(pickupPrep),
                    pickupPrep,
                    0);
        }

        if (!DeliveryType.hasDelivery(shop.getDeliveryTypes())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "门店不支持平台配送");
        }
        BigDecimal lng = validateLng(command.lng(), "lng");
        BigDecimal lat = validateLat(command.lat(), "lat");
        double distance = GeoUtils.distanceMeters(lng, lat, shop.getLng(), shop.getLat());

        boolean inRange = inRange(shop, lng, lat, distance);
        if (!inRange) {
            return new DeliveryCheckResult(
                    false,
                    "超出配送范围，距门店 " + Math.round(distance) + " 米",
                    minOrderAmount,
                    shop.getDeliveryFee(),
                    null,
                    prepMinutes,
                    (int) Math.round(distance));
        }

        assertMinOrder(minOrderAmount, command.goodsAmount());
        long travelMinutes = (long) Math.ceil(distance / DELIVERY_METERS_PER_MINUTE);
        return new DeliveryCheckResult(
                true,
                null,
                minOrderAmount,
                shop.getDeliveryFee(),
                LocalDateTime.now().plusMinutes(prepMinutes + travelMinutes),
                prepMinutes,
                (int) Math.round(distance));
    }

    @Override
    public PageResult<NearbyShopView> nearby(BigDecimal lng, BigDecimal lat, Integer radiusM, String keyword,
                                             String sortBy, long pageNum, long pageSize) {
        BigDecimal centerLng = validateLng(lng, "lng");
        BigDecimal centerLat = validateLat(lat, "lat");
        int radius = radiusM == null ? DEFAULT_RADIUS_M : radiusM;
        if (radius < MIN_RADIUS_M || radius > MAX_RADIUS_M) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "radius 取值 " + MIN_RADIUS_M + "~" + MAX_RADIUS_M + " 米");
        }
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = pageSize < 1L ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        double latDelta = radius / METERS_PER_LAT_DEGREE;
        double cosLat = Math.cos(Math.toRadians(centerLat.doubleValue()));
        // 高纬度 cos 趋近 0，限制经度增量，避免 WHERE 范围退化为全表
        double lngDelta = radius / (METERS_PER_LAT_DEGREE * Math.max(cosLat, 0.01D));

        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<Shop>()
                .eq(Shop::getDeleted, 0)
                .eq(Shop::getStatus, 1)
                .between(Shop::getLat, centerLat.doubleValue() - latDelta,
                        centerLat.doubleValue() + latDelta)
                .between(Shop::getLng, centerLng.doubleValue() - lngDelta,
                        centerLng.doubleValue() + lngDelta);
        String sanitized = sanitizeKeyword(keyword);
        if (StringUtils.hasText(sanitized)) {
            wrapper.and(w -> w.like(Shop::getShopName, sanitized)
                    .or()
                    .like(Shop::getAddress, sanitized));
        }

        List<NearbyShopView> matched = new ArrayList<>();
        for (Shop shop : shopMapper.selectList(wrapper)) {
            double distance = GeoUtils.distanceMeters(
                    centerLng, centerLat, shop.getLng(), shop.getLat());
            if (distance > radius) {
                continue;
            }
            matched.add(toNearby(shop, (int) Math.round(distance)));
        }
        matched.sort(comparator(sortBy));

        int from = (int) Math.min((normalizedPageNum - 1) * normalizedPageSize, matched.size());
        int to = (int) Math.min(from + normalizedPageSize, matched.size());
        List<NearbyShopView> page = matched.subList(from, to);
        return PageResult.of(List.copyOf(page), matched.size(), normalizedPageNum, normalizedPageSize);
    }

    private Comparator<NearbyShopView> comparator(String sortBy) {
        Comparator<NearbyShopView> byDistance = Comparator.comparing(NearbyShopView::distanceM);
        String normalized = sortBy == null ? "comprehensive" : sortBy.trim().toLowerCase();
        return switch (normalized) {
            case "distance" -> byDistance;
            case "sales" -> Comparator
                    .comparing(NearbyShopView::monthSales,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(byDistance);
            default -> Comparator
                    .comparing(NearbyShopView::rating,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(NearbyShopView::monthSales,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(byDistance);
        };
    }

    /**
     * 优先使用已启用围栏；未配置时回落到 t_shop.delivery_radius_m 的圆形范围。
     */
    private boolean inRange(Shop shop, BigDecimal lng, BigDecimal lat, double distance) {
        ShopDeliveryArea area = deliveryAreaMapper.selectOne(new LambdaQueryWrapper<ShopDeliveryArea>()
                .eq(ShopDeliveryArea::getShopId, shop.getId())
                .eq(ShopDeliveryArea::getDeleted, 0)
                .eq(ShopDeliveryArea::getStatus, 0)
                .orderByDesc(ShopDeliveryArea::getId)
                .last("LIMIT 1"));

        if (area == null) {
            Integer fallbackRadius = shop.getDeliveryRadiusM();
            return fallbackRadius != null && fallbackRadius > 0 && distance <= fallbackRadius;
        }
        if (Objects.equals(area.getAreaType(), AREA_TYPE_CIRCLE)) {
            double centerLng = area.getCenterLng() == null
                    ? shop.getLng().doubleValue() : area.getCenterLng().doubleValue();
            double centerLat = area.getCenterLat() == null
                    ? shop.getLat().doubleValue() : area.getCenterLat().doubleValue();
            int radius = area.getRadiusM() == null ? 0 : area.getRadiusM();
            return GeoUtils.withinCircle(lng.doubleValue(), lat.doubleValue(),
                    centerLng, centerLat, radius);
        }
        if (Objects.equals(area.getAreaType(), AREA_TYPE_POLYGON)) {
            List<double[]> polygon = parsePolygon(area.getPolygonJson());
            return GeoUtils.pointInPolygon(lng.doubleValue(), lat.doubleValue(), polygon);
        }
        return false;
    }

    private void assertMinOrder(long minOrderAmount, long goodsAmount) {
        if (goodsAmount < minOrderAmount) {
            throw new BizException(ErrorCode.SHOP_BELOW_MIN_ORDER,
                    "未达起送价，还差 " + (minOrderAmount - goodsAmount) + " 分");
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

    private BigDecimal validateLng(BigDecimal lng, String field) {
        if (lng == null || lng.compareTo(new BigDecimal("-180")) < 0
                || lng.compareTo(new BigDecimal("180")) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 取值范围 -180~180");
        }
        return lng.setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal validateLat(BigDecimal lat, String field) {
        if (lat == null || lat.compareTo(new BigDecimal("-90")) < 0
                || lat.compareTo(new BigDecimal("90")) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 取值范围 -90~90");
        }
        return lat.setScale(6, RoundingMode.HALF_UP);
    }

    private int validateRadius(Integer radiusM) {
        if (radiusM == null || radiusM < MIN_RADIUS_M || radiusM > MAX_RADIUS_M) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "radiusM 取值 " + MIN_RADIUS_M + "~" + MAX_RADIUS_M + " 米");
        }
        return radiusM;
    }

    private String serializePolygon(List<SaveDeliveryAreaCommand.Point> points) {
        if (points == null || points.size() < MIN_POLYGON_POINTS) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "多边形至少 " + MIN_POLYGON_POINTS + " 个点");
        }
        if (points.size() > MAX_POLYGON_POINTS) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "多边形最多 " + MAX_POLYGON_POINTS + " 个点");
        }
        List<List<BigDecimal>> normalized = new ArrayList<>(points.size());
        for (SaveDeliveryAreaCommand.Point point : points) {
            if (point == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "多边形点不能为空");
            }
            normalized.add(List.of(
                    validateLng(point.lng(), "polygon.lng"),
                    validateLat(point.lat(), "polygon.lat")));
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "多边形序列化失败");
        }
    }

    private List<double[]> parsePolygon(String polygonJson) {
        if (!StringUtils.hasText(polygonJson)) {
            return List.of();
        }
        try {
            List<List<BigDecimal>> raw = objectMapper.readValue(
                    polygonJson, new TypeReference<List<List<BigDecimal>>>() {
                    });
            List<double[]> points = new ArrayList<>(raw.size());
            for (List<BigDecimal> point : raw) {
                if (point != null && point.size() >= 2) {
                    points.add(new double[]{point.get(0).doubleValue(), point.get(1).doubleValue()});
                }
            }
            return points;
        } catch (Exception ex) {
            log.warn("shop polygon_json parse failed shopAreaId fallback empty");
            return List.of();
        }
    }

    private DeliveryAreaView toAreaView(ShopDeliveryArea area) {
        List<SaveDeliveryAreaCommand.Point> polygon = List.of();
        if (Objects.equals(area.getAreaType(), AREA_TYPE_POLYGON)) {
            try {
                List<List<BigDecimal>> raw = objectMapper.readValue(
                        area.getPolygonJson(), new TypeReference<List<List<BigDecimal>>>() {
                        });
                polygon = raw.stream()
                        .filter(point -> point != null && point.size() >= 2)
                        .map(point -> new SaveDeliveryAreaCommand.Point(point.get(0), point.get(1)))
                        .toList();
            } catch (Exception ex) {
                log.warn("shop polygon_json parse failed areaId={}", area.getId());
            }
        }
        return new DeliveryAreaView(
                area.getId(),
                area.getAreaType(),
                area.getCenterLng(),
                area.getCenterLat(),
                area.getRadiusM(),
                polygon,
                area.getStatus());
    }

    private NearbyShopView toNearby(Shop shop, int distanceM) {
        return new NearbyShopView(
                shop.getId(),
                shop.getShopName(),
                shop.getLogoUrl(),
                shop.getCoverUrl(),
                shop.getAddress(),
                shop.getLng(),
                shop.getLat(),
                shop.getStatus(),
                shop.getDeliveryTypes(),
                shop.getMinOrderAmount(),
                shop.getDeliveryFee(),
                shop.getPrepMinutes(),
                shop.getRating(),
                shop.getMonthSales(),
                shop.getPickupEnabled(),
                distanceM);
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
}
