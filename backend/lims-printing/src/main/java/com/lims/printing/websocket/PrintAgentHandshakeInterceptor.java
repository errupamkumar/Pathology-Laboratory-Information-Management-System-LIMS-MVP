package com.lims.printing.websocket;

import com.lims.common.security.AccessTokenVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;

/**
 * Print payloads contain patient demographics and full PDF reports, so the spooler socket must
 * be authenticated. Browsers cannot set an Authorization header on a WebSocket upgrade, hence
 * the access token is accepted from the {@code access_token} query parameter.
 */
@Component
public class PrintAgentHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ATTRIBUTE = "lims.user";
    private static final String TOKEN_PARAM = "access_token";
    private static final Logger log = LoggerFactory.getLogger(PrintAgentHandshakeInterceptor.class);

    private final AccessTokenVerifier tokenVerifier;

    public PrintAgentHandshakeInterceptor(AccessTokenVerifier tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        Optional<String> user = token != null ? tokenVerifier.verify(token) : Optional.empty();
        if (user.isEmpty()) {
            log.warn("Rejected unauthenticated print-agent WebSocket handshake from {}", request.getRemoteAddress());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(USER_ATTRIBUTE, user.get());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private static String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst(TOKEN_PARAM);
    }
}
