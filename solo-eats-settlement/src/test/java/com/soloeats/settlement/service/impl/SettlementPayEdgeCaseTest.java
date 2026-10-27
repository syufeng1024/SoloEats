package com.soloeats.settlement.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.settlement.domain.entity.FundFlow;
import com.soloeats.settlement.domain.entity.SettlementBill;
import com.soloeats.settlement.mapper.FundFlowMapper;
import com.soloeats.settlement.mapper.SettlementBillMapper;
import com.soloeats.settlement.mapper.SettlementItemMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 打款与结算支出流水的耦合取舍（QA 独立补充）。
 *
 * <p>验证两个已声明的业务取舍是否真的按声明工作：
 * ① 0 元账单打款不留流水（对账缺口的来源）；
 * ② 流水写入失败不回滚打款（避免「钱已打、账回滚」）。
 */
@ExtendWith(MockitoExtension.class)
class SettlementPayEdgeCaseTest {

    private static final long BILL_ID = 66L;
    private static final long OPERATOR_ID = 9L;

    @Mock
    private SettlementBillMapper settlementBillMapper;
    @Mock
    private SettlementItemMapper settlementItemMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private FundFlowMapper fundFlowMapper;

    @InjectMocks
    private SettlementServiceImpl settlementService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SettlementBill.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(settlementService, "fundFlowService",
                new FundFlowServiceImpl(fundFlowMapper));
    }

    @Test
    @DisplayName("0 元结算单打款：payable_amount = 0 时不写流水，账单侧无任何资金痕迹")
    void zeroPayableBillPayWritesNoFundFlow() {
        when(settlementBillMapper.update(isNull(), any())).thenReturn(1);
        when(settlementBillMapper.selectById(BILL_ID)).thenReturn(bill(0L));

        settlementService.payBill(BILL_ID, OPERATOR_ID);

        verify(fundFlowMapper, never()).insert(any(FundFlow.class));
    }

    @Test
    @DisplayName("流水写入异常：只记日志，不回滚已完成的打款状态")
    void fundFlowFailureDoesNotFailPay() {
        when(settlementBillMapper.update(isNull(), any())).thenReturn(1);
        when(settlementBillMapper.selectById(BILL_ID)).thenReturn(bill(14_118L));
        when(fundFlowMapper.countByBiz(any(), any(), any())).thenReturn(0);
        when(fundFlowMapper.insert(any(FundFlow.class))).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> settlementService.payBill(BILL_ID, OPERATOR_ID))
                .doesNotThrowAnyException();
    }

    private SettlementBill bill(long payableAmount) {
        SettlementBill bill = new SettlementBill();
        bill.setId(BILL_ID);
        bill.setBillNo("SB20261027000066");
        bill.setMerchantId(10L);
        bill.setPayableAmount(payableAmount);
        bill.setStatus(30);
        return bill;
    }
}
