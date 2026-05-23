package com.nurseli.nrsfinanceportal.application.portfolio.ai;

import java.util.Optional;

/**
 * finance-service OpenAI çağrı sonucu kaydı — OpenAI yanıt metni ve başarı durumunu taşır.
 */

public record OpenAiCallResult(
        Optional<String> content,
        OpenAiFailureReason failureReason,
long durationMs
) {
    /**
     * {@code success} — Çağrının başarılı olup olmadığını (hata null ve içerik dolu) kontrol eder.
     */
    public boolean success() {
        return content.isPresent();
    }
    }
