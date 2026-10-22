package com.soloeats.app.dto;

/**
 * 评价审核：status 0待审 1已通过 2已隐藏；topFlag 0普通 1置顶。
 */
public record ReviewStatusRequest(
        Integer status,
        Integer topFlag
) {
}
