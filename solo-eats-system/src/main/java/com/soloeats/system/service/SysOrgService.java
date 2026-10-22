package com.soloeats.system.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.system.service.command.SaveDeptCommand;
import com.soloeats.system.service.command.SavePostCommand;
import com.soloeats.system.service.view.DeptNode;
import com.soloeats.system.service.view.PostView;

import java.util.List;

/**
 * 组织架构：部门树 + 岗位。
 */
public interface SysOrgService {

    List<DeptNode> deptTree(Integer status);

    DeptNode createDept(SaveDeptCommand command);

    /**
     * 修改部门；变更父级时在同一事务内级联更新全部子部门 ancestors。
     */
    DeptNode updateDept(Long id, SaveDeptCommand command);

    /**
     * 逻辑删除部门；存在子部门或仍被账号引用时拒绝。
     */
    void deleteDept(Long id);

    PageResult<PostView> postPage(Integer status, String keyword, long pageNum, long pageSize);

    PostView createPost(SavePostCommand command);

    PostView updatePost(Long id, SavePostCommand command);

    void deletePost(Long id);
}
