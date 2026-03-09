package com.pk.ecommerce.common.exception;

import lombok.Getter;

@Getter
public class EcommerceException extends RuntimeException {
    private final String errorCode;
    private final int    httpStatus;

    public EcommerceException(String message, String errorCode, int httpStatus) {
        super(message);
        this.errorCode  = errorCode;
        this.httpStatus = httpStatus;
    }

    public EcommerceException(String message, String errorCode, int httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode  = errorCode;
        this.httpStatus = httpStatus;
    }
}
