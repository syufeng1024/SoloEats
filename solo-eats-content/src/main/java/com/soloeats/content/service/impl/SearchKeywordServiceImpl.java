package com.soloeats.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.content.domain.entity.SearchKeyword;
import com.soloeats.content.domain.enums.KeywordType;
import com.soloeats.content.mapper.SearchKeywordMapper;
import com.soloeats.content.service.SearchKeywordService;
import com.soloeats.content.service.view.SearchKeywordView;
import com.soloeats.content.service.view.SearchSuggestView;
import com.soloeats.content.support.ContentTexts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchKeywordServiceImpl implements SearchKeywordService {

    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ENABLED = 1;
    private static final int DEFAULT_LIMIT = 5;
    private static final int HOT_LIMIT = 10;
    private static final int SUGGEST_LIMIT = 10;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private final SearchKeywordMapper keywordMapper;

    @Override
    public PageResult<SearchKeywordView> page(Integer keywordType, long pageNum, long pageSize) {
        validateType(keywordType, false);
        long normalizedPageNum = normalizePageNum(pageNum);
        long normalizedPageSize = normalizePageSize(pageSize);

        LambdaQueryWrapper<SearchKeyword> wrapper = new LambdaQueryWrapper<SearchKeyword>()
                .eq(keywordType != null, SearchKeyword::getKeywordType, keywordType)
                .orderByDesc(SearchKeyword::getWeight)
                .orderByDesc(SearchKeyword::getId);

        Page<SearchKeyword> page = keywordMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<SearchKeywordView> views = page.getRecords().stream()
                .map(item -> SearchKeywordView.from(item, typeName(item.getKeywordType())))
                .toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(String keyword, Integer keywordType, Integer weight, Integer status) {
        String normalized = ContentTexts.normalizeKeyword(keyword, 64);
        validateType(keywordType, true);
        validateStatus(status);
        if (exists(normalized, keywordType, null)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "同一类型下关键词已存在");
        }

        SearchKeyword entity = new SearchKeyword();
        entity.setKeyword(normalized);
        entity.setKeywordType(keywordType);
        entity.setWeight(weight == null ? 0 : weight);
        entity.setStatus(status == null ? STATUS_ENABLED : status);
        try {
            keywordMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "同一类型下关键词已存在");
        }
        log.info("search keyword created id={} type={}", entity.getId(), keywordType);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, String keyword, Integer keywordType, Integer weight, Integer status) {
        SearchKeyword entity = requireKeyword(id);
        String normalized = ContentTexts.normalizeKeyword(keyword, 64);
        validateType(keywordType, true);
        validateStatus(status);
        if (exists(normalized, keywordType, id)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "同一类型下关键词已存在");
        }

        entity.setKeyword(normalized);
        entity.setKeywordType(keywordType);
        entity.setWeight(weight == null ? 0 : weight);
        entity.setStatus(status == null ? STATUS_ENABLED : status);
        try {
            keywordMapper.updateById(entity);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "同一类型下关键词已存在");
        }
        log.info("search keyword updated id={} type={}", id, keywordType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireKeyword(id);
        if (keywordMapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "搜索词不存在");
        }
        log.info("search keyword deleted id={}", id);
    }

    @Override
    public SearchSuggestView suggest(String keyword) {
        List<SearchKeyword> all = keywordMapper.selectList(
                new LambdaQueryWrapper<SearchKeyword>()
                        .in(SearchKeyword::getKeywordType,
                                KeywordType.DEFAULT.getCode(),
                                KeywordType.HOT.getCode(),
                                KeywordType.SUGGEST.getCode(),
                                KeywordType.BLOCKED.getCode())
                        .eq(SearchKeyword::getStatus, STATUS_ENABLED)
                        .orderByDesc(SearchKeyword::getWeight)
                        .orderByAsc(SearchKeyword::getId));

        Map<Integer, List<SearchKeyword>> byType = all.stream()
                .collect(Collectors.groupingBy(SearchKeyword::getKeywordType));

        // 屏蔽词只参与过滤，不进入任何返回列表。
        Set<String> blocked = byType.getOrDefault(KeywordType.BLOCKED.getCode(), List.of())
                .stream()
                .map(SearchKeyword::getKeyword)
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        List<String> defaults = candidates(
                byType.getOrDefault(KeywordType.DEFAULT.getCode(), List.of()),
                blocked, item -> true, DEFAULT_LIMIT);
        List<String> hot = candidates(
                byType.getOrDefault(KeywordType.HOT.getCode(), List.of()),
                blocked, item -> true, HOT_LIMIT);

        String input = normalizedInput(keyword);
        List<String> suggestions = input.isEmpty()
                ? List.of()
                : candidates(
                        byType.getOrDefault(KeywordType.SUGGEST.getCode(), List.of()),
                        blocked,
                        item -> item.toLowerCase(Locale.ROOT).contains(input),
                        SUGGEST_LIMIT);

        return new SearchSuggestView(defaults, hot, suggestions);
    }

    private List<String> candidates(List<SearchKeyword> source,
                                    Set<String> blocked,
                                    Predicate<String> extra,
                                    int limit) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (SearchKeyword item : source) {
            if (result.size() >= limit) {
                break;
            }
            String value = item.getKeyword();
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String lower = value.toLowerCase(Locale.ROOT);
            boolean blockedHit = blocked.stream().anyMatch(blockedWord ->
                    !blockedWord.isEmpty() && lower.contains(blockedWord));
            if (blockedHit || !extra.test(value)) {
                continue;
            }
            result.add(value);
        }
        return new ArrayList<>(result);
    }

    private String normalizedInput(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return "";
        }
        // 与入库归一保持一致：全角转半角后再做包含匹配，避免 ＡＢ 搜不到 AB。
        return Normalizer.normalize(keyword, Normalizer.Form.NFKC)
                .strip()
                .replaceAll("[\\p{Cntrl}]", "")
                .toLowerCase(Locale.ROOT);
    }

    private boolean exists(String keyword, Integer keywordType, Long excludeId) {
        LambdaQueryWrapper<SearchKeyword> wrapper = new LambdaQueryWrapper<SearchKeyword>()
                .eq(SearchKeyword::getKeyword, keyword)
                .eq(SearchKeyword::getKeywordType, keywordType)
                .ne(excludeId != null, SearchKeyword::getId, excludeId);
        Long count = keywordMapper.selectCount(wrapper);
        return count != null && count > 0L;
    }

    private SearchKeyword requireKeyword(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "搜索词 ID 不能为空");
        }
        SearchKeyword keyword = keywordMapper.selectById(id);
        if (keyword == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "搜索词不存在");
        }
        return keyword;
    }

    private static void validateType(Integer keywordType, boolean required) {
        if (keywordType == null) {
            if (required) {
                throw new BizException(ErrorCode.PARAM_INVALID, "keywordType 不能为空");
            }
            return;
        }
        if (!KeywordType.isValid(keywordType)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "keywordType 只能是 1默认词 / 2热搜词 / 3联想词 / 4屏蔽词");
        }
    }

    private static void validateStatus(Integer status) {
        if (status != null && status != STATUS_DISABLED && status != STATUS_ENABLED) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0停用 / 1启用");
        }
    }

    private static String typeName(Integer code) {
        KeywordType type = KeywordType.fromCode(code);
        return type == null ? null : type.getLabel();
    }

    private static long normalizePageNum(long pageNum) {
        return pageNum < 1L ? 1L : pageNum;
    }

    private static long normalizePageSize(long pageSize) {
        if (pageSize < 1L) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
