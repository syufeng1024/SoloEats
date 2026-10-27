package com.soloeats.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.member.domain.entity.Favorite;
import com.soloeats.member.mapper.FavoriteMapper;
import com.soloeats.member.service.FavoriteService;
import com.soloeats.member.service.view.FavoriteView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private final FavoriteMapper favoriteMapper;

    @Override
    public FavoriteView add(Long userId, Integer targetType, Long targetId) {
        requireUser(userId);
        requireTarget(targetType, targetId);

        Favorite existing = findOne(userId, targetType, targetId);
        if (existing != null) {
            return FavoriteView.from(existing);
        }

        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setTargetType(targetType);
        favorite.setTargetId(targetId);
        try {
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException ex) {
            // 并发重复收藏：唯一键兜底，按已存在处理，不向前端暴露 500
            Favorite concurrent = findOne(userId, targetType, targetId);
            if (concurrent == null) {
                throw ex;
            }
            return FavoriteView.from(concurrent);
        }

        Favorite saved = favoriteMapper.selectById(favorite.getId());
        log.info("favorite added userId={} targetType={} targetId={}",
                userId, targetType, targetId);
        return FavoriteView.from(saved == null ? favorite : saved);
    }

    @Override
    public void remove(Long userId, Integer targetType, Long targetId) {
        requireUser(userId);
        requireTarget(targetType, targetId);
        int rows = favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, targetType)
                .eq(Favorite::getTargetId, targetId));
        // 重复取消按成功处理：客户端重试/双端操作不应报错
        log.info("favorite removed userId={} targetType={} targetId={} rows={}",
                userId, targetType, targetId, rows);
    }

    @Override
    public PageResult<FavoriteView> page(Long userId, Integer targetType, Long targetId,
                                         long pageNum, long pageSize) {
        requireUser(userId);
        if (targetType != null) {
            validateTargetType(targetType);
        }
        if (targetId != null && targetId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "targetId 必须是正整数");
        }

        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = pageSize < 1L
                ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(targetType != null, Favorite::getTargetType, targetType)
                .eq(targetId != null, Favorite::getTargetId, targetId)
                .orderByDesc(Favorite::getCreatedAt)
                .orderByDesc(Favorite::getId);

        Page<Favorite> page = favoriteMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<FavoriteView> views = page.getRecords().stream().map(FavoriteView::from).toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    private Favorite findOne(Long userId, Integer targetType, Long targetId) {
        return favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, targetType)
                .eq(Favorite::getTargetId, targetId)
                .last("LIMIT 1"));
    }

    private void requireUser(Long userId) {
        if (userId == null || userId <= 0L) {
            throw new BizException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }

    private void requireTarget(Integer targetType, Long targetId) {
        if (targetType == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "targetType 不能为空");
        }
        validateTargetType(targetType);
        if (targetId == null || targetId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "targetId 必须是正整数");
        }
    }

    private void validateTargetType(Integer targetType) {
        if (!Objects.equals(targetType, Favorite.TARGET_TYPE_SHOP)
                && !Objects.equals(targetType, Favorite.TARGET_TYPE_PRODUCT)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "targetType 只能是 1店铺 2商品");
        }
    }
}
