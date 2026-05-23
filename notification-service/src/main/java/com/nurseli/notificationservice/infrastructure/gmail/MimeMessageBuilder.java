package com.nurseli.notificationservice.infrastructure.gmail;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Gmail API {@code raw} alanı için düz metin MIME mesajı üretir;
 * UTF-8 subject encoding ve Base64 URL encoding uygular.
 */
@Component
public class MimeMessageBuilder {

    /**
     * {@code buildRawMessage} — Gönderici, alıcı, konu ve gövdeden Gmail API uyumlu
     * Base64 URL-encoded MIME string oluşturur.
     */
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

    /** {@code base64UrlEncode} — MIME içeriğini Gmail API'nin beklediği Base64 URL formatına dönüştürür. */
    private String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * {@code encodeSubject} — UTF-8 konu satırını RFC 2047 Base64 MIME header formatına kodlar.
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