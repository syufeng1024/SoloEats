package com.soloeats.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 坐席端发送消息。extraJson 只接受结构化 JSON 文本（图片 fileId / 订单卡片 / 坐标），
 * 不接受 HTML 外链；内容长度最终由 ImSessionService.send 再校验一次。
 */
public record SendSessionMessageRequest(
        @NotNull(message = "消息类型不能为空") Integer msgType,
        @Size(max = 2000, message = "消息内容最长 2000 个字符") String content,
        @Size(max = 4000, message = "extraJson 最长 4000 个字符") String extraJson
) {
}
