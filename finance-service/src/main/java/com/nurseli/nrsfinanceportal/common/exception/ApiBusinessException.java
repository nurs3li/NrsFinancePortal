package com.nurseli.nrsfinanceportal.common.exception;

import org.springframework.http.HttpStatus;

public class ApiBusinessException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    public ApiBusinessException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
