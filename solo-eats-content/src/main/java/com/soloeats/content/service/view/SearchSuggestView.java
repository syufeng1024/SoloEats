package com.soloeats.content.service.view;

import java.util.List;

/**
 * 搜索建议。屏蔽词只参与服务端过滤，绝不放入响应。
 */
public record SearchSuggestView(
        List<String> defaultKeywords,
        List<String> hotKeywords,
        List<String> suggestions
) {
}
