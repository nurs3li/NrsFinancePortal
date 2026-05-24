package com.nurseli.marketdata.api;

/**
 * API hata cevaplarında kullanılan makine okunur hata kod sabitleri.
 */
public final class ErrorCode {

    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    public static final String BAD_GATEWAY = "BAD_GATEWAY";
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    private ErrorCode() {}
}
