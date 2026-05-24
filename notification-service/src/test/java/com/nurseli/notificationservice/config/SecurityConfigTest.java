package com.nurseli.notificationservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityConfigTest.ProbeController.class)
@Import({SecurityConfig.class, CorsConfig.class, ApiSecurityErrorHandler.class})
class SecurityConfigTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void protectedNotifications_withoutJwt_returns401Envelope() throws Exception {
    mockMvc
        .perform(get("/api/notifications/probe"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errors.code").value("UNAUTHORIZED"));
  }

  @Test
  void unknownPath_isNotPublic() throws Exception {
    mockMvc
        .perform(get("/api/other"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errors.code").value("UNAUTHORIZED"));
  }

  @RestController
  static class ProbeController {

    @GetMapping("/api/notifications/probe")
    String probe() {
      return "ok";
    }
  }
}
