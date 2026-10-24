package com.soloeats.member.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.member.service.query.MemberQuery;
import com.soloeats.member.service.view.AdminAddressView;
import com.soloeats.member.service.view.MemberDetailView;
import com.soloeats.member.service.view.MemberView;

import java.util.List;

/**
 * 后台会员管理：列表、详情、状态变更、地址查看。
 * 消费画像（order_count / total_amount / last_order_at）只读 t_member_user 自身列，
 * 不跨模块查订单表。
 */
public interface MemberAdminService {

    /** keyword 只按手机号后四位模糊匹配 phone_mask；pageSize 默认 20、上限 100。 */
    PageResult<MemberView> page(MemberQuery query, long pageNum, long pageSize);

    /** 用户不存在抛 80001；钱包缺失时余额按 0 返回。 */
    MemberDetailView detail(Long id);

    /** 只允许 0 正常 / 1 禁用 / 2 拉黑；条件更新 WHERE id = ? AND deleted = 0，未命中抛 80001。 */
    void updateStatus(Long id, Integer status);

    /** 后台地址列表，默认地址排首；用户不存在抛 80001。 */
    List<AdminAddressView> listAddresses(Long id);
}
