package com.soloeats.app.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 创建/复用 IM 会话。当前主体从登录态取，请求体只描述对端与可选订单。
 */
public record CreateImSessionRequest(
        Long orderId,
        @NotNull(message = "对方类型不能为空") Integer peerType,
        @NotNull(message = "对方ID不能为空") Long peerId
) {
}
