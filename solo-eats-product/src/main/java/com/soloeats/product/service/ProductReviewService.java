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

    void reply(Long merchantId, Long reviewId, ReplyReviewCommand command);

    PageResult<ReviewView> adminPage(Long shopId, Long spuId, Long riderId, Integer status,
                                     Integer scoreLevel, long pageNum, long pageSize);

    ReviewView audit(Long id, Integer status, Integer topFlag);

    ReviewView append(Long userId, Long reviewId, AppendReviewCommand command);

    List<ReviewTagView> tags();
}
