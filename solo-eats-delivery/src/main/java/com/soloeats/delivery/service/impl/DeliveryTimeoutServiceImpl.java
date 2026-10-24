package com.soloeats.delivery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soloeats.delivery.domain.entity.DeliveryOrder;
import com.soloeats.delivery.domain.entity.RiderPingAnomaly;
import com.soloeats.delivery.domain.enums.DeliveryOrderStatus;
import com.soloeats.delivery.mapper.DeliveryOrderMapper;
import com.soloeats.delivery.mapper.RiderPingAnomalyMapper;
import com.soloeats.delivery.service.DeliveryTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryTimeoutServiceImpl implements DeliveryTimeoutService {

    static final int SCAN_LIMIT = 500;
    static final int TYPE_TIMEOUT = 1;

    private final DeliveryOrderMapper deliveryOrderMapper;
    private final RiderPingAnomalyMapper riderPingAnomalyMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markTimeouts() {
        LocalDateTime now = LocalDateTime.now();
        List<DeliveryOrder> overdue = deliveryOrderMapper.selectList(
                new LambdaQueryWrapper<DeliveryOrder>()
                        .eq(DeliveryOrder::getStatus, DeliveryOrderStatus.DELIVERING.getCode())
                        .isNotNull(DeliveryOrder::getPromiseDeliverAt)
                        .lt(DeliveryOrder::getPromiseDeliverAt, now)
                        .isNull(DeliveryOrder::getDeliveredAt)
                        .orderByAsc(DeliveryOrder::getPromiseDeliverAt)
                        .last("LIMIT " + SCAN_LIMIT));
        if (overdue.isEmpty()) {
            return 0;
        }

        List<Long> deliveryIds = overdue.stream().map(DeliveryOrder::getId).toList();
        Set<Long> alreadyMarked = new HashSet<>(
                riderPingAnomalyMapper.selectTimedOutDeliveryIds(deliveryIds));

        int inserted = 0;
        for (DeliveryOrder delivery : overdue) {
            if (!alreadyMarked.add(delivery.getId())) {
                continue;
            }
            RiderPingAnomaly anomaly = new RiderPingAnomaly();
            anomaly.setRiderId(delivery.getRiderId());
            anomaly.setDeliveryId(delivery.getId());
            anomaly.setType(TYPE_TIMEOUT);
            anomaly.setRemark("承诺送达时间 " + delivery.getPromiseDeliverAt() + " 已超时");
            riderPingAnomalyMapper.insert(anomaly);
            inserted++;
        }
        if (inserted > 0) {
            log.info("delivery timeout marked={} scanned={}", inserted, overdue.size());
        }
        return inserted;
    }
}
