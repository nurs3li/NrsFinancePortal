package com.nurseli.nrsfinanceportal.api.dto.simulation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Yeni simülasyon geçmişi kaydı oluşturma isteği.
 */
public record SimulationHistorySaveRequest(
        @NotBlank @Size(max = 200) String label,
        @NotBlank @Size(max = 3) String amountCurrency,
        @NotEmpty List<SimulationHistoryItemDto> items
) {
}
