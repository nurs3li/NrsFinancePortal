package com.nurseli.nrsfinanceportal.api.dto.simulation;

import java.time.Instant;
import java.util.List;

/**
 * Tek simülasyon geçmiş kaydı; özet veya detay yanıtı.
 */
public record SimulationHistoryEntryDto(
        String id,
        Instant savedAt,
        String label,
        String amountCurrency,
        List<SimulationHistoryItemDto> items
) {
}
