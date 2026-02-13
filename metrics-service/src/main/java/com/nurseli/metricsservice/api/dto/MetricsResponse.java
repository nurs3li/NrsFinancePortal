package com.nurseli.metricsservice.api.dto;

import java.util.List;

public record MetricsResponse<T>(boolean success, T data, Object errors) {

    public static <T> MetricsResponse<T> ok(T data) {
        return new MetricsResponse<>(true, data, null);
    }

    public static <T> MetricsResponse<T> error(Object errors) {
        return new MetricsResponse<>(false, null, errors);
    }
}