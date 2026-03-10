package com.nurseli.nrsfinanceportal.config;

import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.integration.kafka.NotificationEventKafkaPublisher;
import com.nurseli.nrsfinanceportal.integration.kafka.event.NotificationRequestedEvent;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.nurseli.nrsfinanceportal.config.CorrelationIdFilter.CORRELATION_ID_MDC_KEY;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LogManager.getLogger(GlobalExceptionHandler.class);

    private static final long ERROR_NOTIFY_COOLDOWN_MS = 5 * 60 * 1000L;
    private final AtomicReference<Instant> lastErrorNotifiedAt = new AtomicReference<>(Instant.EPOCH);

    private final NotificationEventKafkaPublisher notificationEventKafkaPublisher;
    private final UserRepository userRepository;

    public GlobalExceptionHandler(NotificationEventKafkaPublisher notificationEventKafkaPublisher,
                                  UserRepository userRepository) {
        this.notificationEventKafkaPublisher = notificationEventKafkaPublisher;
        this.userRepository = userRepository;
    }

    private static Map<String, Object> errorBody(String message, HttpServletRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("timestamp", Instant.now().toString());
        map.put("error", message);
        map.put("path", request != null ? request.getRequestURI() : null);
        map.put("correlationId", ThreadContext.get(CORRELATION_ID_MDC_KEY));
        return map;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("[EXCEPTION] IllegalArgumentException: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(errorBody(ex.getMessage(), request)));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<?>> handleNotFound(IllegalStateException ex, HttpServletRequest request) {
        log.warn("[EXCEPTION] IllegalStateException: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(errorBody(ex.getMessage(), request)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleForbidden(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("[EXCEPTION] AccessDenied: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(errorBody("Access denied", request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("[EXCEPTION] Unexpected error", ex);

        notifyAdminsOnError(ex, request);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(errorBody("Internal server error", request)));
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
            String body = "Endpoint: " + path + "\nHata: " + msg;

            userRepository.findByRole(Role.ADMIN).forEach(admin ->
                    notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                            admin.getKeycloakUserId(),
                            "Sistem hatası tespit edildi",
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