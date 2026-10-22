package com.soloeats.store.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.store.domain.entity.Shop;
import com.soloeats.store.domain.entity.ShopAccount;
import com.soloeats.store.mapper.ShopAccountMapper;
import com.soloeats.store.mapper.ShopMapper;
import com.soloeats.store.service.ShopAccountService;
import com.soloeats.store.service.command.CreateShopAccountCommand;
import com.soloeats.store.service.view.ShopAccountView;
import com.soloeats.system.service.SysRoleService;
import com.soloeats.system.service.SysUserService;
import com.soloeats.system.service.command.CreateUserCommand;
import com.soloeats.system.service.view.SysRoleView;
import com.soloeats.system.service.view.SysUserView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopAccountServiceImpl implements ShopAccountService {

    private static final String MERCHANT_CLERK_ROLE = "MERCHANT_CLERK";
    private static final int USER_TYPE_MERCHANT = 2;
    private static final int ROLE_TYPE_MERCHANT = 2;
    private static final int DATA_SCOPE_SHOP = 4;
    private static final long ROLE_PAGE_SIZE = 100L;
    private static final long USER_PAGE_SIZE = 100L;

    private static final int ACCOUNT_ROLE_MANAGER = 1;
    private static final int ACCOUNT_ROLE_CLERK = 2;

    private final ShopMapper shopMapper;
    private final ShopAccountMapper shopAccountMapper;
    private final SysUserService sysUserService;
    private final SysRoleService sysRoleService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopAccountView create(Long shopId, Long merchantId, CreateShopAccountCommand command) {
        Shop shop = requireOwnedShop(shopId, merchantId);
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        int accountRole = command.accountRole() == null ? ACCOUNT_ROLE_CLERK : command.accountRole();
        if (accountRole != ACCOUNT_ROLE_MANAGER && accountRole != ACCOUNT_ROLE_CLERK) {
            throw new BizException(ErrorCode.PARAM_INVALID, "accountRole 只能是 1店长 或 2店员");
        }

        long existingCount = shopAccountMapper.selectCount(new LambdaQueryWrapper<ShopAccount>()
                .eq(ShopAccount::getShopId, shopId)
                .eq(ShopAccount::getDeleted, 0));
        int isDefault = existingCount == 0 ? 1 : 0;

        Long clerkRoleId = requireClerkRoleId();
        SysUserView user = sysUserService.create(new CreateUserCommand(
                command.username(),
                command.password(),
                command.nickname(),
                command.phone(),
                USER_TYPE_MERCHANT,
                shop.getMerchantId(),
                shopId,
                null,
                null,
                DATA_SCOPE_SHOP,
                List.of(clerkRoleId)));

        ShopAccount account = new ShopAccount();
        account.setTenantId(shop.getTenantId());
        account.setShopId(shopId);
        account.setMerchantId(shop.getMerchantId());
        account.setUserId(user.id());
        account.setAccountRole(accountRole);
        account.setIsDefault(isDefault);
        account.setStatus(0);
        account.setDeleted(0);
        // 与 SysUserService.create 同事务：账号关联失败时 t_sys_user 一并回滚
        try {
            shopAccountMapper.insert(account);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该账号已绑定此门店");
        }

        log.info("shop account created shopId={} userId={} role={}", shopId, user.id(), accountRole);
        return toView(account, user.username(), user.nickname());
    }

    @Override
    public List<ShopAccountView> list(Long shopId, Long merchantId) {
        requireOwnedShop(shopId, merchantId);
        List<ShopAccount> accounts = shopAccountMapper.selectList(new LambdaQueryWrapper<ShopAccount>()
                .eq(ShopAccount::getShopId, shopId)
                .eq(ShopAccount::getDeleted, 0)
                .orderByAsc(ShopAccount::getId));
        if (accounts.isEmpty()) {
            return List.of();
        }

        Map<Long, SysUserView> userById = loadUsers(merchantId);
        List<ShopAccountView> views = new ArrayList<>(accounts.size());
        for (ShopAccount account : accounts) {
            SysUserView user = userById.get(account.getUserId());
            views.add(toView(account,
                    user == null ? null : user.username(),
                    user == null ? null : user.nickname()));
        }
        return views;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long shopId, Long merchantId, Long userId) {
        requireOwnedShop(shopId, merchantId);
        if (userId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userId 不能为空");
        }
        ShopAccount account = shopAccountMapper.selectOne(new LambdaQueryWrapper<ShopAccount>()
                .eq(ShopAccount::getShopId, shopId)
                .eq(ShopAccount::getUserId, userId)
                .eq(ShopAccount::getDeleted, 0)
                .last("LIMIT 1"));
        if (account == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "门店账号不存在");
        }
        // 只解绑门店关联，不删除 t_sys_user（同一账号可能关联其他门店）
        account.setDeleted(1);
        if (shopAccountMapper.updateById(account) == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "门店账号已被修改，请刷新后重试");
        }
        log.info("shop account unbound shopId={} userId={}", shopId, userId);
    }

    private Map<Long, SysUserView> loadUsers(Long merchantId) {
        Map<Long, SysUserView> userById = new HashMap<>();
        PageResult<SysUserView> page = sysUserService.page(
                USER_TYPE_MERCHANT, merchantId, null, null, null, 1, USER_PAGE_SIZE);
        for (SysUserView user : page.list()) {
            userById.put(user.id(), user);
        }
        return userById;
    }

    private Long requireClerkRoleId() {
        PageResult<SysRoleView> roles = sysRoleService.page(
                ROLE_TYPE_MERCHANT, 0, 1, ROLE_PAGE_SIZE);
        return roles.list().stream()
                .filter(role -> MERCHANT_CLERK_ROLE.equals(role.roleCode()))
                .map(SysRoleView::id)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.SYSTEM_ERROR,
                        "缺少 " + MERCHANT_CLERK_ROLE + " 角色，请检查 t_sys_role 种子数据"));
    }

    private Shop requireOwnedShop(Long shopId, Long merchantId) {
        if (merchantId == null) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "令牌缺少 merchantId");
        }
        Shop shop = shopId == null ? null : shopMapper.selectById(shopId);
        if (shop == null || !Objects.equals(shop.getDeleted(), 0)) {
            throw new BizException(ErrorCode.SHOP_NOT_FOUND);
        }
        if (!Objects.equals(shop.getMerchantId(), merchantId)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无权操作其他商家的门店");
        }
        return shop;
    }

    private ShopAccountView toView(ShopAccount account, String username, String nickname) {
        return new ShopAccountView(
                account.getId(),
                account.getShopId(),
                account.getUserId(),
                StringUtils.hasText(username) ? username : null,
                StringUtils.hasText(nickname) ? nickname : null,
                account.getAccountRole(),
                Objects.equals(account.getAccountRole(), ACCOUNT_ROLE_MANAGER) ? "店长" : "店员",
                account.getIsDefault(),
                account.getStatus(),
                account.getCreatedAt());
    }
}
