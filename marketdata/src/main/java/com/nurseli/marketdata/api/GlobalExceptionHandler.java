package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.exception.InvalidRequestException;
import com.nurseli.marketdata.application.bist.BistEquityUnsupportedSymbolException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

/**
 * REST katmanındaki istisnaları standart {@link ApiEnvelope} hata gövdesine dönüştürür.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * {@code handleValidation} — Bean validation hatalarını 400 ve {@link ErrorCode#VALIDATION_ERROR} ile döner.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiEnvelope<?>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String msg =
                ex.getBindingResult().getFieldErrors().stream()
                        .findFirst()
                        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                        .orElse("Geçersiz istek gövdesi");
        log.warn("[EXCEPTION] MethodArgumentNotValidException: {}", msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiEnvelope.error(ApiErrorBody.of(ErrorCode.VALIDATION_ERROR, msg, request)));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiEnvelope<?>> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        log.warn("[EXCEPTION] IllegalStateException: {}", ex.getMessage());
        HttpStatus status = resolveIllegalStateStatus(ex);
        String code = resolveStatusCode(status);
        String message = ex.getMessage() != null ? ex.getMessage() : status.getReasonPhrase();
        return ResponseEntity.status(status)
                .body(ApiEnvelope.error(ApiErrorBody.of(code, message, request)));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiEnvelope<?>> handleInvalidRequest(InvalidRequestException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiEnvelope.error(ApiErrorBody.of(ErrorCode.BAD_REQUEST, ex.getMessage(), request)));
    }

    @ExceptionHandler(BistEquityUnsupportedSymbolException.class)
    public ResponseEntity<ApiEnvelope<?>> handleBistUnsupported(
            BistEquityUnsupportedSymbolException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiEnvelope.error(ApiErrorBody.of(ErrorCode.BAD_REQUEST, ex.getMessage(), request)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiEnvelope<?>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiEnvelope.error(ApiErrorBody.of(ErrorCode.BAD_REQUEST, ex.getMessage(), request)));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiEnvelope<?>> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus resolved = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        String message = ex.getReason() != null ? ex.getReason() : resolved.getReasonPhrase();
        String code = resolveStatusCode(resolved);
        log.warn("[EXCEPTION] ResponseStatusException: {} {}", resolved.value(), message);
        return ResponseEntity.status(resolved)
                .body(ApiEnvelope.error(ApiErrorBody.of(code, message, request)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiEnvelope<?>> handleMissingRequestParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiEnvelope.error(ApiErrorBody.of(ErrorCode.BAD_REQUEST, ex.getMessage(), request)));
    }

    /**
     * {@code handleForbidden} — Yetkisiz erişim istisnalarını 403 ile döner.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiEnvelope<?>> handleForbidden(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("[EXCEPTION] AccessDenied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiEnvelope.error(ApiErrorBody.of(ErrorCode.ACCESS_DENIED, "Access denied", request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnvelope<?>> handleGeneric(Exception ex, HttpServletRequest request) {
        if (isBenignClientDisconnect(ex)) {
            log.debug(
                    "[API] client disconnected {} {} — {}",
                    request != null ? request.getMethod() : "?",
                    request != null ? request.getRequestURI() : "?",
                    ex.getClass().getSimpleName());
            return null;
        }
        log.error(
                "[API] unhandled {} {} — {}",
                request != null ? request.getMethod() : "?",
                request != null ? request.getRequestURI() : "?",
                ex.toString(),
                ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiEnvelope.error(
                        ApiErrorBody.of(ErrorCode.INTERNAL_SERVER_ERROR, "Internal server error", request)));
    }

    static HttpStatus resolveIllegalStateStatus(IllegalStateException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (msg.contains("not configured")
                || msg.contains("configuration missing")
                || msg.contains("disabled")
                || msg.contains("devre dışı")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (msg.contains("not found")
                || msg.contains("bulunamad")
                || msg.contains("yok")
                || msg.contains("missing")) {
            return HttpStatus.NOT_FOUND;
        }
        return HttpStatus.NOT_FOUND;
    }

    private static String resolveStatusCode(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> ErrorCode.BAD_REQUEST;
            case NOT_FOUND -> ErrorCode.RESOURCE_NOT_FOUND;
            case UNAUTHORIZED -> ErrorCode.UNAUTHORIZED;
            case FORBIDDEN -> ErrorCode.ACCESS_DENIED;
            case SERVICE_UNAVAILABLE -> ErrorCode.SERVICE_UNAVAILABLE;
            case BAD_GATEWAY -> ErrorCode.BAD_GATEWAY;
            default ->
                    status.is5xxServerError() ? ErrorCode.INTERNAL_SERVER_ERROR : ErrorCode.BAD_REQUEST;
        };
    }

    /** Tarayıcı/finance timeout — yanıt yazılırken bağlantı kapanır; sunucu hatası değil. */
    private static boolean isBenignClientDisconnect(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ClientAbortException || t instanceof AsyncRequestNotUsableException) {
                return true;
            }
            if (t instanceof IOException io) {
                String msg = io.getMessage();
                if (msg != null) {
                    String lower = msg.toLowerCase();
                    if (lower.contains("broken pipe") || lower.contains("connection reset")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
