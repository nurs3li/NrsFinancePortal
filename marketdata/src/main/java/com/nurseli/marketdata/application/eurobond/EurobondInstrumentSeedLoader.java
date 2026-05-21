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

@Component
@ConditionalOnEurobondEvds
@Slf4j
public class EurobondInstrumentSeedLoader {

    private static final String SEED_DIR = "eurobond-seeds/";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<SeedPoint> load(String isin) {
        if (isin == null || isin.isBlank()) {
            return List.of();
        }
        String path = SEED_DIR + isin.trim().toUpperCase() + ".json";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            return List.of();
        }
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            String source = root.path("source").asText("SEED");
            List<SeedPoint> out = new ArrayList<>();
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
                out.add(new SeedPoint(date, price, yield, source));
            }
            log.info("[EUROBOND_SEED] loaded isin={} points={} source={}", isin, out.size(), source);
            return out;
        } catch (Exception ex) {
            log.warn("[EUROBOND_SEED] failed isin={} reason={}", isin, ex.getMessage());
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

    public record SeedPoint(LocalDate asOfDate, BigDecimal cleanPrice, BigDecimal yieldPct, String source) {}
}
