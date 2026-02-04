package com.nurseli.marketdata.application;

import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.tefas.TefasClient;
import com.nurseli.marketdata.infrastructure.tefas.TefasFundPriceDto;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundPriceIngestService {

    private final TefasClient tefasClient;
    private final MarketPriceHistoryRepository repository;

    /**
     * Bu method ASLA exception fırlatmaz.
     * External data app’i asla düşürmez.
     */
    public void ingestForDate(String fundCode, LocalDate date) {

        try {
            Optional<TefasFundPriceDto> optional =
                    tefasClient.fetchLastAvailablePrice(fundCode, date);

            if (optional.isEmpty()) {
                log.warn(
                        "[TEFAS] No price found for fund={} referenceDate={}",
                        fundCode,
                        date
                );
                return;
            }

            TefasFundPriceDto dto = optional.get();

            // Aynı gün varsa tekrar yazma
            boolean exists = repository
                    .findTopBySymbolOrderByTimestampDesc(dto.fundCode())
                    .map(e -> e.getTimestamp().toLocalDate().equals(dto.date()))
                    .orElse(false);

            if (exists) {
                log.info(
                        "[TEFAS] Already exists fund={} date={}",
                        dto.fundCode(),
                        dto.date()
                );
                return;
            }

            MarketPriceHistory entity = new MarketPriceHistory();
            entity.setSymbol(dto.fundCode());
            entity.setBuyPrice(dto.price());
            entity.setSellPrice(dto.price());
            entity.setSource("TEFAS");
            entity.setTimestamp(LocalDateTime.of(dto.date(), java.time.LocalTime.NOON));

            repository.save(entity);

            log.info(
                    "[TEFAS] SAVED fund={} price={} date={}",
                    dto.fundCode(),
                    dto.price(),
                    dto.date()
            );

        } catch (Exception e) {
            // ❗ BURASI ÇOK ÖNEMLİ
            log.error(
                    "[TEFAS] INGEST FAILED fund={} date={}",
                    fundCode,
                    date,
                    e
            );
        }
    }
}