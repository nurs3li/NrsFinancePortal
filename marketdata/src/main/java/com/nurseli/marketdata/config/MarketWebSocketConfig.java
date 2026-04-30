package com.nurseli.marketdata.config;

import com.nurseli.marketdata.realtime.MarketLiveWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class MarketWebSocketConfig implements WebSocketConfigurer {
    private final MarketLiveWebSocketHandler marketLiveWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(marketLiveWebSocketHandler, "/ws/market")
                .setAllowedOrigins("http://localhost:5173", "http://localhost:3000");
    }
}
