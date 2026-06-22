package com.nurseli.nrsfinanceportal.api.dto.simulation;

import java.util.List;

/**
 * Kullanıcının simülasyon geçmişi özet listesi.
 */
public record SimulationHistoryListResponse(
        List<SimulationHistoryEntryDto> entries
) {
}
