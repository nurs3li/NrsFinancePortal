package com.nurseli.notificationservice.api.response;

/**
 * Standart API cevap zarfı; başarılı yanıtlarda {@code data}, hatalarda {@code errors} taşır.
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
     * {@code success} — Başarılı cevap zarfı oluşturur.
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    /**
     * {@code error} — Hata cevap zarfı oluşturur.
     */
    public static ApiResponse<?> error(Object errors) {
        return new ApiResponse<>(false, null, errors, null);
    }

    /**
     * {@code isSuccess} — İsteğin başarılı olup olmadığını döner.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * {@code getData} — Başarılı cevap yükünü döner.
     */
    public T getData() {
        return data;
    }

    /**
     * {@code getErrors} — Hata detaylarını döner.
     */
    public Object getErrors() {
        return errors;
    }

    /**
     * {@code getMeta} — İsteğe bağlı meta bilgisini döner.
     */
    public Object getMeta() {
        return meta;
    }
}
