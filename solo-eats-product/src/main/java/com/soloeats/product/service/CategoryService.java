package com.soloeats.product.service;

import com.soloeats.product.service.command.SaveCategoryCommand;
import com.soloeats.product.service.view.CategoryNode;

import java.util.List;

public interface CategoryService {

    List<CategoryNode> tree();

    CategoryNode create(SaveCategoryCommand command);

    CategoryNode update(Long id, SaveCategoryCommand command);

    /**
     * 逻辑删除；存在子品类或 SPU 引用时拒绝。
     */
    void delete(Long id);
}
