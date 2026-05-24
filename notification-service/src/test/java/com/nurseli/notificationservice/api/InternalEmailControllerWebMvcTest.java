package com.nurseli.notificationservice.api;

import com.nurseli.notificationservice.application.email.OutboundEmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalEmailController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class InternalEmailControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OutboundEmailService outboundEmailService;

    @Test
    void sendEmail_delegatesToApplicationLayer() throws Exception {
        String body =
                """
                {"to":"user@test.com","subject":"Hi","body":"Text"}
                """;

        mockMvc.perform(
                        post("/api/notifications/internal/email/send")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isAccepted());

        verify(outboundEmailService).sendDirect("user@test.com", "Hi", "Text");
    }

    @Test
    void sendEmail_invalidPayload_returnsValidationEnvelope() throws Exception {
        String body =
                """
                {"to":"not-an-email","subject":"","body":""}
                """;

        mockMvc.perform(
                        post("/api/notifications/internal/email/send")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.code").value("VALIDATION_ERROR"));
    }
}
