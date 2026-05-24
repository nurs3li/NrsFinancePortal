package com.nurseli.nrsfinanceportal.api.dto;

/**
 * Geçmiş fiyat eşleştirme türü; tam eşleşme, önceki kapanış veya bulunamadı durumlarını belirtir.
 */
public enum HistoricalPriceMatchType {
    EXACT,
    PREVIOUS_CLOSE,
    NEXT_AVAILABLE,
    NOT_FOUND
}
