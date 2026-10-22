package com.soloeats.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.product.domain.entity.AddonGroup;
import com.soloeats.product.domain.entity.AddonItem;
import com.soloeats.product.domain.entity.SpecGroup;
import com.soloeats.product.domain.entity.SpecOption;
import com.soloeats.product.domain.entity.SpuAddonRel;
import com.soloeats.product.domain.entity.SpuSpecRel;
import com.soloeats.product.mapper.AddonGroupMapper;
import com.soloeats.product.mapper.AddonItemMapper;
import com.soloeats.product.mapper.SpecGroupMapper;
import com.soloeats.product.mapper.SpecOptionMapper;
import com.soloeats.product.mapper.SpuAddonRelMapper;
import com.soloeats.product.mapper.SpuSpecRelMapper;
import com.soloeats.product.service.ShopMenuService;
import com.soloeats.product.service.SpecTemplateService;
import com.soloeats.product.service.command.SaveAddonTemplateCommand;
import com.soloeats.product.service.command.SaveSpecTemplateCommand;
import com.soloeats.product.service.view.AddonGroupView;
import com.soloeats.product.service.view.SpecGroupView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpecTemplateServiceImpl implements SpecTemplateService {

    private static final int TEMPLATE = 1;
    private static final int MAX_OPTIONS = 20;
    private static final int MAX_ITEMS = 50;

    private final SpecGroupMapper specGroupMapper;
    private final SpecOptionMapper specOptionMapper;
    private final SpuSpecRelMapper spuSpecRelMapper;
    private final AddonGroupMapper addonGroupMapper;
    private final AddonItemMapper addonItemMapper;
    private final SpuAddonRelMapper spuAddonRelMapper;
    private final ShopMenuService shopMenuService;

    @Override
    public List<SpecGroupView> listSpecTemplates() {
        List<SpecGroup> groups = specGroupMapper.selectList(new LambdaQueryWrapper<SpecGroup>()
                .eq(SpecGroup::getIsTemplate, TEMPLATE)
                .orderByAsc(SpecGroup::getSort)
                .orderByAsc(SpecGroup::getId));
        return toSpecViews(groups);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpecGroupView saveSpecTemplate(Long id, SaveSpecTemplateCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        String name = requireName(command.groupName(), "groupName", 64);
        int groupType = command.groupType() == null ? 1 : command.groupType();
        if (groupType != 1 && groupType != 2) {
            throw new BizException(ErrorCode.PARAM_INVALID, "groupType 只能是 1（单选）或 2（多选）");
        }
        int isRequired = command.isRequired() == null ? 0 : command.isRequired();
        if (isRequired != 0 && isRequired != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "isRequired 只能是 0 或 1");
        }
        List<SaveSpecTemplateCommand.Option> options = command.options();
        if (options == null || options.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "规格模板至少需要一个选项");
        }
        if (options.size() > MAX_OPTIONS) {
            throw new BizException(ErrorCode.PARAM_INVALID, "规格选项最多 20 个");
        }

        boolean creating = id == null;
        SpecGroup group;
        if (creating) {
            group = new SpecGroup();
            group.setIsTemplate(TEMPLATE);
        } else {
            group = specGroupMapper.selectById(id);
            if (group == null) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "规格模板不存在");
            }
            if (!Objects.equals(group.getIsTemplate(), TEMPLATE)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "该规格组不是模板");
            }
        }
        group.setGroupName(name);
        group.setGroupType(groupType);
        group.setIsRequired(isRequired);
        group.setSort(command.sort() == null ? 0 : command.sort());
        if (creating) {
            specGroupMapper.insert(group);
        } else {
            specGroupMapper.updateById(group);
        }

        // 整体替换选项：先逻辑删除旧选项，再写入新选项
        specOptionMapper.delete(new LambdaQueryWrapper<SpecOption>()
                .eq(SpecOption::getGroupId, group.getId()));
        int index = 1;
        for (SaveSpecTemplateCommand.Option option : options) {
            SpecOption entity = new SpecOption();
            entity.setGroupId(group.getId());
            entity.setOptionName(requireName(option.optionName(), "optionName", 64));
            entity.setPriceDelta(option.priceDelta() == null ? 0L : option.priceDelta());
            entity.setSort(option.sort() == null ? index : option.sort());
            int status = option.status() == null ? 0 : option.status();
            if (status != 0 && status != 1) {
                throw new BizException(ErrorCode.PARAM_INVALID, "选项 status 只能是 0 或 1");
            }
            entity.setStatus(status);
            specOptionMapper.insert(entity);
            index++;
        }
        shopMenuService.evictAllMenuCache();
        return toSpecViews(List.of(group)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSpecTemplate(Long id) {
        SpecGroup group = specGroupMapper.selectById(requireId(id, "规格模板"));
        if (group == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "规格模板不存在");
        }
        long used = spuSpecRelMapper.selectCount(new LambdaQueryWrapper<SpuSpecRel>()
                .eq(SpuSpecRel::getGroupId, id));
        if (used > 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "规格模板已被商品引用，不能删除");
        }
        specOptionMapper.delete(new LambdaQueryWrapper<SpecOption>()
                .eq(SpecOption::getGroupId, id));
        specGroupMapper.deleteById(id);
        shopMenuService.evictAllMenuCache();
    }

    @Override
    public List<AddonGroupView> listAddonTemplates() {
        List<AddonGroup> groups = addonGroupMapper.selectList(new LambdaQueryWrapper<AddonGroup>()
                .eq(AddonGroup::getIsTemplate, TEMPLATE)
                .orderByAsc(AddonGroup::getSort)
                .orderByAsc(AddonGroup::getId));
        return toAddonViews(groups);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddonGroupView saveAddonTemplate(Long id, SaveAddonTemplateCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        String name = requireName(command.groupName(), "groupName", 64);
        int minSelect = command.minSelect() == null ? 0 : command.minSelect();
        int maxSelect = command.maxSelect() == null ? 0 : command.maxSelect();
        if (minSelect < 0 || maxSelect < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "minSelect/maxSelect 不能为负数");
        }
        if (maxSelect > 0 && maxSelect < minSelect) {
            throw new BizException(ErrorCode.PARAM_INVALID, "maxSelect 不能小于 minSelect");
        }
        List<SaveAddonTemplateCommand.Item> items = command.items();
        if (items == null || items.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "加料模板至少需要一个加料项");
        }
        if (items.size() > MAX_ITEMS) {
            throw new BizException(ErrorCode.PARAM_INVALID, "加料项最多 50 个");
        }

        boolean creating = id == null;
        AddonGroup group;
        if (creating) {
            group = new AddonGroup();
            group.setIsTemplate(TEMPLATE);
        } else {
            group = addonGroupMapper.selectById(id);
            if (group == null) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "加料模板不存在");
            }
            if (!Objects.equals(group.getIsTemplate(), TEMPLATE)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "该加料组不是模板");
            }
        }
        group.setGroupName(name);
        group.setMinSelect(minSelect);
        group.setMaxSelect(maxSelect);
        group.setSort(command.sort() == null ? 0 : command.sort());
        if (creating) {
            addonGroupMapper.insert(group);
        } else {
            addonGroupMapper.updateById(group);
        }

        addonItemMapper.delete(new LambdaQueryWrapper<AddonItem>()
                .eq(AddonItem::getGroupId, group.getId()));
        int index = 1;
        for (SaveAddonTemplateCommand.Item item : items) {
            AddonItem entity = new AddonItem();
            entity.setGroupId(group.getId());
            entity.setItemName(requireName(item.itemName(), "itemName", 64));
            long price = item.price() == null ? 0L : item.price();
            if (price < 0L) {
                throw new BizException(ErrorCode.PARAM_INVALID, "加料价不能为负数（单位分）");
            }
            if (item.stock() != null && item.stock() < 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "加料库存不能为负数");
            }
            entity.setPrice(price);
            entity.setStock(item.stock());
            entity.setSort(item.sort() == null ? index : item.sort());
            int status = item.status() == null ? 0 : item.status();
            if (status != 0 && status != 1) {
                throw new BizException(ErrorCode.PARAM_INVALID, "加料项 status 只能是 0 或 1");
            }
            entity.setStatus(status);
            addonItemMapper.insert(entity);
            index++;
        }
        shopMenuService.evictAllMenuCache();
        return toAddonViews(List.of(group)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAddonTemplate(Long id) {
        AddonGroup group = addonGroupMapper.selectById(requireId(id, "加料模板"));
        if (group == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "加料模板不存在");
        }
        long used = spuAddonRelMapper.selectCount(new LambdaQueryWrapper<SpuAddonRel>()
                .eq(SpuAddonRel::getGroupId, id));
        if (used > 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "加料模板已被商品引用，不能删除");
        }
        addonItemMapper.delete(new LambdaQueryWrapper<AddonItem>()
                .eq(AddonItem::getGroupId, id));
        addonGroupMapper.deleteById(id);
        shopMenuService.evictAllMenuCache();
    }

    @Override
    public List<SpecGroupView> specGroupsOfSpu(Long spuId) {
        List<SpuSpecRel> rels = spuSpecRelMapper.selectList(new LambdaQueryWrapper<SpuSpecRel>()
                .eq(SpuSpecRel::getSpuId, spuId)
                .orderByAsc(SpuSpecRel::getSort));
        if (rels.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> sortByGroup = rels.stream()
                .collect(Collectors.toMap(SpuSpecRel::getGroupId, SpuSpecRel::getSort, (a, b) -> a));
        List<SpecGroup> groups = specGroupMapper.selectBatchIds(sortByGroup.keySet()).stream()
                .sorted(Comparator.comparingInt(group -> sortByGroup.getOrDefault(group.getId(), 0)))
                .toList();
        return toSpecViews(groups);
    }

    @Override
    public List<AddonGroupView> addonGroupsOfSpu(Long spuId) {
        List<SpuAddonRel> rels = spuAddonRelMapper.selectList(new LambdaQueryWrapper<SpuAddonRel>()
                .eq(SpuAddonRel::getSpuId, spuId));
        if (rels.isEmpty()) {
            return List.of();
        }
        List<Long> groupIds = rels.stream().map(SpuAddonRel::getGroupId).distinct().toList();
        List<AddonGroup> groups = addonGroupMapper.selectBatchIds(groupIds).stream()
                .sorted(Comparator.comparingInt(AddonGroup::getSort))
                .toList();
        return toAddonViews(groups);
    }

    private List<SpecGroupView> toSpecViews(List<SpecGroup> groups) {
        if (groups.isEmpty()) {
            return List.of();
        }
        List<Long> groupIds = groups.stream().map(SpecGroup::getId).toList();
        Map<Long, List<SpecOption>> optionsByGroup = specOptionMapper
                .selectList(new LambdaQueryWrapper<SpecOption>()
                        .in(SpecOption::getGroupId, groupIds)
                        .orderByAsc(SpecOption::getSort)
                        .orderByAsc(SpecOption::getId))
                .stream()
                .collect(Collectors.groupingBy(SpecOption::getGroupId, LinkedHashMap::new, Collectors.toList()));
        List<SpecGroupView> views = new ArrayList<>(groups.size());
        for (SpecGroup group : groups) {
            List<SpecGroupView.SpecOptionView> options = optionsByGroup
                    .getOrDefault(group.getId(), List.of()).stream()
                    .map(option -> new SpecGroupView.SpecOptionView(
                            option.getId(), option.getOptionName(), option.getPriceDelta(),
                            option.getSort(), option.getStatus()))
                    .toList();
            views.add(new SpecGroupView(
                    group.getId(), group.getGroupName(), group.getGroupType(),
                    group.getIsRequired(), group.getSort(), options));
        }
        return views;
    }

    private List<AddonGroupView> toAddonViews(List<AddonGroup> groups) {
        if (groups.isEmpty()) {
            return List.of();
        }
        List<Long> groupIds = groups.stream().map(AddonGroup::getId).toList();
        Map<Long, List<AddonItem>> itemsByGroup = addonItemMapper
                .selectList(new LambdaQueryWrapper<AddonItem>()
                        .in(AddonItem::getGroupId, groupIds)
                        .orderByAsc(AddonItem::getSort)
                        .orderByAsc(AddonItem::getId))
                .stream()
                .collect(Collectors.groupingBy(AddonItem::getGroupId, LinkedHashMap::new, Collectors.toList()));
        List<AddonGroupView> views = new ArrayList<>(groups.size());
        for (AddonGroup group : groups) {
            List<AddonGroupView.AddonItemView> items = itemsByGroup
                    .getOrDefault(group.getId(), List.of()).stream()
                    .filter(item -> Objects.equals(item.getStatus(), 0))
                    .map(item -> new AddonGroupView.AddonItemView(
                            item.getId(), item.getItemName(), item.getPrice(),
                            item.getStock(), item.getSort(), item.getStatus()))
                    .toList();
            views.add(new AddonGroupView(
                    group.getId(), group.getGroupName(), group.getMinSelect(),
                    group.getMaxSelect(), group.getSort(), items));
        }
        return views;
    }

    private String requireName(String value, String fieldLabel, int maxLength) {
        String name = value == null ? "" : value.trim();
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.PARAM_INVALID, fieldLabel + " 不能为空");
        }
        if (name.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    fieldLabel + " 长度不能超过 " + maxLength);
        }
        return name;
    }

    private Long requireId(Long id, String label) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, label + "ID必须是正整数");
        }
        return id;
    }
}
