package com.soloeats.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.product.domain.entity.Sku;
import com.soloeats.product.domain.entity.SkuStock;
import com.soloeats.product.mapper.SkuMapper;
import com.soloeats.product.mapper.SkuStockMapper;
import com.soloeats.product.service.MerchantSkuService;
import com.soloeats.product.service.command.CreateSkuCommand;
import com.soloeats.product.service.command.UpdateSkuCommand;
import com.soloeats.product.service.command.UpdateSkuStockCommand;
import com.soloeats.product.service.view.MerchantSkuView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MerchantSkuServiceImpl implements MerchantSkuService {

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_IMAGE_URL_LENGTH = 512;

    private final SkuMapper skuMapper;
    private final SkuStockMapper skuStockMapper;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<MerchantSkuView> page(Long shopId, Integer status, String keyword,
                                            long pageNum, long pageSize) {
        validateShopId(shopId);
        validateStatus(status);
        long normalizedPageNum = normalizePageNum(pageNum);
        long normalizedPageSize = normalizePageSize(pageSize);

        LambdaQueryWrapper<Sku> wrapper = new LambdaQueryWrapper<Sku>()
                .eq(Sku::getShopId, shopId)
                .eq(status != null, Sku::getStatus, status);
        String sanitized = sanitizeKeyword(keyword);
        if (StringUtils.hasText(sanitized)) {
            wrapper.like(Sku::getSkuName, sanitized);
        }
        wrapper.orderByDesc(Sku::getUpdatedAt).orderByDesc(Sku::getId);

        Page<Sku> page = skuMapper.selectPage(new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        Map<Long, SkuStock> stockBySku = loadStock(shopId, page.getRecords());
        List<MerchantSkuView> views = page.getRecords().stream()
                .map(sku -> toView(sku, stockBySku.get(sku.getId())))
                .toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantSkuView create(Long shopId, CreateSkuCommand command) {
        validateShopId(shopId);
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        if (command.spuId() == null || command.spuId() <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "spuId 必须是正整数");
        }
        String name = normalizeName(command.skuName());
        Long price = normalizePrice(command.price());
        int status = command.status() == null ? 0 : command.status();
        validateStatus(status);
        int initialStock = command.initialStock() == null ? 0 : command.initialStock();
        if (initialStock < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "initialStock 不能为负数");
        }

        Sku sku = new Sku();
        sku.setShopId(shopId);
        sku.setSpuId(command.spuId());
        sku.setSkuName(name);
        sku.setSpecJson(normalizeSpecJson(command.specJson()));
        sku.setImageUrl(normalizeImageUrl(command.imageUrl()));
        sku.setPrice(price);
        sku.setStatus(status);
        skuMapper.insert(sku);

        SkuStock stock = new SkuStock();
        stock.setShopId(shopId);
        stock.setSkuId(sku.getId());
        stock.setAvailableQty(initialStock);
        stock.setLockedQty(0);
        stock.setSoldQty(0);
        stock.setSoldOut(false);
        stock.setVersion(0);
        skuStockMapper.insert(stock);

        // 回读数据库默认值（created_at / updated_at 由 DDL 生成）
        Sku savedSku = skuMapper.selectById(sku.getId());
        SkuStock savedStock = findStock(shopId, sku.getId());
        return toView(savedSku == null ? sku : savedSku, savedStock == null ? stock : savedStock);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantSkuView update(Long shopId, Long skuId, UpdateSkuCommand command) {
        validateShopId(shopId);
        Sku sku = requireSku(shopId, skuId);
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }

        if (command.skuName() != null) {
            sku.setSkuName(normalizeName(command.skuName()));
        }
        if (command.specJson() != null) {
            sku.setSpecJson(normalizeSpecJson(command.specJson()));
        }
        if (command.imageUrl() != null) {
            sku.setImageUrl(normalizeImageUrl(command.imageUrl()));
        }
        if (command.price() != null) {
            sku.setPrice(normalizePrice(command.price()));
        }
        if (command.status() != null) {
            validateStatus(command.status());
            sku.setStatus(command.status());
        }
        skuMapper.updateById(sku);
        Sku savedSku = skuMapper.selectById(skuId);
        return toView(savedSku == null ? sku : savedSku, findStock(shopId, skuId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantSkuView updateStock(Long shopId, Long skuId, UpdateSkuStockCommand command) {
        validateShopId(shopId);
        Sku sku = requireSku(shopId, skuId);
        if (command == null || (command.availableQty() == null && command.soldOut() == null)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "availableQty 与 soldOut 至少提供一个");
        }
        if (command.availableQty() != null && command.availableQty() < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "availableQty 不能为负数");
        }

        SkuStock stock = findStock(shopId, skuId);
        if (stock == null) {
            stock = new SkuStock();
            stock.setShopId(shopId);
            stock.setSkuId(skuId);
            stock.setAvailableQty(command.availableQty() == null ? 0 : command.availableQty());
            stock.setLockedQty(0);
            stock.setSoldQty(0);
            stock.setSoldOut(command.soldOut() != null && command.soldOut());
            stock.setVersion(0);
            skuStockMapper.insert(stock);
            return toView(sku, stock);
        }

        skuStockMapper.updateManualStock(shopId, skuId,
                command.availableQty(), command.soldOut());
        Sku savedSku = skuMapper.selectById(skuId);
        return toView(savedSku == null ? sku : savedSku, findStock(shopId, skuId));
    }

    private Map<Long, SkuStock> loadStock(Long shopId, List<Sku> skus) {
        List<Long> skuIds = skus.stream()
                .map(Sku::getId)
                .filter(Objects::nonNull)
                .toList();
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        return skuStockMapper.selectList(new LambdaQueryWrapper<SkuStock>()
                        .eq(SkuStock::getShopId, shopId)
                        .in(SkuStock::getSkuId, skuIds))
                .stream()
                .collect(Collectors.toMap(SkuStock::getSkuId, stock -> stock));
    }

    private Sku requireSku(Long shopId, Long skuId) {
        if (skuId == null || skuId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "skuId 必须是正整数");
        }
        Sku sku = skuMapper.selectById(skuId);
        if (sku == null || !Objects.equals(sku.getShopId(), shopId)) {
            throw new BizException(ErrorCode.SKU_NOT_FOUND);
        }
        return sku;
    }

    private SkuStock findStock(Long shopId, Long skuId) {
        return skuStockMapper.selectOne(new LambdaQueryWrapper<SkuStock>()
                .eq(SkuStock::getShopId, shopId)
                .eq(SkuStock::getSkuId, skuId)
                .last("LIMIT 1"));
    }

    private MerchantSkuView toView(Sku sku, SkuStock stock) {
        return new MerchantSkuView(
                sku.getId(),
                sku.getShopId(),
                sku.getSpuId(),
                sku.getSkuName(),
                sku.getSpecJson(),
                sku.getImageUrl(),
                sku.getPrice(),
                sku.getStatus(),
                stock == null ? 0 : stock.getAvailableQty(),
                stock == null ? 0 : stock.getLockedQty(),
                stock == null ? 0 : stock.getSoldQty(),
                stock != null && Boolean.TRUE.equals(stock.getSoldOut()),
                stock == null ? 0 : stock.getVersion(),
                sku.getUpdatedAt()
        );
    }

    private void validateShopId(Long shopId) {
        if (shopId == null || shopId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "shopId 不能为空");
        }
    }

    private void validateStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0（上架）或 1（下架）");
        }
    }

    private String normalizeName(String value) {
        String name = value == null ? "" : value.trim();
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "skuName 不能为空");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "skuName 长度不能超过 128");
        }
        return name;
    }

    private Long normalizePrice(Long price) {
        if (price == null || price <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "price 必须大于 0（单位分）");
        }
        return price;
    }

    private String normalizeSpecJson(String specJson) {
        if (!StringUtils.hasText(specJson)) {
            return null;
        }
        try {
            objectMapper.readTree(specJson);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "specJson 必须是合法 JSON");
        }
        return specJson.trim();
    }

    private String normalizeImageUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }
        String url = imageUrl.trim();
        if (url.length() > MAX_IMAGE_URL_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "imageUrl 长度不能超过 512");
        }
        return url;
    }

    private long normalizePageNum(long pageNum) {
        return pageNum < 1L ? 1L : pageNum;
    }

    private long normalizePageSize(long pageSize) {
        if (pageSize < 1L) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String sanitizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keyword.trim().replace("\\", "").replace("%", "").replace("_", "");
    }
}
