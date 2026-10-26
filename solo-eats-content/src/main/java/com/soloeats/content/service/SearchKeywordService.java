package com.soloeats.content.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.content.service.view.SearchKeywordView;
import com.soloeats.content.service.view.SearchSuggestView;

public interface SearchKeywordService {

    PageResult<SearchKeywordView> page(Integer keywordType, long pageNum, long pageSize);

    Long create(String keyword, Integer keywordType, Integer weight, Integer status);

    void update(Long id, String keyword, Integer keywordType, Integer weight, Integer status);

    void delete(Long id);

    /** 默认词 + 热搜 Top10 + 联想词；屏蔽词只参与过滤，不返回。 */
    SearchSuggestView suggest(String keyword);
}
