package com.alnumerocinque.config;

import com.alnumerocinque.security.KdsHandshakeAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Feed WebSocket del KDS (vedi docs/05-api-events.md): gli eventi outbox
 * pubblicati su Kafka da OutboxPublisher vengono ripubblicati qui da
 * KdsWebSocketBridge, sul canale STOMP /topic/kds. Il tablet cucina e' per
 * design sempre online (vedi docs/06-offline-sync.md), quindi non serve un
 * fallback SockJS: endpoint WebSocket nativo, autenticato da
 * KdsHandshakeAuthInterceptor invece che dalla catena Spring Security
 * standard (l'handshake HTTP iniziale non passa dal JwtAuthenticationFilter
 * in modo utilizzabile per un client WebSocket nativo da browser).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final KdsHandshakeAuthInterceptor kdsHandshakeAuthInterceptor;

    public WebSocketConfig(KdsHandshakeAuthInterceptor kdsHandshakeAuthInterceptor) {
        this.kdsHandshakeAuthInterceptor = kdsHandshakeAuthInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-kds")
                .addInterceptors(kdsHandshakeAuthInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
