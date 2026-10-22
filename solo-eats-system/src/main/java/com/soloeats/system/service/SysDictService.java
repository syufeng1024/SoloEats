package com.soloeats.system.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.system.service.command.SaveDictItemCommand;
import com.soloeats.system.service.command.SaveDictTypeCommand;
import com.soloeats.system.service.view.DictItemView;
import com.soloeats.system.service.view.DictTypeView;

import java.util.List;

/**
 * 数据字典。字典项读取走 Redis 缓存 {@code dict:{tenantId}:{dictType}}，TTL 1 小时。
 */
public interface SysDictService {

    PageResult<DictTypeView> pageTypes(String dictType, Integer status, long pageNum, long pageSize);

    /**
     * 启用状态的字典项，按 dictSort 升序；供前端渲染枚举，允许匿名读取。
     */
    List<DictItemView> listItems(String dictType);

    DictTypeView createType(SaveDictTypeCommand command);

    DictTypeView updateType(Long id, SaveDictTypeCommand command);

    /**
     * 逻辑删除字典类型，同时逻辑删除其字典项并清理缓存。
     */
    void deleteType(Long id);

    DictItemView createItem(SaveDictItemCommand command);

    DictItemView updateItem(Long id, SaveDictItemCommand command);

    void deleteItem(Long id);
}
