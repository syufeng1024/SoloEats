package com.soloeats.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.product.domain.entity.ProductReview;
import com.soloeats.product.mapper.ProductReviewMapper;
import com.soloeats.product.mapper.ReviewTagMapper;
import com.soloeats.product.service.ReviewOrderPort;
import com.soloeats.product.service.command.AppendReviewCommand;
import com.soloeats.product.service.command.CreateReviewCommand;
import com.soloeats.product.service.view.ReviewView;
import com.soloeats.product.support.ProductProperties;
import com.soloeats.store.service.ShopService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductReviewServiceImplTest {

    private final ProductReviewMapper reviewMapper = mock(ProductReviewMapper.class);
    private final ReviewTagMapper reviewTagMapper = mock(ReviewTagMapper.class);
    private final ReviewOrderPort reviewOrderPort = mock(ReviewOrderPort.class);
    private final ShopService shopService = mock(ShopService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AtomicReference<ProductReview> saved = new AtomicReference<>();

    private final ProductReviewServiceImpl service = new ProductReviewServiceImpl(
            reviewMapper, reviewTagMapper, reviewOrderPort, shopService,
            new ProductProperties(), new ObjectMapper(), eventPublisher);

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ProductReview.class);
    }

    @Test
    @DisplayName("已完成订单可评价，初始状态为待审")
    void createReviewForCompletedOrder() {
        when(reviewOrderPort.find(11L, 21L)).thenReturn(snapshot(60, LocalDateTime.now().minusHours(2)));
        when(reviewMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            ProductReview review = invocation.getArgument(0);
            review.setId(9001L);
            saved.set(review);
            return 1;
        }).when(reviewMapper).insert(any(ProductReview.class));
        when(reviewMapper.selectById(9001L)).thenAnswer(invocation -> saved.get());

        ReviewView view = service.create(1L, request(List.of("/api/v1/files/1/raw")));

        assertThat(view.id()).isEqualTo(9001L);
        assertThat(view.overallScore()).isEqualTo(5);
        assertThat(view.status()).isZero();
        assertThat(saved.get().getOrderItemId()).isEqualTo(21L);
        assertThat(saved.get().getStatus()).isZero();
    }

    @Test
    @DisplayName("未完成订单评价抛 40106")
    void rejectReviewForUnfinishedOrder() {
        when(reviewOrderPort.find(11L, 21L)).thenReturn(snapshot(40, null));

        assertBizCode(() -> service.create(1L, request(List.of())),
                ErrorCode.REVIEW_NOT_ALLOWED);
    }

    @Test
    @DisplayName("同一订单明细重复评价抛 40107")
    void rejectDuplicateOrderItemReview() {
        when(reviewOrderPort.find(11L, 21L)).thenReturn(snapshot(60, LocalDateTime.now()));
        when(reviewMapper.selectCount(any())).thenReturn(1L);

        assertBizCode(() -> service.create(1L, request(List.of())),
                ErrorCode.REVIEW_EXISTS);
    }

    @Test
    @DisplayName("匿名评价不返回 userId")
    void hideUserIdForAnonymousReview() {
        ProductReview review = new ProductReview();
        review.setId(31L);
        review.setUserId(1L);
        review.setIsAnonymous(1);
        review.setStatus(1);
        review.setOverallScore(5);
        Page<ProductReview> page = new Page<>(1L, 20L);
        page.setTotal(1L);
        page.setRecords(List.of(review));
        when(reviewMapper.selectPage(any(), any())).thenReturn(page);

        ReviewView view = service.mine(1L, 1L, 20L).list().get(0);

        assertThat(view.userId()).isNull();
        assertThat(view.anonymous()).isEqualTo(1);
    }

    @Test
    @DisplayName("待审评价不能追评")
    void rejectAppendWhenReviewNotApproved() {
        ProductReview review = new ProductReview();
        review.setId(41L);
        review.setUserId(1L);
        review.setStatus(0);
        when(reviewMapper.selectById(41L)).thenReturn(review);

        assertBizCode(() -> service.append(1L, 41L, new AppendReviewCommand("再评", List.of())),
                ErrorCode.REVIEW_NOT_ALLOWED);
    }

    @Test
    @DisplayName("评分越界抛参数错误")
    void rejectScoreOutOfRange() {
        when(reviewOrderPort.find(11L, 21L)).thenReturn(snapshot(60, LocalDateTime.now()));

        assertBizCode(() -> service.create(1L, new CreateReviewCommand(
                11L, 21L, 6, 5, 5, 5, "好吃", List.of(), List.of(), 0
        )), ErrorCode.PARAM_INVALID);
    }

    private ReviewOrderPort.OrderItemSnapshot snapshot(int status, LocalDateTime completedAt) {
        return new ReviewOrderPort.OrderItemSnapshot(
                11L, 21L, 1L, 100L, 1L, 1001L, status, completedAt);
    }

    private CreateReviewCommand request(List<String> images) {
        return new CreateReviewCommand(
                11L, 21L, 5, 5, 5, 4, "好吃", images, List.of("分量足"), 0);
    }

    private void assertBizCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode())
                        .isEqualTo(expected.getCode()));
    }
}
