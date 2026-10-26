package com.soloeats.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.service.domain.entity.Feedback;
import com.soloeats.service.mapper.FeedbackMapper;
import com.soloeats.service.service.FeedbackService;
import com.soloeats.service.service.view.FeedbackView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_REPLIED = 1;
    private static final Set<Integer> STATUSES = Set.of(STATUS_PENDING, STATUS_REPLIED);
    private static final int MAX_IMAGES = 9;
    private static final int MAX_IMAGE_LENGTH = 512;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private final FeedbackMapper feedbackMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long userId, String content, List<String> images, String contact) {
        if (userId == null || userId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userId 不能为空");
        }
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setContent(normalizeText(content, "content", 1000));
        feedback.setImagesJson(toImagesJson(images));
        feedback.setContact(normalizeOptionalText(contact, "contact", 64));
        feedback.setStatus(STATUS_PENDING);
        feedbackMapper.insert(feedback);
        log.info("feedback created id={} userId={}", feedback.getId(), userId);
        return feedback.getId();
    }

    @Override
    public PageResult<FeedbackView> page(Integer status, long pageNum, long pageSize) {
        if (status != null && !STATUSES.contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0待处理 / 1已回复");
        }
        long normalizedPageNum = normalizePageNum(pageNum);
        long normalizedPageSize = normalizePageSize(pageSize);

        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<Feedback>()
                .eq(status != null, Feedback::getStatus, status)
                .orderByDesc(Feedback::getCreatedAt)
                .orderByDesc(Feedback::getId);

        Page<Feedback> page = feedbackMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<FeedbackView> views = page.getRecords().stream()
                .map(feedback -> FeedbackView.from(feedback, parseImages(feedback.getImagesJson())))
                .toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reply(Long id, String reply) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "反馈ID不能为空");
        }
        String text = normalizeText(reply, "reply", 1000);

        int rows = feedbackMapper.reply(id, text, LocalDateTime.now());
        if (rows == 0) {
            if (feedbackMapper.selectById(id) == null) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "反馈不存在");
            }
            throw new BizException(ErrorCode.PARAM_INVALID, "该反馈已回复");
        }
        log.info("feedback replied id={}", id);
    }

    private String normalizeText(String value, String field, int maxLength) {
        if (value == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空");
        }
        String text = value.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (text.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空");
        }
        if (text.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    field + " 最长 " + maxLength + " 个字符");
        }
        return text;
    }

    private String normalizeOptionalText(String value, String field, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.strip();
        if (text.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    field + " 最长 " + maxLength + " 个字符");
        }
        return text;
    }

    private String toImagesJson(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String image : images) {
            if (!StringUtils.hasText(image)) {
                continue;
            }
            String url = image.strip();
            if (url.length() > MAX_IMAGE_LENGTH) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "图片地址最长 " + MAX_IMAGE_LENGTH + " 个字符");
            }
            normalized.add(url);
        }
        if (normalized.size() > MAX_IMAGES) {
            throw new BizException(ErrorCode.PARAM_INVALID, "图片最多 " + MAX_IMAGES + " 张");
        }
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "图片列表格式不合法");
        }
    }

    private List<String> parseImages(String imagesJson) {
        if (!StringUtils.hasText(imagesJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            log.warn("feedback images_json parse failed, images dropped");
            return List.of();
        }
    }

    private static long normalizePageNum(long pageNum) {
        return pageNum < 1 ? 1 : pageNum;
    }

    private static long normalizePageSize(long pageSize) {
        if (pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
