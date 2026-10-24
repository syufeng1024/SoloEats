package com.soloeats.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.member.domain.entity.MemberAddress;
import com.soloeats.member.domain.entity.MemberUser;
import com.soloeats.member.domain.entity.MemberWallet;
import com.soloeats.member.mapper.MemberAddressMapper;
import com.soloeats.member.mapper.MemberTagMapper;
import com.soloeats.member.mapper.MemberUserMapper;
import com.soloeats.member.mapper.MemberWalletMapper;
import com.soloeats.member.service.MemberAdminService;
import com.soloeats.member.service.query.MemberQuery;
import com.soloeats.member.service.view.AdminAddressView;
import com.soloeats.member.service.view.MemberDetailView;
import com.soloeats.member.service.view.MemberView;
import com.soloeats.member.support.PhoneCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 后台会员管理。手机号检索只走 phone_mask 的后四位，
 * 任何入口都不拼接 phone_cipher / phone_hash，也不返回明文。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberAdminServiceImpl implements MemberAdminService {

    private static final int STATUS_NORMAL = 0;
    private static final int STATUS_BLOCKED = 2;
    private static final int PHONE_SUFFIX_LENGTH = 4;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private final MemberUserMapper memberUserMapper;
    private final MemberAddressMapper memberAddressMapper;
    private final MemberWalletMapper memberWalletMapper;
    private final MemberTagMapper memberTagMapper;
    private final PhoneCipher phoneCipher;

    @Override
    public PageResult<MemberView> page(MemberQuery query, long pageNum, long pageSize) {
        MemberQuery condition = query == null ? MemberQuery.empty() : query;
        validateStatus(condition.status());
        String phoneSuffix = normalizePhoneSuffix(condition.keyword());

        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = pageSize < 1L ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        Page<MemberUser> page = memberUserMapper.selectAdminPage(
                new Page<>(normalizedPageNum, normalizedPageSize),
                condition.status(),
                phoneSuffix,
                condition.levelId());
        List<MemberView> views = page.getRecords().stream().map(this::toView).toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public MemberDetailView detail(Long id) {
        MemberUser user = requireUser(id);

        long addressCount = memberAddressMapper.selectCount(new LambdaQueryWrapper<MemberAddress>()
                .eq(MemberAddress::getUserId, id));

        MemberWallet wallet = memberWalletMapper.selectOne(new LambdaQueryWrapper<MemberWallet>()
                .eq(MemberWallet::getUserId, id)
                .last("LIMIT 1"));
        long walletBalance = wallet == null || wallet.getBalance() == null ? 0L : wallet.getBalance();

        List<String> tagNames = memberTagMapper.findTagNamesByUserId(id);
        return MemberDetailView.of(toView(user), addressCount, walletBalance, tagNames);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        if (status == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 不能为空");
        }
        validateStatus(status);
        if (id == null) {
            throw new BizException(ErrorCode.MEMBER_NOT_FOUND);
        }

        int rows = memberUserMapper.updateStatus(id, status);
        if (rows == 0) {
            // deleted = 0 未命中：用户不存在或已被逻辑删除
            throw new BizException(ErrorCode.MEMBER_NOT_FOUND);
        }
        log.info("member status updated userId={} status={}", id, status);
    }

    @Override
    public List<AdminAddressView> listAddresses(Long id) {
        requireUser(id);
        List<MemberAddress> addresses = memberAddressMapper.selectList(
                new LambdaQueryWrapper<MemberAddress>()
                        .eq(MemberAddress::getUserId, id)
                        .orderByDesc(MemberAddress::getIsDefault)
                        .orderByDesc(MemberAddress::getId));
        return addresses.stream().map(AdminAddressView::from).toList();
    }

    /**
     * keyword 只保留数字并截取后四位，过滤掉 * 与分隔符。
     * 传入完整手机号时也只取后四位，绝不把 11 位明文带进 SQL。
     */
    private static String normalizePhoneSuffix(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String digits = keyword.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "keyword 只能是手机号后四位数字");
        }
        return digits.length() > PHONE_SUFFIX_LENGTH
                ? digits.substring(digits.length() - PHONE_SUFFIX_LENGTH)
                : digits;
    }

    private static void validateStatus(Integer status) {
        if (status != null && (status < STATUS_NORMAL || status > STATUS_BLOCKED)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0 正常 / 1 禁用 / 2 拉黑");
        }
    }

    private MemberUser requireUser(Long id) {
        MemberUser user = id == null ? null : memberUserMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.MEMBER_NOT_FOUND);
        }
        return user;
    }

    /** phone 只取掩码；历史数据只有密文时解密后再掩码，明文不离开本方法。 */
    private String maskedPhone(MemberUser user) {
        if (StringUtils.hasText(user.getPhoneMask())) {
            return user.getPhoneMask();
        }
        if (StringUtils.hasText(user.getPhoneCipher())) {
            return phoneCipher.mask(phoneCipher.decrypt(user.getPhoneCipher()));
        }
        return null;
    }

    private MemberView toView(MemberUser user) {
        return new MemberView(
                user.getId(),
                maskedPhone(user),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getGender(),
                user.getBirthday(),
                user.getStatus(),
                user.getOrderCount(),
                user.getTotalAmount(),
                user.getLastOrderAt(),
                user.getCancelApplyAt(),
                user.getCreatedAt());
    }
}
