package com.soloeats.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.member.domain.entity.MemberAddress;
import com.soloeats.member.mapper.MemberAddressMapper;
import com.soloeats.member.service.MemberAddressService;
import com.soloeats.member.service.command.SaveAddressCommand;
import com.soloeats.member.service.view.AddressView;
import com.soloeats.member.service.view.OrderAddressView;
import com.soloeats.member.support.PhoneCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberAddressServiceImpl implements MemberAddressService {

    private static final int MAX_ADDRESS_COUNT = 20;
    private static final int DEFAULT_YES = 1;
    private static final int DEFAULT_NO = 0;

    private final MemberAddressMapper memberAddressMapper;
    private final PhoneCipher phoneCipher;

    @Override
    public List<AddressView> list(Long userId) {
        List<MemberAddress> addresses = memberAddressMapper.selectList(
                new LambdaQueryWrapper<MemberAddress>()
                        .eq(MemberAddress::getUserId, userId)
                        .orderByDesc(MemberAddress::getIsDefault)
                        .orderByDesc(MemberAddress::getId));
        return addresses.stream().map(AddressView::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressView create(Long userId, SaveAddressCommand command) {
        SaveAddressCommand cmd = requireCommand(command);
        long count = memberAddressMapper.selectCount(new LambdaQueryWrapper<MemberAddress>()
                .eq(MemberAddress::getUserId, userId));
        if (count >= MAX_ADDRESS_COUNT) {
            throw new BizException(ErrorCode.ADDRESS_LIMIT_EXCEEDED);
        }

        boolean makeDefault = Boolean.TRUE.equals(cmd.isDefault()) || count == 0;
        if (makeDefault) {
            memberAddressMapper.clearDefault(userId);
        }

        MemberAddress entity = new MemberAddress();
        entity.setUserId(userId);
        entity.setIsDefault(makeDefault ? DEFAULT_YES : DEFAULT_NO);
        entity.setDeleted(0);
        applyFields(entity, cmd);

        // V12 移除了 uk_user_phone_addr（不含 deleted，逻辑删除后无法复用同一地址），
        // 改为应用层只比对未删除记录；@TableLogic 会自动追加 deleted = 0。
        long duplicated = memberAddressMapper.selectCount(new LambdaQueryWrapper<MemberAddress>()
                .eq(MemberAddress::getUserId, userId)
                .eq(MemberAddress::getReceiverPhoneMask, entity.getReceiverPhoneMask())
                .eq(MemberAddress::getAddress, entity.getAddress()));
        if (duplicated > 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该收货地址已存在");
        }

        memberAddressMapper.insert(entity);

        log.info("member address created userId={} addressId={} isDefault={}",
                userId, entity.getId(), makeDefault);
        return AddressView.from(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressView update(Long userId, Long id, SaveAddressCommand command) {
        SaveAddressCommand cmd = requireCommand(command);
        requireOwned(userId, id);

        boolean makeDefault = Boolean.TRUE.equals(cmd.isDefault());
        boolean removeDefault = Boolean.FALSE.equals(cmd.isDefault());
        if (makeDefault) {
            memberAddressMapper.clearDefault(userId);
        }

        MemberAddress patch = new MemberAddress();
        patch.setId(id);
        applyFields(patch, cmd);
        if (makeDefault) {
            patch.setIsDefault(DEFAULT_YES);
        } else if (removeDefault) {
            patch.setIsDefault(DEFAULT_NO);
        }

        // 条件更新同时带 id 与 user_id，杜绝只按 id 更新导致越权覆盖
        int rows;
        try {
            rows = memberAddressMapper.update(patch, new LambdaUpdateWrapper<MemberAddress>()
                    .eq(MemberAddress::getId, id)
                    .eq(MemberAddress::getUserId, userId));
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该收货地址已存在");
        }
        if (rows == 0) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN);
        }

        log.info("member address updated userId={} addressId={} isDefault={}",
                userId, id, makeDefault);
        MemberAddress updated = memberAddressMapper.selectById(id);
        return AddressView.from(updated == null ? patch : updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        MemberAddress existing = requireOwned(userId, id);
        int rows = memberAddressMapper.deleteById(id);
        if (rows == 0) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN);
        }

        boolean wasDefault = existing.getIsDefault() != null && existing.getIsDefault() == DEFAULT_YES;
        if (wasDefault) {
            promoteLatestRemaining(userId);
        }
        log.info("member address deleted userId={} addressId={} wasDefault={}", userId, id, wasDefault);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long id) {
        requireOwned(userId, id);
        memberAddressMapper.clearDefault(userId);

        MemberAddress patch = new MemberAddress();
        patch.setIsDefault(DEFAULT_YES);
        int rows = memberAddressMapper.update(patch, new LambdaUpdateWrapper<MemberAddress>()
                .eq(MemberAddress::getId, id)
                .eq(MemberAddress::getUserId, userId));
        if (rows == 0) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN);
        }
        log.info("member address set default userId={} addressId={}", userId, id);
    }

    @Override
    public OrderAddressView resolveForOrder(Long userId, Long id) {
        MemberAddress address = requireOwned(userId, id);
        return new OrderAddressView(
                address.getId(),
                address.getReceiverName(),
                phoneCipher.decrypt(address.getReceiverPhoneCipher()),
                fullAddress(address),
                address.getLng(),
                address.getLat());
    }

    /** 省市区 + 详细地址 + 门牌号拼成配送地址，空段跳过。 */
    private static String fullAddress(MemberAddress address) {
        StringBuilder builder = new StringBuilder();
        for (String part : List.of(
                Objects.requireNonNullElse(address.getProvince(), ""),
                Objects.requireNonNullElse(address.getCity(), ""),
                Objects.requireNonNullElse(address.getDistrict(), ""),
                Objects.requireNonNullElse(address.getAddress(), ""))) {
            if (!part.isBlank()) {
                builder.append(part.strip());
            }
        }
        if (address.getHouseNumber() != null && !address.getHouseNumber().isBlank()) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(address.getHouseNumber().strip());
        }
        return builder.toString();
    }

    /** 删除默认地址后，把剩余最新一条提升为默认；没有剩余地址则不补。 */
    private void promoteLatestRemaining(Long userId) {
        MemberAddress next = memberAddressMapper.selectOne(new LambdaQueryWrapper<MemberAddress>()
                .eq(MemberAddress::getUserId, userId)
                .orderByDesc(MemberAddress::getId)
                .last("LIMIT 1"));
        if (next == null) {
            return;
        }
        memberAddressMapper.clearDefault(userId);
        MemberAddress patch = new MemberAddress();
        patch.setIsDefault(DEFAULT_YES);
        memberAddressMapper.update(patch, new LambdaUpdateWrapper<MemberAddress>()
                .eq(MemberAddress::getId, next.getId())
                .eq(MemberAddress::getUserId, userId));
        log.info("member address default promoted userId={} addressId={}", userId, next.getId());
    }

    private MemberAddress requireOwned(Long userId, Long id) {
        MemberAddress address = id == null ? null : memberAddressMapper.selectById(id);
        if (address == null || !Objects.equals(address.getUserId(), userId)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN);
        }
        return address;
    }

    private SaveAddressCommand requireCommand(SaveAddressCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "地址参数不能为空");
        }
        return command;
    }

    /** 外部输入归一 + 手机号加密；密文与掩码同时写入，明文不入库。 */
    private void applyFields(MemberAddress entity, SaveAddressCommand cmd) {
        String receiverPhone = phoneCipher.normalize(cmd.receiverPhone());
        phoneCipher.requireValid(receiverPhone);

        entity.setReceiverName(requireText(cmd.receiverName(), "收货人不能为空"));
        entity.setReceiverPhoneCipher(phoneCipher.encrypt(receiverPhone));
        entity.setReceiverPhoneMask(phoneCipher.mask(receiverPhone));
        entity.setProvince(clean(cmd.province()));
        entity.setCity(clean(cmd.city()));
        entity.setDistrict(clean(cmd.district()));
        entity.setAddress(requireText(cmd.address(), "详细地址不能为空"));
        entity.setHouseNumber(clean(cmd.houseNumber()));
        entity.setLng(cmd.lng());
        entity.setLat(cmd.lat());
        entity.setTag(clean(cmd.tag()));
    }

    private static String requireText(String value, String message) {
        String cleaned = clean(value);
        if (cleaned == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, message);
        }
        return cleaned;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[\\r\\n\\t]", " ").strip();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
