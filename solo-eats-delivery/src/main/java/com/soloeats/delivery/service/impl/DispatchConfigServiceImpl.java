package com.soloeats.delivery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.domain.entity.DispatchConfig;
import com.soloeats.delivery.mapper.DispatchConfigMapper;
import com.soloeats.delivery.service.DispatchConfigService;
import com.soloeats.delivery.service.command.SaveDispatchConfigCommand;
import com.soloeats.delivery.service.view.DispatchConfigView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchConfigServiceImpl implements DispatchConfigService {

    private static final int SCOPE_GLOBAL = 1;
    private static final int SCOPE_SHOP = 3;
    private static final BigDecimal MAX_WEIGHT = BigDecimal.ONE;

    private final DispatchConfigMapper dispatchConfigMapper;

    @Override
    public DispatchConfigView getConfig(Long shopId) {
        return toView(effectiveConfig(shopId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DispatchConfigView save(SaveDispatchConfigCommand command) {
        validate(command);
        DispatchConfig existing = findScope(command.configScope(), command.shopId());
        DispatchConfig config = existing == null ? new DispatchConfig() : existing;

        config.setConfigScope(command.configScope());
        config.setShopId(command.configScope() == SCOPE_SHOP ? command.shopId() : null);
        config.setDistanceWeight(command.distanceWeight());
        config.setLoadWeight(command.loadWeight());
        config.setRatingWeight(command.ratingWeight());
        config.setTimeoutWeight(command.timeoutWeight());
        config.setDispatchExpireSeconds(command.dispatchExpireSeconds());
        config.setMaxRetry(command.maxRetry());
        config.setGrabRadiusM(command.grabRadiusM());
        config.setGrabMaxOrders(command.grabMaxOrders());
        config.setGrabEnabled(command.grabEnabled() == null ? 1 : command.grabEnabled());
        config.setAutoDispatchEnabled(command.autoDispatchEnabled() == null
                ? 1 : command.autoDispatchEnabled());
        config.setStatus(command.status() == null ? 0 : command.status());

        if (existing == null) {
            dispatchConfigMapper.insert(config);
        } else {
            dispatchConfigMapper.updateById(config);
        }
        log.info("dispatch config saved scope={} shopId={} operatorWeights={}/{}/{}/{}",
                command.configScope(), command.shopId(), command.distanceWeight(),
                command.loadWeight(), command.ratingWeight(), command.timeoutWeight());
        return getConfig(command.configScope() == SCOPE_SHOP ? command.shopId() : null);
    }

    @Override
    public DispatchConfig effectiveConfig(Long shopId) {
        if (shopId != null) {
            DispatchConfig shopConfig = findEnabled(SCOPE_SHOP, shopId);
            if (shopConfig != null) {
                return shopConfig;
            }
        }
        DispatchConfig global = findEnabled(SCOPE_GLOBAL, null);
        if (global == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "派单策略未配置");
        }
        return global;
    }

    private DispatchConfig findEnabled(int scope, Long shopId) {
        LambdaQueryWrapper<DispatchConfig> wrapper = new LambdaQueryWrapper<DispatchConfig>()
                .eq(DispatchConfig::getConfigScope, scope)
                .eq(DispatchConfig::getStatus, 0);
        if (shopId == null) {
            wrapper.isNull(DispatchConfig::getShopId);
        } else {
            wrapper.eq(DispatchConfig::getShopId, shopId);
        }
        return dispatchConfigMapper.selectOne(
                wrapper.orderByAsc(DispatchConfig::getId).last("LIMIT 1"));
    }

    private DispatchConfig findScope(int scope, Long shopId) {
        LambdaQueryWrapper<DispatchConfig> wrapper = new LambdaQueryWrapper<DispatchConfig>()
                .eq(DispatchConfig::getConfigScope, scope);
        if (shopId == null) {
            wrapper.isNull(DispatchConfig::getShopId);
        } else {
            wrapper.eq(DispatchConfig::getShopId, shopId);
        }
        return dispatchConfigMapper.selectOne(
                wrapper.orderByAsc(DispatchConfig::getId).last("LIMIT 1"));
    }

    private static void validate(SaveDispatchConfigCommand command) {
        if (command == null || command.configScope() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "configScope 不能为空");
        }
        int scope = command.configScope();
        // t_dispatch_config 暂无 city_id，城市级配置无法定位，本阶段只落全局与门店覆盖
        if (scope != SCOPE_GLOBAL && scope != SCOPE_SHOP) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "configScope 只能是 1全局 / 3门店（城市级配置暂未开放）");
        }
        if (scope == SCOPE_SHOP && command.shopId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "门店级配置必须指定 shopId");
        }
        validateWeight(command.distanceWeight(), "distanceWeight");
        validateWeight(command.loadWeight(), "loadWeight");
        validateWeight(command.ratingWeight(), "ratingWeight");
        validateWeight(command.timeoutWeight(), "timeoutWeight");
        requireRange(command.dispatchExpireSeconds(), 1, 600, "dispatchExpireSeconds");
        requireRange(command.maxRetry(), 0, 10, "maxRetry");
        requireRange(command.grabRadiusM(), 100, 50000, "grabRadiusM");
        requireRange(command.grabMaxOrders(), 1, 50, "grabMaxOrders");
        requireFlag(command.grabEnabled(), "grabEnabled");
        requireFlag(command.autoDispatchEnabled(), "autoDispatchEnabled");
        requireFlag(command.status(), "status");
    }

    private static void validateWeight(BigDecimal weight, String field) {
        if (weight == null || weight.compareTo(BigDecimal.ZERO) < 0
                || weight.compareTo(MAX_WEIGHT) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 必须在 0~1 之间");
        }
    }

    private static void requireRange(Integer value, int min, int max, String field) {
        if (value == null || value < min || value > max) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    field + " 必须在 " + min + "~" + max + " 之间");
        }
    }

    private static void requireFlag(Integer value, String field) {
        if (value != null && value != 0 && value != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 只能是 0 或 1");
        }
    }

    private static DispatchConfigView toView(DispatchConfig config) {
        return new DispatchConfigView(
                config.getId(),
                config.getConfigScope(),
                config.getShopId(),
                config.getDistanceWeight(),
                config.getLoadWeight(),
                config.getRatingWeight(),
                config.getTimeoutWeight(),
                config.getDispatchExpireSeconds(),
                config.getMaxRetry(),
                config.getGrabRadiusM(),
                config.getGrabMaxOrders(),
                config.getGrabEnabled(),
                config.getAutoDispatchEnabled(),
                config.getStatus(),
                config.getUpdatedAt());
    }
}
