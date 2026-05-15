package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.exception.InvalidRequestException;
import com.nurseli.marketdata.application.bist.BistEquityUnsupportedSymbolException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

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

    @ExceptionHandler(BistEquityUnsupportedSymbolException.class)
    public ResponseEntity<ApiEnvelope<?>> handleBistUnsupported(
            BistEquityUnsupportedSymbolException ex, HttpServletRequest request) {
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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiEnvelope<?>> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        int raw = ex.getStatusCode().value();
        HttpStatus status = HttpStatus.resolve(raw);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String code =
                switch (raw) {
                    case 400 -> ErrorCode.BAD_REQUEST;
                    case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
                    default -> status.is5xxServerError()
                            ? ErrorCode.INTERNAL_SERVER_ERROR
                            : ErrorCode.BAD_REQUEST;
                };
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return ResponseEntity.status(status).body(ApiEnvelope.error(errorBody(code, message, request)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiEnvelope<?>> handleMissingRequestParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiEnvelope.error(errorBody(ErrorCode.BAD_REQUEST, ex.getMessage(), request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnvelope<?>> handleGeneric(Exception ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiEnvelope.error(errorBody(ErrorCode.INTERNAL_SERVER_ERROR, "Internal server error", request)));
    }
}