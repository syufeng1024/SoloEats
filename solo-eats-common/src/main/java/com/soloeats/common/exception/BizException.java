package com.soloeats.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务异常。Controller 不捕获，由 GlobalExceptionHandler 统一转换。
 */
public class BizException extends RuntimeException {

    private final int code;
    private final HttpStatus httpStatus;

    public BizException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }

    public BizException(int code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
