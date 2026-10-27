package com.soloeats.delivery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.domain.entity.DeliveryOrder;
import com.soloeats.delivery.domain.enums.DeliveryOrderStatus;
import com.soloeats.delivery.mapper.DeliveryOrderMapper;
import com.soloeats.delivery.service.RiderDeliveryQueryService;
import com.soloeats.delivery.service.view.RiderDeliveryView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RiderDeliveryQueryServiceImpl implements RiderDeliveryQueryService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;
    private static final String UNKNOWN_STATUS = "未知状态";

    private final DeliveryOrderMapper deliveryOrderMapper;

    @Override
    public PageResult<RiderDeliveryView> pageMyDeliveries(Long riderId, Integer status,
                                                          int pageNum, int pageSize) {
        if (riderId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "riderId 不能为空");
        }
        int normalizedPageNum = pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
        int normalizedPageSize = pageSize < 1
                ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        // accepted_at 为空的历史单按 created_at 参与同一排序，避免未接单记录沉底
        LambdaQueryWrapper<DeliveryOrder> wrapper = new LambdaQueryWrapper<DeliveryOrder>()
                .eq(DeliveryOrder::getRiderId, riderId)
                .eq(status != null, DeliveryOrder::getStatus, status)
                .last("ORDER BY COALESCE(accepted_at, created_at) DESC, id DESC");

        Page<DeliveryOrder> page = deliveryOrderMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<RiderDeliveryView> views = page.getRecords().stream()
                .map(this::toView)
                .toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public RiderDeliveryView detail(Long riderId, Long deliveryId) {
        if (riderId == null || deliveryId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "riderId 与 deliveryId 不能为空");
        }
        DeliveryOrder delivery = deliveryOrderMapper.selectById(deliveryId);
        if (delivery == null) {
            throw new BizException(ErrorCode.DELIVERY_NOT_FOUND);
        }
        if (!Objects.equals(delivery.getRiderId(), riderId)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "该配送单不属于当前骑手");
        }
        return toView(delivery);
    }

    private RiderDeliveryView toView(DeliveryOrder delivery) {
        return new RiderDeliveryView(
                delivery.getId(),
                delivery.getDeliveryNo(),
                delivery.getOrderId(),
                delivery.getShopId(),
                delivery.getStatus(),
                statusName(delivery.getStatus()),
                delivery.getPickupAddress(),
                // t_delivery_order 无收货人姓名字段
                null,
                delivery.getReceiverAddress(),
                // 实体映射已覆盖 receiver_lng / receiver_lat，MP 默认查询会带出这两列
                delivery.getReceiverLng(),
                delivery.getReceiverLat(),
                // t_delivery_order 无收货人手机号字段，不额外新增列
                null,
                delivery.getDistanceM(),
                delivery.getDeliveryFee(),
                delivery.getPromiseDeliverAt(),
                delivery.getAcceptedAt(),
                delivery.getPickedUpAt(),
                delivery.getDeliveredAt(),
                delivery.getDispatchExpireAt()
        );
    }

    private String statusName(Integer code) {
        if (code == null) {
            return UNKNOWN_STATUS;
        }
        return Arrays.stream(DeliveryOrderStatus.values())
                .filter(status -> status.getCode() == code)
                .map(DeliveryOrderStatus::getDescription)
                .findFirst()
                .orElse(UNKNOWN_STATUS);
    }
}
