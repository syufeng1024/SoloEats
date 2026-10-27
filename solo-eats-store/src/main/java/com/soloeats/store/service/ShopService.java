package com.soloeats.store.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.store.service.command.SaveBusinessHourCommand;
import com.soloeats.store.service.command.SaveShopCommand;
import com.soloeats.store.service.view.ShopDetailView;
import com.soloeats.store.service.view.ShopView;

import java.math.BigDecimal;
import java.util.List;

public interface ShopService {

    PageResult<ShopView> adminPage(Long merchantId, Integer status, String city, String keyword,
                                   long pageNum, long pageSize);

    /**
     * 商家侧门店列表。operatorShopId 非空（门店账号登录）时只返回该门店。
     */
    List<ShopView> listByMerchant(Long merchantId, Long operatorShopId);

    ShopDetailView detail(Long shopId);

    ShopDetailView createForAdmin(SaveShopCommand command);

    ShopDetailView createForMerchant(Long merchantId, SaveShopCommand command);

    /**
     * 编辑门店。operatorMerchantId 为空表示平台操作，可改 merchantId；
     * 商家侧不允许改 merchantId / rating / month_sales。
     */
    ShopDetailView update(Long shopId, SaveShopCommand command, Long operatorMerchantId);

    /**
     * 人工开关。商家只能切换 0/1/2，平台可设置 3停用。
     */
    void updateStatus(Long shopId, Integer status, Long operatorMerchantId);

    void updatePickup(Long shopId, SaveShopCommand.Pickup pickup, Long operatorMerchantId);

    /**
     * 全量替换营业时段，跨天由 endTime &lt; startTime 自动识别。
     */
    void saveBusinessHours(Long shopId, List<SaveBusinessHourCommand> hours, Long operatorMerchantId);

    /**
     * 商家/门店越权校验：门店必须属于 merchantId；带 operatorShopId 时还必须是该门店。
     */
    void assertMerchantShop(Long shopId, Long merchantId, Long operatorShopId);

    /**
     * 营业状态：status（老板意愿）与营业时段（规则）分离，canOrder 是二者合成结论。
     */
    BusinessStatus businessStatus(Long shopId);

    record BusinessStatus(
            boolean canOrder,
            Integer status,
            String statusText,
            boolean open,
            boolean inBusinessHour,
            String nextStartTime,
            String nextEndTime
    ) {
    }

    /**
     * 评价联动刷新门店评分（T05：评价通过审核后按近 90 天均分更新）。
     *
     * <p>仅供内部服务调用，不暴露给商家侧接口——商家不允许自行改 rating。
     * 四个分项均可能为 null（该门店 90 天内无已审核评价），此时保留门店原值不动。
     *
     * @return 是否命中并更新了门店
     */
    boolean refreshRating(Long shopId,
                          BigDecimal rating,
                          BigDecimal ratingTaste,
                          BigDecimal ratingPackage,
                          BigDecimal ratingDelivery);
}
