package com.soloeats.system.service;

import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.datascope.DataScope;

/**
 * 把角色 / 账号上的 {@code data_scope} 解析为行级过滤条件。
 *
 * <p>档位与 {@code t_sys_role.data_scope} 一致：
 * 1 全部、2 本租户、3 本商家、4 本门店、5 本人。
 */
public interface DataScopeService {

    int SCOPE_ALL = 1;

    int SCOPE_TENANT = 2;

    int SCOPE_MERCHANT = 3;

    int SCOPE_SHOP = 4;

    int SCOPE_SELF = 5;

    /**
     * 当前账号生效的档位：取所有启用角色中最宽松（数值最小）的一档；
     * 没有角色时回落到 {@code t_sys_user.data_scope}，都取不到按「本人」处理。
     */
    int resolveDataScope(UserPrincipal principal);

    /**
     * 当前请求应追加的 SQL 条件（不含 WHERE）。非平台后台身份返回空串，表示不过滤。
     */
    String buildCondition(UserPrincipal principal, DataScope dataScope);

    /**
     * 指定档位生成条件，供单元测试与报表复算使用；不查库。
     */
    String buildCondition(int scope, UserPrincipal principal, DataScope annotation);
}
