package com.soloeats.content.service.view;

import com.soloeats.content.domain.entity.SearchKeyword;

import java.time.LocalDateTime;

public record SearchKeywordView(
        Long id,
        String keyword,
        Integer keywordType,
        String keywordTypeName,
        Integer weight,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SearchKeywordView from(SearchKeyword keyword, String typeName) {
        return new SearchKeywordView(
                keyword.getId(),
                keyword.getKeyword(),
                keyword.getKeywordType(),
                typeName,
                keyword.getWeight(),
                keyword.getStatus(),
                keyword.getCreatedAt(),
                keyword.getUpdatedAt()
        );
    }
}
