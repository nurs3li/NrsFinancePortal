package com.nurseli.nrsfinanceportal.api.dto.pricealert;

import com.nurseli.nrsfinanceportal.domain.pricealert.PriceAlert;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Fiyat alarmı response DTO'su; koşul, eşik, durum ve tetiklenme zamanını taşır.
 * {@link #from(com.nurseli.nrsfinanceportal.domain.pricealert.PriceAlert)} ile entity'den üretilir.
 */
public record PriceAlertDto(
        Long id,
        String assetType,
        String symbol,
        String conditionType,
        BigDecimal threshold,
        String changeWindow,
        String channels,
        String status,
        boolean repeatAlert,
        int cooldownHours,
        Instant lastTriggeredAt,
        Instant createdAt
) {
    public static PriceAlertDto from(PriceAlert entity) {
        return new PriceAlertDto(
                entity.getId(),
                entity.getAssetType().name(),
                entity.getSymbol(),
                entity.getConditionType().name(),
                entity.getThreshold(),
                entity.getChangeWindow() != null ? entity.getChangeWindow().name() : null,
                entity.getChannels().name(),
                entity.getStatus().name(),
                entity.isRepeatAlert(),
                entity.getCooldownHours(),
                entity.getLastTriggeredAt(),
                entity.getCreatedAt()
        );
    }
}
