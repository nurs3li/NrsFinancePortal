package com.nurseli.marketdata.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.marketdata.application.MarketLiveSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketLiveWebSocketHandler extends TextWebSocketHandler {
    private final MarketLiveSnapshotService snapshotService;
    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Scheduled(fixedDelayString = "${app.market.ws.broadcast-ms:5000}")
    public void broadcast() {
        if (sessions.isEmpty()) return;
        try {
            String json = objectMapper.writeValueAsString(snapshotService.snapshot());
            TextMessage message = new TextMessage(json);
            sessions.removeIf(s -> !s.isOpen());
            for (WebSocketSession session : sessions) {
                try {
                    session.sendMessage(message);
                } catch (Exception ex) {
                    log.warn("[WS] broadcast failed session={} reason={}", session.getId(), ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("[WS] payload build failed: {}", ex.getMessage());
        }
    }
}
