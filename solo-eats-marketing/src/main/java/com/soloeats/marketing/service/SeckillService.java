package com.soloeats.marketing.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.marketing.service.command.SeckillActivitySaveCommand;
import com.soloeats.marketing.service.view.SeckillActivityView;
import com.soloeats.marketing.service.view.SeckillSessionView;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀：后台场次维护 + C 端场次展示 + 库存预占/释放。
 *
 * <p>库存三段式：Redis 原子预扣拦截无效流量，DB 条件更新防超卖，释放时两边同时回补。
 * Redis 与 DB 不一致时以 DB 为准（见 {@code SeckillServiceImpl#reserve} 的回补矩阵）。
 */
public interface SeckillService {

    /**
     * 新建场次，返回主键。商品列表在同一事务内写入，独立库存初始 sold_qty=0。
     */
    Long createActivity(SeckillActivitySaveCommand command);

    /**
     * 编辑场次。商品按 skuId 对齐：已存在则更新（库存不得低于已售）、新增则插入、
     * 移除仅允许 sold_qty=0 的商品，避免遗留悬空的预占记录。
     */
    void updateActivity(Long id, SeckillActivitySaveCommand command);

    /**
     * 后台分页。列表不展开商品，需要商品明细调 {@link #detailActivity}。
     */
    PageResult<SeckillActivityView> pageActivities(Integer status, long pageNum, long pageSize);

    SeckillActivityView detailActivity(Long id);

    /**
     * C 端可展示场次（未结束），按 session_start 升序，含各场次商品余量。
     *
     * @param at 视角时间，null 取当前时间
     */
    List<SeckillSessionView> listSessions(LocalDateTime at);

    /**
     * 预占库存。成功返回预占单与 reserveToken；失败时 Redis 与 DB 已回补。
     *
     * @throws com.soloeats.common.exception.BizException 40203 售罄 / 40204 超限购 / 40205 不在场次时间
     */
    SeckillReserveResult reserve(Long itemId, Long userId, int qty);

    /**
     * 释放预占。不存在或已释放时直接返回（幂等），不抛异常。
     */
    void release(Long itemId, Long userId);
}
