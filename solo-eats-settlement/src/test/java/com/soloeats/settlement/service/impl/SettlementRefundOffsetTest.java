package com.soloeats.settlement.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.entity.RefundOrder;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.domain.enums.RefundStatus;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.settlement.domain.entity.SettlementBill;
import com.soloeats.settlement.domain.entity.SettlementItem;
import com.soloeats.settlement.mapper.SettlementBillMapper;
import com.soloeats.settlement.mapper.SettlementItemMapper;
import com.soloeats.settlement.service.CommissionService;
import com.soloeats.settlement.service.FundFlowService;
import com.soloeats.settlement.service.view.CommissionResolveResult;
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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 结算退款冲抵符合性用例。
 *
 * <p>需求来源：规格书 8.8「售后期与结算冻结」、8.4「退款冲抵」，
 * 口径以《技术架构与数据库设计说明书》3.10.2 公式与 3.10.4 退款冲抵为准：
 * <pre>
 * payable = gross - merchant_discount + platform_discount - commission - performance - refund_amount
 * payable &lt; 0 → 本期不扣款，carry_forward_amount 转入下期负项
 * </pre>
 *
 * <p>资金恒等式（任一被打破即资损）：
 * <ol>
 *   <li>已结算订单全额退款 → 该单净额回到 0（冲抵金额 = 原应结金额，非用户退款额）；</li>
 *   <li>同一笔退款只冲抵一次（{@code item_type=4 + source_id=退款单ID} 幂等）；</li>
 *   <li>本期应结为负 → {@code payable_amount=0} 且 {@code carry_forward_amount=负值}；</li>
 *   <li>上期负项在本期以 {@code item_type=5} 承接，且只承接一次。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class SettlementRefundOffsetTest {

    private static final long MERCHANT_ID = 10L;
    private static final long SETTLED_ORDER_ID = 500L;
    private static final long REFUND_ID = 9001L;
    /** 该订单原本实结给商家的金额（商品 + 打包 − 商家承担 + 平台承担 − 佣金）。 */
    private static final long ORIGINAL_SETTLE = 9148L;

    @Mock
    private SettlementBillMapper settlementBillMapper;
    @Mock
    private SettlementItemMapper settlementItemMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private CommissionService commissionService;
    @Mock
    private FundFlowService fundFlowService;

    @InjectMocks
    private SettlementServiceImpl settlementService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> ((TransactionCallback<Object>) invocation.getArgument(0))
                        .doInTransaction(mock(TransactionStatus.class)));
        lenient().when(settlementBillMapper.insert(any(SettlementBill.class)))
                .thenAnswer(invocation -> {
                    ((SettlementBill) invocation.getArgument(0)).setId(800L);
                    return 1;
                });
        // 佣金固定 6%，让断言可以直接算出来（10200 − 1000 = 9200 基数 → 552）
        lenient().when(commissionService.resolveAndRecord(any(Order.class), anyLong()))
                .thenReturn(new CommissionResolveResult(
                        null, null, new BigDecimal("0.0600"), 9200L, 552L));
        lenient().when(orderMapper.update(any(), any())).thenReturn(1);
        lenient().when(settlementBillMapper.findByMerchantAndDate(any(), any())).thenReturn(null);
        lenient().when(settlementBillMapper.findLatestCarryForward(anyLong())).thenReturn(null);
        lenient().when(settlementItemMapper.countByTypeAndSource(anyInt(), anyLong())).thenReturn(0);
    }

    @Test
    @DisplayName("已结算订单退款：按原应结金额冲抵，本期应结 = 订单应结 − 冲抵额")
    void settledOrderRefund_clawsBackOriginalSettleAmount() {
        stubOrders(List.of(pendingOrder()));
        stubRefunds(List.of(refund(REFUND_ID, SETTLED_ORDER_ID)));
        stubSettledOrders(List.of(settledOrder()));
        when(settlementItemMapper.sumOrderSettleAmount(SETTLED_ORDER_ID)).thenReturn(ORIGINAL_SETTLE);

        settlementService.generateDailyBills();

        SettlementBill bill = capturedBill();
        // 订单应结 10200 − 1000 + 500 − 552 = 9148；冲抵 9148 → 本期应结 0
        assertThat(bill.getRefundAmount()).isEqualTo(ORIGINAL_SETTLE);
        assertThat(bill.getPayableAmount()).isZero();
        assertThat(bill.getCarryForwardAmount()).isZero();

        List<SettlementItem> items = capturedItems();
        SettlementItem clawback = items.stream()
                .filter(item -> item.getItemType() == 4)
                .findFirst()
                .orElseThrow();
        assertThat(clawback.getAmount()).isEqualTo(-ORIGINAL_SETTLE);
        assertThat(clawback.getSourceId()).isEqualTo(REFUND_ID);
        assertThat(clawback.getOrderId()).isEqualTo(SETTLED_ORDER_ID);
    }

    @Test
    @DisplayName("同一笔退款已冲抵过则跳过，不重复扣商家钱（幂等）")
    void alreadyClawedBackRefund_isSkipped() {
        stubOrders(List.of(pendingOrder()));
        stubRefunds(List.of(refund(REFUND_ID, SETTLED_ORDER_ID)));
        stubSettledOrders(List.of(settledOrder()));
        when(settlementItemMapper.countByTypeAndSource(4, REFUND_ID)).thenReturn(1);

        settlementService.generateDailyBills();

        SettlementBill bill = capturedBill();
        assertThat(bill.getRefundAmount()).isZero();
        assertThat(bill.getPayableAmount()).isEqualTo(ORIGINAL_SETTLE);
        assertThat(capturedItems()).noneMatch(item -> item.getItemType() == 4);
        verify(settlementItemMapper, never()).sumOrderSettleAmount(anyLong());
    }

    @Test
    @DisplayName("本期应结为负：不在本期扣款，转 carry_forward_amount 进入下期")
    void negativePayable_rollsIntoCarryForward() {
        // 本期无新订单，只有一笔 9148 的退款冲抵 → 应结 −9148
        stubOrders(List.of());
        stubRefunds(List.of(refund(REFUND_ID, SETTLED_ORDER_ID)));
        stubSettledOrders(List.of(settledOrder()));
        when(settlementItemMapper.sumOrderSettleAmount(SETTLED_ORDER_ID)).thenReturn(ORIGINAL_SETTLE);

        settlementService.generateDailyBills();

        SettlementBill bill = capturedBill();
        assertThat(bill.getPayableAmount()).isZero();
        assertThat(bill.getCarryForwardAmount()).isEqualTo(-ORIGINAL_SETTLE);
        assertThat(bill.getRefundAmount()).isEqualTo(ORIGINAL_SETTLE);
        // 只有退款没有新订单时不应误改订单结算状态
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("承接上期负项：以 item_type=5 计入本期，应结相应扣减")
    void previousCarryForward_isCarriedIntoCurrentBill() {
        stubOrders(List.of(pendingOrder()));
        stubRefunds(List.of());

        SettlementBill previous = new SettlementBill();
        previous.setId(700L);
        previous.setBillNo("SB202610260001");
        previous.setCarryForwardAmount(-5000L);
        when(settlementBillMapper.findLatestCarryForward(MERCHANT_ID)).thenReturn(previous);
        when(settlementItemMapper.countByTypeAndSource(5, 700L)).thenReturn(0);

        settlementService.generateDailyBills();

        SettlementBill bill = capturedBill();
        assertThat(bill.getPayableAmount()).isEqualTo(ORIGINAL_SETTLE - 5000L);
        assertThat(bill.getRefundAmount()).isZero();
        assertThat(bill.getCarryForwardAmount()).isZero();

        SettlementItem carryItem = capturedItems().stream()
                .filter(item -> item.getItemType() == 5)
                .findFirst()
                .orElseThrow();
        assertThat(carryItem.getAmount()).isEqualTo(-5000L);
        assertThat(carryItem.getSourceId()).isEqualTo(700L);
    }

    @Test
    @DisplayName("上期负项已被承接则不重复承接")
    void alreadyCarriedForward_isNotConsumedTwice() {
        stubOrders(List.of(pendingOrder()));
        stubRefunds(List.of());

        SettlementBill previous = new SettlementBill();
        previous.setId(700L);
        previous.setBillNo("SB202610260001");
        previous.setCarryForwardAmount(-5000L);
        when(settlementBillMapper.findLatestCarryForward(MERCHANT_ID)).thenReturn(previous);
        when(settlementItemMapper.countByTypeAndSource(5, 700L)).thenReturn(1);

        settlementService.generateDailyBills();

        SettlementBill bill = capturedBill();
        assertThat(bill.getPayableAmount()).isEqualTo(ORIGINAL_SETTLE);
        assertThat(capturedItems()).noneMatch(item -> item.getItemType() == 5);
    }

    @Test
    @DisplayName("订单从未进入过账单（原应结为 0）时不做冲抵，避免凭空扣款")
    void refundWithoutSettledItem_isSkipped() {
        stubOrders(List.of(pendingOrder()));
        stubRefunds(List.of(refund(REFUND_ID, SETTLED_ORDER_ID)));
        stubSettledOrders(List.of(settledOrder()));
        when(settlementItemMapper.sumOrderSettleAmount(SETTLED_ORDER_ID)).thenReturn(0L);

        settlementService.generateDailyBills();

        SettlementBill bill = capturedBill();
        assertThat(bill.getRefundAmount()).isZero();
        assertThat(bill.getPayableAmount()).isEqualTo(ORIGINAL_SETTLE);
        assertThat(capturedItems()).noneMatch(item -> item.getItemType() == 4);
    }

    @Test
    @DisplayName("账单金额恒等式：gross − 商家承担 + 平台承担 − 佣金 − 退款 = 应结")
    void billAmountsSatisfyFormula() {
        stubOrders(List.of(pendingOrder()));
        stubRefunds(List.of(refund(REFUND_ID, SETTLED_ORDER_ID)));
        stubSettledOrders(List.of(settledOrder()));
        when(settlementItemMapper.sumOrderSettleAmount(SETTLED_ORDER_ID)).thenReturn(3000L);

        settlementService.generateDailyBills();

        SettlementBill bill = capturedBill();
        long expected = bill.getGrossAmount()
                - bill.getMerchantDiscount()
                + bill.getPlatformDiscount()
                - bill.getCommissionAmount()
                - bill.getPerformanceFee()
                - bill.getRefundAmount()
                + bill.getCarryForwardAmount();
        assertThat(bill.getPayableAmount()).isEqualTo(expected);
        assertThat(bill.getRefundAmount()).isEqualTo(3000L);
    }

    // ---------- helpers ----------

    private void stubOrders(List<Order> orders) {
        when(orderMapper.selectList(any())).thenReturn(orders);
    }

    private void stubRefunds(List<RefundOrder> refunds) {
        when(settlementBillMapper.findSettleableRefunds(anyInt(), any(), any())).thenReturn(refunds);
    }

    private void stubSettledOrders(List<Order> orders) {
        lenient().when(orderMapper.selectBatchIds(any())).thenReturn(orders);
    }

    private SettlementBill capturedBill() {
        ArgumentCaptor<SettlementBill> captor = ArgumentCaptor.forClass(SettlementBill.class);
        verify(settlementBillMapper).insert(captor.capture());
        return captor.getValue();
    }

    private List<SettlementItem> capturedItems() {
        ArgumentCaptor<SettlementItem> captor = ArgumentCaptor.forClass(SettlementItem.class);
        verify(settlementItemMapper, org.mockito.Mockito.atLeast(0)).insert(captor.capture());
        return captor.getAllValues();
    }

    /** 本期待入账订单：商品 10000 + 打包 200，商家承担 1000，平台承担 500。 */
    private Order pendingOrder() {
        Order order = new Order();
        order.setId(1001L);
        order.setMerchantId(MERCHANT_ID);
        order.setShopId(1L);
        order.setStatus(OrderStatus.COMPLETED.getCode());
        order.setSettlementStatus(0);
        order.setRefundStatus(0);
        order.setRefundedAmount(0L);
        order.setGoodsAmount(10000L);
        order.setPackagingFee(200L);
        order.setMerchantDiscount(1000L);
        order.setPlatformDiscount(500L);
        order.setSettleEligibleAt(LocalDateTime.now().minusHours(1));
        return order;
    }

    /** 已结算过的订单（钱已结给商家），之后发生退款。 */
    private Order settledOrder() {
        Order order = new Order();
        order.setId(SETTLED_ORDER_ID);
        order.setMerchantId(MERCHANT_ID);
        order.setShopId(1L);
        order.setStatus(OrderStatus.COMPLETED.getCode());
        order.setSettlementStatus(1);
        order.setRefundStatus(2);
        order.setRefundedAmount(9200L);
        return order;
    }

    private RefundOrder refund(long refundId, long orderId) {
        RefundOrder refund = new RefundOrder();
        refund.setId(refundId);
        refund.setOrderId(orderId);
        refund.setRefundNo("RF" + refundId);
        refund.setStatus(RefundStatus.SUCCESS.getCode());
        refund.setAmount(9200L);
        refund.setSuccessAt(LocalDateTime.now().minusHours(1));
        return refund;
    }
}
