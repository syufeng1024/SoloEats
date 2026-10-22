package com.soloeats.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.merchant.domain.entity.FileRecord;
import com.soloeats.merchant.domain.entity.Merchant;
import com.soloeats.merchant.domain.entity.MerchantAuditLog;
import com.soloeats.merchant.domain.entity.MerchantQualification;
import com.soloeats.merchant.domain.enums.AuditAction;
import com.soloeats.merchant.domain.enums.MerchantStatus;
import com.soloeats.merchant.domain.enums.QualType;
import com.soloeats.merchant.mapper.FileRecordMapper;
import com.soloeats.merchant.mapper.MerchantAuditLogMapper;
import com.soloeats.merchant.mapper.MerchantMapper;
import com.soloeats.merchant.mapper.MerchantQualificationMapper;
import com.soloeats.merchant.service.MerchantApplyService;
import com.soloeats.merchant.service.command.AddQualificationCommand;
import com.soloeats.merchant.service.command.ApplyMerchantCommand;
import com.soloeats.merchant.service.command.UpdateMerchantApplyCommand;
import com.soloeats.merchant.service.view.ApplyResult;
import com.soloeats.merchant.service.view.MerchantProgressView;
import com.soloeats.merchant.support.ApplyTokenService;
import com.soloeats.merchant.support.PiiCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantApplyServiceImpl implements MerchantApplyService {

    private static final String MOCK_SMS_CODE = "000000";
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^\\d{17}[\\dXx]$");
    private static final DateTimeFormatter NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int MAX_MERCHANT_NAME = 128;
    private static final int MAX_CONTACT_NAME = 64;
    private static final int MAX_REMARK = 512;

    private final MerchantMapper merchantMapper;
    private final MerchantQualificationMapper qualificationMapper;
    private final MerchantAuditLogMapper auditLogMapper;
    private final FileRecordMapper fileRecordMapper;
    private final ApplyTokenService applyTokenService;
    private final PiiCipher piiCipher;

    @Value("${app.auth.mock-sms-enabled:true}")
    private boolean mockSmsEnabled;

    @Value("${app.settlement.default-commission-rate:0.0600}")
    private String defaultCommissionRate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplyResult apply(ApplyMerchantCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        String merchantName = requireText(command.merchantName(), MAX_MERCHANT_NAME, "merchantName");
        String brandName = optionalText(command.brandName(), MAX_MERCHANT_NAME, "brandName");
        String contactName = requireText(command.contactName(), MAX_CONTACT_NAME, "contactName");
        String contactPhone = normalizePhone(command.contactPhone());
        verifySmsCode(command.smsCode());

        Long active = merchantMapper.selectCount(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getContactPhone, contactPhone)
                .in(Merchant::getStatus, MerchantStatus.DRAFT.code(), MerchantStatus.PENDING.code())
                .eq(Merchant::getDeleted, 0));
        if (active != null && active > 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该手机号已有进行中的入驻申请");
        }

        Merchant merchant = new Merchant();
        merchant.setMerchantNo(generateMerchantNo());
        merchant.setMerchantName(merchantName);
        merchant.setBrandName(brandName);
        merchant.setCategoryId(command.categoryId());
        merchant.setContactName(contactName);
        merchant.setContactPhone(contactPhone);
        merchant.setStatus(MerchantStatus.DRAFT.code());
        merchant.setCommissionRate(normalizeRate(defaultCommissionRate));
        merchant.setLevel(1);
        merchant.setDeleted(0);
        merchant.setVersion(0);
        merchantMapper.insert(merchant);

        String token = applyTokenService.issue(merchant.getId());
        log.info("merchant apply created applyId={} merchantNo={}", merchant.getId(), merchant.getMerchantNo());
        return new ApplyResult(merchant.getId(), merchant.getMerchantNo(), token, applyTokenService.expireSeconds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantProgressView updateApply(Long applyId, String applyToken,
                                            UpdateMerchantApplyCommand command) {
        Merchant merchant = requireApply(applyId, applyToken);
        assertSubmittable(merchant.getStatus());
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }

        String businessLicenseNo = optionalText(command.businessLicenseNo(), 64, "businessLicenseNo");
        String legalPerson = optionalText(command.legalPerson(), 64, "legalPerson");
        String legalIdCardCipher = null;
        if (StringUtils.hasText(command.legalIdCard())) {
            String idCard = normalizeIdCard(command.legalIdCard());
            legalIdCardCipher = piiCipher.encrypt(idCard);
        }
        String province = optionalText(command.province(), 64, "province");
        String city = optionalText(command.city(), 64, "city");
        String district = optionalText(command.district(), 64, "district");
        String address = optionalText(command.address(), 255, "address");

        LambdaUpdateWrapper<Merchant> wrapper = new LambdaUpdateWrapper<Merchant>()
                .eq(Merchant::getId, applyId)
                .in(Merchant::getStatus, MerchantStatus.DRAFT.code(), MerchantStatus.REJECTED.code())
                .eq(Merchant::getDeleted, 0)
                .set(businessLicenseNo != null, Merchant::getBusinessLicenseNo, businessLicenseNo)
                .set(legalPerson != null, Merchant::getLegalPerson, legalPerson)
                .set(legalIdCardCipher != null, Merchant::getLegalIdCardCipher, legalIdCardCipher)
                .set(province != null, Merchant::getProvince, province)
                .set(city != null, Merchant::getCity, city)
                .set(district != null, Merchant::getDistrict, district)
                .set(address != null, Merchant::getAddress, address);
        if (merchantMapper.update(null, wrapper) == 0) {
            throw new BizException(ErrorCode.MERCHANT_STATUS_INVALID, "当前状态不允许修改资料");
        }
        // 修改资料后不改变状态；返回最新进度
        Merchant latest = requireMerchant(applyId);
        return toProgress(latest);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addQualification(Long applyId, String applyToken, AddQualificationCommand command) {
        Merchant merchant = requireApply(applyId, applyToken);
        assertSubmittable(merchant.getStatus());
        if (command == null || command.qualType() == null || command.fileId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "qualType 与 fileId 不能为空");
        }
        QualType qualType = QualType.fromCode(command.qualType());

        FileRecord file = fileRecordMapper.selectById(command.fileId());
        if (file == null || !Objects.equals(file.getStatus(), 0)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在");
        }
        // 申请阶段上传的文件绑定 applyId，禁止拿他人文件充抵资质
        if (!"MERCHANT_QUAL".equals(file.getBizType())
                || !"APPLY".equals(file.getUploaderType())
                || !Objects.equals(file.getBizId(), applyId)) {
            throw new BizException(ErrorCode.MERCHANT_APPLY_FORBIDDEN, "文件不属于当前申请");
        }

        MerchantQualification existing = qualificationMapper.selectOne(
                new LambdaQueryWrapper<MerchantQualification>()
                        .eq(MerchantQualification::getMerchantId, applyId)
                        .eq(MerchantQualification::getQualType, qualType.code())
                        .eq(MerchantQualification::getDeleted, 0)
                        .last("LIMIT 1"));

        if (existing == null) {
            MerchantQualification qualification = new MerchantQualification();
            qualification.setMerchantId(applyId);
            qualification.setQualType(qualType.code());
            qualification.setFileId(file.getId());
            qualification.setFileUrl(file.getUrl());
            qualification.setFileName(file.getOriginalName());
            qualification.setStatus(0);
            qualification.setDeleted(0);
            qualificationMapper.insert(qualification);
        } else {
            existing.setFileId(file.getId());
            existing.setFileUrl(file.getUrl());
            existing.setFileName(file.getOriginalName());
            existing.setStatus(0);
            existing.setAuditRemark(null);
            qualificationMapper.updateById(existing);
        }
        log.info("merchant qualification saved applyId={} qualType={} fileId={}",
                applyId, qualType.code(), file.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long applyId, String applyToken) {
        Merchant merchant = requireApply(applyId, applyToken);
        assertSubmittable(merchant.getStatus());

        if (!StringUtils.hasText(merchant.getBusinessLicenseNo())
                || !StringUtils.hasText(merchant.getLegalPerson())
                || !StringUtils.hasText(merchant.getLegalIdCardCipher())) {
            throw new BizException(ErrorCode.MERCHANT_QUALIFICATION_MISSING,
                    "营业执照号、法人姓名与法人身份证必须填写完整");
        }

        Set<Integer> uploadedTypes = qualificationMapper.selectList(
                        new LambdaQueryWrapper<MerchantQualification>()
                                .eq(MerchantQualification::getMerchantId, applyId)
                                .eq(MerchantQualification::getDeleted, 0))
                .stream()
                .map(MerchantQualification::getQualType)
                .collect(Collectors.toSet());
        if (!uploadedTypes.containsAll(QualType.REQUIRED)) {
            throw new BizException(ErrorCode.MERCHANT_QUALIFICATION_MISSING,
                    "请上传营业执照、食品经营许可证、法人身份证正反面");
        }

        int fromStatus = merchant.getStatus();
        int updated = merchantMapper.update(null, new LambdaUpdateWrapper<Merchant>()
                .eq(Merchant::getId, applyId)
                .in(Merchant::getStatus, MerchantStatus.DRAFT.code(), MerchantStatus.REJECTED.code())
                .eq(Merchant::getDeleted, 0)
                .set(Merchant::getStatus, MerchantStatus.PENDING.code())
                .set(Merchant::getAuditRemark, null)
                .set(Merchant::getAuditBy, null)
                .set(Merchant::getAuditAt, null));
        if (updated == 0) {
            throw new BizException(ErrorCode.MERCHANT_STATUS_INVALID);
        }
        insertAuditLog(applyId, fromStatus, MerchantStatus.PENDING.code(),
                AuditAction.SUBMIT, null, "APPLY", "提交入驻审核");
        log.info("merchant apply submitted applyId={} fromStatus={}", applyId, fromStatus);
    }

    @Override
    public MerchantProgressView progress(Long applyId, String applyToken) {
        Merchant merchant = requireApply(applyId, applyToken);
        return toProgress(merchant);
    }

    private Merchant requireApply(Long applyId, String applyToken) {
        Long tokenApplyId = applyTokenService.verify(applyToken);
        if (applyId == null || !Objects.equals(applyId, tokenApplyId)) {
            // applyToken 只能访问自己的申请
            throw new BizException(ErrorCode.MERCHANT_APPLY_FORBIDDEN);
        }
        return requireMerchant(applyId);
    }

    private Merchant requireMerchant(Long applyId) {
        Merchant merchant = merchantMapper.selectById(applyId);
        if (merchant == null || !Objects.equals(merchant.getDeleted(), 0)) {
            throw new BizException(ErrorCode.MERCHANT_NOT_FOUND);
        }
        return merchant;
    }

    private void assertSubmittable(Integer status) {
        if (!MerchantStatus.canSubmit(status)) {
            throw new BizException(ErrorCode.MERCHANT_STATUS_INVALID,
                    "只有草稿或已驳回的申请可以修改/提交");
        }
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

    private MerchantProgressView toProgress(Merchant merchant) {
        return new MerchantProgressView(
                merchant.getId(),
                merchant.getMerchantNo(),
                merchant.getMerchantName(),
                merchant.getBrandName(),
                merchant.getStatus(),
                merchant.getStatus() != null && merchant.getStatus() == MerchantStatus.REJECTED.code()
                        ? merchant.getAuditRemark() : null,
                merchant.getUpdatedAt());
    }

    private void verifySmsCode(String smsCode) {
        // TODO(T06): 接入短信验证码校验后，本分支改为调用 T06 的 SmsCodeService
        if (!mockSmsEnabled) {
            throw new BizException(ErrorCode.PARAM_INVALID, "短信验证码校验尚未接入（T06）");
        }
        if (!MOCK_SMS_CODE.equals(smsCode == null ? null : smsCode.trim())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "验证码错误");
        }
    }

    private String generateMerchantNo() {
        for (int i = 0; i < 5; i++) {
            String candidate = "M" + LocalDateTime.now().format(NO_FORMATTER)
                    + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
            Long count = merchantMapper.selectCount(new LambdaQueryWrapper<Merchant>()
                    .eq(Merchant::getMerchantNo, candidate));
            if (count == null || count == 0L) {
                return candidate;
            }
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR, "商家编号生成失败，请重试");
    }

    private BigDecimal normalizeRate(String value) {
        try {
            BigDecimal rate = new BigDecimal(value);
            if (rate.compareTo(BigDecimal.ZERO) < 0
                    || rate.compareTo(new BigDecimal("0.5000")) > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "抽佣比例必须在 0.0000~0.5000");
            }
            return rate.setScale(4, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "抽佣比例格式错误");
        }
    }

    private String requireText(String value, int maxLength, String field) {
        String normalized = optionalText(value, maxLength, field);
        if (normalized == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空");
        }
        return normalized;
    }

    private String optionalText(String value, int maxLength, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        // 去控制字符与首尾空白，避免把换行写进名称/地址
        String normalized = value.replaceAll("[\\p{Cntrl}]", " ").trim().replaceAll("\\s{2,}", " ");
        if (normalized.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "contactPhone 不能为空");
        }
        String value = toHalfWidthDigits(phone.replaceAll("\\s+", ""));
        if (!PHONE_PATTERN.matcher(value).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "手机号格式不正确");
        }
        return value;
    }

    private String normalizeIdCard(String idCard) {
        String value = idCard.replaceAll("\\s+", "").toUpperCase();
        if (!ID_CARD_PATTERN.matcher(value).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "法人身份证号格式不正确");
        }
        return value;
    }

    private String toHalfWidthDigits(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (char ch : value.toCharArray()) {
            if (ch >= '０' && ch <= '９') {
                builder.append((char) (ch - '０' + '0'));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }
}
