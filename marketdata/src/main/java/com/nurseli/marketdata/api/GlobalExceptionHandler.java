package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.exception.InvalidRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private static Map<String, Object> errorBody(String error, HttpServletRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("timestamp", LocalDateTime.now());
        map.put("error", error);
        map.put("path", request != null ? request.getRequestURI() : null);
        map.put("correlationId", request != null ? request.getHeader(CORRELATION_ID_HEADER) : null);
        return map;
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        return errorBody(ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleInvalidRequest(InvalidRequestException ex, HttpServletRequest request) {
        return errorBody(ex.getMessage(), request);
    }
}