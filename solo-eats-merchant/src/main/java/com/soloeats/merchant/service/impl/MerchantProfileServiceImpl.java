package com.soloeats.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.merchant.domain.entity.FileRecord;
import com.soloeats.merchant.domain.entity.Merchant;
import com.soloeats.merchant.domain.entity.MerchantSettlementAccount;
import com.soloeats.merchant.mapper.FileRecordMapper;
import com.soloeats.merchant.mapper.MerchantMapper;
import com.soloeats.merchant.mapper.MerchantSettlementAccountMapper;
import com.soloeats.merchant.service.MerchantAdminService;
import com.soloeats.merchant.service.MerchantProfileService;
import com.soloeats.merchant.service.command.SaveSettlementAccountCommand;
import com.soloeats.merchant.service.command.UpdateMerchantProfileCommand;
import com.soloeats.merchant.service.view.MerchantDetailView;
import com.soloeats.merchant.service.view.SettlementAccountView;
import com.soloeats.merchant.support.PiiCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantProfileServiceImpl implements MerchantProfileService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern BANK_ACCOUNT_PATTERN = Pattern.compile("^\\d{8,32}$");
    private static final int MAX_ACCOUNT_NAME = 128;
    private static final int MAX_BANK_NAME = 128;
    private static final int MAX_BANK_BRANCH = 255;
    private static final int MAX_ALIPAY = 128;
    private static final int MAX_EMAIL = 128;

    private final MerchantMapper merchantMapper;
    private final MerchantSettlementAccountMapper settlementAccountMapper;
    private final FileRecordMapper fileRecordMapper;
    private final MerchantAdminService merchantAdminService;
    private final PiiCipher piiCipher;

    @Override
    public MerchantDetailView profile(Long merchantId) {
        return merchantAdminService.detail(requireMerchantId(merchantId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantDetailView updateProfile(Long merchantId, UpdateMerchantProfileCommand command) {
        Merchant merchant = requireMerchant(merchantId);
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        String contactName = optionalText(command.contactName(), 64, "contactName");
        String contactPhone = normalizePhone(command.contactPhone());
        String contactEmail = optionalText(command.contactEmail(), MAX_EMAIL, "contactEmail");
        String province = optionalText(command.province(), 64, "province");
        String city = optionalText(command.city(), 64, "city");
        String district = optionalText(command.district(), 64, "district");
        String address = optionalText(command.address(), 255, "address");

        // 商家只能改联系人/电话/邮箱/地址；merchant_no、status、commission_rate 不在更新列中
        merchantMapper.update(null, new LambdaUpdateWrapper<Merchant>()
                .eq(Merchant::getId, merchant.getId())
                .eq(Merchant::getDeleted, 0)
                .set(contactName != null, Merchant::getContactName, contactName)
                .set(contactPhone != null, Merchant::getContactPhone, contactPhone)
                .set(contactEmail != null, Merchant::getContactEmail, contactEmail)
                .set(province != null, Merchant::getProvince, province)
                .set(city != null, Merchant::getCity, city)
                .set(district != null, Merchant::getDistrict, district)
                .set(address != null, Merchant::getAddress, address));
        return merchantAdminService.detail(merchant.getId());
    }

    @Override
    public List<SettlementAccountView> listSettlementAccounts(Long merchantId) {
        Long id = requireMerchantId(merchantId);
        return settlementAccountMapper.selectList(new LambdaQueryWrapper<MerchantSettlementAccount>()
                        .eq(MerchantSettlementAccount::getMerchantId, id)
                        .eq(MerchantSettlementAccount::getDeleted, 0)
                        .orderByDesc(MerchantSettlementAccount::getIsDefault)
                        .orderByDesc(MerchantSettlementAccount::getId))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SettlementAccountView createSettlementAccount(Long merchantId, SaveSettlementAccountCommand command) {
        Long id = requireMerchantId(merchantId);
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        String accountName = requireText(command.accountName(), MAX_ACCOUNT_NAME, "accountName");
        String bankName = requireText(command.bankName(), MAX_BANK_NAME, "bankName");
        String bankBranch = optionalText(command.bankBranch(), MAX_BANK_BRANCH, "bankBranch");
        String bankAccountNo = normalizeBankAccount(command.bankAccountNo());
        String alipayAccount = optionalText(command.alipayAccount(), MAX_ALIPAY, "alipayAccount");

        if (command.effectiveFrom() != null && command.effectiveTo() != null
                && command.effectiveFrom().isAfter(command.effectiveTo())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "生效开始日期不能晚于结束日期");
        }

        Long contractFileId = command.contractFileId();
        String contractFileUrl = null;
        if (contractFileId != null) {
            FileRecord file = fileRecordMapper.selectById(contractFileId);
            if (file == null || !Objects.equals(file.getStatus(), 0)) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "合同附件不存在");
            }
            contractFileUrl = file.getUrl();
        }

        // 默认账户：该商家尚无默认账户时，新账户自动成为默认；唯一性由本事务内的先查后写保证
        Long defaultCount = settlementAccountMapper.selectCount(
                new LambdaQueryWrapper<MerchantSettlementAccount>()
                        .eq(MerchantSettlementAccount::getMerchantId, id)
                        .eq(MerchantSettlementAccount::getIsDefault, 1)
                        .eq(MerchantSettlementAccount::getDeleted, 0));
        int isDefault = (defaultCount == null || defaultCount == 0L) ? 1 : 0;

        MerchantSettlementAccount account = new MerchantSettlementAccount();
        account.setMerchantId(id);
        account.setAccountName(accountName);
        account.setBankName(bankName);
        account.setBankBranch(bankBranch);
        account.setBankAccountNoCipher(piiCipher.encrypt(bankAccountNo));
        account.setAlipayAccount(alipayAccount);
        account.setContractFileId(contractFileId);
        account.setContractFileUrl(contractFileUrl);
        account.setEffectiveFrom(command.effectiveFrom());
        account.setEffectiveTo(command.effectiveTo());
        account.setIsDefault(isDefault);
        account.setStatus(0);
        account.setDeleted(0);
        settlementAccountMapper.insert(account);

        log.info("merchant settlement account created merchantId={} accountId={}", id, account.getId());
        return toView(account);
    }

    private Merchant requireMerchant(Long merchantId) {
        Long id = requireMerchantId(merchantId);
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null || !Objects.equals(merchant.getDeleted(), 0)) {
            throw new BizException(ErrorCode.MERCHANT_NOT_FOUND);
        }
        return merchant;
    }

    private Long requireMerchantId(Long merchantId) {
        if (merchantId == null || merchantId <= 0L) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "令牌缺少 merchantId");
        }
        return merchantId;
    }

    private SettlementAccountView toView(MerchantSettlementAccount account) {
        return new SettlementAccountView(
                account.getId(),
                account.getAccountName(),
                account.getBankName(),
                account.getBankBranch(),
                maskBankAccountSafely(account.getBankAccountNoCipher()),
                account.getAlipayAccount(),
                account.getContractFileId(),
                account.getContractFileUrl(),
                account.getEffectiveFrom(),
                account.getEffectiveTo(),
                account.getIsDefault(),
                account.getStatus(),
                account.getCreatedAt());
    }

    private String maskBankAccountSafely(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            return null;
        }
        try {
            return piiCipher.maskBankAccount(cipherText);
        } catch (RuntimeException ex) {
            log.warn("merchant bank account mask failed reason={}", ex.getMessage());
            return "****";
        }
    }

    private String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        String value = toHalfWidthDigits(phone.replaceAll("\\s+", ""));
        if (!PHONE_PATTERN.matcher(value).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "手机号格式不正确");
        }
        return value;
    }

    /** 全角数字转半角，避免用户从表格粘贴时校验失败。 */
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

    private String normalizeBankAccount(String bankAccountNo) {
        if (!StringUtils.hasText(bankAccountNo)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bankAccountNo 不能为空");
        }
        String value = bankAccountNo.replaceAll("[\\s-]", "");
        if (!BANK_ACCOUNT_PATTERN.matcher(value).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "银行卡号必须是 8~32 位数字");
        }
        return value;
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
        String normalized = value.replaceAll("[\\p{Cntrl}]", " ").trim().replaceAll("\\s{2,}", " ");
        if (normalized.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 长度不能超过 " + maxLength);
        }
        return normalized;
    }
}
