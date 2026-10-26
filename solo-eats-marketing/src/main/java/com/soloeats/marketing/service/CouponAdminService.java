package com.soloeats.marketing.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.marketing.service.command.CouponTemplateSaveCommand;
import com.soloeats.marketing.service.view.CouponGrantRecordView;
import com.soloeats.marketing.service.view.CouponTemplateView;

import java.util.List;

/**
 * 券模板后台管理。与 C 端 {@link CouponService}（lock/release/consume/rollback）
 * 完全分离：本接口只做模板读写与定向发券，不改动用户券的状态流转语义。
 */
public interface CouponAdminService {

    /**
     * 模板分页。pageNum 从 1 起，pageSize 默认 20、上限 100。
     *
     * @param name       模板名，模糊匹配，可空
     * @param status     0启用 1停用，可空
     * @param couponType 1满减 2折扣 3无门槛，可空
     * @param bearer     1平台 2商家，可空
     */
    PageResult<CouponTemplateView> page(String name, Integer status, Integer couponType,
                                        Integer bearer, long pageNum, long pageSize);

    /**
     * 模板详情。不存在抛 {@code COUPON_TEMPLATE_NOT_FOUND}。
     */
    CouponTemplateView detail(Long id);

    /**
     * 新建模板。issuedQty 固定 0、status 固定 0，不接受外部传入。
     */
    CouponTemplateView create(CouponTemplateSaveCommand command);

    /**
     * 编辑模板。只覆盖可编辑字段白名单；totalQty 不得小于已发放 issuedQty。
     */
    CouponTemplateView update(Long id, CouponTemplateSaveCommand command);

    /**
     * 启停模板。0启用 1停用。
     */
    void updateStatus(Long id, Integer status);

    /**
     * 定向发券。整批一个事务，任一张超出 totalQty 则整批回滚。
     *
     * @return 实际发放张数（userIds 已按去重后计数）
     */
    int grant(Long templateId, List<Long> userIds);

    /**
     * 某模板下的用户券记录分页。
     *
     * @param status 0未用 1锁定 2已用 3过期 4作废 5已退款，可空
     */
    PageResult<CouponGrantRecordView> records(Long templateId, Integer status,
                                              long pageNum, long pageSize);
}
