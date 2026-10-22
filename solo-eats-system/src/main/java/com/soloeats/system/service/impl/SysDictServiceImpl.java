package com.soloeats.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.system.domain.entity.SysDictItem;
import com.soloeats.system.domain.entity.SysDictType;
import com.soloeats.system.mapper.SysDictItemMapper;
import com.soloeats.system.mapper.SysDictTypeMapper;
import com.soloeats.system.service.SysDictService;
import com.soloeats.system.service.command.SaveDictItemCommand;
import com.soloeats.system.service.command.SaveDictTypeCommand;
import com.soloeats.system.service.view.DictItemView;
import com.soloeats.system.service.view.DictTypeView;
import com.soloeats.system.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysDictServiceImpl implements SysDictService {

    public static final String CACHE_KEY_PREFIX = "dict:";
    public static final Duration CACHE_TTL = Duration.ofHours(1);

    private static final int STATUS_ENABLED = 0;
    private static final int STATUS_DISABLED = 1;

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private static final int MAX_DICT_TYPE_LENGTH = 64;
    private static final int MAX_DICT_NAME_LENGTH = 64;
    private static final int MAX_LABEL_LENGTH = 128;
    private static final int MAX_VALUE_LENGTH = 128;
    private static final int MAX_CSS_CLASS_LENGTH = 64;
    private static final int MAX_REMARK_LENGTH = 255;

    private static final Pattern DICT_TYPE_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{1,63}$");

    private final SysDictTypeMapper sysDictTypeMapper;
    private final SysDictItemMapper sysDictItemMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<DictTypeView> pageTypes(String dictType, Integer status,
                                              long pageNum, long pageSize) {
        if (status != null) {
            requireStatus(status);
        }
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = normalizePageSize(pageSize);

        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getTenantId, TenantContext.get())
                .eq(SysDictType::getDeleted, 0)
                .eq(status != null, SysDictType::getStatus, status);

        String sanitized = sanitizeKeyword(dictType);
        if (StringUtils.hasText(sanitized)) {
            wrapper.and(w -> w.like(SysDictType::getDictType, sanitized)
                    .or()
                    .like(SysDictType::getDictName, sanitized));
        }
        wrapper.orderByAsc(SysDictType::getId);

        Page<SysDictType> page = sysDictTypeMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<DictTypeView> views = page.getRecords().stream().map(this::toView).toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public List<DictItemView> listItems(String dictType) {
        String type = requireDictTypeParam(dictType);
        Long tenantId = TenantContext.get();
        String key = cacheKey(tenantId, type);

        List<DictItemView> cached = readCache(key);
        if (cached != null) {
            return cached;
        }

        List<SysDictItem> items = sysDictItemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTenantId, tenantId)
                .eq(SysDictItem::getDictType, type)
                .eq(SysDictItem::getDeleted, 0)
                .eq(SysDictItem::getStatus, STATUS_ENABLED)
                .orderByAsc(SysDictItem::getDictSort)
                .orderByAsc(SysDictItem::getId));
        List<DictItemView> views = items.stream().map(this::toView).toList();
        writeCache(key, views);
        return views;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictTypeView createType(SaveDictTypeCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        String dictType = requireDictType(command.dictType());
        assertDictTypeAvailable(dictType, null);

        SysDictType type = new SysDictType();
        type.setTenantId(TenantContext.get());
        type.setDictType(dictType);
        type.setDictName(requireText(command.dictName(), MAX_DICT_NAME_LENGTH, "dictName"));
        type.setStatus(command.status() == null ? STATUS_ENABLED : requireStatus(command.status()));
        type.setRemark(normalizeOptionalText(command.remark(), MAX_REMARK_LENGTH, "remark"));
        type.setDeleted(0);

        try {
            sysDictTypeMapper.insert(type);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "字典类型已存在");
        }
        log.info("sys dict type created id={} dictType={}", type.getId(), dictType);
        return toView(type);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictTypeView updateType(Long id, SaveDictTypeCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        SysDictType type = requireTypeById(id);
        if (command.dictType() != null && !command.dictType().trim().equals(type.getDictType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "dictType 不允许变更");
        }

        type.setDictName(command.dictName() == null
                ? type.getDictName()
                : requireText(command.dictName(), MAX_DICT_NAME_LENGTH, "dictName"));
        type.setStatus(command.status() == null ? type.getStatus() : requireStatus(command.status()));
        type.setRemark(command.remark() == null
                ? type.getRemark()
                : normalizeOptionalText(command.remark(), MAX_REMARK_LENGTH, "remark"));

        if (sysDictTypeMapper.updateById(type) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "字典类型不存在");
        }
        evictCache(type.getTenantId(), type.getDictType());
        log.info("sys dict type updated id={} dictType={}", type.getId(), type.getDictType());
        return toView(type);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long id) {
        SysDictType type = requireTypeById(id);
        type.setDeleted(1);
        if (sysDictTypeMapper.updateById(type) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "字典类型不存在");
        }
        // 字典项跟随类型一起逻辑删除，避免残留项继续被 listItems 读到
        sysDictItemMapper.update(null, new LambdaUpdateWrapper<SysDictItem>()
                .eq(SysDictItem::getTenantId, type.getTenantId())
                .eq(SysDictItem::getDictType, type.getDictType())
                .eq(SysDictItem::getDeleted, 0)
                .set(SysDictItem::getDeleted, 1));
        evictCache(type.getTenantId(), type.getDictType());
        log.info("sys dict type logically deleted id={} dictType={}", id, type.getDictType());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictItemView createItem(SaveDictItemCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        String dictType = requireDictType(command.dictType());
        requireTypeByCode(dictType);
        String dictValue = requireText(command.dictValue(), MAX_VALUE_LENGTH, "dictValue");
        assertDictValueAvailable(dictType, dictValue, null);
        int isDefault = normalizeDefault(command.isDefault());

        SysDictItem item = new SysDictItem();
        item.setTenantId(TenantContext.get());
        item.setDictType(dictType);
        item.setDictLabel(requireText(command.dictLabel(), MAX_LABEL_LENGTH, "dictLabel"));
        item.setDictValue(dictValue);
        item.setDictSort(command.dictSort() == null ? 0 : command.dictSort());
        item.setCssClass(normalizeOptionalText(
                command.cssClass(), MAX_CSS_CLASS_LENGTH, "cssClass"));
        item.setIsDefault(isDefault);
        item.setStatus(command.status() == null ? STATUS_ENABLED : requireStatus(command.status()));
        item.setRemark(normalizeOptionalText(command.remark(), MAX_REMARK_LENGTH, "remark"));
        item.setDeleted(0);

        if (isDefault == 1) {
            clearOtherDefaults(item.getTenantId(), dictType, null);
        }
        sysDictItemMapper.insert(item);
        evictCache(item.getTenantId(), dictType);
        log.info("sys dict item created id={} dictType={} isDefault={}",
                item.getId(), dictType, isDefault);
        return toView(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictItemView updateItem(Long id, SaveDictItemCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        SysDictItem item = requireItem(id);
        if (command.dictType() != null && !command.dictType().trim().equals(item.getDictType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "dictType 不允许变更");
        }

        if (command.dictValue() != null) {
            String dictValue = requireText(command.dictValue(), MAX_VALUE_LENGTH, "dictValue");
            assertDictValueAvailable(item.getDictType(), dictValue, item.getId());
            item.setDictValue(dictValue);
        }
        item.setDictLabel(command.dictLabel() == null
                ? item.getDictLabel()
                : requireText(command.dictLabel(), MAX_LABEL_LENGTH, "dictLabel"));
        item.setDictSort(command.dictSort() == null ? item.getDictSort() : command.dictSort());
        item.setCssClass(command.cssClass() == null
                ? item.getCssClass()
                : normalizeOptionalText(command.cssClass(), MAX_CSS_CLASS_LENGTH, "cssClass"));
        item.setStatus(command.status() == null ? item.getStatus() : requireStatus(command.status()));
        item.setRemark(command.remark() == null
                ? item.getRemark()
                : normalizeOptionalText(command.remark(), MAX_REMARK_LENGTH, "remark"));
        int isDefault = command.isDefault() == null
                ? (item.getIsDefault() == null ? 0 : item.getIsDefault())
                : normalizeDefault(command.isDefault());
        item.setIsDefault(isDefault);

        if (isDefault == 1) {
            clearOtherDefaults(item.getTenantId(), item.getDictType(), item.getId());
        }
        if (sysDictItemMapper.updateById(item) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "字典项不存在");
        }
        evictCache(item.getTenantId(), item.getDictType());
        log.info("sys dict item updated id={} dictType={}", item.getId(), item.getDictType());
        return toView(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long id) {
        SysDictItem item = requireItem(id);
        item.setDeleted(1);
        if (sysDictItemMapper.updateById(item) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "字典项不存在");
        }
        evictCache(item.getTenantId(), item.getDictType());
        log.info("sys dict item logically deleted id={} dictType={}", id, item.getDictType());
    }

    /**
     * 同一 dict_type 只保留一个默认项；传入 excludeId 时表示修改场景跳过自身。
     */
    private void clearOtherDefaults(Long tenantId, String dictType, Long excludeId) {
        sysDictItemMapper.update(null, new LambdaUpdateWrapper<SysDictItem>()
                .eq(SysDictItem::getTenantId, tenantId)
                .eq(SysDictItem::getDictType, dictType)
                .eq(SysDictItem::getIsDefault, 1)
                .eq(SysDictItem::getDeleted, 0)
                .ne(excludeId != null, SysDictItem::getId, excludeId)
                .set(SysDictItem::getIsDefault, 0));
    }

    private SysDictType requireTypeById(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "字典类型 ID 必须是正整数");
        }
        SysDictType type = sysDictTypeMapper.selectOne(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getTenantId, TenantContext.get())
                .eq(SysDictType::getId, id)
                .eq(SysDictType::getDeleted, 0)
                .last("LIMIT 1"));
        if (type == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "字典类型不存在");
        }
        return type;
    }

    private void requireTypeByCode(String dictType) {
        Long count = sysDictTypeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getTenantId, TenantContext.get())
                .eq(SysDictType::getDictType, dictType)
                .eq(SysDictType::getDeleted, 0));
        if (count == null || count == 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "字典类型不存在: " + dictType);
        }
    }

    private SysDictItem requireItem(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "字典项 ID 必须是正整数");
        }
        SysDictItem item = sysDictItemMapper.selectOne(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTenantId, TenantContext.get())
                .eq(SysDictItem::getId, id)
                .eq(SysDictItem::getDeleted, 0)
                .last("LIMIT 1"));
        if (item == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "字典项不存在");
        }
        return item;
    }

    private void assertDictTypeAvailable(String dictType, Long excludeId) {
        Long count = sysDictTypeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getTenantId, TenantContext.get())
                .eq(SysDictType::getDictType, dictType)
                .ne(excludeId != null, SysDictType::getId, excludeId));
        if (count != null && count > 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "字典类型已存在");
        }
    }

    private void assertDictValueAvailable(String dictType, String dictValue, Long excludeId) {
        Long count = sysDictItemMapper.selectCount(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTenantId, TenantContext.get())
                .eq(SysDictItem::getDictType, dictType)
                .eq(SysDictItem::getDictValue, dictValue)
                .eq(SysDictItem::getDeleted, 0)
                .ne(excludeId != null, SysDictItem::getId, excludeId));
        if (count != null && count > 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "同一字典类型下 dictValue 不能重复");
        }
    }

    private List<DictItemView> readCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(json)) {
                return null;
            }
            return objectMapper.readValue(json, new TypeReference<List<DictItemView>>() {
            });
        } catch (Exception ex) {
            // 缓存故障不阻断枚举读取，回退查库
            log.warn("sys dict cache read failed key={} reason={}", key, ex.getMessage());
            return null;
        }
    }

    private void writeCache(String key, List<DictItemView> views) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(views), CACHE_TTL);
        } catch (Exception ex) {
            log.warn("sys dict cache write failed key={} reason={}", key, ex.getMessage());
        }
    }

    private void evictCache(Long tenantId, String dictType) {
        try {
            redisTemplate.delete(cacheKey(tenantId, dictType));
        } catch (Exception ex) {
            // 删除失败时缓存最多残留 TTL 1 小时，不能因此回滚业务事务
            log.warn("sys dict cache evict failed tenantId={} dictType={} reason={}",
                    tenantId, dictType, ex.getMessage());
        }
    }

    private String cacheKey(Long tenantId, String dictType) {
        return CACHE_KEY_PREFIX + tenantId + ":" + dictType;
    }

    private DictTypeView toView(SysDictType type) {
        return new DictTypeView(
                type.getId(),
                type.getDictType(),
                type.getDictName(),
                type.getStatus(),
                type.getRemark(),
                type.getCreatedAt(),
                type.getUpdatedAt());
    }

    private DictItemView toView(SysDictItem item) {
        return new DictItemView(
                item.getId(),
                item.getDictType(),
                item.getDictLabel(),
                item.getDictValue(),
                item.getDictSort(),
                item.getCssClass(),
                item.getIsDefault(),
                item.getStatus(),
                item.getRemark(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }

    private String requireDictTypeParam(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "dictType 不能为空");
        }
        return value.trim();
    }

    private String requireDictType(String value) {
        String dictType = requireDictTypeParam(value);
        if (!DICT_TYPE_PATTERN.matcher(dictType).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "dictType 必须以字母开头，只能包含字母、数字、下划线，长度 2~64");
        }
        return dictType;
    }

    private int normalizeDefault(Integer isDefault) {
        if (isDefault == null) {
            return 0;
        }
        if (isDefault != 0 && isDefault != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "isDefault 只能是 0 或 1");
        }
        return isDefault;
    }

    private int requireStatus(Integer status) {
        if (status == null || (status != STATUS_ENABLED && status != STATUS_DISABLED)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0启用 1停用");
        }
        return status;
    }

    private String requireText(String value, int maxLength, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 长度不能超过 " + maxLength);
        }
        return trimmed;
    }

    private String normalizeOptionalText(String value, int maxLength, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 长度不能超过 " + maxLength);
        }
        return trimmed;
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
