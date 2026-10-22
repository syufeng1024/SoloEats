package com.soloeats.merchant.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.merchant.domain.entity.Merchant;
import com.soloeats.merchant.domain.entity.MerchantAuditLog;
import com.soloeats.merchant.domain.entity.MerchantQualification;
import com.soloeats.merchant.domain.entity.MerchantSettlementAccount;
import com.soloeats.merchant.domain.enums.MerchantStatus;
import com.soloeats.merchant.mapper.MerchantAuditLogMapper;
import com.soloeats.merchant.mapper.MerchantMapper;
import com.soloeats.merchant.mapper.MerchantQualificationMapper;
import com.soloeats.merchant.mapper.MerchantSettlementAccountMapper;
import com.soloeats.merchant.service.MerchantNotifyPort;
import com.soloeats.merchant.service.command.AuditMerchantCommand;
import com.soloeats.merchant.support.PiiCipher;
import com.soloeats.system.service.SysRoleService;
import com.soloeats.system.service.SysTenantService;
import com.soloeats.system.service.SysUserService;
import com.soloeats.system.service.command.CreateUserCommand;
import com.soloeats.system.service.command.SaveTenantCommand;
import com.soloeats.system.service.view.SysRoleView;
import com.soloeats.system.service.view.SysUserView;
import com.soloeats.system.service.view.TenantView;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantAdminServiceImplTest {

    @Mock
    private MerchantMapper merchantMapper;
    @Mock
    private MerchantQualificationMapper qualificationMapper;
    @Mock
    private MerchantAuditLogMapper auditLogMapper;
    @Mock
    private MerchantSettlementAccountMapper settlementAccountMapper;
    @Mock
    private SysUserService sysUserService;
    @Mock
    private SysRoleService sysRoleService;
    @Mock
    private SysTenantService sysTenantService;
    @Mock
    private MerchantNotifyPort merchantNotifyPort;

    private MerchantAdminServiceImpl service;

    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Merchant.class);
        TableInfoHelper.initTableInfo(assistant, MerchantQualification.class);
        TableInfoHelper.initTableInfo(assistant, MerchantAuditLog.class);
        TableInfoHelper.initTableInfo(assistant, MerchantSettlementAccount.class);
    }

    @BeforeEach
    void setUp() {
        service = new MerchantAdminServiceImpl(
                merchantMapper,
                qualificationMapper,
                auditLogMapper,
                settlementAccountMapper,
                sysUserService,
                sysRoleService,
                sysTenantService,
                merchantNotifyPort,
                new PiiCipher(""));
    }

    @Test
    @DisplayName("草稿商家直接审核通过：拒绝 0→2 跳审，不建账号/租户")
    void approveDraftRejected() {
        when(merchantMapper.selectById(1L)).thenReturn(merchant(MerchantStatus.DRAFT.code()));

        assertThatThrownBy(() -> service.approve(1L, approveCommand()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode())
                        .isEqualTo(ErrorCode.MERCHANT_STATUS_INVALID.getCode()));

        verifyNoInteractions(sysUserService, sysTenantService, auditLogMapper, merchantNotifyPort);
    }

    @Test
    @DisplayName("待审核商家通过：条件更新 + 审核流水 + 建商家账号绑 roleId=9 + 建商家租户 + 回填租户")
    void approvePendingCreatesAccountAndTenant() {
        Merchant pending = merchant(MerchantStatus.PENDING.code());
        when(merchantMapper.selectById(1L)).thenReturn(pending);
        when(merchantMapper.update(any(), any())).thenReturn(1);
        when(sysRoleService.page(eq(2), eq(0), eq(1L), eq(100L)))
                .thenReturn(PageResult.of(List.of(new SysRoleView(
                        9L, 1L, "MERCHANT_MANAGER", "商家管理员", 2, 3, 9, 0, null, null)),
                        1, 1, 100));
        when(sysUserService.page(any(), any(), any(), any(), any(), anyLong(), anyLong()))
                .thenReturn(PageResult.of(List.of(), 0, 1, 100));
        when(sysUserService.create(any(CreateUserCommand.class)))
                .thenReturn(new SysUserView(100L, 1L, "demo-merchant", "示例餐饮", "13800000001",
                        2, 1L, null, null, null, 3, 0, null, LocalDateTime.now()));
        when(sysTenantService.create(any(SaveTenantCommand.class)))
                .thenReturn(new TenantView(2L, "M202610220001", "示例餐饮", 2, 1L,
                        null, 0, null, "张三", null, null, null));
        when(qualificationMapper.selectList(any())).thenReturn(List.of());
        when(auditLogMapper.selectList(any())).thenReturn(List.of());

        service.approve(1L, approveCommand());

        ArgumentCaptor<CreateUserCommand> userCaptor =
                ArgumentCaptor.forClass(CreateUserCommand.class);
        verify(sysUserService).create(userCaptor.capture());
        CreateUserCommand createCommand = userCaptor.getValue();
        assertThat(createCommand.userType()).isEqualTo(2);
        assertThat(createCommand.merchantId()).isEqualTo(1L);
        assertThat(createCommand.roleIds()).containsExactly(9L);
        assertThat(createCommand.dataScope()).isEqualTo(3);
        assertThat(createCommand.password()).isEqualTo("Merchant@123");

        ArgumentCaptor<MerchantAuditLog> logCaptor = ArgumentCaptor.forClass(MerchantAuditLog.class);
        verify(auditLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo("APPROVE");
        assertThat(logCaptor.getValue().getFromStatus()).isEqualTo(MerchantStatus.PENDING.code());
        assertThat(logCaptor.getValue().getToStatus()).isEqualTo(MerchantStatus.APPROVED.code());

        verify(sysTenantService).create(any(SaveTenantCommand.class));
        // 第一次条件更新状态，第二次回填 tenant_id
        verify(merchantMapper, times(2)).update(any(), any());
        verify(merchantNotifyPort).notifyAuditResult(
                1L, MerchantStatus.APPROVED.code(), "资料齐全");
    }

    @Test
    @DisplayName("驳回原因少于 5 字：抛 PARAM_INVALID，不更新状态")
    void rejectWithShortRemark() {
        when(merchantMapper.selectById(1L)).thenReturn(merchant(MerchantStatus.PENDING.code()));

        assertThatThrownBy(() -> service.reject(1L, "不行", 1L, "admin"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode())
                        .isEqualTo(ErrorCode.PARAM_INVALID.getCode()));

        verify(merchantMapper, never()).update(any(), any());
        verifyNoInteractions(auditLogMapper, merchantNotifyPort);
    }

    @Test
    @DisplayName("待审核商家不可直接停用：只允许 2↔4")
    void updateStatusRejectsPending() {
        when(merchantMapper.selectById(1L)).thenReturn(merchant(MerchantStatus.PENDING.code()));

        assertThatThrownBy(() -> service.updateStatus(
                1L, MerchantStatus.DISABLED.code(), 1L, "admin"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode())
                        .isEqualTo(ErrorCode.MERCHANT_STATUS_INVALID.getCode()));

        verify(merchantMapper, never()).update(any(), any());
    }

    private Merchant merchant(int status) {
        Merchant merchant = new Merchant();
        merchant.setId(1L);
        merchant.setTenantId(1L);
        merchant.setMerchantNo("M202610220001");
        merchant.setMerchantName("示例餐饮");
        merchant.setContactName("张三");
        merchant.setContactPhone("13800000001");
        merchant.setStatus(status);
        merchant.setCommissionRate(new BigDecimal("0.0600"));
        merchant.setLevel(1);
        merchant.setDeleted(0);
        merchant.setVersion(0);
        return merchant;
    }

    private AuditMerchantCommand approveCommand() {
        return new AuditMerchantCommand(
                new BigDecimal("0.0600"),
                2,
                "demo-merchant",
                "Merchant@123",
                "资料齐全",
                1L,
                "admin");
    }
}
