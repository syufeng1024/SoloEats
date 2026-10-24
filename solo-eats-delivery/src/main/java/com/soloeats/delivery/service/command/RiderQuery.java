package com.soloeats.delivery.service.command;

/**
 * 后台骑手列表查询条件。keyword 支持骑手编号、姓名、手机号后四位。
 */
public record RiderQuery(Integer certStatus, Integer status, String keyword) {

    public static RiderQuery empty() {
        return new RiderQuery(null, null, null);
    }
}
