package com.nurseli.marketdata.infrastructure.isyatirim.viop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class IsYatirimViopHistoricalParser {

    private final ObjectMapper objectMapper;

    public record ChartRow(long millis, BigDecimal price) {}

    public record ParsedHistorical(List<ChartRow> rows, OffsetDateTime providerTimestamp) {}

    public ParsedHistorical parse(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return new ParsedHistorical(List.of(), null);
        }
        JsonNode root = objectMapper.readTree(body);
        if (root.has("d") && root.get("d").isTextual()) {
            root = objectMapper.readTree(root.get("d").asText());
        }
        JsonNode data = root.get("data");
        List<ChartRow> rows = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode row : data) {
                if (!row.isArray() || row.size() < 2) continue;
                long t = row.get(0).asLong(0L);
                BigDecimal p = parsePrice(row.get(1));
                if (t > 0 && p != null && p.signum() > 0) {
                    rows.add(new ChartRow(t, p));
                }
            }
        }
        OffsetDateTime providerTs = null;
        if (root.hasNonNull("timestamp")) {
            providerTs = parseOffsetLoose(root.get("timestamp").asText());
        }
        return new ParsedHistorical(rows, providerTs);
    }

    private static BigDecimal parsePrice(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isNumber()) {
            return n.decimalValue();
        }
        String s = n.asText(null);
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static OffsetDateTime parseOffsetLoose(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        try {
            return OffsetDateTime.parse(s);
        } catch (Exception ignored) {
            // e.g. ...+03 without minutes
        }
        if (s.matches(".+[+-]\\d{2}$") && !s.matches(".+[+-]\\d{2}:\\d{2}$")) {
            try {
                return OffsetDateTime.parse(s + ":00");
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }
}
