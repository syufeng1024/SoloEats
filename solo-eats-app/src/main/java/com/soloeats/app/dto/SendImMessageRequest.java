package com.soloeats.app.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发送 IM 消息。extra 只接受结构化 JSON（图片 fileId / 订单卡片 / 坐标），不收 HTML 字符串。
 */
public record SendImMessageRequest(
        @NotNull(message = "消息类型不能为空") Integer msgType,
        @Size(max = 2000, message = "消息内容最长 2000 个字符") String content,
        JsonNode extra
) {
}
