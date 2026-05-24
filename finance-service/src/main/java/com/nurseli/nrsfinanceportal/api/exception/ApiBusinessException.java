package com.nurseli.nrsfinanceportal.api.exception;

import org.springframework.http.HttpStatus;

/**
 * İş kuralı ihlallerini HTTP status ve makine okunur error code ile {@link com.nurseli.nrsfinanceportal.api.GlobalExceptionHandler}'a taşır.
 */
public class ApiBusinessException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    /**
     * {@code ApiBusinessException} — Verilen status, error code ve mesajla fırlatılır.
     */
    public ApiBusinessException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    /**
     * {@code getErrorCode} — Yanıt gövdesindeki {@code code} alanı için makine okunur kodu döner.
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * {@code getStatus} — HTTP response status kodunu döner.
     */
    public HttpStatus getStatus() {
        return status;
    }
}
