package com.nurseli.nrsfinanceportal.common.response;

public final class ApiErrorCode {

    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    public static final String BUY_PRICE_NOT_FOUND = "BUY_PRICE_NOT_FOUND";
    public static final String SELL_PRICE_NOT_FOUND = "SELL_PRICE_NOT_FOUND";
    public static final String INVALID_SELL_DATE = "INVALID_SELL_DATE";
    public static final String INVALID_PRICE_DATE = "INVALID_PRICE_DATE";
    public static final String OPEN_POSITION_CANNOT_HAVE_SELL_FIELDS = "OPEN_POSITION_CANNOT_HAVE_SELL_FIELDS";
    public static final String SOLD_POSITION_CANNOT_BE_REOPENED = "SOLD_POSITION_CANNOT_BE_REOPENED";
    public static final String POSITION_ALREADY_SOLD = "POSITION_ALREADY_SOLD";
    public static final String AI_DAILY_LIMIT_REACHED = "AI_DAILY_LIMIT_REACHED";
    public static final String AI_ANALYSIS_NOT_FOUND = "AI_ANALYSIS_NOT_FOUND";
    public static final String AI_ANALYSIS_FAILED = "AI_ANALYSIS_FAILED";

    private ApiErrorCode() {
    }
}
