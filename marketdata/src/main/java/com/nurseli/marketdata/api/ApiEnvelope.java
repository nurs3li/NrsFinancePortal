package com.nurseli.marketdata.api;

public class ApiEnvelope<T> {

    private boolean success;
    private T data;
    private Object errors;
    private Object meta;

    private ApiEnvelope(boolean success, T data, Object errors, Object meta) {
        this.success = success;
        this.data = data;
        this.errors = errors;
        this.meta = meta;
    }

    public static <T> ApiEnvelope<T> success(T data) {
        return new ApiEnvelope<>(true, data, null, null);
    }

    public static <T> ApiEnvelope<T> success(T data, Object meta) {
        return new ApiEnvelope<>(true, data, null, meta);
    }

    public static ApiEnvelope<?> error(Object errors) {
        return new ApiEnvelope<>(false, null, errors, null);
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
