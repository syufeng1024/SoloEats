package com.soloeats.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.domain.entity.PointAccount;
import com.soloeats.marketing.domain.entity.PointLog;
import com.soloeats.marketing.mapper.PointAccountMapper;
import com.soloeats.marketing.mapper.PointLogMapper;
import com.soloeats.marketing.service.PointService;
import com.soloeats.marketing.service.view.PointAccountView;
import com.soloeats.marketing.service.view.PointLogView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 积分账户。加减一律走条件更新，幂等靠 uk_biz(source, biz_id)。
 *
 * <p>写流水与改余额的顺序刻意固定为「先插流水（占用 uk_biz）→ 再条件更新余额」：
 * 并发重复请求会在插流水时撞唯一键，直接返回，不会重复加减；余额不足时
 * 抛 BizException，整个事务回滚，刚插入的流水一起撤销。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointServiceImpl implements PointService {

    private static final int SOURCE_EXPIRE = 6;
    private static final int POINT_EXPIRE_DAYS = 365;
    private static final int EXPIRE_BATCH_SIZE = 500;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int REMARK_MAX_LENGTH = 255;

    private static final Set<Integer> SOURCES = Set.of(1, 2, 3, 4, 5, 6, 7);

    private final PointAccountMapper pointAccountMapper;
    private final PointLogMapper pointLogMapper;

    @Override
    public PointAccountView account(Long userId) {
        requireUserId(userId);
        pointAccountMapper.ensureAccount(userId);
        return PointAccountView.from(requireAccount(userId));
    }

    @Override
    public PageResult<PointLogView> logs(Long userId, long pageNum, long pageSize) {
        requireUserId(userId);
        long normalizedPageNum = normalizePageNum(pageNum);
        long normalizedPageSize = normalizePageSize(pageSize);

        Page<PointLog> page = pointLogMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize),
                new LambdaQueryWrapper<PointLog>()
                        .eq(PointLog::getUserId, userId)
                        .orderByDesc(PointLog::getId));

        List<PointLogView> views = page.getRecords().stream()
                .map(PointLogView::from)
                .toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void earn(Long userId, Integer source, Long bizId, int point, String remark) {
        requireUserId(userId);
        requireSource(source);
        requirePositive(point);
        requireBizId(bizId);

        pointAccountMapper.ensureAccount(userId);
        if (pointLogMapper.countByBiz(source, bizId) > 0) {
            log.info("point earn ignored by idempotency userId={} source={} bizId={}",
                    userId, source, bizId);
            return;
        }

        PointAccount account = requireAccount(userId);
        int before = safeInt(account.getBalance());
        LocalDateTime now = LocalDateTime.now();
        PointLog entry = newLog(userId, source, bizId, point, before,
                now.plusDays(POINT_EXPIRE_DAYS), remark);

        try {
            pointLogMapper.insert(entry);
        } catch (DuplicateKeyException e) {
            // 并发重复请求：另一线程已写入同一 (source, biz_id)，本次直接返回
            log.info("point earn duplicate ignored userId={} source={} bizId={}",
                    userId, source, bizId);
            return;
        }

        if (pointAccountMapper.changeBalance(userId, point) == 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "积分入账失败，账户不存在");
        }
        log.info("point earned userId={} source={} bizId={} point={} before={} after={}",
                userId, source, bizId, point, before, before + point);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consume(Long userId, Integer source, Long bizId, int point, String remark) {
        requireUserId(userId);
        requireSource(source);
        requirePositive(point);
        requireBizId(bizId);

        pointAccountMapper.ensureAccount(userId);
        if (pointLogMapper.countByBiz(source, bizId) > 0) {
            log.info("point consume ignored by idempotency userId={} source={} bizId={}",
                    userId, source, bizId);
            return;
        }

        PointAccount account = requireAccount(userId);
        int before = safeInt(account.getBalance());
        if (before < point) {
            throw new BizException(ErrorCode.POINT_NOT_ENOUGH);
        }

        PointLog entry = newLog(userId, source, bizId, -point, before, null, remark);
        try {
            pointLogMapper.insert(entry);
        } catch (DuplicateKeyException e) {
            log.info("point consume duplicate ignored userId={} source={} bizId={}",
                    userId, source, bizId);
            return;
        }

        // 条件更新是第二道防线：并发扣减时后到者返回 0，抛异常回滚刚插入的流水
        if (pointAccountMapper.changeBalance(userId, -point) == 0) {
            throw new BizException(ErrorCode.POINT_NOT_ENOUGH);
        }
        log.info("point consumed userId={} source={} bizId={} point={} before={} after={}",
                userId, source, bizId, point, before, before - point);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int expirePoints(LocalDate date) {
        if (date == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "date 不能为空");
        }

        // expire_at 是 datetime，用「date 次日 0 点」做开区间，覆盖 date 当天全部到期流水
        LocalDateTime before = date.plusDays(1).atStartOfDay();
        long lastId = 0L;
        int expired = 0;

        while (true) {
            List<PointLog> batches =
                    pointLogMapper.selectExpiredBatches(before, lastId, EXPIRE_BATCH_SIZE);
            if (batches.isEmpty()) {
                break;
            }

            for (PointLog batch : batches) {
                lastId = batch.getId();
                if (batch.getUserId() == null || safeInt(batch.getPoint()) <= 0) {
                    continue;
                }
                // 同一原流水只过期一次：uk_biz(6, 原流水id)
                if (pointLogMapper.countByBiz(SOURCE_EXPIRE, batch.getId()) > 0) {
                    continue;
                }

                pointAccountMapper.ensureAccount(batch.getUserId());
                PointAccount account = requireAccount(batch.getUserId());
                int balance = safeInt(account.getBalance());
                // 余额已被消费时最多过期到 0，不允许负余额
                int expireAmount = Math.min(batch.getPoint(), balance);
                if (expireAmount <= 0) {
                    continue;
                }

                PointLog entry = newLog(batch.getUserId(), SOURCE_EXPIRE, batch.getId(),
                        -expireAmount, balance, null, "积分过期，原流水 " + batch.getId());
                try {
                    pointLogMapper.insert(entry);
                } catch (DuplicateKeyException e) {
                    continue;
                }

                if (pointAccountMapper.changeBalance(batch.getUserId(), -expireAmount) == 0) {
                    // 并发扣减导致余额不足：撤销本批次过期流水，留给下次任务重试
                    pointLogMapper.deleteById(entry.getId());
                    continue;
                }
                expired++;
            }

            if (batches.size() < EXPIRE_BATCH_SIZE) {
                break;
            }
        }

        log.info("point expire finished date={} expiredBatches={}", date, expired);
        return expired;
    }

    // ---------------------------------------------------------------
    // 内部方法
    // ---------------------------------------------------------------

    private PointLog newLog(Long userId, Integer source, Long bizId, int point,
                            int beforeBalance, LocalDateTime expireAt, String remark) {
        PointLog entry = new PointLog();
        entry.setUserId(userId);
        entry.setSource(source);
        entry.setBizId(bizId);
        entry.setPoint(point);
        entry.setBeforeBalance(beforeBalance);
        entry.setAfterBalance(beforeBalance + point);
        entry.setExpireAt(expireAt);
        entry.setRemark(normalizeRemark(remark));
        return entry;
    }

    private PointAccount requireAccount(Long userId) {
        PointAccount account = pointAccountMapper.selectOne(
                new LambdaQueryWrapper<PointAccount>().eq(PointAccount::getUserId, userId));
        if (account == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "积分账户初始化失败");
        }
        return account;
    }

    private String normalizeRemark(String remark) {
        if (remark == null) {
            return null;
        }
        String trimmed = remark.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > REMARK_MAX_LENGTH
                ? trimmed.substring(0, REMARK_MAX_LENGTH)
                : trimmed;
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userId 必须是正整数");
        }
    }

    private void requireSource(Integer source) {
        if (source == null || !SOURCES.contains(source)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "source 只能是 1~7");
        }
    }

    private void requirePositive(int point) {
        if (point <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "point 必须大于 0");
        }
    }

    private void requireBizId(Long bizId) {
        if (bizId == null || bizId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bizId 必须是正整数");
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
}
