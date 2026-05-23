package com.nurseli.notificationservice.infrastructure.gmail;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class MimeMessageBuilderTest {

    private final MimeMessageBuilder builder = new MimeMessageBuilder();

    @Test
    void buildRawMessage_producesUrlSafeBase64WithHeaders() {
        String raw = builder.buildRawMessage("from@test.com", "to@test.com", "Konu", "Merhaba");

        assertThat(raw).doesNotContain("+").doesNotContain("/");
        byte[] decoded = Base64.getUrlDecoder().decode(raw);
        String mime = new String(decoded, StandardCharsets.UTF_8);

        assertThat(mime).contains("From: from@test.com");
        assertThat(mime).contains("To: to@test.com");
        assertThat(mime).contains("Content-Type: text/plain");
        assertThat(mime).contains("Merhaba");
        assertThat(mime).contains("=?UTF-8?B?");
    }

    @Test
    void buildRawMessage_nullBody_treatedAsEmpty() {
        String raw = builder.buildRawMessage("f@t.com", "t@t.com", "S", null);
        String mime = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
        assertThat(mime).contains("Content-Type: text/plain");
        assertThat(mime.split("\r\n\r\n", 2)).hasSize(2);
        assertThat(mime.split("\r\n\r\n", 2)[1]).isEmpty();
    }
}
