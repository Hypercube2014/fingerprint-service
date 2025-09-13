package com.hypercube.fingerprint_service.config;

import com.hypercube.fingerprint_service.websocket.FingerprintWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private static final int MAX_CONNECTIONS = 10;
    private final AtomicInteger connectionCount = new AtomicInteger(0);

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(fingerprintWebSocketHandler(), "/ws/fingerprint")
                .setAllowedOrigins("*"); // Allow all origins for development
    }

    @Bean
    public FingerprintWebSocketHandler fingerprintWebSocketHandler() {
        return new LimitedWebSocketHandler();
    }

    /**
     * WebSocket handler with connection limits
     */
    private class LimitedWebSocketHandler extends FingerprintWebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            if (connectionCount.get() >= MAX_CONNECTIONS) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Maximum connections exceeded"));
                return;
            }
            connectionCount.incrementAndGet();
            super.afterConnectionEstablished(session);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
            connectionCount.decrementAndGet();
            super.afterConnectionClosed(session, closeStatus);
        }

        public int getActiveConnectionCount() {
            return connectionCount.get();
        }
    }
}
