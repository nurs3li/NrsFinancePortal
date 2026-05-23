package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.exception.ApiBusinessException;
import com.nurseli.nrsfinanceportal.api.response.ApiResponse;
import com.nurseli.nrsfinanceportal.api.response.ApiErrorCode;
import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.infrastructure.kafka.NotificationEventKafkaPublisher;
import com.nurseli.nrsfinanceportal.infrastructure.kafka.event.NotificationRequestedEvent;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.server.ResponseStatusException;
import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.nurseli.nrsfinanceportal.config.CorrelationIdFilter.CORRELATION_ID_MDC_KEY;

/**
 * Controller katmanÄ±ndaki istisnalarÄ± standart {@link com.nurseli.nrsfinanceportal.api.response.ApiResponse} hata zarfÄ±na ve uygun HTTP status'a dÃ¶nÃ¼ÅŸtÃ¼rÃ¼r.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LogManager.getLogger(GlobalExceptionHandler.class);

    private static final long ERROR_NOTIFY_COOLDOWN_MS = 5 * 60 * 1000L;
    private final AtomicReference<Instant> lastErrorNotifiedAt = new AtomicReference<>(Instant.EPOCH);

    private final NotificationEventKafkaPublisher notificationEventKafkaPublisher;
    private final UserRepository userRepository;

    /**
     * {@code GlobalExceptionHandler} â€” Kafka bildirim yayÄ±ncÄ±sÄ± ve kullanÄ±cÄ± deposu ile oluÅŸturulur.
     */
    public GlobalExceptionHandler(NotificationEventKafkaPublisher notificationEventKafkaPublisher,
                                  UserRepository userRepository) {
        this.notificationEventKafkaPublisher = notificationEventKafkaPublisher;
        this.userRepository = userRepository;
    }

    private static Map<String, Object> errorBody(String code, String message, HttpServletRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("message", message);
        map.put("timestamp", Instant.now().toString());
        map.put("error", message);
        map.put("path", request != null ? request.getRequestURI() : null);
        map.put("correlationId", ThreadContext.get(CORRELATION_ID_MDC_KEY));
        return map;
    }

    /**
     * {@code handleValidation} â€” Bean validation hatalarÄ±nÄ± 400 Bad Request ve alan mesajÄ±yla dÃ¶ner.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Geçersiz istek gövdesi");
        log.warn("[EXCEPTION] MethodArgumentNotValidException: {}", msg);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(errorBody(ApiErrorCode.BAD_REQUEST, msg, request)));
    }

    /**
     * {@code handleBadRequest} â€” GeÃ§ersiz argÃ¼manlarÄ± 400 Bad Request olarak eÅŸler.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("[EXCEPTION] IllegalArgumentException: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(errorBody(ApiErrorCode.BAD_REQUEST, ex.getMessage(), request)));
    }

    /**
     * {@code handleBusiness} â€” {@link com.nurseli.nrsfinanceportal.api.exception.ApiBusinessException} iÃ§indeki status ve error code ile yanÄ±t Ã¼retir.
     */
    @ExceptionHandler(ApiBusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusiness(ApiBusinessException ex, HttpServletRequest request) {
        log.warn("[EXCEPTION] ApiBusinessException {}: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiResponse.error(errorBody(ex.getErrorCode(), ex.getMessage(), request)));
    }

    /**
     * {@code handleStatus} â€” Spring {@link org.springframework.web.server.ResponseStatusException} durumlarÄ±nÄ± standart hata zarfÄ±na Ã§evirir.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<?>> handleStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus code = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus status = code != null ? code : HttpStatus.INTERNAL_SERVER_ERROR;
        String reason = ex.getReason();
        log.warn("[EXCEPTION] ResponseStatusException: {} {}", status.value(), reason);
        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(errorBody(ApiErrorCode.INTERNAL_SERVER_ERROR,
                        reason != null ? reason : status.getReasonPhrase(), request)));
    }

    /**
     * {@code handleIllegalState} â€” Keycloak entegrasyon hatalarÄ±nda 502, diÄŸer durumlarda 400 dÃ¶ner.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        log.warn("[EXCEPTION] IllegalStateException: {}", ex.getMessage());
        String msg = ex.getMessage() != null ? ex.getMessage() : "İşlem tamamlanamadı";
        HttpStatus status = msg.contains("Keycloak") && (msg.contains("401") || msg.contains("UNAUTHORIZED"))
                ? HttpStatus.BAD_GATEWAY
                : HttpStatus.BAD_REQUEST;
        String code = status == HttpStatus.BAD_GATEWAY ? ApiErrorCode.INTERNAL_SERVER_ERROR : ApiErrorCode.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiResponse.error(errorBody(code, msg, request)));
    }

    /**
     * {@code handleForbidden} â€” Yetkisiz eriÅŸimleri 403 Forbidden olarak dÃ¶ner.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleForbidden(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("[EXCEPTION] AccessDenied: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(errorBody(ApiErrorCode.ACCESS_DENIED, "Access denied", request)));
    }

    /**
     * {@code handleAsyncTimeout} â€” SSE uzun baÄŸlantÄ± sÃ¼resi dolduÄŸunda 204 dÃ¶ner; bakÄ±m bildirimi ve ERROR log spam'ini Ã¶nler.
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Void> handleAsyncTimeout(AsyncRequestTimeoutException ex, HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : "";
        if (path != null && path.contains("/sse/")) {
            log.debug("[SSE] Async timeout (normal yaşam döngüsü; istemci EventSource ile yeniden bağlanır): {}", path);
        } else {
            log.warn("[ASYNC] AsyncRequestTimeoutException: {}", path);
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * {@code handleAsyncNotUsable} â€” Ä°stemci SSE/long-poll baÄŸlantÄ±sÄ±nÄ± kestiÄŸinde 204 dÃ¶ner; bakÄ±m maili gÃ¶nderilmez.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleAsyncNotUsable(AsyncRequestNotUsableException ex, HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : "";
        if (path != null && path.contains("/sse/")) {
            log.debug("[SSE] İstemci bağlantıyı kesti veya yanıt kullanılamaz (beklenen): {} — {}", path, ex.getMessage());
        } else {
            log.debug("[ASYNC] AsyncRequestNotUsable: {} — {}", path, ex.getMessage());
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * {@code handleGeneric} â€” Beklenmeyen hatalarda 500 dÃ¶ner ve soÄŸutmalÄ± Kafka ile admin bildirimi tetikler.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("[EXCEPTION] Unexpected error", ex);

        notifyAdminsOnError(ex, request);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(errorBody(ApiErrorCode.INTERNAL_SERVER_ERROR, "Internal server error", request)));
    }

    private void notifyAdminsOnError(Exception ex, HttpServletRequest request) {
        try {
            Instant now = Instant.now();
            Instant last = lastErrorNotifiedAt.get();
            if (now.toEpochMilli() - last.toEpochMilli() < ERROR_NOTIFY_COOLDOWN_MS) {
                return;
            }
            if (!lastErrorNotifiedAt.compareAndSet(last, now)) {
                return;
            }

            String path = request != null ? request.getRequestURI() : "unknown";
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            String body = """
                    Bakım ekibine otomatik bildirim:

                    • Endpoint: %s
                    • Özet: %s

                    Lütfen sunucu günlüklerinden ayrıntılı iz kontrolü yapınız.

                    NRS Finance Portal
                    """.formatted(path, msg);

            userRepository.findByRole(Role.ADMIN).forEach(admin ->
                    notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                            admin.getKeycloakUserId(),
                            "Sistem hatası bildirimi",
                            body,
                            "SYSTEM_ERROR",
                            "system",
                            null
                    ))
            );
        } catch (Exception notifyEx) {
            log.error("[NOTIFICATION] Failed to notify admins about system error", notifyEx);
        }
    }
}
