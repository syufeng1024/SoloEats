package com.soloeats.system.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.system.service.command.SavePackageCommand;
import com.soloeats.system.service.command.SaveTenantCommand;
import com.soloeats.system.service.view.PackageView;
import com.soloeats.system.service.view.TenantView;

import java.util.List;
import java.util.Set;

public interface SysTenantService {

    PageResult<TenantView> page(Integer tenantType, Integer status, String keyword,
                                long pageNum, long pageSize);

    TenantView create(SaveTenantCommand command);

    TenantView update(Long id, SaveTenantCommand command);

    /**
     * 人工启用/停用，只接受 0 或 1；2 为按到期时间派生的只读状态。
     */
    void updateStatus(Long id, Integer status);

    List<PackageView> listPackages();

    PackageView createPackage(SavePackageCommand command);

    PackageView updatePackage(Long id, SavePackageCommand command);

    /**
     * 当前租户可访问的菜单 ID（含祖先目录）。
     *
     * @return {@code null} 表示不限制（平台租户、未绑定套餐或套餐未配置菜单）；
     *         空集合表示租户停用/过期/套餐不可用，任何菜单都不可见
     */
    Set<Long> allowedMenuIds(Long tenantId);
}
