package com.soloeats.marketing.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.marketing.service.view.PointAccountView;
import com.soloeats.marketing.service.view.PointLogView;

import java.time.LocalDate;

/**
 * 积分账户。与成长值完全分离：本接口不读写 t_member_growth_log。
 *
 * <p>所有加减都走 {@code PointAccountMapper#changeBalance} 的条件更新，
 * 幂等由 t_point_log 的 uk_biz(source, biz_id) 兜底。
 */
public interface PointService {

    /** 账户余额；不存在时初始化余额 0。 */
    PointAccountView account(Long userId);

    /** 积分明细，按 id 倒序分页。 */
    PageResult<PointLogView> logs(Long userId, long pageNum, long pageSize);

    /** 增加积分。同一 (source, bizId) 重复调用不再重复加。 */
    void earn(Long userId, Integer source, Long bizId, int point, String remark);

    /** 消耗积分。余额不足抛 40206 POINT_NOT_ENOUGH。 */
    void consume(Long userId, Integer source, Long bizId, int point, String remark);

    /**
     * 扫描 {@code expire_at < date + 1 天} 的正向流水，生成 source=6 的负数流水。
     * 返回本次生成的过期流水条数；同一批次重复执行为 0 条。
     */
    int expirePoints(LocalDate date);
}
