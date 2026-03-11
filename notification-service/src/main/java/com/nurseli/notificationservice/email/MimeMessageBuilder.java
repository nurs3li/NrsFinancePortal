package com.nurseli.notificationservice.email;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class MimeMessageBuilder {

    public String buildRawMessage(String from, String to, String subject, String bodyText) {
        StringBuilder sb = new StringBuilder();
        sb.append("From: ").append(from).append("\r\n");
        sb.append("To: ").append(to).append("\r\n");
        sb.append("Subject: ").append(encodeSubject(subject)).append("\r\n");
        sb.append("Content-Type: text/plain; charset=\"UTF-8\"\r\n");
        sb.append("\r\n");
        sb.append(bodyText != null ? bodyText : "");

        String message = sb.toString();
        return base64UrlEncode(message.getBytes(StandardCharsets.UTF_8));
    }

    private String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Basit UTF-8 subject encoding.
     * İstersen ileride daha gelişmiş MIME header encoding yapabilirsin.
     */
    private String encodeSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            return "";
        }
        // UTF-8 subject'i RFC 2047 formatında Base64 ile encode et
        byte[] bytes = subject.getBytes(StandardCharsets.UTF_8);
        String base64 = Base64.getEncoder().encodeToString(bytes);
        return "=?UTF-8?B?" + base64 + "?=";
    }
}