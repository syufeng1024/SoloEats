package com.soloeats.settlement.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.settlement.domain.entity.SettlementBill;
import com.soloeats.settlement.domain.entity.SettlementItem;
import com.soloeats.settlement.mapper.SettlementBillMapper;
import com.soloeats.settlement.mapper.SettlementItemMapper;
import com.soloeats.settlement.service.view.SettlementGenerateResult;
import com.soloeats.settlement.service.view.SettlementListResult;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceImplTest {

    private static final long MERCHANT_ID = 10L;

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
        // 纯单测不启动 Spring，LambdaUpdateWrapper 需要显式注册实体列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ReflectionTestUtils.setField(settlementService, "commissionRate", new BigDecimal("0.0600"));
        lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> ((TransactionCallback<Object>) invocation.getArgument(0))
                        .doInTransaction(mock(TransactionStatus.class)));
    }

    @Test
    @DisplayName("按商家归集结算单：抽成基数为 goods+packaging-merchantDiscount，按 HALF_UP 取整")
    void generateBillComputesCommissionAndPayable() {
        when(orderMapper.selectList(any())).thenReturn(List.of(
                order(1L, 10000L, 200L, 500L, 300L),
                order(2L, 5000L, 0L, 0L, 0L)
        ));
        when(settlementBillMapper.findByMerchantAndDate(any(), any())).thenReturn(null);
        AtomicLong billIds = new AtomicLong(800L);
        when(settlementBillMapper.insert(any(SettlementBill.class))).thenAnswer(invocation -> {
            ((SettlementBill) invocation.getArgument(0)).setId(billIds.incrementAndGet());
            return 1;
        });
        when(orderMapper.update(any(), any())).thenReturn(2);

        SettlementGenerateResult result = settlementService.generateDailyBills();

        assertThat(result.billCount()).isEqualTo(1);
        assertThat(result.orderCount()).isEqualTo(2);
        assertThat(result.billIds()).containsExactly(801L);

        ArgumentCaptor<SettlementBill> billCaptor = ArgumentCaptor.forClass(SettlementBill.class);
        verify(settlementBillMapper).insert(billCaptor.capture());
        SettlementBill bill = billCaptor.getValue();
        assertThat(bill.getMerchantId()).isEqualTo(MERCHANT_ID);
        assertThat(bill.getGrossAmount()).isEqualTo(15200L);
        assertThat(bill.getMerchantDiscount()).isEqualTo(500L);
        assertThat(bill.getPlatformDiscount()).isEqualTo(300L);
        assertThat(bill.getCommissionBase()).isEqualTo(14700L);
        // 9700*6% = 582, 5000*6% = 300
        assertThat(bill.getCommissionAmount()).isEqualTo(882L);
        // (10200-500+300-582) + (5000-0+0-300) = 9418 + 4700
        assertThat(bill.getPayableAmount()).isEqualTo(14118L);

        ArgumentCaptor<SettlementItem> itemCaptor = ArgumentCaptor.forClass(SettlementItem.class);
        verify(settlementItemMapper, org.mockito.Mockito.times(4)).insert(itemCaptor.capture());
        long commissionItems = itemCaptor.getAllValues().stream()
                .filter(item -> item.getItemType() == 2)
                .mapToLong(SettlementItem::getAmount)
                .sum();
        assertThat(commissionItems).isEqualTo(-882L);
    }

    @Test
    @DisplayName("当日已生成结算单的商家跳过，不重复归集")
    void existingBillIsSkipped() {
        when(orderMapper.selectList(any())).thenReturn(List.of(order(1L, 1000L, 0L, 0L, 0L)));
        when(settlementBillMapper.findByMerchantAndDate(any(), any())).thenReturn(new SettlementBill());

        SettlementGenerateResult result = settlementService.generateDailyBills();

        assertThat(result.billCount()).isZero();
        assertThat(result.orderCount()).isZero();
        verify(settlementBillMapper, never()).insert(any(SettlementBill.class));
    }

    @Test
    @DisplayName("订单结算状态回写数量不一致时整单失败，避免重复结算")
    void orderSettlementStatusUpdateMismatchFails() {
        when(orderMapper.selectList(any())).thenReturn(List.of(order(1L, 1000L, 0L, 0L, 0L)));
        when(settlementBillMapper.findByMerchantAndDate(any(), any())).thenReturn(null);
        when(settlementBillMapper.insert(any(SettlementBill.class))).thenReturn(1);
        when(orderMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> settlementService.generateDailyBills())
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.SETTLEMENT_STATE_INVALID.getCode());
    }

    @Test
    @DisplayName("商家账单汇总：逐项累加不为 null 的金额")
    void merchantBillSummary() {
        when(settlementBillMapper.selectList(any())).thenReturn(List.of(
                bill(10000L, 500L, 9000L),
                bill(20000L, 1000L, 18000L)
        ));

        SettlementListResult result = settlementService.listMerchantBills(MERCHANT_ID, null);

        assertThat(result.summary().billCount()).isEqualTo(2);
        assertThat(result.summary().grossAmount()).isEqualTo(30000L);
        assertThat(result.summary().platformCommission()).isEqualTo(1500L);
        assertThat(result.summary().payableAmount()).isEqualTo(27000L);
    }

    @Test
    @DisplayName("跨商家查询账单详情时按不存在处理")
    void billDetailRejectsOtherMerchant() {
        SettlementBill bill = bill(1000L, 60L, 900L);
        bill.setId(1L);
        bill.setMerchantId(999L);
        when(settlementBillMapper.selectById(1L)).thenReturn(bill);

        assertThatThrownBy(() -> settlementService.getBillDetail(MERCHANT_ID, 1L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.SETTLEMENT_NOT_FOUND.getCode());
    }

    private Order order(long id, long goods, long packaging, long merchantDiscount, long platformDiscount) {
        Order order = new Order();
        order.setId(id);
        order.setMerchantId(MERCHANT_ID);
        order.setShopId(1L);
        order.setStatus(OrderStatus.COMPLETED.getCode());
        order.setSettlementStatus(0);
        order.setRefundStatus(0);
        order.setRefundedAmount(0L);
        order.setGoodsAmount(goods);
        order.setPackagingFee(packaging);
        order.setMerchantDiscount(merchantDiscount);
        order.setPlatformDiscount(platformDiscount);
        order.setCompletedAt(LocalDateTime.now().minusHours(2));
        order.setSettleEligibleAt(LocalDateTime.now().minusHours(1));
        return order;
    }

    private SettlementBill bill(long gross, long commission, long payable) {
        SettlementBill bill = new SettlementBill();
        bill.setMerchantId(MERCHANT_ID);
        bill.setBillDate(LocalDate.now());
        bill.setGrossAmount(gross);
        bill.setCommissionAmount(commission);
        bill.setPayableAmount(payable);
        bill.setStatus(0);
        return bill;
    }
}
