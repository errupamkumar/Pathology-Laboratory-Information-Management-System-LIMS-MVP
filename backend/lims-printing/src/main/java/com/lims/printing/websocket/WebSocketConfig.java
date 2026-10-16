package com.lims.printing.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PrintAgentWebSocketHandler printAgentWebSocketHandler;
    private final PrintAgentHandshakeInterceptor handshakeInterceptor;
    private final List<String> allowedOrigins;

    public WebSocketConfig(PrintAgentWebSocketHandler printAgentWebSocketHandler,
                           PrintAgentHandshakeInterceptor handshakeInterceptor,
                           @Value("${lims.cors.allowed-origins}") List<String> allowedOrigins) {
        this.printAgentWebSocketHandler = printAgentWebSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * Browsers are limited to the same origin allow-list as CORS. Native print agents send no
     * Origin header and are authenticated by the handshake interceptor's token check instead.
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(printAgentWebSocketHandler, "/ws/print-agent")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
    }
}
