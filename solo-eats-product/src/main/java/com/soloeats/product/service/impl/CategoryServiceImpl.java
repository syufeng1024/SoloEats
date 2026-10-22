package com.soloeats.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.product.domain.entity.Category;
import com.soloeats.product.domain.entity.Spu;
import com.soloeats.product.mapper.CategoryMapper;
import com.soloeats.product.mapper.SpuMapper;
import com.soloeats.product.service.CategoryService;
import com.soloeats.product.service.ShopMenuService;
import com.soloeats.product.service.command.SaveCategoryCommand;
import com.soloeats.product.service.view.CategoryNode;
import com.soloeats.product.support.ProductProperties;
import com.soloeats.product.support.ProductTexts;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final int MAX_NAME_LENGTH = 64;
    private static final int STATUS_ENABLED = 0;
    private static final int STATUS_DISABLED = 1;

    private final CategoryMapper categoryMapper;
    private final SpuMapper spuMapper;
    private final ShopMenuService shopMenuService;
    private final ProductProperties productProperties;

    @Override
    public List<CategoryNode> tree() {
        List<Category> all = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getLevel)
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
        Map<Long, List<Category>> childrenByParent = new LinkedHashMap<>();
        for (Category category : all) {
            childrenByParent.computeIfAbsent(category.getParentId(), key -> new ArrayList<>())
                    .add(category);
        }
        return childrenByParent.getOrDefault(0L, List.of()).stream()
                .map(root -> toNode(root, childrenByParent))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryNode create(SaveCategoryCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        long parentId = command.parentId() == null ? 0L : command.parentId();
        int level = 1;
        if (parentId > 0L) {
            Category parent = requireCategory(parentId);
            if (!Objects.equals(parent.getLevel(), 1)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "品类最多两级，父级必须是一级品类");
            }
            level = 2;
        }

        Category category = new Category();
        category.setParentId(parentId);
        category.setLevel(level);
        applyFields(category, command);
        try {
            categoryMapper.insert(category);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "同一父级下品类名称已存在");
        }
        shopMenuService.evictAllMenuCache();
        Category saved = categoryMapper.selectById(category.getId());
        return toNode(saved == null ? category : saved, Map.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryNode update(Long id, SaveCategoryCommand command) {
        Category category = requireCategory(id);
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        long newParentId = command.parentId() == null ? category.getParentId() : command.parentId();
        if (!Objects.equals(newParentId, category.getParentId())) {
            long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                    .eq(Category::getParentId, category.getId()));
            if (childCount > 0L) {
                throw new BizException(ErrorCode.PARAM_INVALID, "存在子品类时不能调整父级");
            }
            int level = 1;
            if (newParentId > 0L) {
                Category parent = requireCategory(newParentId);
                if (!Objects.equals(parent.getLevel(), 1)) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "品类最多两级，父级必须是一级品类");
                }
                level = 2;
            }
            category.setParentId(newParentId);
            category.setLevel(level);
        }
        applyFields(category, command);
        try {
            categoryMapper.updateById(category);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "同一父级下品类名称已存在");
        }
        shopMenuService.evictAllMenuCache();
        Category saved = categoryMapper.selectById(id);
        return toNode(saved == null ? category : saved, Map.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireCategory(id);
        long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, id));
        long spuCount = spuMapper.selectCount(new LambdaQueryWrapper<Spu>()
                .eq(Spu::getCategoryId, id));
        if (childCount > 0L || spuCount > 0L) {
            throw new BizException(ErrorCode.CATEGORY_HAS_CHILDREN);
        }
        categoryMapper.deleteById(id);
        shopMenuService.evictAllMenuCache();
    }

    private void applyFields(Category category, SaveCategoryCommand command) {
        String name = command.categoryName() == null ? "" : command.categoryName().trim();
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "categoryName 不能为空");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "categoryName 长度不能超过 64");
        }
        category.setCategoryName(name);
        category.setIconUrl(ProductTexts.requireImage(command.iconUrl(), productProperties, "iconUrl"));
        category.setSort(command.sort() == null ? 0 : command.sort());
        int status = command.status() == null ? STATUS_ENABLED : command.status();
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0（启用）或 1（停用）");
        }
        category.setStatus(status);
    }

    private Category requireCategory(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "品类ID必须是正整数");
        }
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BizException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return category;
    }

    private CategoryNode toNode(Category category, Map<Long, List<Category>> childrenByParent) {
        List<CategoryNode> children = childrenByParent.getOrDefault(category.getId(), List.of()).stream()
                .map(child -> toNode(child, Map.of()))
                .toList();
        return new CategoryNode(
                category.getId(),
                category.getParentId(),
                category.getCategoryName(),
                category.getIconUrl(),
                category.getLevel(),
                category.getSort(),
                category.getStatus(),
                children
        );
    }
}
