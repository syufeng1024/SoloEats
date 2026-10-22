package com.soloeats.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.system.domain.entity.SysRole;
import com.soloeats.system.domain.entity.SysUser;
import com.soloeats.system.domain.entity.SysUserRole;
import com.soloeats.system.mapper.SysRoleMapper;
import com.soloeats.system.mapper.SysUserMapper;
import com.soloeats.system.mapper.SysUserRoleMapper;
import com.soloeats.system.service.SysUserService;
import com.soloeats.system.service.command.CreateUserCommand;
import com.soloeats.system.service.command.UpdateUserCommand;
import com.soloeats.system.service.view.SysUserView;
import com.soloeats.system.support.PasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    /** TODO(T02): 接入 TenantContext 后从上下文取租户；阶段B 固定租户 1。 */
    private static final Long DEFAULT_TENANT_ID = 1L;

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 64;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 64;
    private static final int MAX_NICKNAME_LENGTH = 64;

    private static final int USER_TYPE_PLATFORM = 1;
    private static final int USER_TYPE_MERCHANT = 2;
    private static final int USER_TYPE_RIDER = 3;

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.@-]{3,64}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordHasher passwordHasher;

    @Override
    public PageResult<SysUserView> page(Integer userType, Long merchantId, Long shopId, Integer status,
                                        String keyword, long pageNum, long pageSize) {
        validateUserTypeIfPresent(userType);
        validateStatusIfPresent(status);
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = normalizePageSize(pageSize);

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, DEFAULT_TENANT_ID)
                .eq(SysUser::getDeleted, 0)
                .eq(userType != null, SysUser::getUserType, userType)
                .eq(merchantId != null, SysUser::getMerchantId, merchantId)
                .eq(shopId != null, SysUser::getShopId, shopId)
                .eq(status != null, SysUser::getStatus, status);

        String sanitized = sanitizeKeyword(keyword);
        if (StringUtils.hasText(sanitized)) {
            wrapper.and(w -> w.like(SysUser::getUsername, sanitized)
                    .or()
                    .like(SysUser::getNickname, sanitized));
        }
        wrapper.orderByDesc(SysUser::getId);

        // DataScopeInnerInterceptor 按当前平台角色的 data_scope 追加行级条件
        Page<SysUser> page = sysUserMapper.selectScopedPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<SysUserView> views = page.getRecords().stream().map(this::toView).toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    /**
     * 插账号 + 插 user_role 必须在同一事务；唯一性先查后插只做友好提示，最终由数据库唯一键兜底。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUserView create(CreateUserCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        String username = normalizeUsername(command.username());
        String password = validatePassword(command.password());
        String nickname = normalizeOptionalText(command.nickname(), MAX_NICKNAME_LENGTH, "nickname");
        String phone = normalizePhone(command.phone());
        // 不设默认值：漏传 userType 时若默认成平台账号会造成越权
        if (command.userType() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userType 不能为空");
        }
        int userType = command.userType();
        validateUserType(userType);
        validateBinding(userType, command.merchantId(), command.shopId(), command.riderId());
        int dataScope = command.dataScope() == null ? 5 : command.dataScope();
        validateDataScope(dataScope);
        List<Long> roleIds = normalizeRoleIds(command.roleIds());
        validateRolesAgainstUserType(roleIds, userType);

        assertUsernameAvailable(username);
        assertPhoneAvailable(phone, null);

        SysUser user = new SysUser();
        user.setTenantId(DEFAULT_TENANT_ID);
        user.setUsername(username);
        user.setPasswordHash(passwordHasher.encode(password));
        user.setNickname(nickname);
        user.setPhone(phone);
        user.setUserType(userType);
        user.setMerchantId(command.merchantId());
        user.setShopId(command.shopId());
        user.setRiderId(command.riderId());
        user.setDeptId(command.deptId());
        user.setDataScope(dataScope);
        user.setStatus(0);
        user.setDeleted(0);

        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            // 并发插入时先查后插会同时通过，靠 uk_tenant_username / uk_tenant_phone 兜底
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名或手机号已存在");
        }

        for (Long roleId : roleIds) {
            sysUserRoleMapper.insert(buildUserRole(user.getId(), roleId));
        }

        // 只记录定位字段，禁止输出密码与 password_hash
        log.info("sys user created userId={} userType={} roleCount={}",
                user.getId(), userType, roleIds.size());
        return toView(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUserView update(Long id, UpdateUserCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        SysUser user = requireUser(id);

        int userType = command.userType() == null ? user.getUserType() : command.userType();
        validateUserType(userType);
        Long merchantId = command.merchantId() == null ? user.getMerchantId() : command.merchantId();
        Long shopId = command.shopId() == null ? user.getShopId() : command.shopId();
        Long riderId = command.riderId() == null ? user.getRiderId() : command.riderId();
        validateBinding(userType, merchantId, shopId, riderId);

        String nickname = command.nickname() == null
                ? user.getNickname()
                : normalizeOptionalText(command.nickname(), MAX_NICKNAME_LENGTH, "nickname");
        String phone = command.phone() == null ? user.getPhone() : normalizePhone(command.phone());
        Integer dataScope = command.dataScope() == null ? user.getDataScope() : command.dataScope();
        validateDataScope(dataScope);
        Long deptId = command.deptId() == null ? user.getDeptId() : command.deptId();

        if (command.phone() != null) {
            assertPhoneAvailable(phone, user.getId());
        }

        List<Long> roleIds = command.roleIds() == null ? null : normalizeRoleIds(command.roleIds());
        if (roleIds != null) {
            validateRolesAgainstUserType(roleIds, userType);
        }

        user.setNickname(nickname);
        user.setPhone(phone);
        user.setUserType(userType);
        user.setMerchantId(merchantId);
        user.setShopId(shopId);
        user.setRiderId(riderId);
        user.setDeptId(deptId);
        user.setDataScope(dataScope);

        // entity 带 @Version 且 version 取自库内，updateById 会自增；返回 0 说明被并发改过
        if (sysUserMapper.updateById(user) == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账号已被修改，请刷新后重试");
        }
        if (roleIds != null) {
            replaceRoles(user.getId(), roleIds);
        }
        log.info("sys user updated userId={} roleReplaced={}", user.getId(), roleIds != null);
        return toView(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        validateStatus(status);
        SysUser user = requireUser(id);
        user.setStatus(status);
        if (sysUserMapper.updateById(user) == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账号已被修改，请刷新后重试");
        }
        log.info("sys user status changed userId={} status={}", id, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long id, List<Long> roleIds) {
        SysUser user = requireUser(id);
        List<Long> normalized = normalizeRoleIds(roleIds);
        validateRolesAgainstUserType(normalized, user.getUserType());
        replaceRoles(user.getId(), normalized);
        log.info("sys user roles assigned userId={} roleCount={}", id, normalized.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newPassword) {
        String password = validatePassword(newPassword);
        SysUser user = requireUser(id);
        user.setPasswordHash(passwordHasher.encode(password));
        if (sysUserMapper.updateById(user) == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账号已被修改，请刷新后重试");
        }
        // 只记录账号定位，禁止输出新密码
        log.info("sys user password reset userId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUser user = requireUser(id);
        user.setDeleted(1);
        if (sysUserMapper.updateById(user) == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账号已被修改，请刷新后重试");
        }
        // t_sys_user_role 无 deleted 列：撤销授权即删除关联行
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getTenantId, DEFAULT_TENANT_ID)
                .eq(SysUserRole::getUserId, user.getId()));
        log.info("sys user logically deleted userId={}", id);
    }

    private void replaceRoles(Long userId, List<Long> roleIds) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getTenantId, DEFAULT_TENANT_ID)
                .eq(SysUserRole::getUserId, userId));
        for (Long roleId : roleIds) {
            sysUserRoleMapper.insert(buildUserRole(userId, roleId));
        }
    }

    private SysUserRole buildUserRole(Long userId, Long roleId) {
        SysUserRole relation = new SysUserRole();
        relation.setTenantId(DEFAULT_TENANT_ID);
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }

    private SysUser requireUser(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户 ID 必须是正整数");
        }
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, DEFAULT_TENANT_ID)
                .eq(SysUser::getId, id)
                .eq(SysUser::getDeleted, 0)
                .last("LIMIT 1"));
        if (user == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "账号不存在");
        }
        return user;
    }

    private void assertUsernameAvailable(String username) {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, DEFAULT_TENANT_ID)
                .eq(SysUser::getUsername, username));
        if (count != null && count > 0L) {
            // 逻辑删除的账号仍占用 uk_tenant_username，故此处不按 deleted 过滤
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名已存在");
        }
    }

    private void assertPhoneAvailable(String phone, Long excludeUserId) {
        if (phone == null) {
            return;
        }
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, DEFAULT_TENANT_ID)
                .eq(SysUser::getPhone, phone)
                .ne(excludeUserId != null, SysUser::getId, excludeUserId));
        if (count != null && count > 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "手机号已存在");
        }
    }

    /**
     * 角色必须存在且 roleType 与账号 userType 一致，避免商家账号被授予平台超管角色。
     */
    private void validateRolesAgainstUserType(List<Long> roleIds, Integer userType) {
        if (roleIds.isEmpty()) {
            return;
        }
        List<SysRole> roles = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, roleIds));
        Map<Long, SysRole> roleById = roles == null
                ? Map.of()
                : roles.stream().collect(Collectors.toMap(SysRole::getId, Function.identity()));
        for (Long roleId : roleIds) {
            SysRole role = roleById.get(roleId);
            if (role == null || (role.getDeleted() != null && role.getDeleted() != 0)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "角色不存在: " + roleId);
            }
            if (!Objects.equals(role.getRoleType(), userType)) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "角色 " + role.getRoleCode() + " 与账号类型不匹配");
            }
        }
    }

    private List<Long> normalizeRoleIds(List<Long> roleIds) {
        Set<Long> normalized = new LinkedHashSet<>();
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                if (roleId == null) {
                    continue;
                }
                if (roleId <= 0L) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "roleIds 必须是正整数");
                }
                normalized.add(roleId);
            }
        }
        return new ArrayList<>(normalized);
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名不能为空");
        }
        String value = username.trim();
        if (value.length() < MIN_USERNAME_LENGTH || value.length() > MAX_USERNAME_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名长度必须是 3~64");
        }
        if (!USERNAME_PATTERN.matcher(value).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "用户名只能包含字母、数字、下划线、点、@ 与中划线");
        }
        return value;
    }

    private String validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "密码不能为空");
        }
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "密码长度必须是 8~64");
        }
        return password;
    }

    private String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        String value = toHalfWidthDigits(phone.trim());
        if (!PHONE_PATTERN.matcher(value).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "手机号格式不正确");
        }
        return value;
    }

    private String normalizeOptionalText(String value, int maxLength, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    field + " 长度不能超过 " + maxLength);
        }
        return trimmed;
    }

    /** 全角数字转半角，避免用户从表格粘贴时校验失败。 */
    private String toHalfWidthDigits(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (char ch : value.toCharArray()) {
            if (ch >= '０' && ch <= '９') {
                builder.append((char) (ch - '０' + '0'));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private void validateBinding(int userType, Long merchantId, Long shopId, Long riderId) {
        if (userType == USER_TYPE_MERCHANT && merchantId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商家账号必须绑定 merchantId");
        }
        if (userType == USER_TYPE_RIDER && riderId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "骑手账号必须绑定 riderId");
        }
    }

    private void validateUserTypeIfPresent(Integer userType) {
        if (userType != null) {
            validateUserType(userType);
        }
    }

    private void validateUserType(int userType) {
        if (userType < USER_TYPE_PLATFORM || userType > USER_TYPE_RIDER) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userType 只能是 1平台 2商家 3骑手");
        }
    }

    private void validateDataScope(int dataScope) {
        if (dataScope < 1 || dataScope > 5) {
            throw new BizException(ErrorCode.PARAM_INVALID, "dataScope 只能是 1~5");
        }
    }

    private void validateStatusIfPresent(Integer status) {
        if (status != null) {
            validateStatus(status);
        }
    }

    private void validateStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0启用 1停用");
        }
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

    private SysUserView toView(SysUser user) {
        return new SysUserView(
                user.getId(),
                user.getTenantId(),
                user.getUsername(),
                user.getNickname(),
                maskPhone(user.getPhone()),
                user.getUserType(),
                user.getMerchantId(),
                user.getShopId(),
                user.getRiderId(),
                user.getDeptId(),
                user.getDataScope(),
                user.getStatus(),
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
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
