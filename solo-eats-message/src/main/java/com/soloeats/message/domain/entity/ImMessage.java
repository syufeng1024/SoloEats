package com.soloeats.message.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM 消息。content 是纯文本，服务端不做任何 HTML 渲染；
 * 图片 fileId、订单卡片、坐标等结构化数据放 extra_json（JSON 文本，按原样透传给前端解析）。
 * read_flag 是消息级标记，会话红点走 t_im_session 的计数列。
 */
@Data
@TableName("t_im_message")
public class ImMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long sessionId;

    /** 1用户 2商家 3骑手 4客服 5系统 */
    private Integer senderType;

    private Long senderId;

    /** 1文字 2图片 3订单卡片 4位置 5常用语 */
    private Integer msgType;

    /** 纯文本，最长 2000 字符 */
    private String content;

    /** 结构化扩展 JSON 文本，禁止存 HTML */
    private String extraJson;

    /** 0未读 1已读 */
    private Integer readFlag;

    private LocalDateTime createdAt;
}
