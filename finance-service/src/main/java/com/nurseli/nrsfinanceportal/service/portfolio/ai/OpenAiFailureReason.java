package com.nurseli.nrsfinanceportal.service.portfolio.ai;

/**
 * OpenAI çağrısı veya sonrası işleme hata sınıflandırması (log / telemetri).
 */
public enum OpenAiFailureReason {
    NONE,
    TIMEOUT,
    RATE_LIMIT_429,
    UNAUTHORIZED_401,
    BAD_REQUEST_400,
    PARSE_ERROR,
    SANITIZER_REJECTED,
    NOT_CONFIGURED,
    UNKNOWN
}
