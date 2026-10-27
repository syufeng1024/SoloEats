package com.soloeats.order.service;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.service.CouponLockResult;
import com.soloeats.marketing.service.CouponService;
import com.soloeats.member.service.MemberAddressService;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.enums.FulfillmentType;
import com.soloeats.order.domain.statemachine.OrderStateMachine;
import com.soloeats.order.mapper.OrderDiscountMapper;
import com.soloeats.order.mapper.OrderItemMapper;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.order.mapper.OrderStatusLogMapper;
import com.soloeats.order.service.command.CreateOrderCommand;
import com.soloeats.order.service.result.CreateOrderResult;
import com.soloeats.product.domain.entity.Sku;
import com.soloeats.product.service.SkuService;
import com.soloeats.product.service.StockService;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 优惠分摊符合性用例（规格书 9.7 / 9.8 / 11.2「优惠」）。
 *
 * <p>核心不变量（任一被打破即判定为缺陷）：
 * <ol>
 *   <li>Σ 明细优惠额 == 订单优惠额（{@code CreateOrderResult.discountAmount}）；</li>
 *   <li>Σ 明细应付额 == 实付 − 打包费 − 配送费；</li>
 *   <li>单项优惠额 ≤ 该项原价（不出现负数应付、不跨项挪用）；</li>
 *   <li>余数（±分）只归金额最大的那一项，不平摊。</li>
 * </ol>
 *
 * <p><b>修复验证型用例</b>：{@code apportionSumEqualsDiscountWhenSplitIsIndivisible}、
 * {@code remainderGoesToSingleLargestItem} 在当前实现下预期失败
 * （{@code OrderApplicationService#apportionDiscount} 逐项 HALF_UP 且只回收正余数）。
 */
@ExtendWith(MockitoExtension.class)
class OrderApportionComplianceTest {

    private static final long USER_ID = 7L;
    private static final long MERCHANT_ID = 1L;
    private static final long SHOP_ID = 1L;
    private static final long COUPON_ID = 3001L;
    private static final long ORDER_ID = 1000L;

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private OrderDiscountMapper orderDiscountMapper;
    @Mock
    private OrderStatusLogMapper orderStatusLogMapper;
    @Mock
    private OrderStateMachine orderStateMachine;
    @Mock
    private SkuService skuService;
    @Mock
    private StockService stockService;
    @Mock
    private CouponService couponService;
    @Mock
    private MemberAddressService memberAddressService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderApplicationService orderApplicationService;

    @Test
    @DisplayName("分摊合计 = 订单优惠额（200 分券分给 3 个 100 分商品，除不尽）")
    void apportionSumEqualsDiscountWhenSplitIsIndivisible() {
        stubThreeItemsAt100();
        stubCoupon(200L, 1);
        stubInsertAndUpdate();

        CreateOrderResult result = orderApplicationService.createOrder(
                command(List.of(item(1001L, 1), item(1002L, 1), item(1003L, 1)), 100L), "it-sum");

        long itemDiscountSum = result.items().stream()
                .mapToLong(CreateOrderResult.ItemResult::discountAmount)
                .sum();
        long itemPayableSum = result.items().stream()
                .mapToLong(CreateOrderResult.ItemResult::payableAmount)
                .sum();

        // 不变量 1：明细优惠合计必须精确等于订单优惠额
        assertThat(itemDiscountSum).isEqualTo(result.discountAmount());
        assertThat(itemDiscountSum).isEqualTo(200L);
        // 不变量 2：明细应付合计 = 实付 − 打包费 − 配送费
        assertThat(itemPayableSum)
                .isEqualTo(result.payAmount() - result.packagingFee() - result.deliveryFee());
    }

    @Test
    @DisplayName("余数只归金额最大的一项，不平摊（3 项等额时不得出现 3 项相同优惠）")
    void remainderGoesToSingleLargestItem() {
        stubThreeItemsAt100();
        stubCoupon(200L, 1);
        stubInsertAndUpdate();

        CreateOrderResult result = orderApplicationService.createOrder(
                command(List.of(item(1001L, 1), item(1002L, 1), item(1003L, 1)), 100L), "it-remainder");

        long max = result.items().stream()
                .mapToLong(CreateOrderResult.ItemResult::discountAmount).max().orElseThrow();
        long min = result.items().stream()
                .mapToLong(CreateOrderResult.ItemResult::discountAmount).min().orElseThrow();
        long sum = result.items().stream()
                .mapToLong(CreateOrderResult.ItemResult::discountAmount).sum();

        assertThat(sum).isEqualTo(200L);
        // 余数 2 分必须集中到一项，故最大项与最小项必然不相等
        assertThat(max).isGreaterThan(min);
        assertThat(max - min).isEqualTo(2L);
    }

    @Test
    @DisplayName("单项优惠额不超过该项原价，且 优惠 + 应付 = 原价")
    void apportionNeverExceedsItemAmount() {
        stubThreeItemsAt100();
        stubCoupon(300L, 1);
        stubInsertAndUpdate();

        CreateOrderResult result = orderApplicationService.createOrder(
                command(List.of(item(1001L, 1), item(1002L, 1), item(1003L, 1)), 100L), "it-cap");

        for (CreateOrderResult.ItemResult item : result.items()) {
            assertThat(item.discountAmount()).isLessThanOrEqualTo(item.originalAmount());
            assertThat(item.payableAmount()).isGreaterThanOrEqualTo(0L);
            assertThat(item.discountAmount() + item.payableAmount())
                    .isEqualTo(item.originalAmount());
        }
        assertThat(result.items().stream()
                .mapToLong(CreateOrderResult.ItemResult::discountAmount).sum()).isEqualTo(300L);
        assertThat(result.discountAmount()).isEqualTo(300L);
    }

    @Test
    @DisplayName("无券时逐项优惠为 0，实付 = 商品 + 打包 + 配送（Σ=0 边界）")
    void noCouponProducesZeroDiscountEverywhere() {
        stubThreeItemsAt100();
        stubInsertOnly();

        // 第 2 个实参是 couponId，无券必须传 null（packagingFee 由 helper 固定为 100）
        CreateOrderResult result = orderApplicationService.createOrder(
                command(List.of(item(1001L, 1), item(1002L, 1), item(1003L, 1)), null), "it-nocoupon");

        assertThat(result.discountAmount()).isZero();
        assertThat(result.items()).allSatisfy(item -> assertThat(item.discountAmount()).isZero());
        assertThat(result.items().stream()
                .mapToLong(CreateOrderResult.ItemResult::payableAmount).sum()).isEqualTo(300L);
        assertThat(result.payAmount())
                .isEqualTo(result.goodsAmount() + result.packagingFee() + result.deliveryFee());
    }

    @Test
    @DisplayName("单商品全额抵扣：优惠 = 商品额，应付 = 0，打包费不计入抵扣")
    void singleItemFullyOffsetKeepsPackagingFeePayable() {
        stubSku(1001L, 100L);
        stubCoupon(100L, 1);
        stubInsertAndUpdate();

        CreateOrderResult result = orderApplicationService.createOrder(
                command(List.of(item(1001L, 1)), 100L), "it-single");

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).discountAmount()).isEqualTo(100L);
        assertThat(result.items().get(0).payableAmount()).isZero();
        assertThat(result.payAmount()).isEqualTo(100L);
    }

    @Test
    @DisplayName("金额溢出时拒绝下单，不写任何订单数据")
    void amountOverflowRejected() {
        Sku sku = new Sku();
        sku.setId(1001L);
        sku.setShopId(SHOP_ID);
        sku.setSpuId(10L);
        sku.setSkuName("超大额商品");
        sku.setPrice(Long.MAX_VALUE / 2);
        when(skuService.getRequired(1001L, SHOP_ID)).thenReturn(sku);

        assertBizCode(() -> orderApplicationService.createOrder(
                command(List.of(item(1001L, 3)), 100L), "it-overflow"), ErrorCode.PARAM_INVALID);

        org.mockito.Mockito.verify(orderMapper, org.mockito.Mockito.never()).insert(any(Order.class));
    }

    private void stubSku(long skuId, long price) {
        Sku sku = new Sku();
        sku.setId(skuId);
        sku.setShopId(SHOP_ID);
        sku.setSpuId(10L);
        sku.setSkuName("商品-" + skuId);
        sku.setPrice(price);
        when(skuService.getRequired(skuId, SHOP_ID)).thenReturn(sku);
    }

    private void stubThreeItemsAt100() {
        stubSku(1001L, 100L);
        stubSku(1002L, 100L);
        stubSku(1003L, 100L);
    }

    private void stubCoupon(long discount, int bearer) {
        when(couponService.lock(anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(new CouponLockResult(COUPON_ID, 5001L, discount, bearer));
    }

    private void stubInsertAndUpdate() {
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(ORDER_ID);
            return 1;
        });
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);
    }

    private void stubInsertOnly() {
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(ORDER_ID);
            return 1;
        });
    }

    private CreateOrderCommand.ItemCommand item(long skuId, int quantity) {
        return new CreateOrderCommand.ItemCommand(skuId, quantity);
    }

    private CreateOrderCommand command(List<CreateOrderCommand.ItemCommand> items, Long couponId) {
        return new CreateOrderCommand(
                USER_ID,
                MERCHANT_ID,
                SHOP_ID,
                FulfillmentType.DELIVERY.getCode(),
                couponId,
                100L,
                0L,
                "张三",
                "13800000000",
                "北京市朝阳区望京 SOHO T1",
                null,
                null,
                null,
                null,
                "少放辣",
                items
        );
    }

    private void assertBizCode(ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }
}
