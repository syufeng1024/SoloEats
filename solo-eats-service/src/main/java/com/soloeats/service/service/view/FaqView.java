package com.soloeats.service.service.view;

import com.soloeats.service.domain.entity.Faq;

import java.time.LocalDateTime;

public record FaqView(
        Long id,
        String category,
        String question,
        String answer,
        Integer sort,
        Integer viewCount,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static FaqView from(Faq faq) {
        return new FaqView(
                faq.getId(),
                faq.getCategory(),
                faq.getQuestion(),
                faq.getAnswer(),
                faq.getSort(),
                faq.getViewCount(),
                faq.getStatus(),
                faq.getCreatedAt(),
                faq.getUpdatedAt()
        );
    }

    /** 用户端列表自增浏览后回显 +1，避免再查一次库。 */
    public FaqView withViewCountPlusOne() {
        int next = viewCount == null ? 1 : viewCount + 1;
        return new FaqView(id, category, question, answer, sort, next, status, createdAt, updatedAt);
    }
}
