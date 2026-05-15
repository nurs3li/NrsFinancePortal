package com.nurseli.marketdata.api.dto.macropanel;

import java.util.List;

/**
 * Faiz &amp; Enflasyon paneli için tek seferde normalize makro yükü — mevcut alt uçları kırmadan
 * tüketilebilir snapshot.
 */
public record InterestInflationMacroPanelResponse(
        String generatedAt,
        List<NormalizedMacroSeriesDto> series,
        MacroPanelDerivedMetricsDto derived,
        List<MacroPanelBondSummaryDto> governmentBonds,
        List<String> notes
) {}
