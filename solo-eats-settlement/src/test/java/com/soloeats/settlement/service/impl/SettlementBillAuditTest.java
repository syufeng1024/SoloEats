package com.soloeats.settlement.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.settlement.domain.entity.SettlementBill;
import com.soloeats.settlement.mapper.SettlementBillMapper;
import com.soloeats.settlement.mapper.SettlementItemMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 结算单审核/模拟打款的合法与非法流转。既有 SettlementServiceImplTest 只覆盖生成与查询，不受本类影响。
 */
@ExtendWith(MockitoExtension.class)
class SettlementBillAuditTest {

    private static final long BILL_ID = 88L;
    private static final long AUDITOR_ID = 99L;

    @Mock
    private SettlementBillMapper settlementBillMapper;
    @Mock
    private SettlementItemMapper settlementItemMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private SettlementServiceImpl settlementService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SettlementBill.class);
    }

    @Test
    @DisplayName("审核通过：0 → 10，写 reviewed_by / reviewed_at")
    void auditPassMovesBillToApproved() {
        when(settlementBillMapper.update(isNull(), any())).thenReturn(1);

        settlementService.auditBill(BILL_ID, true, "金额核对无误", AUDITOR_ID);

        LambdaUpdateWrapper<SettlementBill> wrapper = captureUpdate();
        assertThat(wrapper.getSqlSet()).contains("reviewed_by").contains("reviewed_at");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(10)
                .contains(AUDITOR_ID);
    }

    @Test
    @DisplayName("模拟打款：10 → 30，写 paid_at")
    void payApprovedBillMovesToPaid() {
        when(settlementBillMapper.update(isNull(), any())).thenReturn(1);

        settlementService.payBill(BILL_ID, AUDITOR_ID);

        LambdaUpdateWrapper<SettlementBill> wrapper = captureUpdate();
        assertThat(wrapper.getSqlSet()).contains("paid_at");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(30);
    }

    @Test
    @DisplayName("非待审核结算单不能再次审核：条件更新 0 行抛 60005")
    void auditNonPendingBillFails() {
        when(settlementBillMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> settlementService.auditBill(BILL_ID, true, null, AUDITOR_ID))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PAYOUT_STATE_INVALID.getCode()));
    }

    @Test
    @DisplayName("未审核结算单不能打款：条件更新 0 行抛 60005")
    void payUnauditedBillFails() {
        when(settlementBillMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> settlementService.payBill(BILL_ID, AUDITOR_ID))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PAYOUT_STATE_INVALID.getCode()));
    }

    private LambdaUpdateWrapper<SettlementBill> captureUpdate() {
        ArgumentCaptor<LambdaUpdateWrapper<SettlementBill>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(settlementBillMapper).update(isNull(), captor.capture());
        return captor.getValue();
    }
}
