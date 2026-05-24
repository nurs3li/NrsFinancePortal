package com.nurseli.nrsfinanceportal.api.dto;

/**
 * Kullanıcı hesabını dondurma request'i; opsiyonel gerekçe metnini taşır.
 */
public record FreezeRequest(String reason) {}