package com.nurseli.nrsfinanceportal.api.dto;

/**
 * TOTP durum response DTO'su; etkinlik ve kurulum bekleme durumunu taşır.
 */
public record TotpStatusDto(boolean enabled, boolean setupPending) {}
