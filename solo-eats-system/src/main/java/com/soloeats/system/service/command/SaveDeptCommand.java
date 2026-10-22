package com.soloeats.system.service.command;

/**
 * 部门新增/修改入参。修改时字段为 null 表示保持不变；parentId=0 表示挂到根。
 */
public record SaveDeptCommand(
        Long parentId,
        String deptName,
        String leader,
        String phone,
        Integer sort,
        Integer status
) {
}
