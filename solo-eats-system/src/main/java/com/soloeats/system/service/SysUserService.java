package com.soloeats.system.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.system.service.command.CreateUserCommand;
import com.soloeats.system.service.command.UpdateUserCommand;
import com.soloeats.system.service.view.SysUserView;

import java.util.List;

public interface SysUserService {

    PageResult<SysUserView> page(Integer userType, Long merchantId, Long shopId, Integer status,
                                 String keyword, long pageNum, long pageSize);

    SysUserView create(CreateUserCommand command);

    SysUserView update(Long id, UpdateUserCommand command);

    void updateStatus(Long id, Integer status);

    void assignRoles(Long id, List<Long> roleIds);

    void resetPassword(Long id, String newPassword);

    /**
     * 逻辑删除账号并同步清理账号-角色关联（同一事务）。
     */
    void delete(Long id);
}
