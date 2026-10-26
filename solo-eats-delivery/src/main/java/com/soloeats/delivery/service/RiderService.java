package com.soloeats.delivery.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.delivery.domain.entity.Rider;
import com.soloeats.delivery.service.command.RiderQuery;
import com.soloeats.delivery.service.command.SubmitCertificationCommand;
import com.soloeats.delivery.service.view.MyCertificationView;
import com.soloeats.delivery.service.view.RiderDetailView;
import com.soloeats.delivery.service.view.RiderView;

import java.time.LocalDate;

public interface RiderService {

    /**
     * 提交实名认证：首次创建骑手档案（rider_no = R + 6 位序号），
     * 每次提交新增一条待审认证记录；已通过（cert_status=2）时拒绝重复提交。
     */
    Long submitCertification(Long userId, SubmitCertificationCommand command);

    /** 骑手端认证状态；未创建档案时 certStatus 返回 0。 */
    MyCertificationView myCertification(Long userId);

    /**
     * 同上，但允许在按账号反查不到档案时回落到令牌里的 riderId。
     * 用于 rider1 / rider001 这类「同一骑手多个登录账号」的别名口径。
     */
    MyCertificationView myCertification(Long userId, Long riderIdClaim);

    PageResult<RiderView> adminPage(RiderQuery query, long pageNum, long pageSize);

    /** 档案 + 全部认证记录 + 今日在线时长（秒）。 */
    RiderDetailView adminDetail(Long riderId);

    /**
     * 审核最新一条认证记录。status=1 通过 -> t_rider.cert_status=2；
     * status=2 驳回 -> cert_status=3 并回写 cert_remark。
     */
    void audit(Long riderId, Integer status, String remark, Long operatorId);

    /** 0正常 1停用 2拉黑；非正常状态强制下线。 */
    void updateStatus(Long riderId, Integer status);

    void updateLimits(Long riderId, Integer maxConcurrentOrders, Integer pickupRadiusM);

    /** 按登录账号反查骑手档案，找不到抛 10002。 */
    Long requireRiderIdByUserId(Long userId);

    /**
     * 解析当前登录骑手对应的 t_rider.id。
     *
     * <p>口径：**以按登录账号 user_id 反查为准**（数据库是唯一事实来源），
     * 只有反查不到时才回落到令牌里的 riderId。这样骑手自己提交认证建了新档案后立刻生效，
     * 不会被登录时签发的旧 riderId 覆盖。
     *
     * <p>回落存在的原因：V4 种子把 rider1(id=3) 与 rider001(id=5) 定义为「同一身份的兼容别名」，
     * 两者 t_sys_user.rider_id 都是 1，但 t_rider.user_id 唯一，只能挂其中一个账号。
     * 不做回落时另一个账号登录后全部骑手接口报 10004。
     *
     * @param userId       令牌里的 t_sys_user.id
     * @param riderIdClaim 令牌里的 riderId，可为空
     * @throws com.soloeats.common.exception.BizException 10002 两处都取不到档案
     */
    Long resolveRiderId(Long userId, Long riderIdClaim);

    Rider requireRider(Long riderId);

    /** 健康证到期日 >= today 才有效；null 视为未提交，返回 false。 */
    boolean isHealthCertValid(LocalDate healthCertExpireAt, LocalDate today);
}
