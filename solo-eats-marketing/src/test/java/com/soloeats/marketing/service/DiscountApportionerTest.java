package com.soloeats.marketing.service;

import com.soloeats.marketing.service.view.DiscountApportionItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscountApportionerTest {

    @Test
    @DisplayName("按折后金额比例分摊：3000/500/1500 分 1000，结果为 600/100/300")
    void apportionByProportion() {
        List<DiscountApportionItem> result = DiscountApportioner.apportion(List.of(
                item(1001L, 3000L),
                item(1002L, 500L),
                item(1003L, 1500L)), 1000L);

        assertThat(result).extracting(DiscountApportionItem::discountAmount)
                .containsExactly(600L, 100L, 300L);
        assertThat(sum(result)).isEqualTo(1000L);
    }

    @Test
    @DisplayName("余数归折后金额最大项：333/333/334 分 100，最大项得 34")
    void remainderGoesToLargestItem() {
        List<DiscountApportionItem> result = DiscountApportioner.apportion(List.of(
                item(1001L, 333L),
                item(1002L, 333L),
                item(1003L, 334L)), 100L);

        assertThat(result).extracting(DiscountApportionItem::discountAmount)
                .containsExactly(33L, 33L, 34L);
        assertThat(sum(result)).isEqualTo(100L);
    }

    @Test
    @DisplayName("分摊合计等于优惠总额，且每项不超过自身金额")
    void sumEqualsTotalAndCapped() {
        List<DiscountApportionItem> result = DiscountApportioner.apportion(List.of(
                item(1001L, 1999L),
                item(1002L, 1L),
                item(1003L, 3333L)), 777L);

        assertThat(result).extracting(DiscountApportionItem::discountAmount)
                .containsExactly(291L, 0L, 486L);
        assertThat(sum(result)).isEqualTo(777L);
        assertEachNotExceedPayable(result);
    }

    @Test
    @DisplayName("单项全免：优惠等于商品金额时每项分摊等于自身金额，不出现负数")
    void fullDiscountPerItem() {
        List<DiscountApportionItem> result = DiscountApportioner.apportion(List.of(
                item(1001L, 3000L),
                item(1002L, 500L)), 3500L);

        assertThat(result).extracting(DiscountApportionItem::discountAmount)
                .containsExactly(3000L, 500L);
        assertThat(result).extracting(DiscountApportionItem::discountAmount)
                .allMatch(amount -> amount >= 0L);
        assertThat(sum(result)).isEqualTo(3500L);
    }

    @Test
    @DisplayName("优惠超过商品合计时按商品合计封顶，单项不超过自身金额")
    void discountOverTotalIsCapped() {
        List<DiscountApportionItem> result = DiscountApportioner.apportion(List.of(
                item(1001L, 100L),
                item(1002L, 200L)), 1000L);

        assertThat(result).extracting(DiscountApportionItem::discountAmount)
                .containsExactly(100L, 200L);
        assertThat(sum(result)).isEqualTo(300L);
        assertEachNotExceedPayable(result);
    }

    @Test
    @DisplayName("最大项金额相同且余数超过单额度时，继续分配到次大项")
    void remainderRespectsItemCapWhenTied() {
        List<DiscountApportionItem> result = DiscountApportioner.apportion(List.of(
                item(1001L, 1L),
                item(1002L, 1L),
                item(1003L, 1L)), 2L);

        assertThat(result).extracting(DiscountApportionItem::discountAmount)
                .containsExactly(1L, 1L, 0L);
        assertThat(sum(result)).isEqualTo(2L);
        assertEachNotExceedPayable(result);
    }

    @Test
    @DisplayName("零优惠返回全 0，不改变明细金额")
    void zeroDiscount() {
        List<DiscountApportionItem> result = DiscountApportioner.apportion(List.of(
                item(1001L, 3000L),
                item(1002L, 500L)), 0L);

        assertThat(result).extracting(DiscountApportionItem::discountAmount)
                .containsExactly(0L, 0L);
    }

    @Test
    @DisplayName("负数优惠或无可分摊金额时拒绝")
    void invalidInput() {
        assertThatThrownBy(() -> DiscountApportioner.apportion(
                List.of(item(1001L, 3000L)), -1L))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> DiscountApportioner.apportion(
                List.of(item(1001L, 0L)), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private DiscountApportionItem item(Long skuId, long payableAmount) {
        return new DiscountApportionItem(skuId, payableAmount, 0L);
    }

    private long sum(List<DiscountApportionItem> items) {
        return items.stream().mapToLong(DiscountApportionItem::discountAmount).sum();
    }

    private void assertEachNotExceedPayable(List<DiscountApportionItem> items) {
        assertThat(items).allSatisfy(item ->
                assertThat(item.discountAmount()).isBetween(0L, item.payableAmount()));
    }
}
