package com.soloeats.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.soloeats.system.domain.entity.SysDept;
import com.soloeats.system.mapper.SysDeptMapper;
import com.soloeats.system.mapper.SysPostMapper;
import com.soloeats.system.mapper.SysUserMapper;
import com.soloeats.system.service.command.SaveDeptCommand;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysOrgServiceImplTest {

    @Mock
    private SysDeptMapper sysDeptMapper;

    @Mock
    private SysPostMapper sysPostMapper;

    @Mock
    private SysUserMapper sysUserMapper;

    private SysOrgServiceImpl sysOrgService;

    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysDept.class);
    }

    @BeforeEach
    void setUp() {
        sysOrgService = new SysOrgServiceImpl(sysDeptMapper, sysPostMapper, sysUserMapper);
    }

    @Test
    @DisplayName("移动部门：直接子部门与孙辈 ancestors 都按新节点路径级联更新")
    @SuppressWarnings("unchecked")
    void moveDeptCascadesDirectChildren() {
        SysDept moving = dept(3L, 0L, "0");
        SysDept newParent = dept(5L, 0L, "0");
        SysDept directChild = dept(4L, 3L, "0,3");
        SysDept grandChild = dept(6L, 4L, "0,3,4");

        // requireDept(3) 与 resolveParent(5) 各调一次 selectOne
        when(sysDeptMapper.selectOne(any())).thenReturn(moving, newParent);
        when(sysDeptMapper.selectList(any())).thenReturn(List.of(directChild, grandChild));
        when(sysDeptMapper.updateById(any(SysDept.class))).thenReturn(1);

        sysOrgService.updateDept(3L, new SaveDeptCommand(5L, "Test Ops", null, null, 1, 0));

        assertThat(moving.getParentId()).isEqualTo(5L);
        assertThat(moving.getAncestors()).isEqualTo("0,5");
        // 子部门的 ancestors 必须指向移动后的父节点完整路径，而不是只到新父级
        assertThat(directChild.getAncestors()).isEqualTo("0,5,3");
        assertThat(grandChild.getAncestors()).isEqualTo("0,5,3,4");

        ArgumentCaptor<Wrapper<SysDept>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(sysDeptMapper).selectList(queryCaptor.capture());
        String sql = queryCaptor.getValue().getSqlSegment();
        assertThat(sql).contains("ancestors =").containsIgnoringCase("like");
        verify(sysDeptMapper, org.mockito.Mockito.times(3)).updateById(any(SysDept.class));
    }

    private SysDept dept(Long id, Long parentId, String ancestors) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setTenantId(1L);
        dept.setParentId(parentId);
        dept.setAncestors(ancestors);
        dept.setDeptName("dept-" + id);
        dept.setSort(1);
        dept.setStatus(0);
        dept.setDeleted(0);
        return dept;
    }
}
