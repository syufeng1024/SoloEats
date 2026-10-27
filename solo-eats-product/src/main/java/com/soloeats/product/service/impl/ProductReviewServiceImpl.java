package com.soloeats.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.product.domain.entity.ProductReview;
import com.soloeats.product.domain.entity.ReviewTag;
import com.soloeats.product.mapper.ProductReviewMapper;
import com.soloeats.product.mapper.ReviewTagMapper;
import com.soloeats.product.service.event.ReviewAuditedEvent;
import com.soloeats.product.service.ProductReviewService;
import com.soloeats.product.service.ReviewOrderPort;
import com.soloeats.product.service.command.AppendReviewCommand;
import com.soloeats.product.service.command.CreateReviewCommand;
import com.soloeats.product.service.command.ReplyReviewCommand;
import com.soloeats.product.service.view.ReviewTagView;
import com.soloeats.product.service.view.ReviewView;
import com.soloeats.product.support.JsonTexts;
import com.soloeats.product.support.ProductProperties;
import com.soloeats.product.support.ProductTexts;
import com.soloeats.store.service.ShopService;
import com.soloeats.store.service.view.ShopView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductReviewServiceImpl implements ProductReviewService {

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    /** OrderStatus.COMPLETED = 60，product 不依赖 order 实体，这里用契约常量。 */
    private static final int ORDER_STATUS_COMPLETED = 60;

    private static final int REVIEW_PENDING = 0;
    private static final int REVIEW_APPROVED = 1;
    private static final int REVIEW_HIDDEN = 2;

    private static final int MAX_CONTENT = 500;
    private static final int MAX_IMAGES = 9;
    private static final int MAX_TAGS = 10;
    private static final int MAX_TAG_LENGTH = 16;

    private final ProductReviewMapper productReviewMapper;
    private final ReviewTagMapper reviewTagMapper;
    private final ReviewOrderPort reviewOrderPort;
    private final ShopService shopService;
    private final ProductProperties productProperties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.order.after-sales-days:7}")
    private int afterSalesDays = 7;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewView create(Long userId, CreateReviewCommand command) {
        if (userId == null || userId <= 0L) {
            throw new BizException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        if (command.orderId() == null || command.orderItemId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "orderId 与 orderItemId 不能为空");
        }

        ReviewOrderPort.OrderItemSnapshot snapshot =
                reviewOrderPort.find(command.orderId(), command.orderItemId());
        if (snapshot == null || !Objects.equals(snapshot.userId(), userId)) {
            throw new BizException(ErrorCode.REVIEW_NOT_ALLOWED, "订单不存在或不属于当前用户");
        }
        if (!Objects.equals(snapshot.orderStatus(), ORDER_STATUS_COMPLETED)) {
            throw new BizException(ErrorCode.REVIEW_NOT_ALLOWED, "订单未完成，不能评价");
        }
        LocalDateTime completedAt = snapshot.completedAt();
        if (completedAt == null
                || completedAt.isBefore(LocalDateTime.now().minusDays(Math.max(afterSalesDays, 1)))) {
            throw new BizException(ErrorCode.REVIEW_NOT_ALLOWED, "评价已超过售后期限");
        }
        long exists = productReviewMapper.selectCount(new LambdaQueryWrapper<ProductReview>()
                .eq(ProductReview::getOrderItemId, command.orderItemId()));
        if (exists > 0L) {
            throw new BizException(ErrorCode.REVIEW_EXISTS);
        }

        ProductReview review = new ProductReview();
        review.setOrderId(snapshot.orderId());
        review.setOrderItemId(snapshot.orderItemId());
        review.setUserId(userId);
        review.setShopId(snapshot.shopId());
        review.setSpuId(snapshot.spuId());
        review.setSkuId(snapshot.skuId());
        review.setOverallScore(requireScore(command.overallScore(), "overallScore"));
        review.setTasteScore(optionalScore(command.tasteScore(), "tasteScore"));
        review.setPackageScore(optionalScore(command.packageScore(), "packageScore"));
        review.setDeliveryScore(optionalScore(command.deliveryScore(), "deliveryScore"));
        review.setContent(ProductTexts.escapeText(command.content(), MAX_CONTENT, "content"));
        review.setImagesJson(writeImages(command.images()));
        review.setTagsJson(writeTags(command.tags()));
        review.setIsAnonymous(normalizeAnonymous(command.anonymous()));
        review.setStatus(REVIEW_PENDING);
        review.setTopFlag(0);
        try {
            productReviewMapper.insert(review);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.REVIEW_EXISTS);
        }
        return toView(productReviewMapper.selectById(review.getId()));
    }

    @Override
    public PageResult<ReviewView> shopReviews(Long shopId, String tag, Boolean hasImage,
                                              long pageNum, long pageSize) {
        if (shopId == null || shopId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "shopId 不能为空");
        }
        LambdaQueryWrapper<ProductReview> wrapper = new LambdaQueryWrapper<ProductReview>()
                .eq(ProductReview::getShopId, shopId)
                .eq(ProductReview::getStatus, REVIEW_APPROVED);
        if (StringUtils.hasText(tag)) {
            wrapper.like(ProductReview::getTagsJson, "\"" + sanitizeKeyword(tag) + "\"");
        }
        if (Boolean.TRUE.equals(hasImage)) {
            wrapper.isNotNull(ProductReview::getImagesJson)
                    .ne(ProductReview::getImagesJson, "[]")
                    .ne(ProductReview::getImagesJson, "null");
        }
        wrapper.orderByDesc(ProductReview::getTopFlag)
                .orderByDesc(ProductReview::getCreatedAt)
                .orderByDesc(ProductReview::getId);
        return pageOf(wrapper, pageNum, pageSize);
    }

    @Override
    public PageResult<ReviewView> mine(Long userId, long pageNum, long pageSize) {
        if (userId == null || userId <= 0L) {
            throw new BizException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        LambdaQueryWrapper<ProductReview> wrapper = new LambdaQueryWrapper<ProductReview>()
                .eq(ProductReview::getUserId, userId)
                .orderByDesc(ProductReview::getCreatedAt)
                .orderByDesc(ProductReview::getId);
        return pageOf(wrapper, pageNum, pageSize);
    }

    @Override
    public PageResult<ReviewView> merchantPage(Long merchantId, Integer scoreLevel,
                                               Integer replyStatus, long pageNum, long pageSize) {
        if (merchantId == null || merchantId <= 0L) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "令牌缺少 merchantId");
        }
        if (scoreLevel != null && (scoreLevel < 1 || scoreLevel > 3)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "scoreLevel 只能是 1差评/2中评/3好评");
        }
        if (replyStatus != null && replyStatus != 0 && replyStatus != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "replyStatus 只能是 0未回复/1已回复");
        }

        // 评价表只有 shopId，没有 merchantId：先取商家全部门店，再按 shopId 过滤。
        List<Long> shopIds = shopService.listByMerchant(merchantId, null).stream()
                .map(ShopView::id)
                .toList();
        if (shopIds.isEmpty()) {
            return PageResult.of(List.of(), 0L,
                    pageNum < 1L ? 1L : pageNum,
                    pageSize < 1L ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE));
        }

        LambdaQueryWrapper<ProductReview> wrapper = new LambdaQueryWrapper<ProductReview>()
                .in(ProductReview::getShopId, shopIds)
                // 未审核评价不可回复，商家列表只展示审核通过的
                .eq(ProductReview::getStatus, REVIEW_APPROVED);
        if (scoreLevel != null) {
            if (scoreLevel == 1) {
                wrapper.le(ProductReview::getOverallScore, 2);
            } else if (scoreLevel == 2) {
                wrapper.eq(ProductReview::getOverallScore, 3);
            } else {
                wrapper.ge(ProductReview::getOverallScore, 4);
            }
        }
        if (replyStatus != null) {
            if (replyStatus == 0) {
                wrapper.isNull(ProductReview::getMerchantReply);
            } else {
                wrapper.isNotNull(ProductReview::getMerchantReply);
            }
        }
        wrapper.orderByDesc(ProductReview::getCreatedAt).orderByDesc(ProductReview::getId);
        return pageOf(wrapper, pageNum, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reply(Long merchantId, Long reviewId, ReplyReviewCommand command) {
        ProductReview review = requireReview(reviewId);
        if (merchantId == null || merchantId <= 0L) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "令牌缺少 merchantId");
        }
        shopService.assertMerchantShop(review.getShopId(), merchantId, null);
        if (!Objects.equals(review.getStatus(), REVIEW_APPROVED)) {
            throw new BizException(ErrorCode.REVIEW_NOT_ALLOWED, "评价未通过审核，不能回复");
        }
        String reply = ProductTexts.escapeText(
                command == null ? null : command.reply(), MAX_CONTENT, "reply");
        if (!StringUtils.hasText(reply)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "reply 不能为空");
        }
        int rows = productReviewMapper.replyOnce(reviewId, reply);
        if (rows == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该评价已回复，不能重复回复");
        }
    }

    @Override
    public PageResult<ReviewView> adminPage(Long shopId, Long spuId, Long riderId, Integer status,
                                            Integer scoreLevel, long pageNum, long pageSize) {
        if (status != null && (status < REVIEW_PENDING || status > REVIEW_HIDDEN)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0/1/2");
        }
        if (scoreLevel != null && (scoreLevel < 1 || scoreLevel > 3)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "scoreLevel 只能是 1差评/2中评/3好评");
        }
        LambdaQueryWrapper<ProductReview> wrapper = new LambdaQueryWrapper<ProductReview>()
                .eq(shopId != null, ProductReview::getShopId, shopId)
                .eq(spuId != null, ProductReview::getSpuId, spuId)
                .eq(riderId != null, ProductReview::getRiderId, riderId)
                .eq(status != null, ProductReview::getStatus, status);
        if (scoreLevel != null) {
            if (scoreLevel == 1) {
                wrapper.le(ProductReview::getOverallScore, 2);
            } else if (scoreLevel == 2) {
                wrapper.eq(ProductReview::getOverallScore, 3);
            } else {
                wrapper.ge(ProductReview::getOverallScore, 4);
            }
        }
        wrapper.orderByDesc(ProductReview::getCreatedAt).orderByDesc(ProductReview::getId);
        return pageOf(wrapper, pageNum, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewView audit(Long id, Integer status, Integer topFlag) {
        ProductReview review = requireReview(id);
        if (status == null && topFlag == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 与 topFlag 至少提供一个");
        }
        int targetStatus = status == null ? review.getStatus() : status;
        if (targetStatus < REVIEW_PENDING || targetStatus > REVIEW_HIDDEN) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0/1/2");
        }
        int targetTop = topFlag == null ? review.getTopFlag() : topFlag;
        if (targetTop != 0 && targetTop != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "topFlag 只能是 0 或 1");
        }
        productReviewMapper.audit(id, targetStatus, targetTop);
        // T05 评分联动：审核事务提交后异步重算门店评分（取近 90 天均值）。
        // 走到这里已通过参数校验，只要门店存在就发事件；同步失败不影响审核结果。
        if (review.getShopId() != null) {
            eventPublisher.publishEvent(new ReviewAuditedEvent(review.getShopId()));
        }
        return toView(productReviewMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewView append(Long userId, Long reviewId, AppendReviewCommand command) {
        if (userId == null || userId <= 0L) {
            throw new BizException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        ProductReview review = requireReview(reviewId);
        if (!Objects.equals(review.getUserId(), userId)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN);
        }
        if (!Objects.equals(review.getStatus(), REVIEW_APPROVED)) {
            throw new BizException(ErrorCode.REVIEW_NOT_ALLOWED, "评价未通过审核，不能追评");
        }
        if (StringUtils.hasText(review.getAppendContent())) {
            throw new BizException(ErrorCode.REVIEW_NOT_ALLOWED, "该评价已追评，不能重复追评");
        }
        String content = ProductTexts.escapeText(
                command == null ? null : command.content(), MAX_CONTENT, "content");
        if (!StringUtils.hasText(content)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "追评内容不能为空");
        }
        int rows = productReviewMapper.appendOnce(reviewId, content, writeImages(
                command == null ? null : command.images()));
        if (rows == 0) {
            throw new BizException(ErrorCode.REVIEW_NOT_ALLOWED, "该评价已追评，不能重复追评");
        }
        return toView(productReviewMapper.selectById(reviewId));
    }

    @Override
    public List<ReviewTagView> tags() {
        return reviewTagMapper.selectList(new LambdaQueryWrapper<ReviewTag>()
                        .eq(ReviewTag::getStatus, 0)
                        .orderByAsc(ReviewTag::getTagType)
                        .orderByAsc(ReviewTag::getSort)
                        .orderByAsc(ReviewTag::getId))
                .stream()
                .map(tag -> new ReviewTagView(
                        tag.getId(), tag.getTagName(), tag.getTagType(), tag.getSort()))
                .toList();
    }

    private PageResult<ReviewView> pageOf(LambdaQueryWrapper<ProductReview> wrapper,
                                          long pageNum, long pageSize) {
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = pageSize < 1L ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        Page<ProductReview> page = productReviewMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<ReviewView> views = page.getRecords().stream().map(this::toView).toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    private ReviewView toView(ProductReview review) {
        if (review == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "评价不存在");
        }
        boolean anonymous = Objects.equals(review.getIsAnonymous(), 1);
        return new ReviewView(
                review.getId(),
                review.getOrderId(),
                review.getShopId(),
                review.getSpuId(),
                review.getSkuId(),
                anonymous ? null : review.getUserId(),
                review.getOverallScore(),
                review.getTasteScore(),
                review.getPackageScore(),
                review.getDeliveryScore(),
                review.getContent(),
                JsonTexts.readStringList(objectMapper, review.getImagesJson()),
                JsonTexts.readStringList(objectMapper, review.getTagsJson()),
                review.getIsAnonymous(),
                review.getStatus(),
                review.getTopFlag(),
                review.getMerchantReply(),
                review.getReplyAt(),
                review.getAppendContent(),
                review.getAppendAt(),
                review.getCreatedAt()
        );
    }

    private ProductReview requireReview(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "评价ID必须是正整数");
        }
        ProductReview review = productReviewMapper.selectById(id);
        if (review == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "评价不存在");
        }
        return review;
    }

    private int requireScore(Integer score, String fieldLabel) {
        if (score == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, fieldLabel + " 不能为空");
        }
        return validateScore(score, fieldLabel);
    }

    private Integer optionalScore(Integer score, String fieldLabel) {
        return score == null ? null : validateScore(score, fieldLabel);
    }

    private int validateScore(Integer score, String fieldLabel) {
        if (score < 1 || score > 5) {
            throw new BizException(ErrorCode.PARAM_INVALID, fieldLabel + " 必须在 1~5 之间");
        }
        return score;
    }

    private int normalizeAnonymous(Integer anonymous) {
        if (anonymous == null) {
            return 0;
        }
        if (anonymous != 0 && anonymous != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "anonymous 只能是 0 或 1");
        }
        return anonymous;
    }

    private String writeImages(List<String> images) {
        List<String> values = ProductTexts.requireImages(
                images, productProperties, "images", MAX_IMAGES);
        return values.isEmpty() ? null : JsonTexts.write(objectMapper, values);
    }

    private String writeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        if (tags.size() > MAX_TAGS) {
            throw new BizException(ErrorCode.PARAM_INVALID, "tags 最多 10 个");
        }
        List<String> values = new ArrayList<>(tags.size());
        for (String tag : tags) {
            if (!StringUtils.hasText(tag)) {
                continue;
            }
            String trimmed = tag.trim();
            if (trimmed.length() > MAX_TAG_LENGTH) {
                throw new BizException(ErrorCode.PARAM_INVALID, "单个标签长度不能超过 16");
            }
            values.add(ProductTexts.escapeText(trimmed, MAX_TAG_LENGTH, "tag"));
        }
        return values.isEmpty() ? null : JsonTexts.write(objectMapper, values);
    }

    private String sanitizeKeyword(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replace("\\", "").replace("%", "").replace("_", "");
    }
}
