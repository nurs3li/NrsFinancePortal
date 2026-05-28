package com.nurseli.notificationservice.api;

import com.nurseli.notificationservice.api.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * {@code /api/} altındaki controller response'larını standart {@link ApiResponse} zarfına sarar.
 */
@RestControllerAdvice
public class ApiResponseEnvelopeAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return !StringHttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return body;
        }
        HttpServletRequest raw = servletRequest.getServletRequest();
        String path = raw.getRequestURI();
        if (path == null || !path.startsWith("/api/")) {
            return body;
        }
        if (body instanceof ApiResponse<?> || body instanceof Resource || body instanceof byte[]) {
            return body;
        }
        return ApiResponse.success(body);
    }
}
