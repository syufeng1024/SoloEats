package com.soloeats.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.product.domain.entity.Category;
import com.soloeats.product.domain.entity.Spu;
import com.soloeats.product.mapper.CategoryMapper;
import com.soloeats.product.mapper.SpuMapper;
import com.soloeats.product.service.ShopMenuService;
import com.soloeats.product.service.SpecTemplateService;
import com.soloeats.product.service.SpuService;
import com.soloeats.product.service.command.SaveSpuCommand;
import com.soloeats.product.service.view.SpuDetailView;
import com.soloeats.product.service.view.SpuView;
import com.soloeats.product.support.JsonTexts;
import com.soloeats.product.support.ProductProperties;
import com.soloeats.product.support.ProductTexts;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SpuServiceImpl implements SpuService {

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_PENDING = 1;
    private static final int STATUS_ON_SALE = 2;
    private static final int STATUS_OFF_SALE = 3;

    private static final int MAX_IMAGES = 9;
    private static final int MAX_TAGS = 5;
    private static final int MAX_TAG_LENGTH = 16;

    private final SpuMapper spuMapper;
    private final CategoryMapper categoryMapper;
    private final SpecTemplateService specTemplateService;
    private final ShopMenuService shopMenuService;
    private final ProductProperties productProperties;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<SpuView> adminPage(Long categoryId, Integer status, String keyword,
                                         long pageNum, long pageSize) {
        if (status != null) {
            validateStatus(status);
        }
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = normalizePageSize(pageSize);

        LambdaQueryWrapper<Spu> wrapper = new LambdaQueryWrapper<Spu>()
                .eq(categoryId != null, Spu::getCategoryId, categoryId)
                .eq(status != null, Spu::getStatus, status);
        String sanitized = sanitizeKeyword(keyword);
        if (StringUtils.hasText(sanitized)) {
            wrapper.like(Spu::getSpuName, sanitized);
        }
        wrapper.orderByDesc(Spu::getSortWeight).orderByDesc(Spu::getId);

        Page<Spu> page = spuMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<SpuView> views = page.getRecords().stream().map(this::toView).toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpuView create(SaveSpuCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        Long categoryId = requireCategory(command.categoryId());
        Spu spu = new Spu();
        spu.setCategoryId(categoryId);
        spu.setMerchantId(normalizeMerchantId(command.merchantId()));
        applyFields(spu, command);
        assertNameAvailable(spu.getSpuName(), spu.getMerchantId(), null);
        try {
            spuMapper.insert(spu);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品名称已存在");
        }
        shopMenuService.evictAllMenuCache();
        Spu saved = spuMapper.selectById(spu.getId());
        return toView(saved == null ? spu : saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpuView update(Long id, SaveSpuCommand command) {
        Spu spu = requireSpu(id);
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        if (command.categoryId() != null) {
            spu.setCategoryId(requireCategory(command.categoryId()));
        }
        if (command.merchantId() != null) {
            spu.setMerchantId(normalizeMerchantId(command.merchantId()));
        }
        applyFields(spu, command);
        assertNameAvailable(spu.getSpuName(), spu.getMerchantId(), id);
        try {
            spuMapper.updateById(spu);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品名称已存在");
        }
        shopMenuService.evictAllMenuCache();
        Spu saved = spuMapper.selectById(id);
        return toView(saved == null ? spu : saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        if (status == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 不能为空");
        }
        validateStatus(status);
        Spu spu = requireSpu(id);
        if (status == STATUS_ON_SALE && !StringUtils.hasText(spu.getMainImage())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "上架前必须配置主图");
        }
        int rows = spuMapper.update(null, new LambdaUpdateWrapper<Spu>()
                .eq(Spu::getId, id)
                .eq(Spu::getStatus, spu.getStatus())
                .set(Spu::getStatus, status)
                .set(Spu::getUpdatedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品状态已被修改，请重试");
        }
        shopMenuService.evictAllMenuCache();
    }

    @Override
    public List<SpuView> merchantLibrary(Long merchantId, Long categoryId, String keyword) {
        if (merchantId == null || merchantId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "merchantId 不能为空");
        }
        LambdaQueryWrapper<Spu> wrapper = new LambdaQueryWrapper<Spu>()
                .eq(Spu::getStatus, STATUS_ON_SALE)
                .eq(categoryId != null, Spu::getCategoryId, categoryId)
                .and(w -> w.isNull(Spu::getMerchantId).or().eq(Spu::getMerchantId, merchantId));
        String sanitized = sanitizeKeyword(keyword);
        if (StringUtils.hasText(sanitized)) {
            wrapper.like(Spu::getSpuName, sanitized);
        }
        wrapper.orderByDesc(Spu::getSortWeight).orderByDesc(Spu::getId);
        return spuMapper.selectList(wrapper).stream().map(this::toView).toList();
    }

    @Override
    public SpuDetailView detail(Long spuId) {
        Spu spu = requireSpu(spuId);
        if (!Objects.equals(spu.getStatus(), STATUS_ON_SALE)) {
            throw new BizException(ErrorCode.SPU_NOT_ON_SALE);
        }
        return new SpuDetailView(
                toView(spu),
                specTemplateService.specGroupsOfSpu(spuId),
                specTemplateService.addonGroupsOfSpu(spuId)
        );
    }

    private void applyFields(Spu spu, SaveSpuCommand command) {
        String name = command.spuName() == null ? "" : command.spuName().trim();
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "spuName 不能为空");
        }
        if (name.length() > 128) {
            throw new BizException(ErrorCode.PARAM_INVALID, "spuName 长度不能超过 128");
        }
        spu.setSpuName(name);
        spu.setSubtitle(trimToNull(command.subtitle(), 128, "subtitle"));
        spu.setDescription(ProductTexts.escapeText(command.description(), 500, "description"));
        spu.setDetailHtml(ProductTexts.sanitizeHtml(command.detailHtml()));
        spu.setMainImage(ProductTexts.requireImage(command.mainImage(), productProperties, "mainImage"));
        spu.setImagesJson(writeJson(ProductTexts.requireImages(
                command.images(), productProperties, "images", MAX_IMAGES)));
        spu.setTagsJson(writeJson(normalizeTags(command.tags())));

        long packagingFee = command.packagingFee() == null ? 0L : command.packagingFee();
        if (packagingFee < 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "packagingFee 不能为负数（单位分）");
        }
        spu.setPackagingFee(packagingFee);

        int minPurchaseQty = command.minPurchaseQty() == null ? 1 : command.minPurchaseQty();
        if (minPurchaseQty < 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "minPurchaseQty 不能小于 1");
        }
        spu.setMinPurchaseQty(minPurchaseQty);

        int limitPerOrder = command.limitPerOrder() == null ? 0 : command.limitPerOrder();
        if (limitPerOrder < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "limitPerOrder 不能为负数");
        }
        spu.setLimitPerOrder(limitPerOrder);

        int status = command.status() == null
                ? (spu.getStatus() == null ? STATUS_DRAFT : spu.getStatus())
                : command.status();
        validateStatus(status);
        if (status == STATUS_ON_SALE && !StringUtils.hasText(spu.getMainImage())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "上架商品必须配置主图");
        }
        spu.setStatus(status);
        spu.setSortWeight(command.sortWeight() == null ? 0 : command.sortWeight());
    }

    private SpuView toView(Spu spu) {
        return new SpuView(
                spu.getId(),
                spu.getMerchantId(),
                spu.getCategoryId(),
                spu.getSpuName(),
                spu.getSubtitle(),
                spu.getDescription(),
                ProductTexts.sanitizeHtml(spu.getDetailHtml()),
                spu.getMainImage(),
                JsonTexts.readStringList(objectMapper, spu.getImagesJson()),
                JsonTexts.readStringList(objectMapper, spu.getTagsJson()),
                spu.getPackagingFee(),
                spu.getMinPurchaseQty(),
                spu.getLimitPerOrder(),
                spu.getStatus(),
                spu.getSortWeight(),
                spu.getMonthSales(),
                spu.getUpdatedAt()
        );
    }

    private void assertNameAvailable(String spuName, Long merchantId, Long excludeId) {
        LambdaQueryWrapper<Spu> wrapper = new LambdaQueryWrapper<Spu>()
                .eq(Spu::getSpuName, spuName)
                .ne(excludeId != null, Spu::getId, excludeId);
        if (merchantId == null) {
            wrapper.isNull(Spu::getMerchantId);
        } else {
            wrapper.eq(Spu::getMerchantId, merchantId);
        }
        if (spuMapper.selectCount(wrapper) > 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品名称已存在");
        }
    }

    private Long requireCategory(Long categoryId) {
        if (categoryId == null || categoryId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "categoryId 不能为空");
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BizException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        if (!Objects.equals(category.getLevel(), 2)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品必须挂在二级品类下");
        }
        return categoryId;
    }

    private Spu requireSpu(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "spuId 必须是正整数");
        }
        Spu spu = spuMapper.selectById(id);
        if (spu == null) {
            throw new BizException(ErrorCode.SPU_NOT_FOUND);
        }
        return spu;
    }

    private Long normalizeMerchantId(Long merchantId) {
        if (merchantId == null) {
            return null;
        }
        if (merchantId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "merchantId 必须是正整数");
        }
        return merchantId;
    }

    private void validateStatus(int status) {
        if (status != STATUS_DRAFT && status != STATUS_PENDING
                && status != STATUS_ON_SALE && status != STATUS_OFF_SALE) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0/1/2/3");
        }
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        if (tags.size() > MAX_TAGS) {
            throw new BizException(ErrorCode.PARAM_INVALID, "tags 最多 5 个");
        }
        List<String> values = new ArrayList<>(tags.size());
        for (String tag : tags) {
            if (!StringUtils.hasText(tag)) {
                continue;
            }
            String trimmed = tag.trim();
            if (trimmed.length() > MAX_TAG_LENGTH) {
                throw new BizException(ErrorCode.PARAM_INVALID, "单个标签长度不能超过 16");
            }
            values.add(trimmed);
        }
        return values;
    }

    private String writeJson(Object value) {
        if (value instanceof List<?> list && list.isEmpty()) {
            return null;
        }
        return JsonTexts.write(objectMapper, value);
    }

    private String trimToNull(String value, int maxLength, String fieldLabel) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    fieldLabel + " 长度不能超过 " + maxLength);
        }
        return trimmed;
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
