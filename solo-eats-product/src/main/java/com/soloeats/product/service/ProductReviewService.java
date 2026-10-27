package com.soloeats.product.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.product.service.command.AppendReviewCommand;
import com.soloeats.product.service.command.CreateReviewCommand;
import com.soloeats.product.service.command.ReplyReviewCommand;
import com.soloeats.product.service.view.ReviewTagView;
import com.soloeats.product.service.view.ReviewView;

import java.util.List;

public interface ProductReviewService {

    ReviewView create(Long userId, CreateReviewCommand command);

    PageResult<ReviewView> shopReviews(Long shopId, String tag, Boolean hasImage,
                                       long pageNum, long pageSize);

    PageResult<ReviewView> mine(Long userId, long pageNum, long pageSize);

    /**
     * 商家端评价列表：覆盖该商家全部门店，只含审核通过（status=1）的评价，
     * 与 reply 的前置条件保持一致。
     *
     * @param scoreLevel  1 差评(1~2 星) / 2 中评(3 星) / 3 好评(4~5 星)，null 不过滤
     * @param replyStatus 0 未回复 / 1 已回复，null 不过滤
     */
    PageResult<ReviewView> merchantPage(Long merchantId, Integer scoreLevel, Integer replyStatus,
                                        long pageNum, long pageSize);

    void reply(Long merchantId, Long reviewId, ReplyReviewCommand command);

    PageResult<ReviewView> adminPage(Long shopId, Long spuId, Long riderId, Integer status,
                                     Integer scoreLevel, long pageNum, long pageSize);

    ReviewView audit(Long id, Integer status, Integer topFlag);

    ReviewView append(Long userId, Long reviewId, AppendReviewCommand command);

    List<ReviewTagView> tags();
}
