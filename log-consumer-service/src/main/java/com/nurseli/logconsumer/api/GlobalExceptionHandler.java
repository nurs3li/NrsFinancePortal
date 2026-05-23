package com.nurseli.logconsumer.api;

import com.nurseli.logconsumer.api.response.ApiErrorBody;
import com.nurseli.logconsumer.api.response.ApiErrorCode;
import com.nurseli.logconsumer.api.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST katmanındaki istisnaları standart {@link ApiResponse} hata gövdesine dönüştürür.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String msg =
                ex.getBindingResult().getFieldErrors().stream()
                        .findFirst()
                        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                        .orElse("Geçersiz istek gövdesi");
        log.warn("[EXCEPTION] MethodArgumentNotValidException: {}", msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ApiErrorBody.of(ApiErrorCode.VALIDATION_ERROR, msg, request)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleBadRequest(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("[EXCEPTION] IllegalArgumentException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ApiErrorBody.of(ApiErrorCode.BAD_REQUEST, ex.getMessage(), request)));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {
        log.warn("[EXCEPTION] IllegalStateException: {}", ex.getMessage());
        HttpStatus status = resolveIllegalStateStatus(ex);
        String code = resolveStatusCode(status);
        String message = ex.getMessage() != null ? ex.getMessage() : "İşlem tamamlanamadı";
        return ResponseEntity.status(status)
                .body(ApiResponse.error(ApiErrorBody.of(code, message, request)));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<?>> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus resolved = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        String message = ex.getReason() != null ? ex.getReason() : resolved.getReasonPhrase();
        String code = resolveStatusCode(resolved);
        log.warn("[EXCEPTION] ResponseStatusException: {} {}", resolved.value(), message);
        return ResponseEntity.status(resolved)
                .body(ApiResponse.error(ApiErrorBody.of(code, message, request)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<?>> handleMissingRequestParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ApiErrorBody.of(ApiErrorCode.BAD_REQUEST, ex.getMessage(), request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("[EXCEPTION] Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        ApiErrorBody.of(ApiErrorCode.INTERNAL_SERVER_ERROR, "Internal server error", request)));
    }

    static HttpStatus resolveIllegalStateStatus(IllegalStateException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (msg.contains("opensearch") || msg.contains("kafka") || msg.contains("not configured")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (msg.contains("not found") || msg.contains("bulunamad")) {
            return HttpStatus.NOT_FOUND;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String resolveStatusCode(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> ApiErrorCode.BAD_REQUEST;
            case NOT_FOUND -> ApiErrorCode.RESOURCE_NOT_FOUND;
            case UNAUTHORIZED -> ApiErrorCode.UNAUTHORIZED;
            case FORBIDDEN -> ApiErrorCode.ACCESS_DENIED;
            case SERVICE_UNAVAILABLE -> ApiErrorCode.SERVICE_UNAVAILABLE;
            case BAD_GATEWAY -> ApiErrorCode.BAD_GATEWAY;
            default ->
                    status.is5xxServerError() ? ApiErrorCode.INTERNAL_SERVER_ERROR : ApiErrorCode.BAD_REQUEST;
        };
    }
}
