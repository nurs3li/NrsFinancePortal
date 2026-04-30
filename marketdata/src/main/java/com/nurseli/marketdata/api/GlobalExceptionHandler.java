package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.exception.InvalidRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private static Map<String, Object> errorBody(String code, String error, HttpServletRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("message", error);
        map.put("timestamp", LocalDateTime.now());
        map.put("error", error);
        map.put("path", request != null ? request.getRequestURI() : null);
        map.put("correlationId", request != null ? request.getHeader(CORRELATION_ID_HEADER) : null);
        return map;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiEnvelope<?>> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiEnvelope.error(errorBody(ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), request)));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiEnvelope<?>> handleInvalidRequest(InvalidRequestException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiEnvelope.error(errorBody(ErrorCode.BAD_REQUEST, ex.getMessage(), request)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiEnvelope<?>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiEnvelope.error(errorBody(ErrorCode.BAD_REQUEST, ex.getMessage(), request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnvelope<?>> handleGeneric(Exception ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiEnvelope.error(errorBody(ErrorCode.INTERNAL_SERVER_ERROR, "Internal server error", request)));
    }
}