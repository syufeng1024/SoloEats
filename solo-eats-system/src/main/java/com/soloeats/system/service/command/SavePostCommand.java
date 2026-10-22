package com.soloeats.system.service.command;

/**
 * 岗位新增/修改入参。修改时 postCode 不可变更，传值将被忽略。
 */
public record SavePostCommand(
        String postCode,
        String postName,
        Integer sort,
        Integer status,
        String remark
) {
}
