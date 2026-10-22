package com.soloeats.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.product.domain.entity.ShopSpu;
import com.soloeats.product.domain.entity.Sku;
import com.soloeats.product.domain.entity.Spu;
import com.soloeats.product.mapper.ShopSpuMapper;
import com.soloeats.product.mapper.ShopSpuRow;
import com.soloeats.product.mapper.SkuMapper;
import com.soloeats.product.mapper.SpuMapper;
import com.soloeats.product.service.ShopMenuService;
import com.soloeats.product.service.ShopSpuService;
import com.soloeats.product.service.SoldOutService;
import com.soloeats.product.service.command.PutOnShopCommand;
import com.soloeats.product.service.command.UpdateShopSpuCommand;
import com.soloeats.product.service.view.ShopSpuView;
import com.soloeats.product.support.ProductProperties;
import com.soloeats.store.service.ShopService;
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
public class ShopSpuServiceImpl implements ShopSpuService {

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private static final int SPU_ON_SALE = 2;
    private static final int SHOP_SPU_ON = 0;
    private static final int SHOP_SPU_OFF = 1;
    private static final int SHOP_SPU_SOLD_OUT = 2;

    private static final int AUDIT_EXEMPT = 0;
    private static final int AUDIT_PENDING = 1;
    private static final int AUDIT_PASSED = 2;
    private static final int AUDIT_REJECTED = 3;

    private final ShopSpuMapper shopSpuMapper;
    private final SpuMapper spuMapper;
    private final SkuMapper skuMapper;
    private final ShopService shopService;
    private final SoldOutService soldOutService;
    private final ShopMenuService shopMenuService;
    private final ProductProperties productProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopSpuView putOn(Long merchantId, PutOnShopCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        Long shopId = requireShopId(command.shopId());
        requireMerchantId(merchantId);
        shopService.assertMerchantShop(shopId, merchantId, null);

        Long spuId = command.spuId();
        if (spuId == null || spuId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "spuId 必须是正整数");
        }
        Spu spu = spuMapper.selectById(spuId);
        if (spu == null) {
            throw new BizException(ErrorCode.SPU_NOT_FOUND);
        }
        if (!Objects.equals(spu.getStatus(), SPU_ON_SALE)) {
            throw new BizException(ErrorCode.SPU_NOT_ON_SALE);
        }
        if (spu.getMerchantId() != null && !Objects.equals(spu.getMerchantId(), merchantId)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN);
        }

        long exists = shopSpuMapper.selectCount(new LambdaQueryWrapper<ShopSpu>()
                .eq(ShopSpu::getShopId, shopId)
                .eq(ShopSpu::getSpuId, spuId));
        if (exists > 0L) {
            throw new BizException(ErrorCode.SHOP_SPU_EXISTS);
        }

        ShopSpu shopSpu = new ShopSpu();
        shopSpu.setShopId(shopId);
        shopSpu.setSpuId(spuId);
        shopSpu.setPriceOverride(normalizePriceOverride(command.priceOverride()));
        shopSpu.setSortWeight(command.sortWeight() == null ? 0 : command.sortWeight());
        shopSpu.setStatus(SHOP_SPU_ON);
        shopSpu.setAuditStatus(productProperties.isPriceAuditEnabled() ? AUDIT_PENDING : AUDIT_PASSED);
        shopSpu.setMonthSales(0);
        try {
            shopSpuMapper.insert(shopSpu);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.SHOP_SPU_EXISTS);
        }
        shopMenuService.evictMenuCache(shopId);
        return toView(shopSpuMapper.selectById(shopSpu.getId()), spu);
    }

    @Override
    public PageResult<ShopSpuView> page(Long shopId, Integer status, String keyword,
                                        long pageNum, long pageSize) {
        Long requiredShopId = requireShopId(shopId);
        if (status != null && status != SHOP_SPU_ON && status != SHOP_SPU_OFF
                && status != SHOP_SPU_SOLD_OUT) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0/1/2");
        }
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = normalizePageSize(pageSize);
        Page<ShopSpuRow> page = shopSpuMapper.selectShopSpuPage(
                new Page<>(normalizedPageNum, normalizedPageSize),
                requiredShopId, status, null, sanitizeKeyword(keyword));
        List<ShopSpuView> views = page.getRecords().stream().map(this::toView).toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopSpuView update(Long id, Long merchantId, UpdateShopSpuCommand command) {
        ShopSpu shopSpu = requireShopSpu(id);
        requireMerchantId(merchantId);
        shopService.assertMerchantShop(shopSpu.getShopId(), merchantId, null);
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        Long newPrice = normalizePriceOverride(command.priceOverride());
        boolean priceChanged = !Objects.equals(shopSpu.getPriceOverride(), newPrice);
        shopSpu.setPriceOverride(newPrice);
        if (command.sortWeight() != null) {
            shopSpu.setSortWeight(command.sortWeight());
        }
        if (priceChanged && productProperties.isPriceAuditEnabled()) {
            shopSpu.setAuditStatus(AUDIT_PENDING);
        } else if (priceChanged) {
            shopSpu.setAuditStatus(AUDIT_PASSED);
        }
        shopSpuMapper.updateById(shopSpu);
        shopMenuService.evictMenuCache(shopSpu.getShopId());
        return toView(shopSpuMapper.selectById(id), spuMapper.selectById(shopSpu.getSpuId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Long merchantId, Integer status) {
        if (status == null || (status != SHOP_SPU_ON && status != SHOP_SPU_OFF
                && status != SHOP_SPU_SOLD_OUT)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0上架/1下架/2估清");
        }
        ShopSpu shopSpu = requireShopSpu(id);
        requireMerchantId(merchantId);
        shopService.assertMerchantShop(shopSpu.getShopId(), merchantId, null);

        if (status == SHOP_SPU_SOLD_OUT) {
            // 估清走 SoldOutService，保证 SKU 库存/记录/门店商品状态三者同一事务
            soldOutService.markBySpu(shopSpu.getShopId(), shopSpu.getSpuId(),
                    "门店商品估清", operatorId(), operatorName());
            return;
        }
        int fromStatus = shopSpu.getStatus();
        if (Objects.equals(fromStatus, SHOP_SPU_SOLD_OUT)) {
            soldOutService.restoreBySpu(shopSpu.getShopId(), shopSpu.getSpuId(),
                    operatorId(), operatorName());
            // restoreBySpu 已把门店商品置为 0上架
            fromStatus = SHOP_SPU_ON;
            if (status == SHOP_SPU_ON) {
                return;
            }
        }
        int rows = shopSpuMapper.update(null, new LambdaUpdateWrapper<ShopSpu>()
                .eq(ShopSpu::getId, id)
                .eq(ShopSpu::getStatus, fromStatus)
                .set(ShopSpu::getStatus, status)
                .set(ShopSpu::getUpdatedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "门店商品状态已被修改，请重试");
        }
        shopMenuService.evictMenuCache(shopSpu.getShopId());
    }

    @Override
    public PageResult<ShopSpuView> adminPage(Long shopId, Integer auditStatus, String keyword,
                                             long pageNum, long pageSize) {
        if (auditStatus != null && (auditStatus < AUDIT_EXEMPT || auditStatus > AUDIT_REJECTED)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "auditStatus 只能是 0/1/2/3");
        }
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = normalizePageSize(pageSize);
        Page<ShopSpuRow> page = shopSpuMapper.selectShopSpuPage(
                new Page<>(normalizedPageNum, normalizedPageSize),
                shopId, null, auditStatus, sanitizeKeyword(keyword));
        List<ShopSpuView> views = page.getRecords().stream().map(this::toView).toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopSpuView audit(Long id, Integer auditStatus, String remark) {
        if (auditStatus == null || (auditStatus != AUDIT_PASSED && auditStatus != AUDIT_REJECTED)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "auditStatus 只能是 2通过 / 3驳回");
        }
        ShopSpu shopSpu = requireShopSpu(id);
        if (!Objects.equals(shopSpu.getAuditStatus(), AUDIT_PENDING)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该门店商品不在待审状态");
        }
        int targetStatus = auditStatus == AUDIT_PASSED ? SHOP_SPU_ON : SHOP_SPU_OFF;
        int rows = shopSpuMapper.update(null, new LambdaUpdateWrapper<ShopSpu>()
                .eq(ShopSpu::getId, id)
                .eq(ShopSpu::getAuditStatus, AUDIT_PENDING)
                .set(ShopSpu::getAuditStatus, auditStatus)
                .set(ShopSpu::getStatus, targetStatus)
                .set(ShopSpu::getRemark, trimToNull(remark, 255))
                .set(ShopSpu::getUpdatedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审核状态已被修改，请重试");
        }
        shopMenuService.evictMenuCache(shopSpu.getShopId());
        return toView(shopSpuMapper.selectById(id), spuMapper.selectById(shopSpu.getSpuId()));
    }

    private ShopSpuView toView(ShopSpuRow row) {
        return new ShopSpuView(
                row.getId(), row.getShopId(), row.getSpuId(), row.getSpuName(), row.getMainImage(),
                row.getPriceOverride(), row.getSkuMinPrice(), row.getStatus(), row.getAuditStatus(),
                row.getSortWeight(), row.getMonthSales(), row.getSuspendedUntil(), row.getRemark(),
                row.getUpdatedAt()
        );
    }

    private ShopSpuView toView(ShopSpu shopSpu, Spu spu) {
        if (shopSpu == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "门店商品不存在");
        }
        Long minPrice = null;
        List<Sku> skus = skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                .eq(Sku::getShopId, shopSpu.getShopId())
                .eq(Sku::getSpuId, shopSpu.getSpuId())
                .eq(Sku::getStatus, 0));
        for (Sku sku : skus) {
            if (minPrice == null || sku.getPrice() < minPrice) {
                minPrice = sku.getPrice();
            }
        }
        return new ShopSpuView(
                shopSpu.getId(),
                shopSpu.getShopId(),
                shopSpu.getSpuId(),
                spu == null ? null : spu.getSpuName(),
                spu == null ? null : spu.getMainImage(),
                shopSpu.getPriceOverride(),
                minPrice,
                shopSpu.getStatus(),
                shopSpu.getAuditStatus(),
                shopSpu.getSortWeight(),
                shopSpu.getMonthSales(),
                shopSpu.getSuspendedUntil(),
                shopSpu.getRemark(),
                shopSpu.getUpdatedAt()
        );
    }

    private ShopSpu requireShopSpu(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "门店商品ID必须是正整数");
        }
        ShopSpu shopSpu = shopSpuMapper.selectById(id);
        if (shopSpu == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "门店商品不存在");
        }
        return shopSpu;
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

    private Long normalizePriceOverride(Long price) {
        if (price == null) {
            return null;
        }
        if (price <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "priceOverride 必须大于 0（单位分）");
        }
        return price;
    }

    private Long operatorId() {
        UserPrincipal principal = AuthContext.current();
        return principal == null ? null : principal.userId();
    }

    private String operatorName() {
        UserPrincipal principal = AuthContext.current();
        return principal == null ? null : principal.username();
    }

    private String trimToNull(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private String sanitizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keyword.trim().replace("\\", "").replace("%", "").replace("_", "");
    }

    private long normalizePageSize(long pageSize) {
        if (pageSize < 1L) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
