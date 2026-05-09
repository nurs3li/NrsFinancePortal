package com.nurseli.nrsfinanceportal.integration.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * FM görev havuzu ve admin kritik gecikme için sunucudan tek yönlü push (SSE).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskPoolSseService {

    /** {@code spring.mvc.async.request-timeout} ile aynı (31 dk); ikisi farklı olursa AsyncRequestTimeoutException oluşur. */
    private static final long SSE_TIMEOUT_MS = 31 * 60 * 1000L;

    private final ObjectMapper objectMapper;
    private final CopyOnWriteArrayList<SseEmitter> fmEmitters = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<SseEmitter> adminEmitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribeFm() {
        return register(fmEmitters);
    }

    public SseEmitter subscribeAdmin() {
        return register(adminEmitters);
    }

    private SseEmitter register(CopyOnWriteArrayList<SseEmitter> list) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        list.add(emitter);
        Runnable remove = () -> list.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            log.debug("[SSE] immediate send failed", e);
            remove.run();
        }
        return emitter;
    }

    /**
     * Reverse proxy / LB idle timeout önleme; SSE comment satırı istemci tarafında olay üretmez.
     */
    @Scheduled(fixedDelay = 25_000, initialDelay = 20_000)
    public void keepAliveSseConnections() {
        ping(fmEmitters);
        ping(adminEmitters);
    }

    private void ping(CopyOnWriteArrayList<SseEmitter> list) {
        if (list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (Exception ex) {
                list.remove(emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void broadcastFm(Map<String, Object> payload) {
        broadcast(fmEmitters, "task-pool", payload);
    }

    public void broadcastAdmin(Map<String, Object> payload) {
        broadcast(adminEmitters, "admin-task", payload);
    }

    private void broadcast(CopyOnWriteArrayList<SseEmitter> list, String eventName, Map<String, Object> payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("[SSE] serialize failed", e);
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(json));
            } catch (Exception e) {
                list.remove(emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
