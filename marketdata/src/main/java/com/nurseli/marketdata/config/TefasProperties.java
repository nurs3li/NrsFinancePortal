package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Data
@Component
@ConfigurationProperties(prefix = "app.tefas")
public class TefasProperties {

    private boolean enabled = true;
    private String baseUrl = "https://www.tefas.gov.tr";
    private int connectTimeoutMs = 8_000;
    private int readTimeoutMs = 45_000;
    /** YAT = yatırım fonları (TEFAS şemsiye). */
    private String fundKind = "YAT";

    /**
     * Takip edilen Türk fon kodları (TEFAS). Liste ve grafik yalnızca bunlardan gelir;
     * canlı 1000+ fon listesi çekilmez.
     */
    private List<String> symbols = new ArrayList<>();

    /** İlk açılışta DB boşsa geçmiş NAV backfill (fon başına 1 API). */
    private boolean backfillOnStartup = true;

    /** İlk açılışta profil tablosu boşsa tek seferlik toplu getiri → yalnızca symbols filtresi. */
    private boolean syncProfilesOnStartup = true;

    /** Geçmiş backfill periyodu (ay): 1, 3, 6, 12, 36, 60. */
    private int historyBackfillMonths = 12;

    /** Bu kadar NAV satırı varsa startup backfill atlanır. */
    private int minHistoryRowsBeforeSkip = 20;

    /** Günlük son NAV (Europe/Istanbul). Fon başına 1 API. */
    private String dailyCron = "0 35 7 * * *";

    public List<String> normalizedSymbols() {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        return symbols.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.toList());
    }
}
