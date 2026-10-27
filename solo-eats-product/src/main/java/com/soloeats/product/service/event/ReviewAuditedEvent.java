package com.soloeats.product.service.event;

/**
 * 评价审核状态变更事件（T05 评分联动）。
 *
 * <p>在审核事务提交后由 {@code ReviewRatingSyncService} 以异步方式消费，
 * 重算该门店近 90 天评分。用事件而非直接调用，是为了保证异步线程能读到
 * 已提交的 {@code status=1}，同时不阻塞审核主流程。
 *
 * @param shopId 被审核评价所属门店
 */
public record ReviewAuditedEvent(Long shopId) {
}
