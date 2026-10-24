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

    Rider requireRider(Long riderId);

    /** 健康证到期日 >= today 才有效；null 视为未提交，返回 false。 */
    boolean isHealthCertValid(LocalDate healthCertExpireAt, LocalDate today);
}
