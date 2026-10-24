package com.soloeats.member.service;

import com.soloeats.member.service.command.SaveAddressCommand;
import com.soloeats.member.service.view.AddressView;

import java.util.List;

/**
 * C 端收货地址簿。所有按 id 的操作都必须校验地址归属，越权抛 10004。
 */
public interface MemberAddressService {

    /** 默认地址排首，其余按 id 倒序。 */
    List<AddressView> list(Long userId);

    /** 新增地址；上限 20 条（含未删除），首条自动默认。 */
    AddressView create(Long userId, SaveAddressCommand command);

    /** 修改地址；isDefault=true 时同事务清除旧默认。 */
    AddressView update(Long userId, Long id, SaveAddressCommand command);

    /** 逻辑删除；删除默认地址后把剩余最新一条提升为默认。 */
    void delete(Long userId, Long id);

    /** 设为默认；同事务清除该用户其余默认标记。 */
    void setDefault(Long userId, Long id);
}
