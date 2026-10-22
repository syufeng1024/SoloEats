package com.soloeats.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.merchant.domain.entity.Merchant;
import com.soloeats.merchant.domain.entity.MerchantAuditLog;
import com.soloeats.merchant.domain.entity.MerchantQualification;
import com.soloeats.merchant.domain.entity.MerchantSettlementAccount;
import com.soloeats.merchant.domain.enums.AuditAction;
import com.soloeats.merchant.domain.enums.MerchantStatus;
import com.soloeats.merchant.mapper.MerchantAuditLogMapper;
import com.soloeats.merchant.mapper.MerchantMapper;
import com.soloeats.merchant.mapper.MerchantQualificationMapper;
import com.soloeats.merchant.mapper.MerchantSettlementAccountMapper;
import com.soloeats.merchant.service.MerchantAdminService;
import com.soloeats.merchant.service.MerchantNotifyPort;
import com.soloeats.merchant.service.command.AuditMerchantCommand;
import com.soloeats.merchant.service.view.AuditLogView;
import com.soloeats.merchant.service.view.MerchantDetailView;
import com.soloeats.merchant.service.view.MerchantView;
import com.soloeats.merchant.service.view.QualificationView;
import com.soloeats.merchant.support.PiiCipher;
import com.soloeats.system.service.SysRoleService;
import com.soloeats.system.service.SysTenantService;
import com.soloeats.system.service.SysUserService;
import com.soloeats.system.service.command.CreateUserCommand;
import com.soloeats.system.service.command.SaveTenantCommand;
import com.soloeats.system.service.view.SysRoleView;
import com.soloeats.system.service.view.SysUserView;
import com.soloeats.system.service.view.TenantView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantAdminServiceImpl implements MerchantAdminService {

    private static final String MERCHANT_MANAGER_ROLE = "MERCHANT_MANAGER";
    private static final int USER_TYPE_MERCHANT = 2;
    private static final int DATA_SCOPE_MERCHANT = 3;
    private static final int TENANT_TYPE_MERCHANT = 2;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int MIN_REJECT_REMARK = 5;
    private static final int MAX_REMARK = 512;
    private static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD = 64;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.@-]{3,64}$");

    private final MerchantMapper merchantMapper;
    private final MerchantQualificationMapper qualificationMapper;
    private final MerchantAuditLogMapper auditLogMapper;
    private final MerchantSettlementAccountMapper settlementAccountMapper;
    private final SysUserService sysUserService;
    private final SysRoleService sysRoleService;
    private final SysTenantService sysTenantService;
    private final MerchantNotifyPort merchantNotifyPort;
    private final PiiCipher piiCipher;

    @Value("${app.settlement.default-commission-rate:0.0600}")
    private String defaultCommissionRate;

    @Override
    public PageResult<MerchantView> page(Integer status, String keyword, Long categoryId,
                                         long pageNum, long pageSize) {
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = pageSize < 1L ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getDeleted, 0)
                .eq(status != null, Merchant::getStatus, status)
                .eq(categoryId != null, Merchant::getCategoryId, categoryId);
        String sanitized = sanitizeKeyword(keyword);
        if (StringUtils.hasText(sanitized)) {
            wrapper.and(w -> w.like(Merchant::getMerchantName, sanitized)
                    .or()
                    .like(Merchant::getMerchantNo, sanitized)
                    .or()
                    .like(Merchant::getContactPhone, sanitized));
        }
        wrapper.orderByDesc(Merchant::getId);

        Page<Merchant> page = merchantMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<MerchantView> views = page.getRecords().stream().map(this::toView).toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public MerchantDetailView detail(Long id) {
        return toDetail(requireMerchant(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantDetailView approve(Long id, AuditMerchantCommand command) {
        Merchant merchant = requireMerchant(id);
        // 幂等：已通过的商家直接返回详情，不重复建账号/租户
        if (Objects.equals(merchant.getStatus(), MerchantStatus.APPROVED.code())) {
            log.info("merchant approve ignored (already approved) merchantId={}", id);
            return toDetail(merchant);
        }
        if (!MerchantStatus.canAudit(merchant.getStatus())) {
            throw new BizException(ErrorCode.MERCHANT_STATUS_INVALID, "只有待审核的商家可以通过");
        }
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        String initialPassword = requirePassword(command.initialPassword());
        BigDecimal commissionRate = normalizeRate(command.commissionRate());
        int level = normalizeLevel(command.merchantLevel());
        String remark = optionalText(command.remark(), MAX_REMARK, "remark");
        String adminUsername = resolveAdminUsername(command.adminUsername(), merchant.getMerchantNo());

        LocalDateTime now = LocalDateTime.now();
        int updated = merchantMapper.update(null, new LambdaUpdateWrapper<Merchant>()
                .eq(Merchant::getId, id)
                .eq(Merchant::getStatus, MerchantStatus.PENDING.code())
                .eq(Merchant::getDeleted, 0)
                .set(Merchant::getStatus, MerchantStatus.APPROVED.code())
                .set(Merchant::getCommissionRate, commissionRate)
                .set(Merchant::getLevel, level)
                .set(Merchant::getAuditBy, command.operatorId())
                .set(Merchant::getAuditAt, now)
                .set(Merchant::getAuditRemark, remark)
                .set(Merchant::getOpenedAt, now));
        if (updated == 0) {
            throw new BizException(ErrorCode.MERCHANT_STATUS_INVALID, "商家状态已变更，请刷新后重试");
        }

        insertAuditLog(id, MerchantStatus.PENDING.code(), MerchantStatus.APPROVED.code(),
                AuditAction.APPROVE, command.operatorId(), command.operatorName(), remark);

        Long roleId = requireMerchantManagerRoleId();
        SysUserView user = sysUserService.create(new CreateUserCommand(
                adminUsername,
                initialPassword,
                merchant.getMerchantName(),
                merchant.getContactPhone(),
                USER_TYPE_MERCHANT,
                merchant.getId(),
                null,
                null,
                null,
                DATA_SCOPE_MERCHANT,
                List.of(roleId)
        ));

        TenantView tenant = sysTenantService.create(new SaveTenantCommand(
                "M" + merchant.getMerchantNo(),
                merchant.getMerchantName(),
                TENANT_TYPE_MERCHANT,
                merchant.getId(),
                null,
                null,
                merchant.getContactName(),
                // 联系电话属敏感信息，t_sys_tenant 无加密写入通道，这里不重复落库
                null
        ));

        // 商家账号由 SysUserService 固定创建在租户 1；回填 t_merchant.tenant_id 必须与账号
        // 实际登录租户一致，否则商家登录后 TenantLine 会过滤掉自己的商家行。
        Long merchantTenantId = user.tenantId() == null ? tenant.id() : user.tenantId();
        merchantMapper.update(null, new LambdaUpdateWrapper<Merchant>()
                .eq(Merchant::getId, id)
                .set(Merchant::getTenantId, merchantTenantId));

        // TODO(T08): notify 实现接入站内信后应改为事务提交后异步发送
        merchantNotifyPort.notifyAuditResult(id, MerchantStatus.APPROVED.code(), remark);
        log.info("merchant approved merchantId={} userId={} tenantId={}",
                id, user.id(), tenant.id());
        return toDetail(requireMerchant(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantDetailView reject(Long id, String remark, Long operatorId, String operatorName) {
        Merchant merchant = requireMerchant(id);
        if (!MerchantStatus.canAudit(merchant.getStatus())) {
            throw new BizException(ErrorCode.MERCHANT_STATUS_INVALID, "只有待审核的商家可以驳回");
        }
        String normalizedRemark = optionalText(remark, MAX_REMARK, "remark");
        if (normalizedRemark == null || normalizedRemark.length() < MIN_REJECT_REMARK) {
            throw new BizException(ErrorCode.PARAM_INVALID, "驳回原因不能少于 5 个字");
        }
        int updated = merchantMapper.update(null, new LambdaUpdateWrapper<Merchant>()
                .eq(Merchant::getId, id)
                .eq(Merchant::getStatus, MerchantStatus.PENDING.code())
                .eq(Merchant::getDeleted, 0)
                .set(Merchant::getStatus, MerchantStatus.REJECTED.code())
                .set(Merchant::getAuditBy, operatorId)
                .set(Merchant::getAuditAt, LocalDateTime.now())
                .set(Merchant::getAuditRemark, normalizedRemark));
        if (updated == 0) {
            throw new BizException(ErrorCode.MERCHANT_STATUS_INVALID, "商家状态已变更，请刷新后重试");
        }
        insertAuditLog(id, MerchantStatus.PENDING.code(), MerchantStatus.REJECTED.code(),
                AuditAction.REJECT, operatorId, operatorName, normalizedRemark);
        merchantNotifyPort.notifyAuditResult(id, MerchantStatus.REJECTED.code(), normalizedRemark);
        log.info("merchant rejected merchantId={}", id);
        return toDetail(requireMerchant(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status, Long operatorId, String operatorName) {
        Merchant merchant = requireMerchant(id);
        if (status == null
                || (status != MerchantStatus.APPROVED.code() && status != MerchantStatus.DISABLED.code())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 2已通过 或 4已停用");
        }
        int current = merchant.getStatus() == null ? -1 : merchant.getStatus();
        if (!MerchantStatus.canToggle(current, status)) {
            throw new BizException(ErrorCode.MERCHANT_STATUS_INVALID);
        }
        int updated = merchantMapper.update(null, new LambdaUpdateWrapper<Merchant>()
                .eq(Merchant::getId, id)
                .eq(Merchant::getStatus, current)
                .eq(Merchant::getDeleted, 0)
                .set(Merchant::getStatus, status));
        if (updated == 0) {
            throw new BizException(ErrorCode.MERCHANT_STATUS_INVALID, "商家状态已变更，请刷新后重试");
        }
        AuditAction action = status == MerchantStatus.DISABLED.code()
                ? AuditAction.DISABLE : AuditAction.ENABLE;
        insertAuditLog(id, current, status, action, operatorId, operatorName, null);
        log.info("merchant status changed merchantId={} from={} to={}", id, current, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCommission(Long id, BigDecimal commissionRate) {
        requireMerchant(id);
        BigDecimal normalized = normalizeRate(commissionRate);
        merchantMapper.update(null, new LambdaUpdateWrapper<Merchant>()
                .eq(Merchant::getId, id)
                .eq(Merchant::getDeleted, 0)
                .set(Merchant::getCommissionRate, normalized));
        log.info("merchant commission updated merchantId={} rate={}", id, normalized);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditSettlementAccount(Long id, Integer status) {
        if (status == null || (status != 1 && status != 2)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 1生效 或 2停用");
        }
        MerchantSettlementAccount account = settlementAccountMapper.selectById(id);
        if (account == null || !Objects.equals(account.getDeleted(), 0)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "结算账户不存在");
        }
        int current = account.getStatus() == null ? -1 : account.getStatus();
        boolean allowed = status == 1 ? current == 0 : (current == 0 || current == 1);
        if (!allowed) {
            throw new BizException(ErrorCode.MERCHANT_STATUS_INVALID, "结算账户状态不允许当前操作");
        }
        int updated = settlementAccountMapper.update(null, new LambdaUpdateWrapper<MerchantSettlementAccount>()
                .eq(MerchantSettlementAccount::getId, id)
                .eq(MerchantSettlementAccount::getStatus, current)
                .eq(MerchantSettlementAccount::getDeleted, 0)
                .set(MerchantSettlementAccount::getStatus, status));
        if (updated == 0) {
            throw new BizException(ErrorCode.MERCHANT_STATUS_INVALID, "结算账户状态已变更，请刷新后重试");
        }
        log.info("merchant settlement account audited accountId={} status={}", id, status);
    }

    private Merchant requireMerchant(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商家 ID 必须是正整数");
        }
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null || !Objects.equals(merchant.getDeleted(), 0)) {
            throw new BizException(ErrorCode.MERCHANT_NOT_FOUND);
        }
        return merchant;
    }

    private Long requireMerchantManagerRoleId() {
        PageResult<SysRoleView> roles = sysRoleService.page(USER_TYPE_MERCHANT, 0, 1, MAX_PAGE_SIZE);
        return roles.list().stream()
                .filter(role -> MERCHANT_MANAGER_ROLE.equals(role.roleCode()))
                .map(SysRoleView::id)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.SYSTEM_ERROR,
                        "缺少 " + MERCHANT_MANAGER_ROLE + " 角色，请检查 t_sys_role 种子数据"));
    }

    /**
     * 用户名默认取 merchant_no 小写；重名追加 _2 ~ _5。
     * 只做先查后建，并发下最终由 uk_tenant_username 兜底。
     */
    private String resolveAdminUsername(String requested, String merchantNo) {
        String base = StringUtils.hasText(requested)
                ? requested.trim()
                : merchantNo.toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(base).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "adminUsername 只能包含字母、数字、下划线、点、@ 与中划线，长度 3~64");
        }
        for (int i = 1; i <= 5; i++) {
            String candidate = i == 1 ? base : base + "_" + i;
            if (!usernameExists(candidate)) {
                return candidate;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "管理员用户名冲突，请手动指定");
    }

    private boolean usernameExists(String username) {
        PageResult<SysUserView> page = sysUserService.page(
                null, null, null, null, username, 1, MAX_PAGE_SIZE);
        return page.list().stream()
                .anyMatch(user -> user.username() != null && user.username().equalsIgnoreCase(username));
    }

    private String requirePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "initialPassword 不能为空");
        }
        if (password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            throw new BizException(ErrorCode.PARAM_INVALID, "initialPassword 长度必须是 8~64");
        }
        return password;
    }

    private BigDecimal normalizeRate(BigDecimal value) {
        BigDecimal rate = value == null ? new BigDecimal(defaultCommissionRate) : value;
        if (rate.compareTo(BigDecimal.ZERO) < 0
                || rate.compareTo(new BigDecimal("0.5000")) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "抽佣比例必须在 0.0000~0.5000");
        }
        return rate.setScale(4, RoundingMode.HALF_UP);
    }

    private int normalizeLevel(Integer level) {
        int value = level == null ? 1 : level;
        if (value < 1 || value > 5) {
            throw new BizException(ErrorCode.PARAM_INVALID, "merchantLevel 只能是 1~5");
        }
        return value;
    }

    private String sanitizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String value = keyword.replaceAll("[\\p{Cntrl}]", "").trim();
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private String optionalText(String value, int maxLength, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replaceAll("[\\p{Cntrl}]", " ").trim().replaceAll("\\s{2,}", " ");
        if (normalized.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private void insertAuditLog(Long merchantId, int fromStatus, int toStatus, AuditAction action,
                                Long operatorId, String operatorName, String remark) {
        MerchantAuditLog auditLog = new MerchantAuditLog();
        auditLog.setMerchantId(merchantId);
        auditLog.setFromStatus(fromStatus);
        auditLog.setToStatus(toStatus);
        auditLog.setAction(action.name());
        auditLog.setOperatorId(operatorId);
        auditLog.setOperatorName(operatorName);
        auditLog.setRemark(remark);
        auditLogMapper.insert(auditLog);
    }

    private MerchantView toView(Merchant merchant) {
        return new MerchantView(
                merchant.getId(),
                merchant.getMerchantNo(),
                merchant.getMerchantName(),
                merchant.getBrandName(),
                merchant.getCategoryId(),
                merchant.getContactName(),
                merchant.getContactPhone(),
                merchant.getContactEmail(),
                merchant.getProvince(),
                merchant.getCity(),
                merchant.getDistrict(),
                merchant.getAddress(),
                merchant.getStatus(),
                merchant.getCommissionRate(),
                merchant.getLevel(),
                merchant.getAuditAt(),
                merchant.getAuditRemark(),
                merchant.getCreatedAt());
    }

    private MerchantDetailView toDetail(Merchant merchant) {
        List<QualificationView> qualifications = qualificationMapper.selectList(
                        new LambdaQueryWrapper<MerchantQualification>()
                                .eq(MerchantQualification::getMerchantId, merchant.getId())
                                .eq(MerchantQualification::getDeleted, 0)
                                .orderByAsc(MerchantQualification::getQualType))
                .stream()
                .map(this::toQualificationView)
                .toList();
        List<AuditLogView> auditLogs = auditLogMapper.selectList(
                        new LambdaQueryWrapper<MerchantAuditLog>()
                                .eq(MerchantAuditLog::getMerchantId, merchant.getId())
                                .orderByDesc(MerchantAuditLog::getId))
                .stream()
                .map(this::toAuditLogView)
                .toList();
        return new MerchantDetailView(
                merchant.getId(),
                merchant.getMerchantNo(),
                merchant.getMerchantName(),
                merchant.getBrandName(),
                merchant.getCategoryId(),
                merchant.getContactName(),
                merchant.getContactPhone(),
                merchant.getContactEmail(),
                merchant.getBusinessLicenseNo(),
                merchant.getLegalPerson(),
                maskIdCardSafely(merchant.getLegalIdCardCipher()),
                merchant.getProvince(),
                merchant.getCity(),
                merchant.getDistrict(),
                merchant.getAddress(),
                merchant.getStatus(),
                merchant.getCommissionRate(),
                merchant.getLevel(),
                merchant.getAuditBy(),
                merchant.getAuditAt(),
                merchant.getAuditRemark(),
                merchant.getOpenedAt(),
                merchant.getRemark(),
                merchant.getCreatedAt(),
                merchant.getUpdatedAt(),
                qualifications,
                auditLogs);
    }

    private QualificationView toQualificationView(MerchantQualification qualification) {
        return new QualificationView(
                qualification.getId(),
                qualification.getQualType(),
                qualification.getFileId(),
                qualification.getFileUrl(),
                qualification.getFileName(),
                qualification.getExpireAt(),
                qualification.getStatus(),
                qualification.getAuditRemark(),
                qualification.getCreatedAt());
    }

    private AuditLogView toAuditLogView(MerchantAuditLog auditLog) {
        return new AuditLogView(
                auditLog.getId(),
                auditLog.getFromStatus(),
                auditLog.getToStatus(),
                auditLog.getAction(),
                auditLog.getOperatorId(),
                auditLog.getOperatorName(),
                auditLog.getRemark(),
                auditLog.getCreatedAt());
    }

    private String maskIdCardSafely(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            return null;
        }
        try {
            return piiCipher.maskIdCard(cipherText);
        } catch (RuntimeException ex) {
            // 密钥缺失或变更时不阻断详情查询，也不回显密文
            log.warn("merchant legal id card mask failed merchantId=? reason={}", ex.getMessage());
            return "****";
        }
    }
}
