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
}
