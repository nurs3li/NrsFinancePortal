package com.nurseli.nrsfinanceportal.application.portfolio.ai;


/**
 * finance-service OpenAI hata nedeni enum'u — OpenAI çağrısı ve sonrası işleme hatalarını sınıflandırır.
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
