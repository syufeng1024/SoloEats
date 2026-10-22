package com.soloeats.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.system.domain.entity.SysMenu;
import com.soloeats.system.domain.entity.SysPackage;
import com.soloeats.system.domain.entity.SysTenant;
import com.soloeats.system.mapper.SysMenuMapper;
import com.soloeats.system.mapper.SysPackageMapper;
import com.soloeats.system.mapper.SysTenantMapper;
import com.soloeats.system.service.SysTenantService;
import com.soloeats.system.service.command.SavePackageCommand;
import com.soloeats.system.service.command.SaveTenantCommand;
import com.soloeats.system.service.view.PackageView;
import com.soloeats.system.service.view.TenantView;
import com.soloeats.system.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysTenantServiceImpl implements SysTenantService {

    private static final int TENANT_TYPE_PLATFORM = 1;
    private static final int TENANT_TYPE_MERCHANT = 2;

    private static final int STATUS_ENABLED = 0;
    private static final int STATUS_DISABLED = 1;
    private static final int STATUS_EXPIRED = 2;

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private static final int MAX_TENANT_NAME_LENGTH = 128;
    private static final int MAX_CONTACT_NAME_LENGTH = 64;
    private static final int MAX_CONTACT_PHONE_LENGTH = 32;
    private static final int MAX_PACKAGE_NAME_LENGTH = 64;
    private static final int MAX_REMARK_LENGTH = 255;

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{2,64}$");

    private final SysTenantMapper sysTenantMapper;
    private final SysPackageMapper sysPackageMapper;
    private final SysMenuMapper sysMenuMapper;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<TenantView> page(Integer tenantType, Integer status, String keyword,
                                       long pageNum, long pageSize) {
        if (tenantType != null) {
            requireTenantType(tenantType);
        }
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = normalizePageSize(pageSize);
        LocalDateTime now = LocalDateTime.now();

        LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getDeleted, 0)
                .eq(tenantType != null, SysTenant::getTenantType, tenantType);

        if (status != null) {
            requireStatusRange(status);
            if (status == STATUS_EXPIRED) {
                wrapper.eq(SysTenant::getStatus, STATUS_ENABLED)
                        .isNotNull(SysTenant::getExpireAt)
                        .le(SysTenant::getExpireAt, now);
            } else if (status == STATUS_ENABLED) {
                wrapper.eq(SysTenant::getStatus, STATUS_ENABLED)
                        .and(w -> w.isNull(SysTenant::getExpireAt)
                                .or()
                                .gt(SysTenant::getExpireAt, now));
            } else {
                wrapper.eq(SysTenant::getStatus, STATUS_DISABLED);
            }
        }

        String sanitized = sanitizeKeyword(keyword);
        if (StringUtils.hasText(sanitized)) {
            wrapper.and(w -> w.like(SysTenant::getTenantCode, sanitized)
                    .or()
                    .like(SysTenant::getTenantName, sanitized));
        }
        wrapper.orderByDesc(SysTenant::getId);

        Page<SysTenant> page = sysTenantMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<TenantView> views = page.getRecords().stream().map(this::toView).toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TenantView create(SaveTenantCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        String tenantCode = requireCode(command.tenantCode(), "tenantCode");
        String tenantName = requireText(command.tenantName(), MAX_TENANT_NAME_LENGTH, "tenantName");
        int tenantType = requireTenantType(command.tenantType());
        validateMerchantBinding(tenantType, command.merchantId());
        validateExpireAt(command.expireAt());
        requirePackage(command.packageId());
        assertTenantCodeAvailable(tenantCode, null);

        SysTenant tenant = new SysTenant();
        tenant.setTenantCode(tenantCode);
        tenant.setTenantName(tenantName);
        tenant.setTenantType(tenantType);
        tenant.setMerchantId(command.merchantId());
        tenant.setPackageId(command.packageId());
        tenant.setStatus(STATUS_ENABLED);
        tenant.setExpireAt(command.expireAt());
        tenant.setContactName(normalizeOptionalText(
                command.contactName(), MAX_CONTACT_NAME_LENGTH, "contactName"));
        tenant.setContactPhone(normalizeOptionalText(
                command.contactPhone(), MAX_CONTACT_PHONE_LENGTH, "contactPhone"));
        tenant.setVersion(0);
        tenant.setDeleted(0);

        try {
            sysTenantMapper.insert(tenant);
        } catch (DuplicateKeyException ex) {
            // 先查后插在并发下会同时通过，最终由 uk_tenant_code 兜底
            throw new BizException(ErrorCode.PARAM_INVALID, "租户编码已存在");
        }
        log.info("sys tenant created tenantId={} tenantType={}", tenant.getId(), tenantType);
        return toView(tenant);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TenantView update(Long id, SaveTenantCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        SysTenant tenant = requireTenant(id);

        int tenantType = command.tenantType() == null ? tenant.getTenantType() : command.tenantType();
        requireTenantType(tenantType);
        Long merchantId = command.merchantId() == null ? tenant.getMerchantId() : command.merchantId();
        validateMerchantBinding(tenantType, merchantId);
        if (command.expireAt() != null) {
            validateExpireAt(command.expireAt());
        }
        if (command.packageId() != null) {
            requirePackage(command.packageId());
        }

        tenant.setTenantName(command.tenantName() == null
                ? tenant.getTenantName()
                : requireText(command.tenantName(), MAX_TENANT_NAME_LENGTH, "tenantName"));
        tenant.setTenantType(tenantType);
        tenant.setMerchantId(merchantId);
        tenant.setPackageId(command.packageId() == null ? tenant.getPackageId() : command.packageId());
        tenant.setExpireAt(command.expireAt() == null ? tenant.getExpireAt() : command.expireAt());
        tenant.setContactName(command.contactName() == null
                ? tenant.getContactName()
                : normalizeOptionalText(command.contactName(), MAX_CONTACT_NAME_LENGTH, "contactName"));
        tenant.setContactPhone(command.contactPhone() == null
                ? tenant.getContactPhone()
                : normalizeOptionalText(command.contactPhone(), MAX_CONTACT_PHONE_LENGTH, "contactPhone"));

        // entity 带 @Version：版本取自库内，updateById 会自增；返回 0 说明被并发改过
        if (sysTenantMapper.updateById(tenant) == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "租户已被修改，请刷新后重试");
        }
        log.info("sys tenant updated tenantId={}", tenant.getId());
        return toView(tenant);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != STATUS_ENABLED && status != STATUS_DISABLED)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0启用 1停用");
        }
        requireTenant(id);

        LambdaUpdateWrapper<SysTenant> wrapper = new LambdaUpdateWrapper<SysTenant>()
                .eq(SysTenant::getId, id)
                .eq(SysTenant::getDeleted, 0)
                .set(SysTenant::getStatus, status);
        if (status == STATUS_ENABLED) {
            // 已过期租户不能直接启用，必须先续期，避免"启用即过期"的空转状态
            wrapper.and(w -> w.isNull(SysTenant::getExpireAt)
                    .or()
                    .gt(SysTenant::getExpireAt, LocalDateTime.now()));
        }
        if (sysTenantMapper.update(null, wrapper) == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "租户不存在或已过期，请先续期");
        }
        log.info("sys tenant status changed tenantId={} status={}", id, status);
    }

    @Override
    public List<PackageView> listPackages() {
        List<SysPackage> packages = sysPackageMapper.selectList(new LambdaQueryWrapper<SysPackage>()
                .eq(SysPackage::getDeleted, 0)
                .orderByAsc(SysPackage::getId));
        return packages.stream().map(this::toView).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PackageView createPackage(SavePackageCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        String packageCode = requireCode(command.packageCode(), "packageCode");
        String packageName = requireText(
                command.packageName(), MAX_PACKAGE_NAME_LENGTH, "packageName");
        List<Long> menuIds = normalizeMenuIds(command.menuIds());
        assertPackageCodeAvailable(packageCode, null);

        SysPackage pkg = new SysPackage();
        pkg.setPackageCode(packageCode);
        pkg.setPackageName(packageName);
        pkg.setMenuIds(writeMenuIds(menuIds));
        applyPackageLimits(pkg, command);
        if (pkg.getStatus() == null) {
            pkg.setStatus(STATUS_ENABLED);
        }
        pkg.setDeleted(0);

        try {
            sysPackageMapper.insert(pkg);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "套餐编码已存在");
        }
        log.info("sys package created packageId={} menuCount={}",
                pkg.getId(), menuIds == null ? -1 : menuIds.size());
        return toView(pkg);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PackageView updatePackage(Long id, SavePackageCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        SysPackage pkg = requirePackageEntity(id);

        pkg.setPackageName(command.packageName() == null
                ? pkg.getPackageName()
                : requireText(command.packageName(), MAX_PACKAGE_NAME_LENGTH, "packageName"));
        if (command.menuIds() != null) {
            pkg.setMenuIds(writeMenuIds(normalizeMenuIds(command.menuIds())));
        }
        applyPackageLimits(pkg, command);
        if (command.status() == null) {
            pkg.setStatus(pkg.getStatus() == null ? STATUS_ENABLED : pkg.getStatus());
        }

        if (sysPackageMapper.updateById(pkg) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "套餐不存在");
        }
        log.info("sys package updated packageId={}", pkg.getId());
        return toView(pkg);
    }

    @Override
    public Set<Long> allowedMenuIds(Long tenantId) {
        Long effectiveTenantId = tenantId == null ? TenantContext.DEFAULT_TENANT_ID : tenantId;
        if (TenantContext.DEFAULT_TENANT_ID.equals(effectiveTenantId)) {
            return null;
        }
        SysTenant tenant = sysTenantMapper.selectOne(new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getId, effectiveTenantId)
                .eq(SysTenant::getDeleted, 0)
                .last("LIMIT 1"));
        if (tenant == null) {
            return Set.of();
        }
        if (tenant.getStatus() != null && tenant.getStatus() != STATUS_ENABLED) {
            return Set.of();
        }
        if (tenant.getExpireAt() != null && !tenant.getExpireAt().isAfter(LocalDateTime.now())) {
            return Set.of();
        }
        if (tenant.getPackageId() == null) {
            return null;
        }
        SysPackage pkg = sysPackageMapper.selectOne(new LambdaQueryWrapper<SysPackage>()
                .eq(SysPackage::getId, tenant.getPackageId())
                .eq(SysPackage::getDeleted, 0)
                .eq(SysPackage::getStatus, STATUS_ENABLED)
                .last("LIMIT 1"));
        if (pkg == null) {
            return Set.of();
        }
        List<Long> menuIds = readMenuIds(pkg.getMenuIds());
        if (menuIds == null) {
            return null;
        }
        return expandAncestors(menuIds);
    }

    /**
     * 套餐只配置叶子菜单时补齐祖先目录，否则菜单树会把子菜单提升为根节点。
     */
    private Set<Long> expandAncestors(List<Long> menuIds) {
        if (menuIds.isEmpty()) {
            return Set.of();
        }
        List<SysMenu> menus = sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getTenantId, TenantContext.DEFAULT_TENANT_ID)
                .eq(SysMenu::getDeleted, 0));
        Map<Long, Long> parentById = menus.stream()
                .collect(Collectors.toMap(SysMenu::getId, SysMenu::getParentId, (a, b) -> a));

        Set<Long> allowed = new LinkedHashSet<>(menuIds);
        for (Long menuId : menuIds) {
            Long cursor = parentById.get(menuId);
            int guard = 0;
            while (cursor != null && cursor > 0L && guard++ < 32) {
                allowed.add(cursor);
                cursor = parentById.get(cursor);
            }
        }
        return allowed;
    }

    private void applyPackageLimits(SysPackage pkg, SavePackageCommand command) {
        if (command.maxShop() != null) {
            pkg.setMaxShop(requireNonNegative(command.maxShop(), "maxShop"));
        } else if (pkg.getMaxShop() == null) {
            pkg.setMaxShop(0);
        }
        if (command.maxUser() != null) {
            pkg.setMaxUser(requireNonNegative(command.maxUser(), "maxUser"));
        } else if (pkg.getMaxUser() == null) {
            pkg.setMaxUser(0);
        }
        if (command.price() != null) {
            if (command.price() < 0L) {
                throw new BizException(ErrorCode.PARAM_INVALID, "price 不能为负数");
            }
            pkg.setPrice(command.price());
        } else if (pkg.getPrice() == null) {
            // 金额单位为分，禁止用 double 承载
            pkg.setPrice(0L);
        }
        if (command.validDays() != null) {
            pkg.setValidDays(requireNonNegative(command.validDays(), "validDays"));
        } else if (pkg.getValidDays() == null) {
            pkg.setValidDays(0);
        }
        if (command.status() != null) {
            pkg.setStatus(requireEnabledDisabled(command.status()));
        }
        if (command.remark() != null) {
            pkg.setRemark(normalizeOptionalText(command.remark(), MAX_REMARK_LENGTH, "remark"));
        }
    }

    private SysPackage requirePackage(Long packageId) {
        if (packageId == null) {
            return null;
        }
        return requirePackageEntity(packageId);
    }

    private SysPackage requirePackageEntity(Long packageId) {
        if (packageId == null || packageId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "packageId 必须是正整数");
        }
        SysPackage pkg = sysPackageMapper.selectOne(new LambdaQueryWrapper<SysPackage>()
                .eq(SysPackage::getId, packageId)
                .eq(SysPackage::getDeleted, 0)
                .last("LIMIT 1"));
        if (pkg == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "套餐不存在: " + packageId);
        }
        return pkg;
    }

    private SysTenant requireTenant(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "租户 ID 必须是正整数");
        }
        SysTenant tenant = sysTenantMapper.selectOne(new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getId, id)
                .eq(SysTenant::getDeleted, 0)
                .last("LIMIT 1"));
        if (tenant == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "租户不存在");
        }
        return tenant;
    }

    private void assertTenantCodeAvailable(String tenantCode, Long excludeId) {
        Long count = sysTenantMapper.selectCount(new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getTenantCode, tenantCode)
                .ne(excludeId != null, SysTenant::getId, excludeId));
        if (count != null && count > 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "租户编码已存在");
        }
    }

    private void assertPackageCodeAvailable(String packageCode, Long excludeId) {
        Long count = sysPackageMapper.selectCount(new LambdaQueryWrapper<SysPackage>()
                .eq(SysPackage::getPackageCode, packageCode)
                .ne(excludeId != null, SysPackage::getId, excludeId));
        if (count != null && count > 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "套餐编码已存在");
        }
    }

    private List<Long> normalizeMenuIds(List<Long> menuIds) {
        if (menuIds == null) {
            // null 表示不限制菜单范围；空数组表示一个菜单都不开放
            return null;
        }
        Set<Long> distinct = new LinkedHashSet<>();
        for (Long menuId : menuIds) {
            if (menuId == null) {
                continue;
            }
            if (menuId <= 0L) {
                throw new BizException(ErrorCode.PARAM_INVALID, "menuIds 必须是正整数");
            }
            distinct.add(menuId);
        }
        if (distinct.isEmpty()) {
            return List.of();
        }
        List<SysMenu> menus = sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .in(SysMenu::getId, distinct)
                .eq(SysMenu::getTenantId, TenantContext.DEFAULT_TENANT_ID)
                .eq(SysMenu::getDeleted, 0));
        Set<Long> existing = menus == null
                ? Set.of()
                : menus.stream().map(SysMenu::getId).collect(Collectors.toSet());
        List<Long> missing = distinct.stream().filter(id -> !existing.contains(id)).toList();
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "菜单不存在: " + missing);
        }
        return new ArrayList<>(distinct);
    }

    private String writeMenuIds(List<Long> menuIds) {
        if (menuIds == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(menuIds);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "menuIds 序列化失败");
        }
    }

    /**
     * 解析失败按“无可用菜单”处理，避免脏数据把租户意外放开到全量菜单。
     */
    private List<Long> readMenuIds(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            List<Long> menuIds = objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
            return menuIds == null ? List.of() : menuIds;
        } catch (Exception ex) {
            log.warn("sys package menu_ids 解析失败，按空菜单处理 reason={}", ex.getMessage());
            return List.of();
        }
    }

    private TenantView toView(SysTenant tenant) {
        int status = tenant.getStatus() == null ? STATUS_DISABLED : tenant.getStatus();
        if (status == STATUS_ENABLED
                && tenant.getExpireAt() != null
                && !tenant.getExpireAt().isAfter(LocalDateTime.now())) {
            status = STATUS_EXPIRED;
        }
        return new TenantView(
                tenant.getId(),
                tenant.getTenantCode(),
                tenant.getTenantName(),
                tenant.getTenantType(),
                tenant.getMerchantId(),
                tenant.getPackageId(),
                status,
                tenant.getExpireAt(),
                tenant.getContactName(),
                maskPhone(tenant.getContactPhone()),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt());
    }

    private PackageView toView(SysPackage pkg) {
        return new PackageView(
                pkg.getId(),
                pkg.getPackageCode(),
                pkg.getPackageName(),
                readMenuIds(pkg.getMenuIds()),
                pkg.getMaxShop(),
                pkg.getMaxUser(),
                pkg.getPrice(),
                pkg.getValidDays(),
                pkg.getStatus(),
                pkg.getRemark(),
                pkg.getCreatedAt(),
                pkg.getUpdatedAt());
    }

    private void validateMerchantBinding(int tenantType, Long merchantId) {
        if (tenantType == TENANT_TYPE_MERCHANT && merchantId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商家租户必须绑定 merchantId");
        }
        if (tenantType == TENANT_TYPE_PLATFORM && merchantId != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "平台租户不能绑定 merchantId");
        }
    }

    private void validateExpireAt(LocalDateTime expireAt) {
        if (expireAt != null && !expireAt.isAfter(LocalDateTime.now())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "expireAt 必须晚于当前时间");
        }
    }

    private int requireTenantType(Integer tenantType) {
        if (tenantType == null
                || (tenantType != TENANT_TYPE_PLATFORM && tenantType != TENANT_TYPE_MERCHANT)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "tenantType 只能是 1平台 2商家");
        }
        return tenantType;
    }

    private void requireStatusRange(int status) {
        if (status < STATUS_ENABLED || status > STATUS_EXPIRED) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0启用 1停用 2过期");
        }
    }

    private int requireEnabledDisabled(int status) {
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0启用 1停用");
        }
        return status;
    }

    private int requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为负数");
        }
        return value;
    }

    private String requireCode(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空");
        }
        String trimmed = value.trim();
        if (!CODE_PATTERN.matcher(trimmed).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    field + " 只能包含字母、数字、下划线、中划线，长度 2~64");
        }
        return trimmed;
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

    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        if (phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
