package com.soloeats.delivery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.domain.entity.Rider;
import com.soloeats.delivery.domain.entity.RiderCertification;
import com.soloeats.delivery.domain.entity.RiderOnlineLog;
import com.soloeats.delivery.domain.enums.RiderCertStatus;
import com.soloeats.delivery.domain.enums.RiderStatus;
import com.soloeats.delivery.domain.enums.VehicleType;
import com.soloeats.delivery.mapper.RiderCertificationMapper;
import com.soloeats.delivery.mapper.RiderMapper;
import com.soloeats.delivery.mapper.RiderOnlineLogMapper;
import com.soloeats.delivery.service.RiderOnlineService;
import com.soloeats.delivery.service.RiderService;
import com.soloeats.delivery.service.command.RiderQuery;
import com.soloeats.delivery.service.command.SubmitCertificationCommand;
import com.soloeats.delivery.service.view.MyCertificationView;
import com.soloeats.delivery.service.view.RiderCertificationView;
import com.soloeats.delivery.service.view.RiderDetailView;
import com.soloeats.delivery.service.view.RiderView;
import com.soloeats.delivery.support.IdCardCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 骑手档案与认证。身份证明文只在提交请求内出现一次，
 * 入库与返回一律只用 id_card_cipher / id_card_mask。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiderServiceImpl implements RiderService {

    private static final int CERT_RECORD_PENDING = 0;
    private static final int CERT_RECORD_APPROVED = 1;
    private static final int CERT_RECORD_REJECTED = 2;
    private static final int RIDER_NO_MAX_ATTEMPTS = 3;
    private static final int PHONE_SUFFIX_LENGTH = 4;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private final RiderMapper riderMapper;
    private final RiderCertificationMapper riderCertificationMapper;
    private final RiderOnlineLogMapper riderOnlineLogMapper;
    private final IdCardCipher idCardCipher;
    private final RiderOnlineService riderOnlineService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitCertification(Long userId, SubmitCertificationCommand command) {
        if (userId == null) {
            throw new BizException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "认证信息不能为空");
        }
        String realName = normalizeRealName(command.realName());
        String idCard = idCardCipher.normalize(command.idCard());
        idCardCipher.requireValid(idCard);
        if (!VehicleType.isValid(command.vehicleType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "车型只能是 1电动车 / 2摩托 / 3自行车 / 4步行");
        }
        if (command.healthCertExpireAt() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "健康证到期日不能为空");
        }
        if (command.healthCertExpireAt().isBefore(LocalDate.now())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "健康证已过期，请更换有效证件");
        }

        Rider existing = findByUserId(userId);
        if (existing != null && RiderCertStatus.isApproved(existing.getCertStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "认证已通过，无需重复提交");
        }

        String cipher = idCardCipher.encrypt(idCard);
        String mask = idCardCipher.mask(idCard);

        Rider rider = existing;
        if (rider == null) {
            rider = new Rider();
            rider.setUserId(userId);
            rider.setRealName(realName);
            rider.setIdCardCipher(cipher);
            rider.setIdCardMask(mask);
            rider.setVehicleType(command.vehicleType());
            rider.setCertStatus(RiderCertStatus.PENDING.getCode());
            rider.setHealthCertExpireAt(command.healthCertExpireAt());
            rider.setStatus(RiderStatus.NORMAL.getCode());
            // max_concurrent_orders / pickup_radius_m / rating / on_time_rate 使用表默认值，
            // 不在 Java 里重复定义业务默认量，避免与后台配置漂移
            insertWithGeneratedRiderNo(rider);
        } else {
            int rows = riderMapper.updateCertSubmission(
                    rider.getId(),
                    realName,
                    cipher,
                    mask,
                    command.vehicleType(),
                    command.healthCertExpireAt());
            if (rows != 1) {
                // 条件里带 cert_status <> 2：并发审核通过后此处返回 0
                throw new BizException(ErrorCode.PARAM_INVALID, "认证已通过，无需重复提交");
            }
            rider.setRealName(realName);
            rider.setIdCardCipher(cipher);
            rider.setIdCardMask(mask);
            rider.setVehicleType(command.vehicleType());
            rider.setCertStatus(RiderCertStatus.PENDING.getCode());
            rider.setCertRemark(null);
            rider.setHealthCertExpireAt(command.healthCertExpireAt());
        }

        RiderCertification certification = new RiderCertification();
        certification.setRiderId(rider.getId());
        certification.setRealName(realName);
        certification.setIdCardCipher(cipher);
        certification.setIdCardFrontFileId(command.idCardFrontFileId());
        certification.setIdCardBackFileId(command.idCardBackFileId());
        certification.setHealthCertFileId(command.healthCertFileId());
        certification.setHealthCertExpireAt(command.healthCertExpireAt());
        certification.setStatus(CERT_RECORD_PENDING);
        riderCertificationMapper.insert(certification);

        log.info("rider certification submitted riderId={} userId={}", rider.getId(), userId);
        return rider.getId();
    }

    @Override
    public MyCertificationView myCertification(Long userId) {
        Rider rider = userId == null ? null : findByUserId(userId);
        if (rider == null) {
            return new MyCertificationView(null, null, null,
                    RiderCertStatus.NOT_SUBMITTED.getCode(), null, null, null);
        }
        return new MyCertificationView(
                rider.getId(),
                rider.getRiderNo(),
                rider.getRealName(),
                rider.getCertStatus() == null
                        ? RiderCertStatus.NOT_SUBMITTED.getCode() : rider.getCertStatus(),
                rider.getCertRemark(),
                rider.getHealthCertExpireAt(),
                rider.getVehicleType());
    }

    @Override
    public PageResult<RiderView> adminPage(RiderQuery query, long pageNum, long pageSize) {
        RiderQuery condition = query == null ? RiderQuery.empty() : query;
        validateCertStatus(condition.certStatus());
        validateRiderStatus(condition.status());

        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = pageSize < 1L
                ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        LambdaQueryWrapper<Rider> wrapper = new LambdaQueryWrapper<Rider>()
                .eq(condition.certStatus() != null, Rider::getCertStatus, condition.certStatus())
                .eq(condition.status() != null, Rider::getStatus, condition.status())
                .orderByDesc(Rider::getId);
        applyKeyword(wrapper, condition.keyword());

        Page<Rider> page = riderMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<RiderView> views = page.getRecords().stream().map(this::toView).toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public RiderDetailView adminDetail(Long riderId) {
        Rider rider = requireRider(riderId);
        List<RiderCertification> certifications = riderCertificationMapper.selectList(
                new LambdaQueryWrapper<RiderCertification>()
                        .eq(RiderCertification::getRiderId, riderId)
                        .orderByDesc(RiderCertification::getId));
        List<RiderCertificationView> certViews = certifications.stream()
                .map(cert -> new RiderCertificationView(
                        cert.getId(),
                        cert.getRealName(),
                        cert.getHealthCertExpireAt(),
                        cert.getStatus(),
                        cert.getAuditBy(),
                        cert.getAuditAt(),
                        cert.getAuditRemark(),
                        cert.getCreatedAt()))
                .toList();
        return new RiderDetailView(toView(rider), certViews, todayOnlineSeconds(rider));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long riderId, Integer status, String remark, Long operatorId) {
        if (status == null
                || (status != CERT_RECORD_APPROVED && status != CERT_RECORD_REJECTED)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审核结论只能是 1 通过 / 2 驳回");
        }
        Rider rider = requireRider(riderId);
        RiderCertification certification = riderCertificationMapper.selectOne(
                new LambdaQueryWrapper<RiderCertification>()
                        .eq(RiderCertification::getRiderId, riderId)
                        .orderByDesc(RiderCertification::getId)
                        .last("LIMIT 1"));
        if (certification == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "认证记录不存在");
        }
        if (!Integer.valueOf(CERT_RECORD_PENDING).equals(certification.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该认证记录已审核");
        }

        certification.setStatus(status);
        certification.setAuditBy(operatorId);
        certification.setAuditAt(LocalDateTime.now());
        certification.setAuditRemark(remark);
        riderCertificationMapper.updateById(certification);

        boolean approved = status == CERT_RECORD_APPROVED;
        rider.setCertStatus(approved
                ? RiderCertStatus.APPROVED.getCode() : RiderCertStatus.REJECTED.getCode());
        rider.setCertRemark(approved ? null : remark);
        riderMapper.updateById(rider);

        log.info("rider certification audited riderId={} approved={} operatorId={}",
                riderId, approved, operatorId);
    }

    @Override
    public void updateStatus(Long riderId, Integer status) {
        if (!RiderStatus.isValid(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0 正常 / 1 停用 / 2 拉黑");
        }
        int rows = riderMapper.updateStatus(riderId, status);
        if (rows != 1) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "骑手不存在");
        }
        if (status != RiderStatus.NORMAL.getCode()) {
            // 强制下线：Redis 操作在数据库更新之后，不放进事务
            riderOnlineService.goOffline(riderId,
                    status == RiderStatus.BLACKLISTED.getCode() ? "后台拉黑" : "后台停用");
        }
        log.info("rider status updated riderId={} status={}", riderId, status);
    }

    @Override
    public void updateLimits(Long riderId, Integer maxConcurrentOrders, Integer pickupRadiusM) {
        if (maxConcurrentOrders == null || maxConcurrentOrders < 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "最大同时接单量必须大于 0");
        }
        if (pickupRadiusM == null || pickupRadiusM < 100) {
            throw new BizException(ErrorCode.PARAM_INVALID, "接单半径不能小于 100 米");
        }
        int rows = riderMapper.updateLimits(riderId, maxConcurrentOrders, pickupRadiusM);
        if (rows != 1) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "骑手不存在");
        }
        log.info("rider limits updated riderId={} maxConcurrentOrders={} pickupRadiusM={}",
                riderId, maxConcurrentOrders, pickupRadiusM);
    }

    @Override
    public Long requireRiderIdByUserId(Long userId) {
        Rider rider = userId == null ? null : findByUserId(userId);
        if (rider == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "骑手档案不存在，请先提交认证");
        }
        return rider.getId();
    }

    @Override
    public Rider requireRider(Long riderId) {
        Rider rider = riderId == null ? null : riderMapper.selectById(riderId);
        if (rider == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "骑手不存在");
        }
        return rider;
    }

    @Override
    public boolean isHealthCertValid(LocalDate healthCertExpireAt, LocalDate today) {
        LocalDate checkDate = today == null ? LocalDate.now() : today;
        return healthCertExpireAt != null && !healthCertExpireAt.isBefore(checkDate);
    }

    private Rider findByUserId(Long userId) {
        return riderMapper.selectOne(new LambdaQueryWrapper<Rider>()
                .eq(Rider::getUserId, userId)
                .last("LIMIT 1"));
    }

    private void insertWithGeneratedRiderNo(Rider rider) {
        for (int attempt = 1; attempt <= RIDER_NO_MAX_ATTEMPTS; attempt++) {
            rider.setRiderNo(nextRiderNo());
            try {
                riderMapper.insert(rider);
                return;
            } catch (DuplicateKeyException ex) {
                // uk_rider_no 并发撞号：重新取号；最后一次仍失败则终止
                if (attempt == RIDER_NO_MAX_ATTEMPTS) {
                    throw new BizException(ErrorCode.SYSTEM_ERROR, "生成骑手编号失败，请重试");
                }
                log.warn("rider_no collision, retry attempt={} riderNo={}",
                        attempt, rider.getRiderNo());
            }
        }
    }

    private String nextRiderNo() {
        String max = riderMapper.findMaxRiderNo();
        int next = 1;
        if (StringUtils.hasText(max)) {
            String digits = max.replaceAll("\\D", "");
            if (!digits.isEmpty()) {
                next = Integer.parseInt(digits) + 1;
            }
        }
        return String.format("R%06d", next);
    }

    private static void applyKeyword(LambdaQueryWrapper<Rider> wrapper, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        String trimmed = keyword.trim();
        String digits = trimmed.replaceAll("\\D", "");
        String phoneSuffix = digits.length() >= PHONE_SUFFIX_LENGTH
                ? digits.substring(digits.length() - PHONE_SUFFIX_LENGTH) : digits;
        wrapper.and(query -> {
            query.like(Rider::getRiderNo, trimmed).or().like(Rider::getRealName, trimmed);
            if (!phoneSuffix.isEmpty()) {
                query.or().like(Rider::getPhoneMask, phoneSuffix);
            }
        });
    }

    private static String normalizeRealName(String realName) {
        if (!StringUtils.hasText(realName)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "真实姓名不能为空");
        }
        String normalized = realName.trim().replaceAll("\\s+", "");
        if (normalized.length() > 64) {
            throw new BizException(ErrorCode.PARAM_INVALID, "真实姓名不能超过 64 个字符");
        }
        return normalized;
    }

    private static void validateCertStatus(Integer certStatus) {
        if (certStatus != null && (certStatus < 0 || certStatus > 3)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "certStatus 只能是 0未提交 / 1待审核 / 2已通过 / 3已驳回");
        }
    }

    private static void validateRiderStatus(Integer status) {
        if (status != null && !RiderStatus.isValid(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0 正常 / 1 停用 / 2 拉黑");
        }
    }

    private long todayOnlineSeconds(Rider rider) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        List<RiderOnlineLog> logs = riderOnlineLogMapper.selectList(
                new LambdaQueryWrapper<RiderOnlineLog>()
                        .eq(RiderOnlineLog::getRiderId, rider.getId())
                        .ge(RiderOnlineLog::getCreatedAt, start)
                        .orderByAsc(RiderOnlineLog::getCreatedAt));
        long seconds = 0L;
        LocalDateTime onlineAt = null;
        for (RiderOnlineLog record : logs) {
            Integer action = record.getAction();
            if (Integer.valueOf(1).equals(action) && onlineAt == null) {
                onlineAt = record.getCreatedAt();
            } else if (Integer.valueOf(2).equals(action) && onlineAt != null) {
                seconds += positiveSeconds(onlineAt, record.getCreatedAt());
                onlineAt = null;
            }
        }
        if (onlineAt != null) {
            seconds += positiveSeconds(onlineAt, LocalDateTime.now());
        }
        return seconds;
    }

    private static long positiveSeconds(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return 0L;
        }
        long value = Duration.between(from, to).getSeconds();
        return Math.max(value, 0L);
    }

    private RiderView toView(Rider rider) {
        return new RiderView(
                rider.getId(),
                rider.getRiderNo(),
                rider.getRealName(),
                rider.getPhoneMask(),
                rider.getVehicleType(),
                rider.getCertStatus(),
                rider.getStatus(),
                rider.getOnlineStatus(),
                rider.getRating(),
                rider.getOnTimeRate(),
                rider.getTotalOrders(),
                rider.getLastOnlineAt(),
                rider.getCreatedAt());
    }
}
