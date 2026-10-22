package com.soloeats.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.system.domain.entity.SysDept;
import com.soloeats.system.domain.entity.SysPost;
import com.soloeats.system.domain.entity.SysUser;
import com.soloeats.system.mapper.SysDeptMapper;
import com.soloeats.system.mapper.SysPostMapper;
import com.soloeats.system.mapper.SysUserMapper;
import com.soloeats.system.service.SysOrgService;
import com.soloeats.system.service.command.SaveDeptCommand;
import com.soloeats.system.service.command.SavePostCommand;
import com.soloeats.system.service.view.DeptNode;
import com.soloeats.system.service.view.PostView;
import com.soloeats.system.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysOrgServiceImpl implements SysOrgService {

    private static final long ROOT_PARENT_ID = 0L;
    private static final String ROOT_ANCESTORS = "0";

    private static final int STATUS_ENABLED = 0;
    private static final int STATUS_DISABLED = 1;

    private static final int MAX_DEPT_DEPTH = 32;
    private static final int MAX_DEPT_NAME_LENGTH = 64;
    private static final int MAX_LEADER_LENGTH = 64;
    private static final int MAX_PHONE_LENGTH = 32;
    private static final int MAX_POST_NAME_LENGTH = 64;
    private static final int MAX_REMARK_LENGTH = 255;

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{2,64}$");

    private final SysDeptMapper sysDeptMapper;
    private final SysPostMapper sysPostMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public List<DeptNode> deptTree(Integer status) {
        if (status != null) {
            requireStatus(status);
        }
        List<SysDept> depts = sysDeptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getTenantId, TenantContext.get())
                .eq(SysDept::getDeleted, 0)
                .eq(status != null, SysDept::getStatus, status)
                .orderByAsc(SysDept::getSort)
                .orderByAsc(SysDept::getId));
        if (depts == null || depts.isEmpty()) {
            return List.of();
        }

        Map<Long, List<SysDept>> childrenByParent = new LinkedHashMap<>();
        Set<Long> presentIds = new HashSet<>();
        for (SysDept dept : depts) {
            presentIds.add(dept.getId());
        }
        for (SysDept dept : depts) {
            childrenByParent.computeIfAbsent(dept.getParentId(), key -> new ArrayList<>()).add(dept);
        }

        List<DeptNode> roots = new ArrayList<>();
        for (SysDept dept : depts) {
            // 父节点被过滤或已删除时提升为根，避免整棵子树消失
            if (!presentIds.contains(dept.getParentId())) {
                roots.add(buildNode(dept, childrenByParent, new HashSet<>()));
            }
        }
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeptNode createDept(SaveDeptCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        long parentId = command.parentId() == null ? ROOT_PARENT_ID : command.parentId();
        if (parentId < 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "parentId 不能为负数");
        }
        SysDept parent = resolveParent(parentId, null);
        String ancestors = parent == null
                ? ROOT_ANCESTORS
                : parent.getAncestors() + "," + parent.getId();
        assertDepth(ancestors);

        SysDept dept = new SysDept();
        dept.setTenantId(TenantContext.get());
        dept.setParentId(parent == null ? ROOT_PARENT_ID : parent.getId());
        dept.setAncestors(ancestors);
        dept.setDeptName(requireText(command.deptName(), MAX_DEPT_NAME_LENGTH, "deptName"));
        dept.setLeader(normalizeOptionalText(command.leader(), MAX_LEADER_LENGTH, "leader"));
        dept.setPhone(normalizeOptionalText(command.phone(), MAX_PHONE_LENGTH, "phone"));
        dept.setSort(command.sort() == null ? 0 : command.sort());
        dept.setStatus(command.status() == null ? STATUS_ENABLED : requireStatus(command.status()));
        dept.setDeleted(0);

        sysDeptMapper.insert(dept);
        log.info("sys dept created deptId={} parentId={} ancestors={}",
                dept.getId(), dept.getParentId(), ancestors);
        return toNode(dept, List.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeptNode updateDept(Long id, SaveDeptCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        SysDept dept = requireDept(id);
        long newParentId = command.parentId() == null ? dept.getParentId() : command.parentId();
        if (newParentId < 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "parentId 不能为负数");
        }

        if (newParentId != dept.getParentId()) {
            SysDept newParent = resolveParent(newParentId, dept.getId());
            moveSubtree(dept, newParent);
        }

        dept.setDeptName(command.deptName() == null
                ? dept.getDeptName()
                : requireText(command.deptName(), MAX_DEPT_NAME_LENGTH, "deptName"));
        dept.setLeader(command.leader() == null
                ? dept.getLeader()
                : normalizeOptionalText(command.leader(), MAX_LEADER_LENGTH, "leader"));
        dept.setPhone(command.phone() == null
                ? dept.getPhone()
                : normalizeOptionalText(command.phone(), MAX_PHONE_LENGTH, "phone"));
        dept.setSort(command.sort() == null ? dept.getSort() : command.sort());
        dept.setStatus(command.status() == null ? dept.getStatus() : requireStatus(command.status()));

        if (sysDeptMapper.updateById(dept) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "部门不存在");
        }
        log.info("sys dept updated deptId={} parentId={}", dept.getId(), dept.getParentId());
        return toNode(dept, List.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDept(Long id) {
        SysDept dept = requireDept(id);

        Long childCount = sysDeptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getTenantId, TenantContext.get())
                .eq(SysDept::getParentId, dept.getId())
                .eq(SysDept::getDeleted, 0));
        if (childCount != null && childCount > 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "存在子部门，请先删除子节点");
        }

        Long userCount = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, TenantContext.get())
                .eq(SysUser::getDeptId, dept.getId())
                .eq(SysUser::getDeleted, 0));
        if (userCount != null && userCount > 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "部门仍有账号引用，请先调整账号部门");
        }

        dept.setDeleted(1);
        if (sysDeptMapper.updateById(dept) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "部门不存在");
        }
        log.info("sys dept logically deleted deptId={}", id);
    }

    @Override
    public PageResult<PostView> postPage(Integer status, String keyword,
                                         long pageNum, long pageSize) {
        if (status != null) {
            requireStatus(status);
        }
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = normalizePageSize(pageSize);

        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<SysPost>()
                .eq(SysPost::getTenantId, TenantContext.get())
                .eq(SysPost::getDeleted, 0)
                .eq(status != null, SysPost::getStatus, status);

        String sanitized = sanitizeKeyword(keyword);
        if (StringUtils.hasText(sanitized)) {
            wrapper.and(w -> w.like(SysPost::getPostCode, sanitized)
                    .or()
                    .like(SysPost::getPostName, sanitized));
        }
        wrapper.orderByAsc(SysPost::getSort).orderByAsc(SysPost::getId);

        Page<SysPost> page = sysPostMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<PostView> views = page.getRecords().stream().map(this::toView).toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostView createPost(SavePostCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        String postCode = requireCode(command.postCode(), "postCode");
        assertPostCodeAvailable(postCode, null);

        SysPost post = new SysPost();
        post.setTenantId(TenantContext.get());
        post.setPostCode(postCode);
        post.setPostName(requireText(command.postName(), MAX_POST_NAME_LENGTH, "postName"));
        post.setSort(command.sort() == null ? 0 : command.sort());
        post.setStatus(command.status() == null ? STATUS_ENABLED : requireStatus(command.status()));
        post.setRemark(normalizeOptionalText(command.remark(), MAX_REMARK_LENGTH, "remark"));
        post.setDeleted(0);

        try {
            sysPostMapper.insert(post);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "岗位编码已存在");
        }
        log.info("sys post created postId={} postCode={}", post.getId(), postCode);
        return toView(post);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostView updatePost(Long id, SavePostCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        SysPost post = requirePost(id);

        post.setPostName(command.postName() == null
                ? post.getPostName()
                : requireText(command.postName(), MAX_POST_NAME_LENGTH, "postName"));
        post.setSort(command.sort() == null ? post.getSort() : command.sort());
        post.setStatus(command.status() == null ? post.getStatus() : requireStatus(command.status()));
        post.setRemark(command.remark() == null
                ? post.getRemark()
                : normalizeOptionalText(command.remark(), MAX_REMARK_LENGTH, "remark"));

        if (sysPostMapper.updateById(post) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "岗位不存在");
        }
        log.info("sys post updated postId={}", post.getId());
        return toView(post);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long id) {
        SysPost post = requirePost(id);

        Long userCount = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, TenantContext.get())
                .eq(SysUser::getPostId, post.getId())
                .eq(SysUser::getDeleted, 0));
        if (userCount != null && userCount > 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "岗位仍有账号引用，请先调整账号岗位");
        }

        post.setDeleted(1);
        if (sysPostMapper.updateById(post) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "岗位不存在");
        }
        log.info("sys post logically deleted postId={}", id);
    }

    /**
     * 移动节点并级联更新全部子部门 ancestors；子部门前缀替换在同一事务内完成。
     *
     * <p>路径语义：节点自身的路径 = ancestors + "," + id。直接子部门的 ancestors 恰好等于
     * 「移动前节点自身路径」，孙辈才带后缀，所以查询必须同时匹配相等与前缀。
     */
    private void moveSubtree(SysDept dept, SysDept newParent) {
        String newAncestors = newParent == null
                ? ROOT_ANCESTORS
                : newParent.getAncestors() + "," + newParent.getId();
        assertDepth(newAncestors);

        String oldNodePath = dept.getAncestors() + "," + dept.getId();
        String newNodePath = newAncestors + "," + dept.getId();
        if (oldNodePath.equals(newNodePath)) {
            return;
        }

        List<SysDept> descendants = sysDeptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getTenantId, dept.getTenantId())
                .eq(SysDept::getDeleted, 0)
                .and(w -> w.eq(SysDept::getAncestors, oldNodePath)
                        .or()
                        .likeRight(SysDept::getAncestors, oldNodePath + ",")));

        dept.setParentId(newParent == null ? ROOT_PARENT_ID : newParent.getId());
        dept.setAncestors(newAncestors);

        for (SysDept descendant : descendants) {
            String ancestorPath = descendant.getAncestors();
            if (ancestorPath == null || !ancestorPath.startsWith(oldNodePath)) {
                throw new BizException(ErrorCode.SYSTEM_ERROR,
                        "部门祖级路径损坏: " + descendant.getId());
            }
            descendant.setAncestors(newNodePath + ancestorPath.substring(oldNodePath.length()));
            if (sysDeptMapper.updateById(descendant) == 0) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND,
                        "子部门已被删除: " + descendant.getId());
            }
        }
        log.info("sys dept moved deptId={} newAncestors={} cascaded={}",
                dept.getId(), newAncestors, descendants.size());
    }

    private SysDept resolveParent(long parentId, Long movingDeptId) {
        if (parentId == ROOT_PARENT_ID) {
            return null;
        }
        if (movingDeptId != null && parentId == movingDeptId) {
            throw new BizException(ErrorCode.PARAM_INVALID, "父部门不能是自身");
        }
        SysDept parent = sysDeptMapper.selectOne(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getTenantId, TenantContext.get())
                .eq(SysDept::getId, parentId)
                .eq(SysDept::getDeleted, 0)
                .last("LIMIT 1"));
        if (parent == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "父部门不存在: " + parentId);
        }
        if (movingDeptId != null && containsId(parent.getAncestors(), movingDeptId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "父部门不能是自身或其子节点");
        }
        assertDepth(parent.getAncestors() + "," + parent.getId());
        return parent;
    }

    private boolean containsId(String ancestors, Long deptId) {
        if (!StringUtils.hasText(ancestors) || deptId == null) {
            return false;
        }
        for (String part : ancestors.split(",")) {
            if (part.trim().equals(String.valueOf(deptId))) {
                return true;
            }
        }
        return false;
    }

    private void assertDepth(String ancestors) {
        int depth = ancestors.split(",").length;
        if (depth > MAX_DEPT_DEPTH) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "部门层级不能超过 " + MAX_DEPT_DEPTH + " 级");
        }
    }

    private SysDept requireDept(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "部门 ID 必须是正整数");
        }
        SysDept dept = sysDeptMapper.selectOne(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getTenantId, TenantContext.get())
                .eq(SysDept::getId, id)
                .eq(SysDept::getDeleted, 0)
                .last("LIMIT 1"));
        if (dept == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "部门不存在");
        }
        return dept;
    }

    private SysPost requirePost(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "岗位 ID 必须是正整数");
        }
        SysPost post = sysPostMapper.selectOne(new LambdaQueryWrapper<SysPost>()
                .eq(SysPost::getTenantId, TenantContext.get())
                .eq(SysPost::getId, id)
                .eq(SysPost::getDeleted, 0)
                .last("LIMIT 1"));
        if (post == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "岗位不存在");
        }
        return post;
    }

    private void assertPostCodeAvailable(String postCode, Long excludeId) {
        Long count = sysPostMapper.selectCount(new LambdaQueryWrapper<SysPost>()
                .eq(SysPost::getTenantId, TenantContext.get())
                .eq(SysPost::getPostCode, postCode)
                .ne(excludeId != null, SysPost::getId, excludeId));
        if (count != null && count > 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "岗位编码已存在");
        }
    }

    private DeptNode buildNode(SysDept dept,
                               Map<Long, List<SysDept>> childrenByParent,
                               Set<Long> path) {
        if (!path.add(dept.getId())) {
            return null;
        }
        List<DeptNode> children = new ArrayList<>();
        for (SysDept child : childrenByParent.getOrDefault(dept.getId(), List.of())) {
            if (path.contains(child.getId())) {
                continue;
            }
            DeptNode childNode = buildNode(child, childrenByParent, path);
            if (childNode != null) {
                children.add(childNode);
            }
        }
        path.remove(dept.getId());
        return toNode(dept, children);
    }

    private DeptNode toNode(SysDept dept, List<DeptNode> children) {
        return new DeptNode(
                dept.getId(),
                dept.getParentId(),
                dept.getAncestors(),
                dept.getDeptName(),
                dept.getLeader(),
                maskPhone(dept.getPhone()),
                dept.getSort(),
                dept.getStatus(),
                children);
    }

    private PostView toView(SysPost post) {
        return new PostView(
                post.getId(),
                post.getPostCode(),
                post.getPostName(),
                post.getSort(),
                post.getStatus(),
                post.getRemark(),
                post.getCreatedAt());
    }

    private int requireStatus(int status) {
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0启用 1停用");
        }
        return status;
    }

    private String requireCode(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空");
        }
        String trimmed = value.trim();
        if (!CODE_PATTERN.matcher(trimmed).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    field + " 只能包含字母、数字、下划线、中划线，长度 2~64");
        }
        return trimmed;
    }

    private String requireText(String value, int maxLength, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 长度不能超过 " + maxLength);
        }
        return trimmed;
    }

    private String normalizeOptionalText(String value, int maxLength, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 长度不能超过 " + maxLength);
        }
        return trimmed;
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
