package com.soloeats.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.domain.entity.MemberGrowthLog;
import com.soloeats.marketing.domain.entity.MemberLevel;
import com.soloeats.marketing.domain.entity.UserGrowthSnapshot;
import com.soloeats.marketing.mapper.MemberGrowthLogMapper;
import com.soloeats.marketing.mapper.MemberLevelMapper;
import com.soloeats.marketing.mapper.MemberUserGrowthMapper;
import com.soloeats.marketing.service.MemberLevelService;
import com.soloeats.marketing.service.command.MemberLevelCommand;
import com.soloeats.marketing.service.view.GrowthLogView;
import com.soloeats.marketing.service.view.MemberLevelView;
import com.soloeats.marketing.service.view.MemberProfileView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 会员等级与成长值。
 *
 * <p>成长值口径固定为「近 365 天 t_member_growth_log.growth 合计」，含负值；
 * 等级按 growth_threshold 从高到低取第一个满足的档位。t_member_user 的
 * growth_value / member_level_id 只是缓存，权威值始终由流水计算得出。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberLevelServiceImpl implements MemberLevelService {

    private static final int GROWTH_WINDOW_DAYS = 365;
    private static final int RECALC_BATCH_SIZE = 500;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int LEVEL_CODE_MAX_LENGTH = 16;
    private static final int LEVEL_NAME_MAX_LENGTH = 32;
    private static final int ICON_URL_MAX_LENGTH = 255;
    private static final int BENEFITS_JSON_MAX_LENGTH = 4096;

    private static final Set<Integer> GROWTH_SOURCES = Set.of(1, 2, 3, 4, 5);
    private static final Set<Integer> LEVEL_STATUSES = Set.of(0, 1);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MemberLevelMapper memberLevelMapper;
    private final MemberGrowthLogMapper memberGrowthLogMapper;
    private final MemberUserGrowthMapper memberUserGrowthMapper;

    @Override
    public List<MemberLevelView> listLevels() {
        return memberLevelMapper.selectList(new LambdaQueryWrapper<MemberLevel>()
                        .orderByAsc(MemberLevel::getLevelNo)
                        .orderByAsc(MemberLevel::getId))
                .stream()
                .map(MemberLevelView::from)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createLevel(MemberLevelCommand command) {
        Normalized normalized = normalize(command);
        ensureCodeAvailable(normalized.levelCode(), null);

        MemberLevel level = new MemberLevel();
        apply(level, normalized);
        memberLevelMapper.insert(level);
        if (level.getId() == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "等级创建失败，未生成主键");
        }
        log.info("member level created id={} code={} threshold={}",
                level.getId(), normalized.levelCode(), normalized.growthThreshold());
        return level.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLevel(Long id, MemberLevelCommand command) {
        MemberLevel level = requireLevel(id);
        Normalized normalized = normalize(command);
        ensureCodeAvailable(normalized.levelCode(), id);

        Integer oldStatus = level.getStatus();
        apply(level, normalized);
        // status 为空表示本次不改状态，避免编辑名称时把停用等级意外启用
        if (command.status() == null && oldStatus != null) {
            level.setStatus(oldStatus);
        }
        // 复用查出的 version，@Version 生成 WHERE version = ?；0 行表示已被他人改过
        if (memberLevelMapper.updateById(level) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "会员等级不存在或已被修改，请刷新后重试");
        }
        log.info("member level updated id={} code={} threshold={}",
                id, normalized.levelCode(), normalized.growthThreshold());
    }

    @Override
    public MemberProfileView profile(Long userId) {
        requireUserId(userId);
        int growth = currentGrowth(userId);
        List<MemberLevel> levels = memberLevelMapper.selectEnabledDesc();
        int index = resolveIndex(levels, growth);
        if (index < 0) {
            return new MemberProfileView(
                    userId, null, null, null, null, null, growth, 0, 0, false, null);
        }

        MemberLevel current = levels.get(index);
        // 列表按门槛倒序，index-1 才是更高一档
        boolean maxLevel = index == 0;
        MemberLevel next = maxLevel ? null : levels.get(index - 1);
        int nextThreshold = maxLevel ? 0 : safeInt(next.getGrowthThreshold());
        int growthToNext = maxLevel ? 0 : Math.max(0, nextThreshold - growth);

        return new MemberProfileView(
                userId,
                current.getId(),
                current.getLevelCode(),
                current.getLevelName(),
                current.getLevelNo(),
                current.getStar(),
                growth,
                nextThreshold,
                growthToNext,
                maxLevel,
                current.getBenefitsJson());
    }

    @Override
    public List<GrowthLogView> growthLogs(Long userId, long pageNum, long pageSize) {
        requireUserId(userId);
        Page<MemberGrowthLog> page = memberGrowthLogMapper.selectPage(
                new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize)),
                new LambdaQueryWrapper<MemberGrowthLog>()
                        .eq(MemberGrowthLog::getUserId, userId)
                        .orderByDesc(MemberGrowthLog::getId));
        return page.getRecords().stream().map(GrowthLogView::from).toList();
    }

    /**
     * 不包大事务：单批 500 人，逐条提交，中途失败可直接重跑；
     * 重算本身幂等（同一成长值算出同一等级），不会产生累计副作用。
     */
    @Override
    public int recalcAll() {
        List<MemberLevel> levels = memberLevelMapper.selectEnabledDesc();
        if (levels.isEmpty()) {
            log.warn("member level recalc skipped: no enabled level configured");
            return 0;
        }

        LocalDateTime since = LocalDateTime.now().minusDays(GROWTH_WINDOW_DAYS);
        long lastId = 0L;
        int changed = 0;

        while (true) {
            List<UserGrowthSnapshot> batch =
                    memberGrowthLogMapper.selectUserGrowthBatch(since, lastId, RECALC_BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            for (UserGrowthSnapshot snapshot : batch) {
                lastId = snapshot.getUserId();
                int growth = safeInt(snapshot.getGrowth());
                MemberLevel level = resolve(levels, growth);
                Long levelId = level == null ? null : level.getId();

                boolean unchanged = Objects.equals(snapshot.getOldGrowthValue(), growth)
                        && Objects.equals(snapshot.getOldMemberLevelId(), levelId);
                if (unchanged) {
                    continue;
                }
                if (memberUserGrowthMapper.updateGrowthAndLevel(
                        snapshot.getUserId(), growth, levelId) > 0) {
                    changed++;
                }
            }
            if (batch.size() < RECALC_BATCH_SIZE) {
                break;
            }
        }

        log.info("member level recalc finished changed={} batchSize={}", changed, RECALC_BATCH_SIZE);
        return changed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addGrowth(Long userId, Integer source, Long bizId, int growth, String remark) {
        requireUserId(userId);
        if (source == null || !GROWTH_SOURCES.contains(source)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "source 只能是 1消费 2签到 3评价 4邀请 5手工调整");
        }
        if (growth == 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int before = currentGrowth(userId);
        int after = before + growth;

        MemberGrowthLog entry = new MemberGrowthLog();
        entry.setUserId(userId);
        entry.setSource(source);
        entry.setBizId(bizId);
        entry.setGrowth(growth);
        entry.setBeforeValue(before);
        entry.setAfterValue(after);
        entry.setEffectiveAt(now);
        entry.setExpireAt(now.plusDays(GROWTH_WINDOW_DAYS));
        entry.setRemark(trimToNull(remark));
        memberGrowthLogMapper.insert(entry);

        MemberLevel level = resolve(memberLevelMapper.selectEnabledDesc(), after);
        Long levelId = level == null ? null : level.getId();
        if (memberUserGrowthMapper.updateGrowthAndLevel(userId, after, levelId) == 0) {
            // 用户不存在或已删除：抛异常让成长值流水一起回滚
            throw new BizException(ErrorCode.MEMBER_NOT_FOUND);
        }

        log.info("member growth added userId={} source={} bizId={} growth={} before={} after={} levelId={}",
                userId, source, bizId, growth, before, after, levelId);
    }

    // ---------------------------------------------------------------
    // 内部方法
    // ---------------------------------------------------------------

    private int currentGrowth(Long userId) {
        return memberGrowthLogMapper.sumGrowthSince(
                userId, LocalDateTime.now().minusDays(GROWTH_WINDOW_DAYS));
    }

    private MemberLevel resolve(List<MemberLevel> levelsDesc, int growth) {
        int index = resolveIndex(levelsDesc, growth);
        return index < 0 ? null : levelsDesc.get(index);
    }

    private int resolveIndex(List<MemberLevel> levelsDesc, int growth) {
        if (levelsDesc.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < levelsDesc.size(); i++) {
            if (growth >= safeInt(levelsDesc.get(i).getGrowthThreshold())) {
                return i;
            }
        }
        // 成长值低于最低档门槛（含负值）：留在最低档，避免 member_level_id 为空。
        // levelsDesc 按门槛倒序，最低档是最后一个，不是索引 0。
        return levelsDesc.size() - 1;
    }

    private MemberLevel requireLevel(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "id 必须是正整数");
        }
        MemberLevel level = memberLevelMapper.selectById(id);
        if (level == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "会员等级不存在");
        }
        return level;
    }

    private void ensureCodeAvailable(String levelCode, Long excludeId) {
        Long count = memberLevelMapper.selectCount(new LambdaQueryWrapper<MemberLevel>()
                .eq(MemberLevel::getLevelCode, levelCode)
                .ne(excludeId != null, MemberLevel::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "等级编码已存在");
        }
    }

    private void apply(MemberLevel level, Normalized normalized) {
        level.setLevelCode(normalized.levelCode());
        level.setLevelName(normalized.levelName());
        level.setLevelNo(normalized.levelNo());
        level.setGrowthThreshold(normalized.growthThreshold());
        level.setStar(normalized.star());
        level.setBenefitsJson(normalized.benefitsJson());
        level.setIconUrl(normalized.iconUrl());
        level.setSort(normalized.sort());
        level.setStatus(normalized.status());
    }

    private Normalized normalize(MemberLevelCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求参数不能为空");
        }

        String levelCode = trimToNull(command.levelCode());
        if (levelCode == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "levelCode 不能为空");
        }
        if (levelCode.length() > LEVEL_CODE_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "levelCode 不能超过 " + LEVEL_CODE_MAX_LENGTH + " 个字符");
        }

        String levelName = trimToNull(command.levelName());
        if (levelName == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "levelName 不能为空");
        }
        if (levelName.length() > LEVEL_NAME_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "levelName 不能超过 " + LEVEL_NAME_MAX_LENGTH + " 个字符");
        }

        Integer levelNo = command.levelNo();
        if (levelNo == null || levelNo < 1 || levelNo > 6) {
            throw new BizException(ErrorCode.PARAM_INVALID, "levelNo 只能是 1~6");
        }

        int threshold = command.growthThreshold() == null ? 0 : command.growthThreshold();
        if (threshold < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "growthThreshold 不能为负");
        }

        int star = command.star() == null ? 1 : command.star();
        if (star < 1 || star > 3) {
            throw new BizException(ErrorCode.PARAM_INVALID, "star 只能是 1~3");
        }

        int status = command.status() == null ? 0 : command.status();
        if (!LEVEL_STATUSES.contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0 启用 / 1 停用");
        }

        String iconUrl = trimToNull(command.iconUrl());
        if (iconUrl != null && iconUrl.length() > ICON_URL_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "iconUrl 不能超过 " + ICON_URL_MAX_LENGTH + " 个字符");
        }

        return new Normalized(
                levelCode,
                levelName,
                levelNo,
                threshold,
                star,
                normalizeBenefits(command.benefitsJson()),
                iconUrl,
                command.sort() == null ? 0 : command.sort(),
                status);
    }

    private String normalizeBenefits(String benefitsJson) {
        if (!StringUtils.hasText(benefitsJson)) {
            return null;
        }
        String value = benefitsJson.trim();
        if (value.length() > BENEFITS_JSON_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "benefitsJson 不能超过 " + BENEFITS_JSON_MAX_LENGTH + " 个字符");
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(value);
            if (node == null || !node.isObject()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "benefitsJson 必须是 JSON 对象");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "benefitsJson 不是合法的 JSON");
        }
        return value;
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userId 必须是正整数");
        }
    }

    private long normalizePageNum(long pageNum) {
        return pageNum < 1 ? 1 : pageNum;
    }

    private long normalizePageSize(long pageSize) {
        if (pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record Normalized(
            String levelCode,
            String levelName,
            Integer levelNo,
            int growthThreshold,
            int star,
            String benefitsJson,
            String iconUrl,
            int sort,
            int status
    ) {
    }
}
