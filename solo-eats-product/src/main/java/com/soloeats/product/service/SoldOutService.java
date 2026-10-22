package com.soloeats.product.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.product.service.command.SoldOutCommand;
import com.soloeats.product.service.view.ReasonStatView;
import com.soloeats.product.service.view.SoldOutRecordView;

import java.time.LocalDateTime;
import java.util.List;

public interface SoldOutService {

    /**
     * 批量估清：t_sku_stock.sold_out=1 + t_shop_spu.status=2 + t_sold_out_record，同一事务。
     */
    void markSoldOut(Long merchantId, SoldOutCommand command, Long operatorId, String operatorName);

    /**
     * 批量恢复：三步反向，重复恢复幂等。
     */
    void restore(Long merchantId, SoldOutCommand command, Long operatorId, String operatorName);

    void markBySpu(Long shopId, Long spuId, String reason, Long operatorId, String operatorName);

    void restoreBySpu(Long shopId, Long spuId, Long operatorId, String operatorName);

    PageResult<SoldOutRecordView> adminPage(Long shopId, Long skuId, String reason,
                                            LocalDateTime start, LocalDateTime end,
                                            long pageNum, long pageSize);

    List<ReasonStatView> reasonStats(Long shopId, LocalDateTime start, LocalDateTime end);
}
