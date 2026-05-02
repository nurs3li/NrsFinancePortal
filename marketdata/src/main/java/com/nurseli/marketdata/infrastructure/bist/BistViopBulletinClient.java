package com.nurseli.marketdata.infrastructure.bist;

import com.nurseli.marketdata.config.ViopHybridProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Slf4j
public class BistViopBulletinClient {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private final ViopHybridProperties properties;
    private final WebClient webClient = WebClient.create();

    public BistViopBulletinClient(ViopHybridProperties properties) {
        this.properties = properties;
    }

    public List<ViopBulletinRow> fetchRows() {
        if (!properties.isEnabled() || !properties.isBistBulletinEnabled()) {
            return List.of();
        }
        if (properties.getBistBulletinUrl() == null || properties.getBistBulletinUrl().isBlank()) {
            return List.of();
        }
        try {
            String csv = webClient.get()
                    .uri(properties.getBistBulletinUrl())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofMillis(properties.getTimeoutMs()))
                    .onErrorResume(ex -> {
                        log.warn("[VIOP_BULLETIN] fetch failed: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .block();
            if (csv == null || csv.isBlank()) {
                return List.of();
            }
            return parse(csv);
        } catch (Exception ex) {
            log.warn("[VIOP_BULLETIN] parse pipeline failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<ViopBulletinRow> parse(String csv) {
        String[] lines = csv.split("\\r?\\n");
        if (lines.length < 2) {
            return List.of();
        }
        String delimiter = lines[0].contains(";") ? ";" : ",";
        String[] headers = lines[0].split(delimiter);
        List<ViopBulletinRow> out = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] values = line.split(delimiter, -1);
            Map<String, String> m = new java.util.HashMap<>();
            for (int j = 0; j < headers.length && j < values.length; j++) {
                m.put(norm(headers[j]), values[j].trim());
            }
            String contractCode = pick(m, "CONTRACT_CODE", "CONTRACT", "SYMBOL", "SOZLESME_KODU");
            if (contractCode == null || contractCode.isBlank()) continue;
            Long openInterest = parseLong(pick(m, "OPEN_INTEREST", "OPENINTEREST", "ACIK_POZISYON"));
            Long dailyVolume = parseLong(pick(m, "DAILY_VOLUME", "VOLUME", "GUNLUK_HACIM", "HACIM"));
            BigDecimal settlement = parseDecimal(pick(m, "SETTLEMENT_PRICE", "FINAL_SETTLEMENT", "UZLASMA_FIYATI"));
            LocalDateTime asOf = parseDate(pick(m, "DATE", "TARIH"));
            out.add(new ViopBulletinRow(contractCode, openInterest, dailyVolume, settlement, asOf));
        }
        return out;
    }

    private String norm(String header) {
        return header == null ? "" : header.trim()
                .toUpperCase(Locale.ROOT)
                .replace('İ', 'I')
                .replace('Ş', 'S')
                .replace('Ğ', 'G')
                .replace('Ü', 'U')
                .replace('Ö', 'O')
                .replace('Ç', 'C')
                .replace(" ", "_")
                .replace("-", "_");
    }

    private String pick(Map<String, String> m, String... keys) {
        for (String key : keys) {
            String value = m.get(key);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String v = raw.replace(".", "").replace(",", "").trim();
            return Long.parseLong(v);
        } catch (Exception ignored) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String t = raw.trim();
            if (t.contains(",") && t.contains(".")) {
                return new BigDecimal(t.replace(".", "").replace(",", "."));
            }
            if (t.contains(",")) {
                return new BigDecimal(t.replace(",", "."));
            }
            return new BigDecimal(t);
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) return LocalDateTime.now();
        try {
            return LocalDate.parse(raw, DATE_FMT).atStartOfDay();
        } catch (Exception ignored) {
            return LocalDateTime.now();
        }
    }

    public record ViopBulletinRow(
            String contractCode,
            Long openInterest,
            Long dailyVolume,
            BigDecimal settlementPrice,
            LocalDateTime asOf
    ) {}
}

