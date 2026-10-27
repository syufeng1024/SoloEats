package com.soloeats.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.product.domain.entity.ProductReview;
import com.soloeats.product.mapper.ProductReviewMapper;
import com.soloeats.product.mapper.ReviewTagMapper;
import com.soloeats.product.service.ReviewOrderPort;
import com.soloeats.product.service.view.ReviewView;
import com.soloeats.product.support.ProductProperties;
import com.soloeats.store.service.ShopService;
import com.soloeats.store.service.view.ShopView;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductReviewMerchantPageTest {

    private final ProductReviewMapper reviewMapper = mock(ProductReviewMapper.class);
    private final ReviewTagMapper reviewTagMapper = mock(ReviewTagMapper.class);
    private final ReviewOrderPort reviewOrderPort = mock(ReviewOrderPort.class);
    private final ShopService shopService = mock(ShopService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final ProductReviewServiceImpl service = new ProductReviewServiceImpl(
            reviewMapper, reviewTagMapper, reviewOrderPort, shopService,
            new ProductProperties(), new ObjectMapper(), eventPublisher);

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ProductReview.class);
    }

    @Test
    @DisplayName("商家评价列表覆盖本商家全部门店并返回回复状态")
    void listReviewsForMerchantShops() {
        when(shopService.listByMerchant(10L, null))
                .thenReturn(List.of(shop(1L, 10L), shop(2L, 10L)));

        ProductReview review = new ProductReview();
        review.setId(31L);
        review.setShopId(2L);
        review.setOverallScore(2);
        review.setContent("菜凉了");
        review.setStatus(1);
        review.setMerchantReply("已反馈后厨");
        review.setReplyAt(LocalDateTime.now());
        review.setCreatedAt(LocalDateTime.now());
        Page<ProductReview> page = new Page<>(1L, 20L);
        page.setTotal(1L);
        page.setRecords(List.of(review));
        when(reviewMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<ReviewView> result = service.merchantPage(10L, 1, 1, 1L, 20L);

        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.list()).hasSize(1);
        assertThat(result.list().get(0).merchantReply()).isEqualTo("已反馈后厨");
        assertThat(result.list().get(0).overallScore()).isEqualTo(2);
        verify(shopService).listByMerchant(eq(10L), isNull());
    }

    @Test
    @DisplayName("非法 replyStatus 抛参数错误")
    void rejectInvalidReplyStatus() {
        assertBizCode(() -> service.merchantPage(10L, null, 2, 1L, 20L),
                ErrorCode.PARAM_INVALID);
        verify(reviewMapper, never()).selectPage(any(), any());
    }

    @Test
    @DisplayName("商家无门店时返回空页且不查评价表")
    void emptyWhenMerchantHasNoShop() {
        when(shopService.listByMerchant(10L, null)).thenReturn(List.of());

        PageResult<ReviewView> result = service.merchantPage(10L, null, 0, 2L, 10L);

        assertThat(result.list()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.pageNum()).isEqualTo(2L);
        assertThat(result.pageSize()).isEqualTo(10L);
        verify(reviewMapper, never()).selectPage(any(), any());
    }

    private ShopView shop(Long id, Long merchantId) {
        return new ShopView(id, merchantId, null, "测试门店" + id, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, 1, "营业", 1, 0L, 0L, 0L, 15,
                BigDecimal.ZERO, 0, 0, LocalDateTime.now());
    }

    private void assertBizCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode())
                        .isEqualTo(expected.getCode()));
    }
}
