package com.nurseli.nrsfinanceportal.application.portfolio.ai;

import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * finance-service OpenAI hata sınıflandırıcı — istisnaları OpenAiFailureReason enum değerine map eder.
 */
public final class OpenAiExceptionClassifier {

    private OpenAiExceptionClassifier() {
}

    /**
     * {@code classify} — Throwable'dan timeout, 429, 401, parse veya sanitizer reddi gibi failure reason çıkarır.
     */
    public static OpenAiFailureReason classify(Throwable ex) {
        if (ex == null) {
            return OpenAiFailureReason.UNKNOWN;
    }
        Throwable root = unwrap(ex);
        if (root instanceof TimeoutException || root instanceof SocketTimeoutException) {
            return OpenAiFailureReason.TIMEOUT;
        }
        String msg = root.getMessage() != null ? root.getMessage().toLowerCase() : "";
        if (msg.contains("timeout")
                || msg.contains("timed out")
                || root.getClass().getSimpleName().contains("Timeout")) {
            return OpenAiFailureReason.TIMEOUT;
        }
        if (root instanceof WebClientResponseException wce) {
            return switch (wce.getStatusCode().value()) {
                case 429 -> OpenAiFailureReason.RATE_LIMIT_429;
                case 401 -> OpenAiFailureReason.UNAUTHORIZED_401;
                case 400 -> OpenAiFailureReason.BAD_REQUEST_400;
                default -> OpenAiFailureReason.UNKNOWN;
            };
        }
        return OpenAiFailureReason.UNKNOWN;
    }

    private static Throwable unwrap(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }
}
