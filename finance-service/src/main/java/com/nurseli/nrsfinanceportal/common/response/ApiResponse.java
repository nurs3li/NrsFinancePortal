package com.nurseli.nrsfinanceportal.common.response;

public class ApiResponse<T> {

    private boolean success;
    private T data;
    private Object errors;

    private ApiResponse(boolean success, T data, Object errors) {
        this.success = success;
        this.data = data;
        this.errors = errors;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<?> error(Object errors) {
        return new ApiResponse<>(false, null, errors);
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
}
