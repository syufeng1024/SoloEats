package com.soloeats.message.service.view;

/**
 * 渲染结果。msgType 由模板 template_type 映射而来：
 * 1订单→1，2营销→2，3系统→3，4客服→3（站内信不区分客服类型）。
 */
public record RenderedMessage(
        String templateCode,
        Integer msgType,
        String title,
        String content
) {
}
