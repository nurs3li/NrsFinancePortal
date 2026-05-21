package com.nurseli.marketdata.application.eurobond;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.marketdata.config.ConditionalOnEurobondEvds;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Haftalık tekil eurobond fiyat/getiri — broker ekranı veya manuel import (eurobond-quotes/*.json).
 * EVDS tekil ISIN serisi olmadığı için makro TP_EBOND* yerine bu dosyalar kullanılır.
 */
@Component
@ConditionalOnEurobondEvds
@Slf4j
public class EurobondQuoteFileLoader {

    private static final String QUOTE_DIR = "eurobond-quotes/";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<QuotePoint> load(String isin) {
        if (isin == null || isin.isBlank()) {
            return List.of();
        }
        String path = QUOTE_DIR + isin.trim().toUpperCase() + ".json";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            return List.of();
        }
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            String source = root.path("source").asText("BROKER_MID_WEEKLY");
            List<QuotePoint> out = new ArrayList<>();
            for (JsonNode p : root.path("points")) {
                String dateText = p.path("date").asText(null);
                if (dateText == null || dateText.isBlank()) {
                    continue;
                }
                LocalDate date = LocalDate.parse(dateText);
                BigDecimal price = parseDecimal(p.path("cleanPrice").asText(null));
                if (price == null) {
                    continue;
                }
                BigDecimal yield = parseDecimal(p.path("yieldPct").asText(null));
                out.add(new QuotePoint(date, price, yield, source));
            }
            log.info("[EUROBOND_QUOTES] loaded isin={} points={} source={}", isin, out.size(), source);
            return out;
        } catch (Exception ex) {
            log.warn("[EUROBOND_QUOTES] failed isin={} reason={}", isin, ex.getMessage());
            return List.of();
        }
    }

    private BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank() || "-".equals(raw.trim())) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim().replace(",", "."));
        } catch (Exception ignored) {
            return null;
        }
    }

    public record QuotePoint(LocalDate asOfDate, BigDecimal cleanPrice, BigDecimal yieldPct, String source) {}
}
