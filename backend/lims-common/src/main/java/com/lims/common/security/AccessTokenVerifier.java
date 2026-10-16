package com.lims.common.security;

import java.util.Optional;

/**
 * Verifies a bearer access token outside the servlet security filter chain (e.g. during a
 * WebSocket handshake, where browsers cannot send an Authorization header).
 * Implemented by lims-server so that domain modules never depend on the JWT library.
 */
public interface AccessTokenVerifier {

    /**
     * @return the username the token was issued to, if the token is valid and the account is active
     */
    Optional<String> verify(String token);
}
