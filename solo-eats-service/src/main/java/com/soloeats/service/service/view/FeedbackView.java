package com.soloeats.service.service.view;

import com.soloeats.service.domain.entity.Feedback;

import java.time.LocalDateTime;
import java.util.List;

public record FeedbackView(
        Long id,
        Long userId,
        String content,
        List<String> images,
        String contact,
        Integer status,
        String reply,
        LocalDateTime replyAt,
        LocalDateTime createdAt
) {

    public static FeedbackView from(Feedback feedback, List<String> images) {
        return new FeedbackView(
                feedback.getId(),
                feedback.getUserId(),
                feedback.getContent(),
                images,
                feedback.getContact(),
                feedback.getStatus(),
                feedback.getReply(),
                feedback.getReplyAt(),
                feedback.getCreatedAt()
        );
    }
}
