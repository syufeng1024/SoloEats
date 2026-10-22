package com.soloeats.product.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.product.mapper.MenuAddonRow;
import com.soloeats.product.mapper.MenuRow;
import com.soloeats.product.mapper.ShopMenuMapper;
import com.soloeats.product.service.ShopMenuService;
import com.soloeats.product.service.view.ShopMenuView;
import com.soloeats.product.support.JsonTexts;
import com.soloeats.product.support.ProductProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 用户端菜单聚合。
 *
 * <p>查询固定为两条 SQL：门店商品/SPU/SKU/库存主查询 + 加料组批量查询，
 * 均在内存分组，不在循环内 select。Redis 缓存 key 带版本号，
 * 商品变更时递增版本并删除旧 key，避免全库 SCAN。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopMenuServiceImpl implements ShopMenuService {

    private static final String VERSION_KEY = "menu:shop:%d:ver";
    private static final String CACHE_KEY = "menu:shop:%d:v%d";
    private static final int DEFAULT_CACHE_SECONDS = 300;
    private static final int SHOP_SPU_SOLD_OUT = 2;

    private final ShopMenuMapper shopMenuMapper;
    private final StringRedisTemplate redisTemplate;
    private final ProductProperties productProperties;
    private final ObjectMapper objectMapper;

    @Override
    public List<ShopMenuView> menu(Long shopId) {
        if (shopId == null || shopId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "shopId 必须是正整数");
        }
        String cacheKey = cacheKey(shopId, currentVersion(shopId));
        List<ShopMenuView> cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<MenuRow> rows = shopMenuMapper.selectMenuRows(shopId);
        if (rows.isEmpty()) {
            writeCache(cacheKey, List.of());
            return List.of();
        }
        List<Long> spuIds = rows.stream().map(MenuRow::getSpuId).distinct().toList();
        List<MenuAddonRow> addonRows = shopMenuMapper.selectAddonRows(spuIds);
        Map<Long, Map<Long, AddonGroupBuilder>> addonsBySpu = groupAddons(addonRows);

        List<ShopMenuView> views = assemble(rows, addonsBySpu);
        writeCache(cacheKey, views);
        return views;
    }

    @Override
    public void evictMenuCache(Long shopId) {
        if (shopId == null || shopId <= 0L) {
            return;
        }
        String versionKey = String.format(VERSION_KEY, shopId);
        try {
            String oldValue = redisTemplate.opsForValue().get(versionKey);
            Long newVersion = redisTemplate.opsForValue().increment(versionKey);
            long oldVersion = oldValue == null ? 0L : parseVersion(oldValue);
            redisTemplate.delete(String.format(CACHE_KEY, shopId, oldVersion));
            if (newVersion != null) {
                log.debug("menu cache evicted shopId={} oldVersion={} newVersion={}",
                        shopId, oldVersion, newVersion);
            }
        } catch (Exception ex) {
            // 缓存故障不能阻塞商品变更；旧 key 5 分钟后自然过期
            log.warn("menu cache evict failed shopId={} reason={}", shopId, ex.getMessage());
        }
    }

    @Override
    public void evictAllMenuCache() {
        try {
            java.util.Set<String> keys = redisTemplate.keys("menu:shop:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ex) {
            log.warn("menu cache evict-all failed reason={}", ex.getMessage());
        }
    }

    private List<ShopMenuView> assemble(List<MenuRow> rows,
                                        Map<Long, Map<Long, AddonGroupBuilder>> addonsBySpu) {
        Map<Long, CategoryBuilder> categories = new LinkedHashMap<>();
        for (MenuRow row : rows) {
            Long categoryId = row.getCategoryId() == null ? 0L : row.getCategoryId();
            String categoryName = StringUtils.hasText(row.getCategoryName())
                    ? row.getCategoryName() : "其他";
            CategoryBuilder category = categories.computeIfAbsent(categoryId,
                    key -> new CategoryBuilder(categoryId, categoryName));

            ItemBuilder item = category.items.computeIfAbsent(row.getSpuId(), key -> new ItemBuilder(row));
            boolean shopSpuSoldOut = Objects.equals(row.getShopSpuStatus(), SHOP_SPU_SOLD_OUT);
            boolean skuSoldOut = shopSpuSoldOut || Boolean.TRUE.equals(row.getSoldOut());
            item.soldOutAll = item.soldOutAll && skuSoldOut;
            item.skus.add(new ShopMenuView.MenuItemSkuView(
                    row.getSkuId(),
                    row.getSkuName(),
                    row.getSpecJson(),
                    row.getSkuImage() != null ? row.getSkuImage() : row.getMainImage(),
                    row.getPrice(),
                    row.getAvailableQty() == null ? 0 : row.getAvailableQty(),
                    skuSoldOut
            ));
            if (item.minPrice == null || row.getPrice() < item.minPrice) {
                item.minPrice = row.getPrice();
            }
        }

        List<ShopMenuView> views = new ArrayList<>(categories.size());
        for (CategoryBuilder category : categories.values()) {
            List<ShopMenuView.MenuItemView> items = new ArrayList<>(category.items.size());
            for (ItemBuilder item : category.items.values()) {
                List<ShopMenuView.MenuAddonGroupView> addonGroups = toAddonViews(
                        addonsBySpu.getOrDefault(item.spuId, Map.of()));
                items.add(new ShopMenuView.MenuItemView(
                        item.spuId,
                        item.spuName,
                        item.subtitle,
                        item.mainImage,
                        item.minPrice,
                        item.monthSales,
                        JsonTexts.readStringList(objectMapper, item.tagsJson),
                        item.soldOutAll,
                        item.packagingFee,
                        item.minPurchaseQty,
                        item.limitPerOrder,
                        List.copyOf(item.skus),
                        addonGroups
                ));
            }
            views.add(new ShopMenuView(category.categoryId, category.categoryName, items));
        }
        return views;
    }

    private Map<Long, Map<Long, AddonGroupBuilder>> groupAddons(List<MenuAddonRow> rows) {
        Map<Long, Map<Long, AddonGroupBuilder>> result = new LinkedHashMap<>();
        for (MenuAddonRow row : rows) {
            AddonGroupBuilder group = result
                    .computeIfAbsent(row.getSpuId(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(row.getGroupId(), key -> new AddonGroupBuilder(row));
            if (row.getItemId() != null) {
                group.items.add(new ShopMenuView.MenuAddonItemView(
                        row.getItemId(), row.getItemName(), row.getItemPrice(), row.getItemStock()));
            }
        }
        return result;
    }

    private List<ShopMenuView.MenuAddonGroupView> toAddonViews(Map<Long, AddonGroupBuilder> groups) {
        return groups.values().stream()
                .map(group -> new ShopMenuView.MenuAddonGroupView(
                        group.groupId, group.groupName, group.minSelect, group.maxSelect,
                        List.copyOf(group.items)))
                .toList();
    }

    private long currentVersion(Long shopId) {
        try {
            String value = redisTemplate.opsForValue().get(String.format(VERSION_KEY, shopId));
            return value == null ? 0L : parseVersion(value);
        } catch (Exception ex) {
            log.warn("menu cache version read failed shopId={} reason={}", shopId, ex.getMessage());
            return 0L;
        }
    }

    private long parseVersion(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private String cacheKey(Long shopId, long version) {
        return String.format(CACHE_KEY, shopId, version);
    }

    private List<ShopMenuView> readCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(json)) {
                return null;
            }
            return objectMapper.readValue(json, new TypeReference<List<ShopMenuView>>() {
            });
        } catch (Exception ex) {
            log.warn("menu cache read failed key={} reason={}", key, ex.getMessage());
            return null;
        }
    }

    private void writeCache(String key, List<ShopMenuView> views) {
        try {
            int seconds = productProperties.getMenuCacheSeconds() <= 0
                    ? DEFAULT_CACHE_SECONDS : productProperties.getMenuCacheSeconds();
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(views),
                    Duration.ofSeconds(seconds));
        } catch (Exception ex) {
            log.warn("menu cache write failed key={} reason={}", key, ex.getMessage());
        }
    }

    private static final class CategoryBuilder {
        private final Long categoryId;
        private final String categoryName;
        private final Map<Long, ItemBuilder> items = new LinkedHashMap<>();

        private CategoryBuilder(Long categoryId, String categoryName) {
            this.categoryId = categoryId;
            this.categoryName = categoryName;
        }
    }

    private static final class ItemBuilder {
        private final Long spuId;
        private final String spuName;
        private final String subtitle;
        private final String mainImage;
        private final String tagsJson;
        private final Long packagingFee;
        private final Integer minPurchaseQty;
        private final Integer limitPerOrder;
        private final Integer monthSales;
        private final List<ShopMenuView.MenuItemSkuView> skus = new ArrayList<>();
        private Long minPrice;
        private boolean soldOutAll = true;

        private ItemBuilder(MenuRow row) {
            this.spuId = row.getSpuId();
            this.spuName = row.getSpuName();
            this.subtitle = row.getSubtitle();
            this.mainImage = row.getMainImage();
            this.tagsJson = row.getTagsJson();
            this.packagingFee = row.getPackagingFee();
            this.minPurchaseQty = row.getMinPurchaseQty();
            this.limitPerOrder = row.getLimitPerOrder();
            this.monthSales = row.getMonthSales();
        }
    }

    private static final class AddonGroupBuilder {
        private final Long groupId;
        private final String groupName;
        private final Integer minSelect;
        private final Integer maxSelect;
        private final List<ShopMenuView.MenuAddonItemView> items = new ArrayList<>();

        private AddonGroupBuilder(MenuAddonRow row) {
            this.groupId = row.getGroupId();
            this.groupName = row.getGroupName();
            this.minSelect = row.getMinSelect();
            this.maxSelect = row.getMaxSelect();
        }
    }
}
