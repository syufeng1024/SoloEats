package com.soloeats.system.service.command;

/**
 * 角色新增/修改。roleCode 一经创建即被 JWT 角色码引用，修改需评估已签发 token。
 */
public record SaveRoleCommand(
        String roleCode,
        String roleName,
        Integer roleType,
        Integer dataScope,
        Integer sort,
        Integer status,
        String remark
) {
}
