package com.lims.server.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitingFilterTest {

    final RateLimitingFilter filter = new RateLimitingFilter(
            new RateLimitProperties(true, 3, 100), new ObjectMapper().findAndRegisterModules());

    @Test
    void loginIsLimitedPerClientIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(login("10.0.0.1").getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = login("10.0.0.1");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
        assertThat(blocked.getContentType()).isEqualTo("application/problem+json");
        // A different client is unaffected
        assertThat(login("10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void nonApiPathsAreNotLimited() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        for (int i = 0; i < 200; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse login(String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", RateLimitingFilter.LOGIN_PATH);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
