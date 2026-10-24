package com.soloeats.message.mapper;

import lombok.Data;

/**
 * GROUP BY msg_type 的聚合结果。显式用 @Results 映射而不是 Map，
 * 避免 MySQL 列标签大小写差异导致取值为 null。
 */
@Data
public class UnreadCountRow {

    private Integer msgType;

    private Long cnt;
}
