package com.soloeats.marketing.service;

/**
 * 邀请有礼。邀请码由 userId 确定性编码，不落库。
 */
public interface InviteService {

    /** 返回当前用户固定不变的邀请码。 */
    String inviteCode(Long userId);

    /**
     * 绑定邀请关系。不能绑自己、不能重复绑定、邀请码必须能解出有效用户。
     * 无设备/IP 上下文，防刷降级为「同一邀请人 24h 内成功绑定 ≤ 10」。
     */
    void bind(Long userId, String inviteCode);

    /**
     * 被邀请人首单完成后结算：status 0→1，给邀请人发积分与成长值。
     *
     * @return 1 本次完成结算；0 记录不存在或已结算（幂等）
     */
    int settleReward(Long inviteeUserId);
}
