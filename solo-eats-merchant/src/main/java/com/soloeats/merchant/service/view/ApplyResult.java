package com.soloeats.merchant.service.view;

/**
 * 入驻申请创建结果。applyToken 只在本次响应返回，客户端凭它完成资料补充、资质上传与提交。
 */
public record ApplyResult(
        Long applyId,
        String applyNo,
        String applyToken,
        long applyTokenExpiresIn
) {
}
