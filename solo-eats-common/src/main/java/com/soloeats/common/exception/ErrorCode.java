package com.soloeats.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 错误码分段：1xxxx 系统、2xxxx 订单、3xxxx 资金、4xxxx 库存/营销、5xxxx 配送。
 */
public enum ErrorCode {

    SUCCESS(0, "ok", HttpStatus.OK),
    SYSTEM_ERROR(10000, "系统异常", HttpStatus.INTERNAL_SERVER_ERROR),
    PARAM_INVALID(10001, "参数错误", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(10002, "资源不存在", HttpStatus.NOT_FOUND),
    AUTH_UNAUTHORIZED(10003, "未登录或令牌无效", HttpStatus.UNAUTHORIZED),
    AUTH_FORBIDDEN(10004, "无权访问", HttpStatus.FORBIDDEN),
    RATE_LIMITED(10005, "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    AUTH_ACCOUNT_LOCKED(10006, "账号已锁定", HttpStatus.FORBIDDEN),
    AUTH_BAD_CREDENTIALS(10007, "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    AUTH_ACCOUNT_DISABLED(10008, "账号已停用", HttpStatus.FORBIDDEN),

    ORDER_STATUS_INVALID(20001, "订单状态不允许当前操作", HttpStatus.CONFLICT),
    ORDER_NOT_FOUND(20002, "订单不存在", HttpStatus.NOT_FOUND),
    ORDER_CONCURRENT_MODIFIED(20003, "订单状态已被修改，请重试", HttpStatus.CONFLICT),

    PAY_AMOUNT_MISMATCH(30001, "支付金额不一致", HttpStatus.BAD_REQUEST),
    PAY_SIGN_INVALID(30002, "支付回调签名校验失败", HttpStatus.BAD_REQUEST),
    PAYMENT_NOT_FOUND(30003, "支付单不存在", HttpStatus.NOT_FOUND),
    PAYMENT_STATE_INVALID(30004, "支付单状态不一致", HttpStatus.CONFLICT),
    PAYMENT_CONFLICT(30005, "支付单冲突", HttpStatus.CONFLICT),
    REFUND_GATEWAY_FAILED(30006, "退款网关处理失败", HttpStatus.CONFLICT),
    REFUND_STATE_INVALID(30007, "退款单状态不一致", HttpStatus.CONFLICT),

    STOCK_NOT_ENOUGH(40001, "库存不足", HttpStatus.CONFLICT),
    COUPON_LOCK_FAILED(40002, "优惠券锁定失败", HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_REUSED(40003, "幂等键重复使用", HttpStatus.CONFLICT),
    REQUEST_IN_PROGRESS(40004, "请求处理中", HttpStatus.CONFLICT),
    LOCK_TIMEOUT(40005, "获取锁超时", HttpStatus.CONFLICT),
    STOCK_STATE_INVALID(40006, "库存状态不一致", HttpStatus.CONFLICT),
    SKU_NOT_FOUND(40007, "商品不存在或已下架", HttpStatus.NOT_FOUND),
    COUPON_NOT_AVAILABLE(40008, "优惠券不可用", HttpStatus.CONFLICT),
    COUPON_STATE_INVALID(40009, "优惠券状态不一致", HttpStatus.CONFLICT),

    DISPATCH_TAKEN(50001, "配送单已被抢", HttpStatus.CONFLICT),
    PICKUP_CODE_INVALID(50002, "取餐码错误或已使用", HttpStatus.CONFLICT),
    DELIVERY_NOT_FOUND(50003, "配送单不存在", HttpStatus.NOT_FOUND),
    DELIVERY_STATE_INVALID(50004, "配送单状态不允许当前操作", HttpStatus.CONFLICT),
    DELIVERY_RIDER_MISMATCH(50005, "骑手与配送单不匹配", HttpStatus.CONFLICT),

    SETTLEMENT_NOT_FOUND(60001, "结算单不存在", HttpStatus.NOT_FOUND),
    SETTLEMENT_STATE_INVALID(60002, "结算状态不一致", HttpStatus.CONFLICT),

    MERCHANT_NOT_FOUND(70001, "商家不存在", HttpStatus.NOT_FOUND),
    MERCHANT_STATUS_INVALID(70002, "商家状态不允许当前操作", HttpStatus.CONFLICT),
    MERCHANT_QUALIFICATION_MISSING(70003, "入驻资质不齐全", HttpStatus.BAD_REQUEST),
    // 公共文件服务，建议后续迁移 1xxxx；后续新增文件类错误码一律用 1xxxx
    FILE_UPLOAD_FAILED(70004, "文件上传失败", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_TYPE_NOT_ALLOWED(70005, "文件类型不支持", HttpStatus.BAD_REQUEST),
    MERCHANT_APPLY_FORBIDDEN(70006, "入驻申请凭证无效或已过期", HttpStatus.FORBIDDEN);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
