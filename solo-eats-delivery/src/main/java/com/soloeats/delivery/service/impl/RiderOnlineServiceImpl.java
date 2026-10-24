package com.soloeats.delivery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.domain.entity.DeliveryOrder;
import com.soloeats.delivery.domain.entity.Rider;
import com.soloeats.delivery.domain.entity.RiderOnlineLog;
import com.soloeats.delivery.domain.enums.DeliveryOrderStatus;
import com.soloeats.delivery.mapper.DeliveryOrderMapper;
import com.soloeats.delivery.mapper.RiderMapper;
import com.soloeats.delivery.mapper.RiderOnlineLogMapper;
import com.soloeats.delivery.service.RiderOnlineService;
import com.soloeats.delivery.service.view.NearbyRiderView;
import com.soloeats.delivery.service.view.RiderPositionView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiderOnlineServiceImpl implements RiderOnlineService {

    static final String GEO_KEY = "rider:geo";
    static final String ONLINE_KEY_PREFIX = "rider:online:";
    static final int ONLINE_TTL_SECONDS = 120;
    private static final int NEARBY_LIMIT = 50;
    private static final int ACTION_ONLINE = 1;
    private static final int ACTION_OFFLINE = 2;

    private final StringRedisTemplate redisTemplate;
    private final RiderMapper riderMapper;
    private final RiderOnlineLogMapper riderOnlineLogMapper;
    private final DeliveryOrderMapper deliveryOrderMapper;

    @Override
    public void goOnline(Long riderId, BigDecimal lng, BigDecimal lat) {
        validateCoordinate(lng, lat);
        Rider rider = riderId == null ? null : riderMapper.selectById(riderId);
        if (rider == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "骑手不存在");
        }

        int load = currentLoad(riderId);
        redisTemplate.opsForGeo().add(GEO_KEY,
                new Point(lng.doubleValue(), lat.doubleValue()), String.valueOf(riderId));

        Map<String, String> fields = new HashMap<>(8);
        fields.put("lng", lng.toPlainString());
        fields.put("lat", lat.toPlainString());
        fields.put("lastPing", String.valueOf(System.currentTimeMillis()));
        fields.put("load", String.valueOf(load));
        String onlineKey = onlineKey(riderId);
        redisTemplate.opsForHash().putAll(onlineKey, fields);
        redisTemplate.expire(onlineKey, ONLINE_TTL_SECONDS, TimeUnit.SECONDS);

        riderMapper.updateOnlineStatusAndLastOnline(riderId, 1);
        writeLog(riderId, ACTION_ONLINE, lng, lat, null);
        log.info("rider online riderId={} load={}", riderId, load);
    }

    @Override
    public void goOffline(Long riderId, String reason) {
        if (riderId == null) {
            return;
        }
        boolean wasOnline = isOnline(riderId);
        RiderPositionView position = wasOnline ? getPosition(riderId) : null;

        redisTemplate.opsForGeo().remove(GEO_KEY, String.valueOf(riderId));
        redisTemplate.delete(onlineKey(riderId));
        riderMapper.updateOnlineStatus(riderId, 0);

        // 下线日志一律落库：后台停用/拉黑触发的强制下线同样要留审计
        writeLog(riderId, ACTION_OFFLINE,
                position == null ? null : position.lng(),
                position == null ? null : position.lat(),
                reason);
        log.info("rider offline riderId={} wasOnline={} reason={}", riderId, wasOnline, reason);
    }

    @Override
    public boolean isOnline(Long riderId) {
        return riderId != null && Boolean.TRUE.equals(redisTemplate.hasKey(onlineKey(riderId)));
    }

    @Override
    public RiderPositionView getPosition(Long riderId) {
        if (riderId == null) {
            return null;
        }
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(onlineKey(riderId));
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        BigDecimal lng = toBigDecimal(entries.get("lng"));
        BigDecimal lat = toBigDecimal(entries.get("lat"));
        if (lng == null || lat == null) {
            return null;
        }
        Long lastPingMillis = toLong(entries.get("lastPing"));
        LocalDateTime lastPing = lastPingMillis == null ? null
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(lastPingMillis), ZoneId.systemDefault());
        Integer load = toInteger(entries.get("load"));
        return new RiderPositionView(lng, lat, lastPing, load);
    }

    @Override
    public List<NearbyRiderView> nearbyRiders(BigDecimal lng, BigDecimal lat, int radiusM) {
        validateCoordinate(lng, lat);
        if (radiusM <= 0) {
            return List.of();
        }
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().search(
                GEO_KEY,
                GeoReference.fromCoordinate(
                        new Point(lng.doubleValue(), lat.doubleValue())),
                new Distance(radiusM, Metrics.NEUTRAL),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                        .includeDistance()
                        .sortAscending()
                        .limit(NEARBY_LIMIT));
        if (results == null) {
            return List.of();
        }
        List<NearbyRiderView> riders = new ArrayList<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results) {
            RedisGeoCommands.GeoLocation<String> location = result.getContent();
            if (location == null || !StringUtils.hasText(location.getName())) {
                continue;
            }
            try {
                Long riderId = Long.parseLong(location.getName());
                double distance = result.getDistance() == null ? 0D : result.getDistance().getValue();
                riders.add(new NearbyRiderView(riderId, distance));
            } catch (NumberFormatException ex) {
                // 脏 member 不阻塞整次查询
                log.warn("invalid rider:geo member={}", location.getName());
            }
        }
        return riders;
    }

    @Override
    public int getLoad(Long riderId) {
        if (riderId == null) {
            return 0;
        }
        Object value = redisTemplate.opsForHash().get(onlineKey(riderId), "load");
        Integer load = toInteger(value);
        return load == null ? 0 : load;
    }

    private int currentLoad(Long riderId) {
        Long count = deliveryOrderMapper.selectCount(new LambdaQueryWrapper<DeliveryOrder>()
                .eq(DeliveryOrder::getRiderId, riderId)
                .in(DeliveryOrder::getStatus,
                        DeliveryOrderStatus.ACCEPTED.getCode(),
                        DeliveryOrderStatus.PICKED_UP.getCode(),
                        DeliveryOrderStatus.DELIVERING.getCode()));
        return count == null ? 0 : count.intValue();
    }

    private void writeLog(Long riderId, int action, BigDecimal lng, BigDecimal lat, String reason) {
        RiderOnlineLog onlineLog = new RiderOnlineLog();
        onlineLog.setRiderId(riderId);
        onlineLog.setAction(action);
        onlineLog.setLng(lng);
        onlineLog.setLat(lat);
        onlineLog.setReason(reason);
        riderOnlineLogMapper.insert(onlineLog);
    }

    private static String onlineKey(Long riderId) {
        return ONLINE_KEY_PREFIX + riderId;
    }

    private static void validateCoordinate(BigDecimal lng, BigDecimal lat) {
        if (lng == null || lat == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "经纬度不能为空");
        }
        double lngValue = lng.doubleValue();
        double latValue = lat.doubleValue();
        if (lngValue < -180D || lngValue > 180D || latValue < -90D || latValue > 90D) {
            throw new BizException(ErrorCode.PARAM_INVALID, "经纬度超出合法范围");
        }
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
