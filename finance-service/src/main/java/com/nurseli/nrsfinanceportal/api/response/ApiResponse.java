package com.nurseli.nrsfinanceportal.api.response;

/**
 * Tüm public API endpoint'leri için ortak başarı/hata response zarfı.
 */
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private Object errors;
    private Object meta;

    private ApiResponse(boolean success, T data, Object errors, Object meta) {
        this.success = success;
        this.data = data;
        this.errors = errors;
        this.meta = meta;
    }

    /**
     * {@code success} — Veri alanı dolu, hata alanı boş başarılı zarf oluşturur.
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    /**
     * {@code success} — Meta bilgisiyle birlikte başarılı zarf oluşturur.
     */
    public static <T> ApiResponse<T> success(T data, Object meta) {
        return new ApiResponse<>(true, data, null, meta);
    }

    /**
     * {@code error} — Hata detaylarını {@code errors} alanına yazan başarısız zarf oluşturur.
     */
    public static ApiResponse<?> error(Object errors) {
        return new ApiResponse<>(false, null, errors, null);
    }

    /**
     * {@code isSuccess} — İsteğin iş kuralı açısından başarılı olup olmadığını döner.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * {@code getData} — Başarılı yanıttaki payload'ı döner.
     */
    public T getData() {
        return data;
    }

    /**
     * {@code getErrors} — Hata yanıtındaki detay nesnesini döner.
     */
    public Object getErrors() {
        return errors;
    }

    /**
     * {@code getMeta} — Sayfalama veya ek bağlam için opsiyonel meta alanını döner.
     */
    public Object getMeta() {
        return meta;
    }
}
