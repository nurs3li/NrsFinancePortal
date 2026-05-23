package com.nurseli.notificationservice.domain.email;

/**
 * E-posta gönderim denemesinin sonucunu ifade eder; {@link EmailDeliveryAudit} kayıtlarında kullanılır.
 */
public enum EmailDeliveryStatus {

    /** Gmail üzerinden başarıyla gönderildi. */
    SENT,

    /** Kanal politikası yalnızca uygulama içi bildirime izin verdi. */
    SKIPPED_POLICY,

    /** Redis rate limit eşiği aşıldı. */
    SKIPPED_RATE_LIMIT,

    /** Redis dedup anahtarı TTL süresi içinde daha önce işlendi. */
    SKIPPED_DEDUP,

    /** Gmail veya OAuth sağlayıcısı hata döndürdü. */
    FAILED_PROVIDER
}