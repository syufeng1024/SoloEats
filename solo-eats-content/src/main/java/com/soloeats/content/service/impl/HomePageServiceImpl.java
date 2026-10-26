package com.soloeats.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.content.domain.entity.Banner;
import com.soloeats.content.domain.entity.HomeFloor;
import com.soloeats.content.domain.entity.HomePage;
import com.soloeats.content.domain.enums.FloorType;
import com.soloeats.content.mapper.BannerMapper;
import com.soloeats.content.mapper.ContentSourceMapper;
import com.soloeats.content.mapper.HomeFloorMapper;
import com.soloeats.content.mapper.HomePageMapper;
import com.soloeats.content.service.HomePageService;
import com.soloeats.content.service.command.SaveFloorCommand;
import com.soloeats.content.service.view.BannerView;
import com.soloeats.content.service.view.FloorView;
import com.soloeats.content.service.view.HomePageView;
import com.soloeats.content.service.view.NoticeBriefView;
import com.soloeats.content.service.view.RecommendedShopView;
import com.soloeats.content.service.view.RecommendedSpuView;
import com.soloeats.content.support.ContentTexts;
import com.soloeats.product.service.CategoryService;
import com.soloeats.product.service.SpuService;
import com.soloeats.product.service.view.CategoryNode;
import com.soloeats.store.service.ShopService;
import com.soloeats.system.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomePageServiceImpl implements HomePageService {

    static final String DEFAULT_PAGE_CODE = "USER_HOME";
    private static final Set<String> PAGE_CODES = Set.of("USER_HOME", "USER_MINE");
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final int AGGREGATE_LIMIT = 10;
    private static final int MAX_SORT_SIZE = 100;
    private static final int STATUS_OFFLINE = 2;
    private static final int STATUS_ENABLED = 1;
    private static final int CATEGORY_ENABLED = 0;

    private final HomePageMapper pageMapper;
    private final HomeFloorMapper floorMapper;
    private final BannerMapper bannerMapper;
    private final ContentSourceMapper sourceMapper;
    private final CategoryService categoryService;
    private final ShopService shopService;
    private final SpuService spuService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public HomePageView adminDetail(String pageCode) {
        HomePage page = requirePage(pageCode);
        List<HomeFloor> floors = floorMapper.selectAllByPageId(page.getId());
        return toPageView(page, null,
                floors.stream().map(this::toFloorView).toList());
    }

    @Override
    public HomePageView preview(String pageCode) {
        HomePage page = requirePage(pageCode);
        // 预览不写库、不累加 Banner 曝光；只展示启用楼层，便于排期内容提前看效果。
        List<HomeFloor> floors = floorMapper.selectEnabledByPageId(page.getId());
        return aggregate(page, floors);
    }

    @Override
    public HomePageView clientHome(String pageCode, Long userId, String deviceId) {
        HomePage page = requirePage(pageCode);
        if (!Objects.equals(page.getStatus(), STATUS_ENABLED)) {
            // 未发布/已下线：不返回任何楼层与 Banner，避免把草稿内容透给用户端。
            return toPageView(page, List.of(), List.of());
        }

        String cacheKey = cacheKey(page.getPageCode(), page.getVersion());
        HomePageView cached = readCache(cacheKey);
        if (cached == null) {
            List<HomeFloor> floors = floorMapper.selectActiveByPageId(
                    page.getId(), LocalDateTime.now());
            cached = aggregate(page, floors);
            writeCache(cacheKey, cached);
        }
        return new HomePageView(
                cached.id(),
                cached.pageCode(),
                cached.pageName(),
                cached.status(),
                cached.version(),
                cached.publishAt(),
                cached.banners(),
                filterByAudience(cached.floors(), userId)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFloor(SaveFloorCommand command) {
        HomePage page = resolveCommandPage(command, DEFAULT_PAGE_CODE);
        HomeFloor floor = new HomeFloor();
        applyFloor(floor, command);
        floor.setPageId(page.getId());
        floorMapper.insert(floor);
        evictCache(page);
        log.info("home floor created id={} pageId={} type={}",
                floor.getId(), page.getId(), floor.getFloorType());
        return floor.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFloor(Long id, SaveFloorCommand command) {
        HomeFloor floor = requireFloor(id);
        HomePage currentPage = requirePageById(floor.getPageId());
        HomePage targetPage = StringUtils.hasText(command == null ? null : command.pageCode())
                ? requirePage(command.pageCode()) : currentPage;

        applyFloor(floor, command);
        floor.setPageId(targetPage.getId());
        floorMapper.updateById(floor);

        evictCache(currentPage);
        if (!Objects.equals(currentPage.getId(), targetPage.getId())) {
            evictCache(targetPage);
        }
        log.info("home floor updated id={} pageId={} type={}",
                id, targetPage.getId(), floor.getFloorType());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFloor(Long id) {
        HomeFloor floor = requireFloor(id);
        HomePage page = pageMapper.selectById(floor.getPageId());
        if (page != null && Objects.equals(page.getStatus(), STATUS_ENABLED)) {
            // 需求：已发布页面删除楼层前必须先下线，避免用户端出现半截页面。
            pageMapper.offline(page.getId());
            log.info("home page offlined before floor deletion pageId={} floorId={}",
                    page.getId(), id);
        }
        if (floorMapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "楼层不存在");
        }
        if (page != null) {
            evictCache(page);
        }
        log.info("home floor deleted id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sortFloors(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "ids 不能为空");
        }
        if (ids.size() > MAX_SORT_SIZE) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "单次最多排序 " + MAX_SORT_SIZE + " 个楼层");
        }
        Set<Long> unique = new HashSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0L) {
                throw new BizException(ErrorCode.PARAM_INVALID, "楼层 ID 不合法");
            }
            if (!unique.add(id)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "楼层 ID 不能重复");
            }
        }

        List<HomeFloor> floors = floorMapper.selectBatchIds(ids);
        if (floors.size() != ids.size()) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "存在已删除或不存在的楼层");
        }
        Long pageId = floors.get(0).getPageId();
        boolean samePage = floors.stream().allMatch(item -> Objects.equals(item.getPageId(), pageId));
        if (!samePage) {
            throw new BizException(ErrorCode.PARAM_INVALID, "一次只能排序同一页面的楼层");
        }

        for (int index = 0; index < ids.size(); index++) {
            if (floorMapper.updateSort(ids.get(index), index + 1) == 0) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "楼层不存在");
            }
        }
        HomePage page = pageMapper.selectById(pageId);
        if (page != null) {
            evictCache(page);
        }
        log.info("home floors sorted pageId={} count={}", pageId, ids.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(String pageCode) {
        HomePage page = requirePage(pageCode);
        List<HomeFloor> floors = floorMapper.selectEnabledByPageId(page.getId());
        validateReferences(floors);

        int currentVersion = page.getVersion() == null ? 0 : page.getVersion();
        int rows = pageMapper.publish(page.getId(), currentVersion, LocalDateTime.now());
        if (rows == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "页面已被其他会话修改，请刷新后重试");
        }
        evictCache(page.getPageCode(), currentVersion);
        evictCache(page.getPageCode(), currentVersion + 1);
        log.info("home page published pageCode={} version={}",
                page.getPageCode(), currentVersion + 1);
    }

    // ------------------------------------------------------------------ 聚合

    /**
     * 一次性查楼层 / Banner / 推荐店铺 / 猜你喜欢 / 公告，再内存组装。
     * 每个数据源最多一次查询，不随楼层数量增加。
     */
    private HomePageView aggregate(HomePage page, List<HomeFloor> floors) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, List<BannerView>> bannersBySlot = bannerMapper
                .selectActiveForHome(now).stream()
                .map(BannerView::from)
                .collect(Collectors.groupingBy(
                        BannerView::slotCode, LinkedHashMap::new, Collectors.toList()));
        List<BannerView> homeBanners = bannersBySlot.getOrDefault("HOME_TOP", List.of());

        boolean needShops = hasFloorType(floors, FloorType.RECOMMEND_SHOP);
        boolean needProducts = hasFloorType(floors, FloorType.GUESS_LIKE);
        boolean needNotices = hasFloorType(floors, FloorType.NOTICE);

        List<RecommendedShopView> shops = needShops
                ? sourceMapper.recommendedShops(AGGREGATE_LIMIT).stream()
                        .map(item -> new RecommendedShopView(item.id(), item.shopName(),
                                item.logoUrl(), item.coverUrl(), item.rating(),
                                item.monthSales(), item.minOrderAmount(),
                                item.deliveryFee(), item.prepMinutes()))
                        .toList()
                : List.of();
        List<RecommendedSpuView> products = needProducts
                ? sourceMapper.guessYouLike(AGGREGATE_LIMIT).stream()
                        .map(item -> new RecommendedSpuView(item.spuId(), item.spuName(),
                                item.subtitle(), item.mainImage(), item.shopId(),
                                item.shopName(), item.price(), item.monthSales()))
                        .toList()
                : List.of();
        List<NoticeBriefView> notices = needNotices
                ? sourceMapper.activeNotices(now, AGGREGATE_LIMIT).stream()
                        .map(item -> new NoticeBriefView(
                                item.id(), item.title(), item.publishAt()))
                        .toList()
                : List.of();

        List<FloorView> views = floors.stream()
                .map(floor -> attach(toFloorView(floor), homeBanners, shops, products, notices))
                .toList();
        return toPageView(page, homeBanners, views);
    }

    private FloorView attach(FloorView floor,
                             List<BannerView> homeBanners,
                             List<RecommendedShopView> shops,
                             List<RecommendedSpuView> products,
                             List<NoticeBriefView> notices) {
        FloorType type = FloorType.fromCode(floor.floorType());
        if (type == null) {
            return floor;
        }
        return switch (type) {
            case BANNER -> floor.withBanners(homeBanners);
            case RECOMMEND_SHOP -> floor.withShops(shops);
            case GUESS_LIKE -> floor.withProducts(products);
            case NOTICE -> floor.withNotices(notices);
            default -> floor;
        };
    }

    private boolean hasFloorType(List<HomeFloor> floors, FloorType type) {
        return floors.stream().anyMatch(item -> Objects.equals(item.getFloorType(), type.getCode()));
    }

    /**
     * 人群过滤：
     * 0 全部；1 新客；2 会员。P0 无法跨域查首单，1 近似为「未登录」，
     * 2 近似为「已登录消费者」；3 指定用户落库但不生效（TODO(P1)）。
     */
    private List<FloorView> filterByAudience(List<FloorView> floors, Long userId) {
        if (floors == null || floors.isEmpty()) {
            return List.of();
        }
        Predicate<Integer> visible = audience -> {
            if (audience == null || audience == 0) {
                return true;
            }
            if (audience == 1) {
                return userId == null;
            }
            if (audience == 2) {
                return userId != null;
            }
            return false;
        };
        return floors.stream()
                .filter(item -> visible.test(item.audience()))
                .toList();
    }

    private HomePageView toPageView(HomePage page, List<BannerView> banners, List<FloorView> floors) {
        return new HomePageView(
                page.getId(),
                page.getPageCode(),
                page.getPageName(),
                page.getStatus(),
                page.getVersion(),
                page.getPublishAt(),
                banners,
                floors
        );
    }

    private FloorView toFloorView(HomeFloor floor) {
        FloorType type = FloorType.fromCode(floor.getFloorType());
        return new FloorView(
                floor.getId(),
                floor.getPageId(),
                floor.getFloorType(),
                type == null ? null : type.getLabel(),
                floor.getTitle(),
                ContentTexts.readConfig(objectMapper, floor.getConfigJson()),
                floor.getSort(),
                floor.getEffectiveFrom(),
                floor.getEffectiveTo(),
                floor.getAudience(),
                floor.getStatus(),
                null, null, null, null
        );
    }

    // ------------------------------------------------------------------ 引用校验

    private void validateReferences(List<HomeFloor> floors) {
        Set<Long> categoryIds = new LinkedHashSet<>();
        Set<Long> shopIds = new LinkedHashSet<>();
        Set<Long> spuIds = new LinkedHashSet<>();
        for (HomeFloor floor : floors) {
            JsonNode config = ContentTexts.readConfig(objectMapper, floor.getConfigJson());
            collectReferences(config, categoryIds, shopIds, spuIds);
        }
        if (categoryIds.isEmpty() && shopIds.isEmpty() && spuIds.isEmpty()) {
            return;
        }

        List<String> invalid = new ArrayList<>();
        if (!categoryIds.isEmpty()) {
            Map<Long, CategoryNode> categories = new HashMap<>();
            flattenCategories(categoryService.tree(), categories);
            for (Long id : categoryIds) {
                CategoryNode node = categories.get(id);
                if (node == null || !Objects.equals(node.status(), CATEGORY_ENABLED)) {
                    invalid.add("categoryId=" + id + " 不存在或已停用");
                }
            }
        }
        for (Long id : shopIds) {
            try {
                if (shopService.detail(id) == null) {
                    invalid.add("shopId=" + id + " 不存在");
                    continue;
                }
                if (!shopService.businessStatus(id).canOrder()) {
                    invalid.add("shopId=" + id + " 未营业");
                }
            } catch (BizException ex) {
                invalid.add("shopId=" + id + " 不存在");
            }
        }
        for (Long id : spuIds) {
            try {
                if (spuService.detail(id) == null) {
                    invalid.add("spuId=" + id + " 不存在");
                }
            } catch (BizException ex) {
                invalid.add("spuId=" + id + " 不存在或未上架");
            }
        }

        if (!invalid.isEmpty()) {
            throw new BizException(ErrorCode.CONTENT_FLOOR_INVALID,
                    "首页楼层引用无效：" + String.join("；", invalid));
        }
    }

    /** config_json 里只识别 categoryId(s) / shopId(s) / spuId(s)，兼容单值与数组。 */
    private void collectReferences(JsonNode node,
                                   Set<Long> categoryIds,
                                   Set<Long> shopIds,
                                   Set<Long> spuIds) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectReferences(item, categoryIds, shopIds, spuIds);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String name = entry.getKey();
            JsonNode value = entry.getValue();
            if ("categoryId".equals(name) || "categoryIds".equals(name)) {
                collectIds(value, categoryIds);
            } else if ("shopId".equals(name) || "shopIds".equals(name)) {
                collectIds(value, shopIds);
            } else if ("spuId".equals(name) || "spuIds".equals(name)) {
                collectIds(value, spuIds);
            }
            collectReferences(value, categoryIds, shopIds, spuIds);
        }
    }

    private void collectIds(JsonNode value, Set<Long> target) {
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isArray()) {
            for (JsonNode item : value) {
                collectIds(item, target);
            }
            return;
        }
        Long id = null;
        if (value.isIntegralNumber() && value.canConvertToLong()) {
            id = value.asLong();
        } else if (value.isTextual()) {
            try {
                id = Long.parseLong(value.asText().strip());
            } catch (NumberFormatException ignored) {
                id = null;
            }
        }
        if (id != null && id > 0L) {
            target.add(id);
        }
    }

    private void flattenCategories(List<CategoryNode> nodes, Map<Long, CategoryNode> target) {
        if (nodes == null) {
            return;
        }
        for (CategoryNode node : nodes) {
            target.put(node.id(), node);
            flattenCategories(node.children(), target);
        }
    }

    // ------------------------------------------------------------------ 楼层写入

    private void applyFloor(HomeFloor floor, SaveFloorCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求参数不能为空");
        }
        FloorType type = FloorType.fromCode(command.floorType());
        if (type == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "floorType 只能是 1~7");
        }
        floor.setFloorType(type.getCode());
        floor.setTitle(ContentTexts.requirePlainText(command.title(), "title", 64));
        floor.setConfigJson(ContentTexts.sanitizeConfigJson(
                objectMapper, command.config(), "config"));

        LocalDateTime from = command.effectiveFrom() == null
                ? LocalDateTime.now() : command.effectiveFrom();
        if (command.effectiveTo() != null && !command.effectiveTo().isAfter(from)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "effectiveTo 必须晚于 effectiveFrom");
        }
        floor.setEffectiveFrom(from);
        floor.setEffectiveTo(command.effectiveTo());

        int audience = command.audience() == null ? 0 : command.audience();
        if (audience < 0 || audience > 3) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "audience 只能是 0全部 / 1新客 / 2会员 / 3指定用户");
        }
        floor.setAudience(audience);
        floor.setAudienceJson(command.audienceConfig() == null ? null
                : ContentTexts.sanitizeConfigJson(
                        objectMapper, command.audienceConfig(), "audienceConfig"));

        int status = command.status() == null ? STATUS_ENABLED : command.status();
        if (status != 0 && status != STATUS_ENABLED) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0停用 / 1启用");
        }
        floor.setStatus(status);
        floor.setSort(command.sort() == null ? 0 : command.sort());
    }

    // ------------------------------------------------------------------ 页面与缓存

    private HomePage requirePage(String pageCode) {
        String normalized = normalizePageCode(pageCode, false);
        HomePage page = pageMapper.selectOne(new LambdaQueryWrapper<HomePage>()
                .eq(HomePage::getPageCode, normalized));
        if (page == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "首页配置不存在");
        }
        return page;
    }

    private HomePage requirePageById(Long pageId) {
        HomePage page = pageMapper.selectById(pageId);
        if (page == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "首页配置不存在");
        }
        return page;
    }

    private HomePage resolveCommandPage(SaveFloorCommand command, String defaultCode) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求参数不能为空");
        }
        String code = StringUtils.hasText(command.pageCode())
                ? command.pageCode() : defaultCode;
        return requirePage(code);
    }

    private HomeFloor requireFloor(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "楼层 ID 不能为空");
        }
        HomeFloor floor = floorMapper.selectById(id);
        if (floor == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "楼层不存在");
        }
        return floor;
    }

    static String normalizePageCode(String pageCode, boolean useDefault) {
        if (!StringUtils.hasText(pageCode)) {
            if (useDefault) {
                return DEFAULT_PAGE_CODE;
            }
            throw new BizException(ErrorCode.PARAM_INVALID, "pageCode 不能为空");
        }
        String code = pageCode.strip().toUpperCase(Locale.ROOT);
        if (!PAGE_CODES.contains(code)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "pageCode 只能是 USER_HOME / USER_MINE");
        }
        return code;
    }

    /**
     * 默认租户保持任务卡约定的 home:{pageCode}:v{version}；
     * 非默认租户追加 :t{tenantId}，避免多租户复用同一页面编码时串页。
     */
    static String cacheKey(String pageCode, Integer version) {
        String base = "home:" + pageCode + ":v" + (version == null ? 0 : version);
        Long tenantId = TenantContext.get();
        if (tenantId == null || TenantContext.DEFAULT_TENANT_ID.equals(tenantId)) {
            return base;
        }
        return base + ":t" + tenantId;
    }

    private HomePageView readCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(json)) {
                return null;
            }
            return objectMapper.readValue(json, HomePageView.class);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("read home cache failed key={} reason={}", key, ex.getMessage());
            return null;
        }
    }

    private void writeCache(String key, HomePageView view) {
        try {
            redisTemplate.opsForValue().set(
                    key, objectMapper.writeValueAsString(view), CACHE_TTL);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            // 缓存只是加速，失败时按实时聚合结果返回。
            log.warn("write home cache failed key={} reason={}", key, ex.getMessage());
        }
    }

    private void evictCache(HomePage page) {
        if (page == null) {
            return;
        }
        evictCache(page.getPageCode(), page.getVersion());
    }

    private void evictCache(String pageCode, Integer version) {
        if (!StringUtils.hasText(pageCode)) {
            return;
        }
        try {
            redisTemplate.delete(cacheKey(pageCode, version));
        } catch (RuntimeException ex) {
            log.warn("evict home cache failed pageCode={} version={} reason={}",
                    pageCode, version, ex.getMessage());
        }
    }
}
