package com.soloeats.common.api;

import java.util.List;

/**
 * 统一分页结构。pageNum 从 1 开始，pageSize 默认 20，上限 100。
 */
public record PageResult<T>(List<T> list, long total, long pageNum, long pageSize) {

    public static <T> PageResult<T> of(List<T> list, long total, long pageNum, long pageSize) {
        return new PageResult<>(list, total, pageNum, pageSize);
    }
}
