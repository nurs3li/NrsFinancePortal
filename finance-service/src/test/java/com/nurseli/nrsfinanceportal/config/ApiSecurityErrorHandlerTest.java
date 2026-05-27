package com.nurseli.nrsfinanceportal.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ApiSecurityErrorHandlerTest {

    private ApiSecurityErrorHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ApiSecurityErrorHandler(new ObjectMapper());
    }

    @Test
    void commence_returns401Envelope() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/portfolio/manual/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(request, response, new BadCredentialsException("bad token"));

        assertThat(response.getStatus()).isEqualTo(401);
        JsonNode body = readBody(response);
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("errors").get("code").asText()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void accessDenied_returns403Envelope() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/users/1/suspend-login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        JsonNode body = readBody(response);
        assertThat(body.get("errors").get("code").asText()).isEqualTo("ACCESS_DENIED");
    }

    private static JsonNode readBody(MockHttpServletResponse response) throws Exception {
        return new ObjectMapper().readTree(response.getContentAsString(StandardCharsets.UTF_8));
    }
}
