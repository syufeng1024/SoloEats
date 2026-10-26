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

    // T05 商品中心（401xx 段）
    CATEGORY_NOT_FOUND(40101, "品类不存在", HttpStatus.NOT_FOUND),
    CATEGORY_HAS_CHILDREN(40102, "品类下存在子品类或商品，不能删除", HttpStatus.CONFLICT),
    SPU_NOT_FOUND(40103, "商品库商品不存在", HttpStatus.NOT_FOUND),
    SHOP_SPU_EXISTS(40104, "该商品已上架到门店", HttpStatus.CONFLICT),
    SPU_NOT_ON_SALE(40105, "商品未上架", HttpStatus.CONFLICT),
    REVIEW_NOT_ALLOWED(40106, "当前订单不满足评价条件", HttpStatus.CONFLICT),
    REVIEW_EXISTS(40107, "该商品已评价", HttpStatus.CONFLICT),

    DISPATCH_TAKEN(50001, "配送单已被抢", HttpStatus.CONFLICT),
    PICKUP_CODE_INVALID(50002, "取餐码错误或已使用", HttpStatus.CONFLICT),
    DELIVERY_NOT_FOUND(50003, "配送单不存在", HttpStatus.NOT_FOUND),
    DELIVERY_STATE_INVALID(50004, "配送单状态不允许当前操作", HttpStatus.CONFLICT),
    DELIVERY_RIDER_MISMATCH(50005, "骑手与配送单不匹配", HttpStatus.CONFLICT),
    RIDER_NOT_CERTIFIED(50006, "骑手未通过实名认证", HttpStatus.FORBIDDEN),
    RIDER_OFFLINE(50007, "骑手未上线", HttpStatus.CONFLICT),
    RIDER_ORDER_LIMIT(50008, "骑手进行中订单已达上限", HttpStatus.CONFLICT),
    RIDER_HEALTH_CERT_EXPIRED(50009, "健康证已过期", HttpStatus.FORBIDDEN),
    DISPATCH_EXPIRED(50010, "派单已超时", HttpStatus.CONFLICT),
    FREIGHT_OUT_OF_RANGE(50011, "超出配送范围", HttpStatus.BAD_REQUEST),

    SETTLEMENT_NOT_FOUND(60001, "结算单不存在", HttpStatus.NOT_FOUND),
    SETTLEMENT_STATE_INVALID(60002, "结算状态不一致", HttpStatus.CONFLICT),
    PAYOUT_AMOUNT_EXCEEDED(60004, "可提现余额不足", HttpStatus.CONFLICT),
    PAYOUT_STATE_INVALID(60005, "提现单状态不允许当前操作", HttpStatus.CONFLICT),

    MERCHANT_NOT_FOUND(70001, "商家不存在", HttpStatus.NOT_FOUND),
    MERCHANT_STATUS_INVALID(70002, "商家状态不允许当前操作", HttpStatus.CONFLICT),
    MERCHANT_QUALIFICATION_MISSING(70003, "入驻资质不齐全", HttpStatus.BAD_REQUEST),
    // 公共文件服务，建议后续迁移 1xxxx；后续新增文件类错误码一律用 1xxxx
    FILE_UPLOAD_FAILED(70004, "文件上传失败", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_TYPE_NOT_ALLOWED(70005, "文件类型不支持", HttpStatus.BAD_REQUEST),
    MERCHANT_APPLY_FORBIDDEN(70006, "入驻申请凭证无效或已过期", HttpStatus.FORBIDDEN),

    SHOP_NOT_FOUND(70101, "门店不存在", HttpStatus.NOT_FOUND),
    SHOP_CLOSED(70102, "门店未营业或已打烊", HttpStatus.CONFLICT),
    SHOP_OUT_OF_RANGE(70103, "超出配送范围", HttpStatus.BAD_REQUEST),
    SHOP_BELOW_MIN_ORDER(70104, "未达起送价", HttpStatus.BAD_REQUEST),
    SHOP_PICKUP_DISABLED(70105, "门店未开通到店自取", HttpStatus.BAD_REQUEST),

    // T06 C 端用户 / 钱包（8xxxx 段）
    MEMBER_NOT_FOUND(80001, "用户不存在", HttpStatus.NOT_FOUND),
    MEMBER_DISABLED(80002, "账号已被禁用", HttpStatus.FORBIDDEN),
    SMS_CODE_INVALID(80003, "验证码错误或已失效", HttpStatus.BAD_REQUEST),
    SMS_CODE_EXPIRED(80004, "验证码已过期", HttpStatus.BAD_REQUEST),
    ADDRESS_LIMIT_EXCEEDED(80005, "收货地址数量已达上限", HttpStatus.CONFLICT),
    WALLET_BALANCE_NOT_ENOUGH(80006, "钱包余额不足", HttpStatus.CONFLICT),
    WALLET_AMOUNT_INVALID(80007, "钱包金额不合法", HttpStatus.BAD_REQUEST),

    // T08 消息中心（9xxxx 段）
    IM_SESSION_NOT_FOUND(90001, "会话不存在", HttpStatus.NOT_FOUND),
    IM_SESSION_CLOSED(90002, "会话已结束或只读", HttpStatus.CONFLICT),
    IM_NOT_PARTICIPANT(90003, "非会话参与者", HttpStatus.FORBIDDEN),
    WS_UNAUTHORIZED(90004, "WebSocket 鉴权失败", HttpStatus.UNAUTHORIZED),

    // T11 报表（10xxxx 段）
    REPORT_DATE_INVALID(100010, "报表日期范围不合法", HttpStatus.BAD_REQUEST),
    REPORT_NOT_READY(100011, "报表数据尚未生成", HttpStatus.CONFLICT);

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
