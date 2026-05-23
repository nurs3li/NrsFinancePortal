package com.nurseli.nrsfinanceportal.api.dto;

/**
 * TOTP kurulum response DTO'su; secret, otpauth URL ve hesap bilgilerini taşır.
 */
public record TotpSetupDto(String secret, String otpauthUrl, String issuer, String accountName) {}
