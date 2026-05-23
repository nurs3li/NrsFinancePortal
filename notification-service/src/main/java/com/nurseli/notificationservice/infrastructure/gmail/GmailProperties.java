package com.nurseli.notificationservice.infrastructure.gmail;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Gmail OAuth2 ve gönderici adresi yapılandırmasını {@code gmail.*} prefix'i ile bağlar.
 */
@Configuration
@ConfigurationProperties(prefix = "gmail")
public class GmailProperties {

    private String clientId;
    private String clientSecret;
    private String refreshToken;
    private String fromAddress;

    // getters & setters

    /** {@code getClientId} — Google OAuth2 client id değerini döner. */
    public String getClientId() {
        return clientId;
    }

    /** {@code setClientId} — Google OAuth2 client id değerini ayarlar. */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /** {@code getClientSecret} — Google OAuth2 client secret değerini döner. */
    public String getClientSecret() {
        return clientSecret;
    }

    /** {@code setClientSecret} — Google OAuth2 client secret değerini ayarlar. */
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    /** {@code getRefreshToken} — Gmail API için kullanılan OAuth2 refresh token değerini döner. */
    public String getRefreshToken() {
        return refreshToken;
    }

    /** {@code setRefreshToken} — Gmail API için kullanılan OAuth2 refresh token değerini ayarlar. */
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    /** {@code getFromAddress} — Gönderici e-posta adresini döner. */
    public String getFromAddress() {
        return fromAddress;
    }

    /** {@code setFromAddress} — Gönderici e-posta adresini ayarlar. */
    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }
}