package com.nurseli.marketdata.infrastructure.isyatirim.viop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.marketdata.domain.viop.ViopDataQuality;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class IsYatirimViopSnapshotParser {

    private final ObjectMapper objectMapper;

    public record ParsedSnapshot(
            String symbolFromProvider,
            OffsetDateTime updateDate,
            BigDecimal bid,
            BigDecimal ask,
            BigDecimal low,
            BigDecimal high,
            BigDecimal last,
            BigDecimal dayClose,
            BigDecimal openPrice,
            BigDecimal changeAmount,
            BigDecimal changePercent,
            BigDecimal quantity,
            BigDecimal volume,
            BigDecimal settlement,
            BigDecimal preSettlement,
            BigDecimal limitUp,
            BigDecimal limitDown,
            BigDecimal priceStep,
            BigDecimal initialMargin,
            BigDecimal weekLow,
            BigDecimal weekHigh,
            BigDecimal weekClose,
            BigDecimal monthLow,
            BigDecimal monthHigh,
            BigDecimal monthClose,
            BigDecimal yearClose,
            BigDecimal prevYearClose,
            ViopDataQuality dataQuality) {}

    public ParsedSnapshot parse(String body, String expectedContractCode) throws Exception {
        if (body == null || body.isBlank()) {
            return empty(ViopDataQuality.EMPTY_RESPONSE);
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode first;
        if (root.isArray()) {
            if (root.isEmpty()) {
                return empty(ViopDataQuality.EMPTY_RESPONSE);
            }
            first = root.get(0);
        } else if (root.has("d")) {
            JsonNode d = root.get("d");
            if (d.isTextual()) {
                root = objectMapper.readTree(d.asText());
            } else {
                root = d;
            }
            if (root.isArray()) {
                if (root.isEmpty()) {
                    return empty(ViopDataQuality.EMPTY_RESPONSE);
                }
                first = root.get(0);
            } else {
                first = root;
            }
        } else {
            first = root;
        }
        if (first == null || first.isNull()) {
            return empty(ViopDataQuality.EMPTY_RESPONSE);
        }

        String sym = text(first, "symbol");
        if (sym != null
                && expectedContractCode != null
                && !sym.trim().equalsIgnoreCase(expectedContractCode.trim())) {
            log.warn(
                    "VIOP snapshot symbol mismatch expected={} providerSymbol={}",
                    expectedContractCode,
                    sym);
        }

        BigDecimal last = bd(first, "last");
        BigDecimal dayClose = bd(first, "dayClose");
        BigDecimal changeAmount = null;
        BigDecimal changePercent = null;
        if (last != null && dayClose != null && dayClose.signum() != 0) {
            changeAmount = last.subtract(dayClose);
            changePercent = last.subtract(dayClose)
                    .divide(dayClose, 8, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(6, RoundingMode.HALF_UP);
        }

        OffsetDateTime update = IsYatirimViopHistoricalParser.parseOffsetLoose(text(first, "updateDate"));
        if (update == null) {
            return empty(ViopDataQuality.MALFORMED_RESPONSE);
        }

        return new ParsedSnapshot(
                sym,
                update,
                bd(first, "bid"),
                bd(first, "ask"),
                bd(first, "low"),
                bd(first, "high"),
                last,
                dayClose,
                bd(first, "open"),
                changeAmount,
                changePercent,
                bd(first, "quantity"),
                bd(first, "volume"),
                bd(first, "settlement"),
                bd(first, "preSettlement"),
                bd(first, "limitUp"),
                bd(first, "limitDown"),
                bd(first, "priceStep"),
                bd(first, "initialMargin"),
                bd(first, "weekLow"),
                bd(first, "weekHigh"),
                bd(first, "weekClose"),
                bd(first, "monthLow"),
                bd(first, "monthHigh"),
                bd(first, "monthClose"),
                bd(first, "yearClose"),
                bd(first, "prevYearClose"),
                ViopDataQuality.OK);
    }

    private static ParsedSnapshot empty(ViopDataQuality q) {
        return new ParsedSnapshot(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                q);
    }

    public static LocalDateTime toLocalDateTime(OffsetDateTime odt, ZoneId zone) {
        if (odt == null) {
            return null;
        }
        return odt.atZoneSameInstant(zone).toLocalDateTime();
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText(null);
    }

    private static BigDecimal bd(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || v.isMissingNode()) {
            return null;
        }
        try {
            if (v.isNumber()) {
                return v.decimalValue();
            }
            String s = v.asText(null);
            if (s == null || s.isBlank()) {
                return null;
            }
            return new BigDecimal(s.trim().replace(',', '.'));
        } catch (Exception e) {
            log.warn("VIOP snapshot field parse skip field={} reason={}", field, e.getMessage());
            return null;
        }
    }
}
