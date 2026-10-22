package com.soloeats.system.service.view;

import java.util.List;

/**
 * 部门树节点。phone 返回前已脱敏。
 */
public record DeptNode(
        Long id,
        Long parentId,
        String ancestors,
        String deptName,
        String leader,
        String phone,
        Integer sort,
        Integer status,
        List<DeptNode> children
) {
}
