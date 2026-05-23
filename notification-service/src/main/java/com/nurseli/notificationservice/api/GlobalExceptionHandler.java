package com.nurseli.notificationservice.api;

import com.nurseli.notificationservice.api.response.ApiErrorBody;
import com.nurseli.notificationservice.api.response.ApiErrorCode;
import com.nurseli.notificationservice.api.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * REST katmanındaki istisnaları standart {@link ApiResponse} hata gövdesine dönüştürür.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * {@code handleValidation} — Bean validation hatalarını 400 ve {@link ApiErrorCode#VALIDATION_ERROR} ile döner.
     */
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

    /**
     * {@code handleBadRequest} — Geçersiz argüman istisnalarını 400 ile döner.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleBadRequest(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("[EXCEPTION] IllegalArgumentException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ApiErrorBody.of(ApiErrorCode.BAD_REQUEST, ex.getMessage(), request)));
    }

    /**
     * {@code handleIllegalState} — İş kuralı / OAuth / JWT kaynaklı durum hatalarını uygun HTTP status ile döner.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {
        log.warn("[EXCEPTION] IllegalStateException: {}", ex.getMessage());
        HttpStatus status = resolveIllegalStateStatus(ex);
        String code = resolveIllegalStateCode(status);
        String message = ex.getMessage() != null ? ex.getMessage() : "İşlem tamamlanamadı";
        return ResponseEntity.status(status)
                .body(ApiResponse.error(ApiErrorBody.of(code, message, request)));
    }

    /**
     * {@code handleResponseStatus} — {@link ResponseStatusException} mesajını standart hata gövdesine eşler.
     */
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

    /**
     * {@code handleForbidden} — Yetkisiz erişim istisnalarını 403 ile döner.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleForbidden(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("[EXCEPTION] AccessDenied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ApiErrorBody.of(ApiErrorCode.ACCESS_DENIED, "Access denied", request)));
    }

    /**
     * {@code handleGeneric} — Beklenmeyen hataları yakalar; OAuth / bağlantı sorunlarında 502, aksi halde 500 döner.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("[EXCEPTION] Unexpected error", ex);
        String message = "Internal server error";
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String rootMsg = root.getMessage() != null ? root.getMessage().toLowerCase() : "";
        if (rootMsg.contains("ssl")
                || rootMsg.contains("connection")
                || rootMsg.contains("oauth2.googleapis.com")) {
            message = "E-posta servisi şu an Google'a bağlanamıyor. Lütfen kısa süre sonra tekrar deneyin.";
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error(ApiErrorBody.of(ApiErrorCode.BAD_GATEWAY, message, request)));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ApiErrorBody.of(ApiErrorCode.INTERNAL_SERVER_ERROR, message, request)));
    }

    static HttpStatus resolveIllegalStateStatus(IllegalStateException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (msg.contains("gmail oauth")
                || msg.contains("oauth2.googleapis.com")
                || msg.contains("google api")
                || msg.contains("gmail api")
                || msg.contains("google'a bağlanamadı")
                || msg.contains("failed to send email via gmail")) {
            return HttpStatus.BAD_GATEWAY;
        }
        if (msg.contains("jwt authentication") || msg.contains("jwt subject")) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (msg.contains("not configured") || msg.contains("configuration missing")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String resolveIllegalStateCode(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> ApiErrorCode.UNAUTHORIZED;
            case SERVICE_UNAVAILABLE -> ApiErrorCode.SERVICE_UNAVAILABLE;
            case BAD_GATEWAY -> ApiErrorCode.BAD_GATEWAY;
            default -> ApiErrorCode.INTERNAL_SERVER_ERROR;
        };
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
                    status.is5xxServerError()
                            ? ApiErrorCode.INTERNAL_SERVER_ERROR
                            : ApiErrorCode.BAD_REQUEST;
        };
    }
}
