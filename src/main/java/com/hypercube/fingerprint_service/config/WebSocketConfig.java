package com.hypercube.fingerprint_service.config;

import com.hypercube.fingerprint_service.websocket.FingerprintWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(fingerprintWebSocketHandler(), "/ws/fingerprint")
                .setAllowedOrigins("*"); // Allow all origins for development
    }

    @Bean
    public FingerprintWebSocketHandler fingerprintWebSocketHandler() {
        return new FingerprintWebSocketHandler();
    }
}
