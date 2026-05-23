package com.nurseli.logconsumer.api.response;

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

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static ApiResponse<?> error(Object errors) {
        return new ApiResponse<>(false, null, errors, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public Object getErrors() {
        return errors;
    }

    public Object getMeta() {
        return meta;
    }
}
