package com.nurseli.marketdata.application.bist;

import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.bist.BistDataQuality;
import com.nurseli.marketdata.infrastructure.bist.BistEquityDailyPrice;
import com.nurseli.marketdata.infrastructure.bist.BistProviderSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BistEquityPersistenceMapperTest {

    private final BistEquityPersistenceMapper mapper = new BistEquityPersistenceMapper();

    @Test
    void maps_fields_and_timestamp_at_istanbul_start_of_day() {
        LocalDate d = LocalDate.of(2025, 6, 10);
        BistEquityDailyPrice row =
                new BistEquityDailyPrice(
                        "thyao",
                        d,
                        new BigDecimal("50.12"),
                        new BigDecimal("49"),
                        new BigDecimal("48"),
                        new BigDecimal("51"),
                        new BigDecimal("1000000"),
                        new BigDecimal("49.5"),
                        new BigDecimal("48.5"),
                        new BigDecimal("47.5"),
                        new BigDecimal("50.5"),
                        new BigDecimal("900000"),
                        new BigDecimal("32.1"),
                        new BigDecimal("9000"),
                        new BigDecimal("1.2"),
                        new BigDecimal("0.5"),
                        new BigDecimal("100"),
                        new BigDecimal("200"),
                        new BigDecimal("150"),
                        new BigDecimal("80"),
                        new BigDecimal("90"),
                        new BigDecimal("95"),
                        new BigDecimal("1.5"),
                        new BigDecimal("2.5"),
                        new BigDecimal("2.0"),
                        BistProviderSource.IS_YATIRIM,
                        BistDataQuality.HISTORICAL,
                        Instant.parse("2025-06-10T12:00:00Z"));

        MarketPriceHistory e = mapper.toNewEntity(row);
        assertEquals("THYAO", e.getSymbol());
        assertEquals(BistProviderSource.IS_YATIRIM.name(), e.getSource());
        assertEquals(LocalDateTime.of(2025, 6, 10, 0, 0), e.getTimestamp());
        assertEquals(new BigDecimal("50.12"), e.getAdjustedClose());
        assertEquals(new BigDecimal("49"), e.getAdjustedAverage());
        assertEquals(new BigDecimal("48"), e.getAdjustedLow());
        assertEquals(new BigDecimal("51"), e.getAdjustedHigh());
        assertEquals(new BigDecimal("1000000"), e.getAdjustedVolume());
        assertEquals(new BigDecimal("49.5"), e.getRawClose());
        assertEquals(BistDataQuality.HISTORICAL.name(), e.getDataQuality());
        assertEquals("TRY", e.getCurrency());
        assertNotNull(e.getBuyPrice());
        assertNotNull(e.getSellPrice());
    }

    @Test
    void copyOnto_updates_existing_row() {
        LocalDate d = LocalDate.of(2025, 1, 3);
        MarketPriceHistory existing = new MarketPriceHistory();
        existing.setId(99L);
        existing.setSymbol("ASELS");
        existing.setSource(BistProviderSource.IS_YATIRIM.name());
        existing.setTimestamp(d.atStartOfDay(BistEquityDailyConstants.IST).toLocalDateTime());
        existing.setBuyPrice(BigDecimal.ONE);
        existing.setSellPrice(BigDecimal.ONE);

        BistEquityDailyPrice row =
                new BistEquityDailyPrice(
                        "asels",
                        d,
                        new BigDecimal("10"),
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
                        BistProviderSource.IS_YATIRIM,
                        BistDataQuality.PARTIAL,
                        null);
        mapper.copyOnto(existing, row);
        assertEquals(99L, existing.getId());
        assertEquals(new BigDecimal("10"), existing.getAdjustedClose());
        assertEquals(BistDataQuality.PARTIAL.name(), existing.getDataQuality());
    }
}
