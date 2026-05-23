package com.nurseli.notificationservice.api.response;

/**
 * API hata cevaplarında kullanılan makine okunur hata kod sabitleri.
 */
public final class ApiErrorCode {

    /** Geçersiz istek parametreleri veya gövde. */
    public static final String BAD_REQUEST = "BAD_REQUEST";
    /** Bean validation hatası. */
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    /** İstenen kaynak bulunamadı. */
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    /** Kimlik doğrulama gerekli veya JWT geçersiz. */
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    /** Yetkisiz erişim. */
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    /** Bağımlı servis veya yapılandırma kullanılamıyor. */
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    /** Upstream (ör. OAuth / e-posta) geçici olarak erişilemez. */
    public static final String BAD_GATEWAY = "BAD_GATEWAY";
    /** Beklenmeyen sunucu hatası. */
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    private ApiErrorCode() {}
}
