package com.soloeats.report.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报表只读数据源。
 *
 * <p>report 模块不依赖 order / product / delivery / settlement 模块，这里直接对源表写原生
 * SQL。所有方法只 SELECT，不写任何业务表；订单状态使用本地字面量（60 = 已完成，70 = 已取消），
 * 不为了复用 OrderStatus 引入跨模块依赖。
 *
 * <p>分页扫描统一使用 {@code id > lastId ORDER BY id LIMIT ?}，禁止一次性拉全量订单。
 */
@Mapper
public interface ReportSourceMapper {

    /** 已完成订单事实，按 id 升序分页。 */
    @Select("""
            SELECT id,
                   shop_id           AS shopId,
                   merchant_id       AS merchantId,
                   user_id           AS userId,
                   pay_amount        AS payAmount,
                   platform_discount AS platformDiscount,
                   refund_status     AS refundStatus,
                   fulfillment_type  AS fulfillmentType,
                   pickup_ready_at   AS pickupReadyAt,
                   created_at        AS createdAt
            FROM t_order
            WHERE status = 60
              AND completed_at >= #{start}
              AND completed_at < #{end}
              AND id > #{lastId}
            ORDER BY id
            LIMIT #{limit}
            """)
    List<CompletedOrderFact> scanCompletedOrders(@Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end,
                                                 @Param("lastId") long lastId,
                                                 @Param("limit") int limit);

    /** 当日创建的订单，用于取消率分母；不拉明细，只取必要列。 */
    @Select("""
            SELECT id,
                   shop_id     AS shopId,
                   merchant_id AS merchantId,
                   status
            FROM t_order
            WHERE created_at >= #{start}
              AND created_at < #{end}
              AND id > #{lastId}
            ORDER BY id
            LIMIT #{limit}
            """)
    List<CreatedOrderFact> scanCreatedOrders(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end,
                                             @Param("lastId") long lastId,
                                             @Param("limit") int limit);

    /** 工作台今日实时卡。全部为单日条件聚合，走 idx_status_completed / idx_status_created。 */
    @Select("""
            SELECT
              (SELECT COALESCE(SUM(pay_amount), 0)
                 FROM t_order
                WHERE status = 60
                  AND refund_status <> 2
                  AND completed_at >= #{start}
                  AND completed_at < #{end}) AS todayGmv,
              (SELECT COUNT(*)
                 FROM t_order
                WHERE status = 60
                  AND completed_at >= #{start}
                  AND completed_at < #{end}) AS orderCount,
              (SELECT COUNT(*)
                 FROM t_order
                WHERE status IN (0, 10, 20, 30, 31, 40, 50, 51)
                  AND exception_type <> 0
                  AND created_at >= #{start}
                  AND created_at < #{end}) AS exceptionOrders,
              (SELECT COUNT(*)
                 FROM t_order
                WHERE status IN (10, 20, 30, 31, 40)
                  AND created_at >= #{start}
                  AND created_at < #{end}) AS inProgressOrders,
              (SELECT COUNT(*)
                 FROM t_member_user
                WHERE deleted = 0
                  AND created_at >= #{start}
                  AND created_at < #{end}) AS newUsers,
              (SELECT COUNT(*)
                 FROM t_rider
                WHERE deleted = 0
                  AND status = 0
                  AND online_status IN (1, 2)) AS onlineRiders
            """)
    DashboardTotals dashboardTotals(@Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    /** 当日退款成功单数与金额（t_refund_order.status = 30 表示退款成功）。 */
    @Select("""
            SELECT COUNT(*)                  AS refundOrderCount,
                   COALESCE(SUM(amount), 0)  AS refundAmount
            FROM t_refund_order
            WHERE status = 30
              AND success_at >= #{start}
              AND success_at < #{end}
            """)
    RefundTotals refundTotals(@Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);

    /** 当日各门店退款成功金额，按门店汇总。 */
    @Select("""
            SELECT o.shop_id                 AS shopId,
                   COUNT(*)                  AS refundOrderCount,
                   COALESCE(SUM(r.amount), 0) AS refundAmount
            FROM t_refund_order r
            JOIN t_order o ON o.id = r.order_id
            WHERE r.status = 30
              AND r.success_at >= #{start}
              AND r.success_at < #{end}
            GROUP BY o.shop_id
            """)
    List<ShopRefundFact> shopRefunds(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    /** 当日支付成功单数（t_payment_order.status = 20）。 */
    @Select("""
            SELECT COUNT(*)
            FROM t_payment_order
            WHERE status = 20
              AND paid_at >= #{start}
              AND paid_at < #{end}
            """)
    int countPaidOrders(@Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

    /** 当日新增用户。 */
    @Select("""
            SELECT COUNT(*)
            FROM t_member_user
            WHERE deleted = 0
              AND created_at >= #{start}
              AND created_at < #{end}
            """)
    int countNewUsers(@Param("start") LocalDateTime start,
                      @Param("end") LocalDateTime end);

    /** 当日下单用户数（去重）。 */
    @Select("""
            SELECT COUNT(DISTINCT user_id)
            FROM t_order
            WHERE status = 60
              AND completed_at >= #{start}
              AND completed_at < #{end}
            """)
    int countActiveUsers(@Param("start") LocalDateTime start,
                         @Param("end") LocalDateTime end);

    /** 当前在线骑手（1 在线 2 接单中）。 */
    @Select("""
            SELECT COUNT(*)
            FROM t_rider
            WHERE deleted = 0
              AND status = 0
              AND online_status IN (1, 2)
            """)
    int countOnlineRiders();

    /** 当日佣金收入：t_settlement_item.item_type = 2，排除已驳回账单。 */
    @Select("""
            SELECT COALESCE(SUM(i.amount), 0)
            FROM t_settlement_item i
            JOIN t_settlement_bill b ON b.id = i.bill_id
            WHERE i.item_type = 2
              AND b.bill_date = #{statDate}
              AND b.status <> 40
            """)
    long commissionIncome(@Param("statDate") LocalDate statDate);

    /** 当日结算支出：账单应结金额，排除已驳回账单。 */
    @Select("""
            SELECT COALESCE(SUM(payable_amount), 0)
            FROM t_settlement_bill
            WHERE bill_date = #{statDate}
              AND status <> 40
            """)
    long settlementExpense(@Param("statDate") LocalDate statDate);

    /** 当日门店评价均分（仅已通过评价）。 */
    @Select("""
            SELECT shop_id            AS shopId,
                   AVG(overall_score) AS rating
            FROM t_product_review
            WHERE deleted = 0
              AND status = 1
              AND created_at >= #{start}
              AND created_at < #{end}
            GROUP BY shop_id
            """)
    List<ShopRatingFact> shopRatings(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    /**
     * 复购率分子分母：按 periodKey 统计下单用户数与其中下单 >= 2 次的用户数。
     * format 由服务层按 granularity 白名单生成，不接收外部原始字符串。
     */
    @Select("""
            SELECT t.period_key AS periodKey,
                   COUNT(*)     AS buyerCount,
                   SUM(CASE WHEN t.order_count >= 2 THEN 1 ELSE 0 END) AS repeatBuyerCount
            FROM (
              SELECT user_id,
                     DATE_FORMAT(completed_at, #{format}) AS period_key,
                     COUNT(*)                             AS order_count
              FROM t_order
              WHERE status = 60
                AND completed_at >= #{start}
                AND completed_at < #{end}
              GROUP BY user_id, DATE_FORMAT(completed_at, #{format})
            ) t
            GROUP BY t.period_key
            """)
    List<RepurchaseFact> repurchaseByPeriod(@Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end,
                                            @Param("format") String format);

    // ------------------------------------------------------------------
    // 阶段B：排行榜与分析类实时查询
    //
    // 排行榜不读 t_report_daily_*，直接聚合业务表，保证历史区间在未回放聚合时
    // 也能出数。日期跨度由服务层限制在 366 天内；订单完成口径统一用 completed_at，
    // 与 buildPlatform 的 GMV 口径保持一致。
    // ------------------------------------------------------------------

    /** 商品榜（按销售额），categoryId 为空时不过滤。 */
    @Select("""
            SELECT oi.spu_id            AS spuId,
                   oi.sku_id            AS skuId,
                   spu.spu_name         AS spuName,
                   spu.category_id      AS categoryId,
                   COALESCE(SUM(oi.quantity), 0)                         AS saleQty,
                   COALESCE(SUM(oi.payable_amount - oi.refunded_amount), 0) AS saleAmount,
                   COALESCE(SUM(oi.refunded_qty), 0)                     AS refundQty
            FROM t_order_item oi
            JOIN t_order o ON o.id = oi.order_id
            JOIN t_spu spu ON spu.id = oi.spu_id
            WHERE o.status = 60
              AND o.completed_at >= #{start}
              AND o.completed_at < #{end}
              AND (#{categoryId,jdbcType=BIGINT} IS NULL OR spu.category_id = #{categoryId,jdbcType=BIGINT})
            GROUP BY oi.spu_id, oi.sku_id, spu.spu_name, spu.category_id
            ORDER BY saleAmount DESC, oi.spu_id, oi.sku_id
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<ProductRankFact> productRankByAmount(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end,
                                              @Param("categoryId") Long categoryId,
                                              @Param("limit") int limit,
                                              @Param("offset") long offset);

    /** 商品榜（按销量）。 */
    @Select("""
            SELECT oi.spu_id            AS spuId,
                   oi.sku_id            AS skuId,
                   spu.spu_name         AS spuName,
                   spu.category_id      AS categoryId,
                   COALESCE(SUM(oi.quantity), 0)                         AS saleQty,
                   COALESCE(SUM(oi.payable_amount - oi.refunded_amount), 0) AS saleAmount,
                   COALESCE(SUM(oi.refunded_qty), 0)                     AS refundQty
            FROM t_order_item oi
            JOIN t_order o ON o.id = oi.order_id
            JOIN t_spu spu ON spu.id = oi.spu_id
            WHERE o.status = 60
              AND o.completed_at >= #{start}
              AND o.completed_at < #{end}
              AND (#{categoryId,jdbcType=BIGINT} IS NULL OR spu.category_id = #{categoryId,jdbcType=BIGINT})
            GROUP BY oi.spu_id, oi.sku_id, spu.spu_name, spu.category_id
            ORDER BY saleQty DESC, oi.spu_id, oi.sku_id
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<ProductRankFact> productRankByQty(@Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end,
                                           @Param("categoryId") Long categoryId,
                                           @Param("limit") int limit,
                                           @Param("offset") long offset);

    /** 商品榜总数（与列表同口径：spu_id + sku_id 分组）。 */
    @Select("""
            SELECT COUNT(*)
            FROM (
              SELECT oi.spu_id, oi.sku_id
              FROM t_order_item oi
              JOIN t_order o ON o.id = oi.order_id
              JOIN t_spu spu ON spu.id = oi.spu_id
              WHERE o.status = 60
                AND o.completed_at >= #{start}
                AND o.completed_at < #{end}
                AND (#{categoryId,jdbcType=BIGINT} IS NULL OR spu.category_id = #{categoryId,jdbcType=BIGINT})
              GROUP BY oi.spu_id, oi.sku_id
            ) t
            """)
    long countProductRank(@Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end,
                          @Param("categoryId") Long categoryId);

    /**
     * 门店排行：GMV/单量按 completed_at，取消率与出餐时长按 created_at 窗口。
     * 出餐时长沿用阶段A 的近似口径（created_at → pickup_ready_at）。
     */
    @Select("""
            SELECT s.id                                   AS shopId,
                   s.shop_name                            AS shopName,
                   s.merchant_id                          AS merchantId,
                   t.order_count                          AS orderCount,
                   t.gmv                                  AS gmv,
                   s.rating                               AS rating,
                   CAST(COALESCE(c.avg_prep_minutes, 0) AS SIGNED) AS avgPrepMinutes,
                   CAST(COALESCE(c.cancel_rate, 0) AS DECIMAL(5,2)) AS cancelRate
            FROM (
              SELECT shop_id,
                     COUNT(*) AS order_count,
                     COALESCE(SUM(CASE WHEN refund_status <> 2 THEN pay_amount ELSE 0 END), 0) AS gmv
              FROM t_order
              WHERE status = 60
                AND completed_at >= #{start}
                AND completed_at < #{end}
              GROUP BY shop_id
            ) t
            JOIN t_shop s ON s.id = t.shop_id AND s.deleted = 0
            LEFT JOIN (
              SELECT shop_id,
                     CAST(ROUND(AVG(CASE WHEN status = 60
                                          AND pickup_ready_at IS NOT NULL
                                          AND pickup_ready_at >= created_at
                                     THEN TIMESTAMPDIFF(MINUTE, created_at, pickup_ready_at) END), 0) AS SIGNED)
                       AS avg_prep_minutes,
                     CAST(ROUND(SUM(CASE WHEN status = 70 THEN 1 ELSE 0 END) * 100 / COUNT(*), 2) AS DECIMAL(5,2))
                       AS cancel_rate
              FROM t_order
              WHERE created_at >= #{start}
                AND created_at < #{end}
              GROUP BY shop_id
            ) c ON c.shop_id = t.shop_id
            ORDER BY t.gmv DESC, t.order_count DESC, s.id
            LIMIT #{limit}
            """)
    List<ShopRankFact> shopRank(@Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end,
                                @Param("limit") int limit);

    /** 骑手分析：准时率分母为当日送达单量；promise_deliver_at 为空不计入准时。 */
    @Select("""
            SELECT d.rider_id                             AS riderId,
                   r.real_name                            AS riderName,
                   COUNT(*)                               AS deliveryCount,
                   COALESCE(SUM(CASE WHEN d.promise_deliver_at IS NOT NULL
                                      AND d.delivered_at <= d.promise_deliver_at
                                 THEN 1 ELSE 0 END), 0) AS onTimeCount,
                   r.rating                               AS rating,
                   CAST(COALESCE(SUM(d.delivery_fee), 0) AS SIGNED) AS incomeAmount,
                   CAST(ROUND(AVG(TIMESTAMPDIFF(MINUTE,
                            COALESCE(d.accepted_at, d.created_at), d.delivered_at)), 0) AS SIGNED)
                                                          AS avgDeliveryMinutes
            FROM t_delivery_order d
            JOIN t_rider r ON r.id = d.rider_id
            WHERE d.status = 50
              AND d.delivered_at >= #{start}
              AND d.delivered_at < #{end}
            GROUP BY d.rider_id, r.real_name, r.rating
            ORDER BY deliveryCount DESC, d.rider_id
            LIMIT #{limit}
            """)
    List<RiderAnalysisFact> riderAnalysis(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end,
                                          @Param("limit") int limit);

    /** 漏斗区间汇总。无数据时 SUM 返回 NULL，外层已 COALESCE。 */
    @Select("""
            SELECT COALESCE(SUM(expose_count), 0) AS exposeCount,
                   COALESCE(SUM(click_count), 0)  AS clickCount,
                   COALESCE(SUM(cart_count), 0)   AS cartCount,
                   COALESCE(SUM(order_count), 0)  AS orderCount,
                   COALESCE(SUM(pay_count), 0)    AS payCount
            FROM t_report_funnel_daily
            WHERE stat_date >= #{dateFrom}
              AND stat_date <= #{dateTo}
            """)
    FunnelTotals funnelTotals(@Param("dateFrom") LocalDate dateFrom,
                              @Param("dateTo") LocalDate dateTo);

    /** 区间内下单 >= 2 次的用户数（复购分子）。 */
    @Select("""
            SELECT COUNT(*)
            FROM (
              SELECT user_id
              FROM t_order
              WHERE status = 60
                AND completed_at >= #{start}
                AND completed_at < #{end}
              GROUP BY user_id
              HAVING COUNT(*) >= 2
            ) t
            """)
    int countRepurchaseUsers(@Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    /** 平台资金流水按类型汇总。flow_type：1佣金 2结算 3补贴 4优惠 5提现 6退款冲抵。 */
    @Select("""
            SELECT flow_type              AS flowType,
                   COALESCE(SUM(amount), 0) AS amount
            FROM t_fund_flow
            WHERE created_at >= #{start}
              AND created_at < #{end}
            GROUP BY flow_type
            """)
    List<FundFlowFact> fundFlowTotals(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    // ------------------------------------------------------------------
    // 阶段B：日报聚合分页扫描
    // ------------------------------------------------------------------

    /** 已完成订单明细，按明细 id 升序分页。 */
    @Select("""
            SELECT oi.id             AS id,
                   oi.shop_id        AS shopId,
                   oi.spu_id         AS spuId,
                   spu.category_id   AS categoryId,
                   oi.quantity       AS quantity,
                   oi.payable_amount AS payableAmount,
                   oi.refunded_qty   AS refundedQty,
                   oi.refunded_amount AS refundedAmount
            FROM t_order_item oi
            JOIN t_order o ON o.id = oi.order_id
            JOIN t_spu spu ON spu.id = oi.spu_id
            WHERE o.status = 60
              AND o.completed_at >= #{start}
              AND o.completed_at < #{end}
              AND oi.id > #{lastId}
            ORDER BY oi.id
            LIMIT #{limit}
            """)
    List<OrderItemFact> scanCompletedOrderItems(@Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end,
                                                @Param("lastId") long lastId,
                                                @Param("limit") int limit);

    /** 当日已送达配送单，按配送单 id 升序分页。 */
    @Select("""
            SELECT d.id                 AS id,
                   d.rider_id           AS riderId,
                   r.rating             AS rating,
                   d.delivery_fee       AS deliveryFee,
                   d.accepted_at        AS acceptedAt,
                   d.created_at         AS createdAt,
                   d.delivered_at       AS deliveredAt,
                   d.promise_deliver_at AS promiseDeliverAt
            FROM t_delivery_order d
            JOIN t_rider r ON r.id = d.rider_id
            WHERE d.status = 50
              AND d.delivered_at >= #{start}
              AND d.delivered_at < #{end}
              AND d.id > #{lastId}
            ORDER BY d.id
            LIMIT #{limit}
            """)
    List<DeliveryOrderFact> scanDeliveredOrders(@Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end,
                                                @Param("lastId") long lastId,
                                                @Param("limit") int limit);

    /**
     * 骑手在线时长（分钟）。按上下线日志配对，跨日会话按区间边界裁剪。
     * 回看 7 天以捕获区间开始前已上线、区间内才下线的会话；超过 7 天的单人连续在线不计入。
     */
    @Select("""
            SELECT x.rider_id AS riderId,
                   COALESCE(SUM(TIMESTAMPDIFF(MINUTE,
                       GREATEST(x.online_at, #{start}),
                       LEAST(COALESCE(x.offline_at, #{end}), #{end}))), 0) AS onlineMinutes
            FROM (
              SELECT rider_id,
                     created_at AS online_at,
                     LEAD(created_at) OVER (PARTITION BY rider_id ORDER BY created_at) AS offline_at,
                     action
              FROM t_rider_online_log
              WHERE created_at < #{end}
                AND created_at >= #{start} - INTERVAL 7 DAY
            ) x
            WHERE x.action = 1
              AND COALESCE(x.offline_at, #{end}) > #{start}
            GROUP BY x.rider_id
            """)
    List<RiderOnlineFact> riderOnlineMinutes(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    /** 骑手当日收入与罚款（结算日期口径）。 */
    @Select("""
            SELECT rider_id                       AS riderId,
                   COALESCE(SUM(total_amount), 0) AS incomeAmount,
                   COALESCE(SUM(penalty_amount), 0) AS penaltyAmount
            FROM t_rider_income
            WHERE settlement_date = #{statDate}
            GROUP BY rider_id
            """)
    List<RiderIncomeFact> riderIncomeTotals(@Param("statDate") LocalDate statDate);

    /** 当日埋点按事件类型计数。 */
    @Select("""
            SELECT COALESCE(SUM(CASE WHEN event_type = 'HOME_EXPOSE' THEN 1 ELSE 0 END), 0)  AS exposeCount,
                   COALESCE(SUM(CASE WHEN event_type = 'SHOP_CLICK' THEN 1 ELSE 0 END), 0)   AS clickCount,
                   COALESCE(SUM(CASE WHEN event_type = 'ADD_CART' THEN 1 ELSE 0 END), 0)     AS cartCount,
                   COALESCE(SUM(CASE WHEN event_type = 'ORDER_SUBMIT' THEN 1 ELSE 0 END), 0) AS orderCount,
                   COALESCE(SUM(CASE WHEN event_type = 'PAY_SUCCESS' THEN 1 ELSE 0 END), 0)  AS payCount
            FROM t_track_event
            WHERE event_at >= #{start}
              AND event_at < #{end}
            """)
    FunnelCounts trackEventCounts(@Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);

    record CompletedOrderFact(Long id, Long shopId, Long merchantId, Long userId,
                              Long payAmount, Long platformDiscount, Integer refundStatus,
                              Integer fulfillmentType, LocalDateTime pickupReadyAt,
                              LocalDateTime createdAt) {
    }

    record CreatedOrderFact(Long id, Long shopId, Long merchantId, Integer status) {
    }

    record DashboardTotals(Long todayGmv, Long orderCount, Long exceptionOrders,
                           Long inProgressOrders, Long newUsers, Long onlineRiders) {
    }

    record RefundTotals(Long refundOrderCount, Long refundAmount) {
    }

    record ShopRefundFact(Long shopId, Long refundOrderCount, Long refundAmount) {
    }

    record ShopRatingFact(Long shopId, BigDecimal rating) {
    }

    record RepurchaseFact(String periodKey, Long buyerCount, Long repeatBuyerCount) {
    }

    record ProductRankFact(Long spuId, Long skuId, String spuName, Long categoryId,
                           Long saleQty, Long saleAmount, Long refundQty) {
    }

    record ShopRankFact(Long shopId, String shopName, Long merchantId, Long orderCount,
                        Long gmv, BigDecimal rating, Long avgPrepMinutes,
                        BigDecimal cancelRate) {
    }

    record RiderAnalysisFact(Long riderId, String riderName, Long deliveryCount,
                             Long onTimeCount, BigDecimal rating, Long incomeAmount,
                             Long avgDeliveryMinutes) {
    }

    record FunnelTotals(Long exposeCount, Long clickCount, Long cartCount,
                        Long orderCount, Long payCount) {
    }

    record FundFlowFact(Integer flowType, Long amount) {
    }

    record OrderItemFact(Long id, Long shopId, Long spuId, Long categoryId,
                         Integer quantity, Long payableAmount, Integer refundedQty,
                         Long refundedAmount) {
    }

    record DeliveryOrderFact(Long id, Long riderId, BigDecimal rating, Long deliveryFee,
                             LocalDateTime acceptedAt, LocalDateTime createdAt,
                             LocalDateTime deliveredAt, LocalDateTime promiseDeliverAt) {
    }

    record RiderIncomeFact(Long riderId, Long incomeAmount, Long penaltyAmount) {
    }

    record RiderOnlineFact(Long riderId, Long onlineMinutes) {
    }

    record FunnelCounts(Long exposeCount, Long clickCount, Long cartCount,
                        Long orderCount, Long payCount) {
    }
}
