package com.soloeats.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.service.domain.entity.Faq;
import com.soloeats.service.mapper.FaqMapper;
import com.soloeats.service.service.FaqService;
import com.soloeats.service.service.command.FaqCommand;
import com.soloeats.service.service.view.FaqView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaqServiceImpl implements FaqService {

    private static final int STATUS_ENABLED = 0;
    private static final int STATUS_DISABLED = 1;
    private static final Set<Integer> STATUSES = Set.of(STATUS_ENABLED, STATUS_DISABLED);
    private static final int PUBLIC_LIMIT = 100;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private final FaqMapper faqMapper;

    @Override
    public PageResult<FaqView> pageAdmin(String category, String keyword, Integer status,
                                         long pageNum, long pageSize) {
        if (status != null && !STATUSES.contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0启用 / 1停用");
        }
        long normalizedPageNum = normalizePageNum(pageNum);
        long normalizedPageSize = normalizePageSize(pageSize);

        LambdaQueryWrapper<Faq> wrapper = new LambdaQueryWrapper<Faq>()
                .eq(StringUtils.hasText(category), Faq::getCategory, trim(category))
                .eq(status != null, Faq::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Faq::getQuestion, trim(keyword))
                        .or()
                        .like(Faq::getAnswer, trim(keyword)))
                .orderByAsc(Faq::getSort)
                .orderByDesc(Faq::getId);

        Page<Faq> page = faqMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<FaqView> views = page.getRecords().stream().map(FaqView::from).toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public FaqView detail(Long id) {
        return FaqView.from(requireFaq(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(FaqCommand command) {
        Faq faq = new Faq();
        applyCommand(faq, command);
        faq.setViewCount(0);
        faqMapper.insert(faq);
        log.info("faq created id={} category={}", faq.getId(), faq.getCategory());
        return faq.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, FaqCommand command) {
        Faq faq = requireFaq(id);
        applyCommand(faq, command);
        faqMapper.updateById(faq);
        log.info("faq updated id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireFaq(id);
        if (faqMapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "FAQ 不存在");
        }
        log.info("faq deleted id={}", id);
    }

    @Override
    public List<FaqView> listPublic(String category, String keyword) {
        LambdaQueryWrapper<Faq> wrapper = new LambdaQueryWrapper<Faq>()
                .eq(Faq::getStatus, STATUS_ENABLED)
                .eq(StringUtils.hasText(category), Faq::getCategory, trim(category))
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Faq::getQuestion, trim(keyword))
                        .or()
                        .like(Faq::getAnswer, trim(keyword)))
                .orderByAsc(Faq::getSort)
                .orderByDesc(Faq::getId)
                .last("LIMIT " + PUBLIC_LIMIT);

        return faqMapper.selectList(wrapper).stream()
                .map(faq -> {
                    // 原子自增，不做「先读后写」；返回给前端的计数同步 +1。
                    faqMapper.increaseViewCount(faq.getId());
                    return FaqView.from(faq).withViewCountPlusOne();
                })
                .toList();
    }

    private void applyCommand(Faq faq, FaqCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求参数不能为空");
        }
        faq.setCategory(normalizeText(command.category(), "category", 64));
        faq.setQuestion(normalizeText(command.question(), "question", 200));
        faq.setAnswer(normalizeText(command.answer(), "answer", 65_535));
        faq.setSort(command.sort() == null ? 0 : command.sort());
        int status = command.status() == null ? STATUS_ENABLED : command.status();
        if (!STATUSES.contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0启用 / 1停用");
        }
        faq.setStatus(status);
    }

    private Faq requireFaq(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "FAQ ID 不能为空");
        }
        Faq faq = faqMapper.selectById(id);
        if (faq == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "FAQ 不存在");
        }
        return faq;
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

    private static String trim(String value) {
        return value == null ? null : value.strip();
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
