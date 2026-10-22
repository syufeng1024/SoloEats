package com.soloeats.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.product.domain.entity.ShopSpu;
import com.soloeats.product.domain.entity.Sku;
import com.soloeats.product.domain.entity.SkuStock;
import com.soloeats.product.domain.entity.SoldOutRecord;
import com.soloeats.product.mapper.ShopSpuMapper;
import com.soloeats.product.mapper.SkuMapper;
import com.soloeats.product.mapper.SkuStockMapper;
import com.soloeats.product.mapper.SoldOutRecordMapper;
import com.soloeats.product.service.ShopMenuService;
import com.soloeats.product.service.SoldOutService;
import com.soloeats.product.service.command.SoldOutCommand;
import com.soloeats.product.service.view.ReasonStatView;
import com.soloeats.product.service.view.SoldOutRecordView;
import com.soloeats.store.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 估清/恢复。三方一致性：t_shop_spu.status=2、t_sku_stock.sold_out=1、
 * t_sold_out_record 未恢复记录，全部在同一事务内完成。
 */
@Service
@RequiredArgsConstructor
public class SoldOutServiceImpl implements SoldOutService {

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int MAX_BATCH = 200;

    private static final int SHOP_SPU_ON = 0;
    private static final int SHOP_SPU_SOLD_OUT = 2;
    private final SkuMapper skuMapper;
    private final SkuStockMapper skuStockMapper;
    private final ShopSpuMapper shopSpuMapper;
    private final SoldOutRecordMapper soldOutRecordMapper;
    private final ShopService shopService;
    private final ShopMenuService shopMenuService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSoldOut(Long merchantId, SoldOutCommand command, Long operatorId, String operatorName) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        Long shopId = requireShopId(command.shopId());
        requireMerchantId(merchantId);
        shopService.assertMerchantShop(shopId, merchantId, null);
        String reason = requireReason(command.reason());
        List<Long> skuIds = normalizeSkuIds(command.skuIds());
        doMark(shopId, skuIds, reason, operatorId, operatorName);
        shopMenuService.evictMenuCache(shopId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long merchantId, SoldOutCommand command, Long operatorId, String operatorName) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        Long shopId = requireShopId(command.shopId());
        requireMerchantId(merchantId);
        shopService.assertMerchantShop(shopId, merchantId, null);
        List<Long> skuIds = normalizeSkuIds(command.skuIds());
        doRestore(shopId, skuIds);
        shopMenuService.evictMenuCache(shopId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markBySpu(Long shopId, Long spuId, String reason,
                          Long operatorId, String operatorName) {
        requireShopId(shopId);
        if (spuId == null || spuId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "spuId 必须是正整数");
        }
        String normalizedReason = requireReason(reason);
        List<Long> skuIds = skuIdsOfSpu(shopId, spuId);
        if (skuIds.isEmpty()) {
            throw new BizException(ErrorCode.SPU_NOT_FOUND, "该商品在门店没有可估清的 SKU");
        }
        doMark(shopId, skuIds, normalizedReason, operatorId, operatorName);
        shopMenuService.evictMenuCache(shopId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreBySpu(Long shopId, Long spuId, Long operatorId, String operatorName) {
        requireShopId(shopId);
        if (spuId == null || spuId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "spuId 必须是正整数");
        }
        doRestore(shopId, skuIdsOfSpu(shopId, spuId));
        soldOutRecordMapper.markRestoredBySpu(shopId, spuId);
        updateShopSpuStatus(shopId, spuId, SHOP_SPU_ON);
        shopMenuService.evictMenuCache(shopId);
    }

    @Override
    public PageResult<SoldOutRecordView> adminPage(Long shopId, Long skuId, String reason,
                                                   LocalDateTime start, LocalDateTime end,
                                                   long pageNum, long pageSize) {
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = pageSize < 1L ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        LambdaQueryWrapper<SoldOutRecord> wrapper = new LambdaQueryWrapper<SoldOutRecord>()
                .eq(shopId != null, SoldOutRecord::getShopId, shopId)
                .eq(skuId != null, SoldOutRecord::getSkuId, skuId)
                .like(StringUtils.hasText(reason), SoldOutRecord::getReason, sanitizeKeyword(reason))
                .ge(start != null, SoldOutRecord::getSoldOutAt, start)
                .lt(end != null, SoldOutRecord::getSoldOutAt, end)
                .orderByDesc(SoldOutRecord::getSoldOutAt)
                .orderByDesc(SoldOutRecord::getId);
        Page<SoldOutRecord> page = soldOutRecordMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<SoldOutRecordView> views = page.getRecords().stream()
                .map(record -> new SoldOutRecordView(
                        record.getId(), record.getShopId(), record.getSkuId(), record.getSpuId(),
                        record.getReason(), record.getSoldOutAt(), record.getRestoredAt(),
                        record.getOperatorId(), record.getOperatorName()))
                .toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public List<ReasonStatView> reasonStats(Long shopId, LocalDateTime start, LocalDateTime end) {
        Long requiredShopId = requireShopId(shopId);
        LocalDateTime rangeEnd = end == null ? LocalDateTime.now() : end;
        LocalDateTime rangeStart = start == null ? rangeEnd.minusDays(30) : start;
        if (!rangeStart.isBefore(rangeEnd)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "开始时间必须早于结束时间");
        }
        return soldOutRecordMapper.selectReasonStats(requiredShopId, rangeStart, rangeEnd).stream()
                .map(row -> new ReasonStatView(row.getReason(), row.getCount() == null ? 0L : row.getCount()))
                .toList();
    }

    private void doMark(Long shopId, List<Long> skuIds, String reason,
                        Long operatorId, String operatorName) {
        List<Sku> skus = requireSkus(shopId, skuIds);
        Set<Long> spuIds = new LinkedHashSet<>();
        for (Sku sku : skus) {
            SkuStock stock = findStock(shopId, sku.getId());
            boolean alreadySoldOut = stock != null && Boolean.TRUE.equals(stock.getSoldOut());
            if (stock == null) {
                SkuStock created = new SkuStock();
                created.setShopId(shopId);
                created.setSkuId(sku.getId());
                created.setAvailableQty(0);
                created.setLockedQty(0);
                created.setSoldQty(0);
                created.setSoldOut(true);
                created.setVersion(0);
                skuStockMapper.insert(created);
            } else {
                skuStockMapper.updateManualStock(shopId, sku.getId(), null, true);
            }
            if (!alreadySoldOut) {
                SoldOutRecord record = new SoldOutRecord();
                record.setShopId(shopId);
                record.setSkuId(sku.getId());
                record.setSpuId(sku.getSpuId());
                record.setReason(reason);
                record.setSoldOutAt(LocalDateTime.now());
                record.setOperatorId(operatorId);
                record.setOperatorName(operatorName);
                soldOutRecordMapper.insert(record);
            }
            spuIds.add(sku.getSpuId());
        }
        for (Long spuId : spuIds) {
            updateShopSpuStatus(shopId, spuId, SHOP_SPU_SOLD_OUT);
        }
    }

    private void doRestore(Long shopId, List<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return;
        }
        List<Sku> skus = requireSkus(shopId, skuIds);
        Set<Long> spuIds = new LinkedHashSet<>();
        for (Sku sku : skus) {
            if (findStock(shopId, sku.getId()) != null) {
                skuStockMapper.updateManualStock(shopId, sku.getId(), null, false);
            }
            soldOutRecordMapper.markRestored(shopId, sku.getId());
            spuIds.add(sku.getSpuId());
        }
        for (Long spuId : spuIds) {
            if (countSoldOutSkus(shopId, spuId) == 0L) {
                updateShopSpuStatus(shopId, spuId, SHOP_SPU_ON);
            }
        }
    }

    private List<Sku> requireSkus(Long shopId, List<Long> skuIds) {
        List<Sku> skus = skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                .eq(Sku::getShopId, shopId)
                .in(Sku::getId, skuIds));
        if (skus.size() != new LinkedHashSet<>(skuIds).size()) {
            throw new BizException(ErrorCode.SKU_NOT_FOUND, "存在不属于该门店的 SKU");
        }
        return skus;
    }

    private List<Long> skuIdsOfSpu(Long shopId, Long spuId) {
        // 估清/恢复覆盖该 SPU 的全部 SKU；下架 SKU 的 sold_out 若残留会阻塞门店商品恢复
        return skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                        .eq(Sku::getShopId, shopId)
                        .eq(Sku::getSpuId, spuId))
                .stream()
                .map(Sku::getId)
                .toList();
    }

    private long countSoldOutSkus(Long shopId, Long spuId) {
        List<Long> skuIds = skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                        .eq(Sku::getShopId, shopId)
                        .eq(Sku::getSpuId, spuId))
                .stream()
                .map(Sku::getId)
                .toList();
        if (skuIds.isEmpty()) {
            return 0L;
        }
        return skuStockMapper.selectCount(new LambdaQueryWrapper<SkuStock>()
                .eq(SkuStock::getShopId, shopId)
                .in(SkuStock::getSkuId, skuIds)
                .eq(SkuStock::getSoldOut, true));
    }

    private void updateShopSpuStatus(Long shopId, Long spuId, int status) {
        shopSpuMapper.update(null, new LambdaUpdateWrapper<ShopSpu>()
                .eq(ShopSpu::getShopId, shopId)
                .eq(ShopSpu::getSpuId, spuId)
                .ne(ShopSpu::getStatus, status)
                .set(ShopSpu::getStatus, status)
                .set(ShopSpu::getUpdatedAt, LocalDateTime.now()));
    }

    private SkuStock findStock(Long shopId, Long skuId) {
        return skuStockMapper.selectOne(new LambdaQueryWrapper<SkuStock>()
                .eq(SkuStock::getShopId, shopId)
                .eq(SkuStock::getSkuId, skuId)
                .last("LIMIT 1"));
    }

    private List<Long> normalizeSkuIds(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "skuIds 不能为空");
        }
        List<Long> values = new ArrayList<>(new LinkedHashSet<>(skuIds));
        if (values.size() > MAX_BATCH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单次最多处理 200 个 SKU");
        }
        for (Long skuId : values) {
            if (skuId == null || skuId <= 0L) {
                throw new BizException(ErrorCode.PARAM_INVALID, "skuId 必须是正整数");
            }
        }
        return values;
    }

    private Long requireShopId(Long shopId) {
        if (shopId == null || shopId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "shopId 不能为空");
        }
        return shopId;
    }

    private void requireMerchantId(Long merchantId) {
        if (merchantId == null || merchantId <= 0L) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "令牌缺少 merchantId");
        }
    }

    private String requireReason(String reason) {
        String value = reason == null ? "" : reason.trim();
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "估清原因不能为空");
        }
        if (value.length() > 128) {
            throw new BizException(ErrorCode.PARAM_INVALID, "估清原因长度不能超过 128");
        }
        return value;
    }

    private String sanitizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keyword.trim().replace("\\", "").replace("%", "").replace("_", "");
    }
}
