package com.nurseli.marketdata.application;

import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.tcmb.TcmbClient;
import com.nurseli.marketdata.infrastructure.tcmb.TcmbRate;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tarihsel FX verisini (USD/EUR/GBP vb.) TCMB'den çekip market_price_history tablosuna doldurmak için
 * kullanılan backfill servisi.
 *
 * Bu servis normal ingest akışından bağımsızdır; ihtiyaca göre manuel/tek seferlik çalıştırılır.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketPriceBackfillService {

    private static final int MAX_DAYS = 365;

    private final TcmbClient tcmbClient;
    private final MarketPriceHistoryRepository repository;

    /**
     * Bugünden geriye doğru son N gün için (örneğin 90) TCMB günlük kurlarını çekip
     * market_price_history tablosuna yazar.
     *
     * Örn: days=90 → bugün hariç, dünden başlayarak son 90 günü doldurur.
     * Güvenlik:
     * - days 1..365 aralığında olmalı
     * - Aynı gün + sembol için zaten kayıt varsa tekrar yazılmaz
     */
    @Transactional
    public void backfillFxLastDays(int days) {
        if (days <= 0 || days > MAX_DAYS) {
            log.warn("[BACKFILL] days 1..{} aralığında olmalı. days={}", MAX_DAYS, days);
            return;
        }

        LocalDate today = LocalDate.now();
        log.info("[BACKFILL] TCMB FX backfill başlıyor. days={}", days);

        for (int i = 1; i <= days; i++) {
            LocalDate date = today.minusDays(i);

            try {
                List<TcmbRate> rates = tcmbClient.fetchRatesForDate(date);
                if (rates.isEmpty()) {
                    log.info("[BACKFILL] TCMB verisi bulunamadı. date={}", date);
                    continue;
                }

                int savedCount = 0;
                int skippedCount = 0;

                LocalDateTime startOfDay = date.atStartOfDay();
                LocalDateTime endOfDay = startOfDay.plusDays(1);

                for (TcmbRate rate : rates) {
                    String symbol = rate.symbol() + "TRY";

                    boolean exists = repository.existsForDay(symbol, startOfDay, endOfDay);
                    if (exists) {
                        skippedCount++;
                        continue;
                    }

                    MarketPriceHistory entity = new MarketPriceHistory();
                    entity.setSymbol(symbol);
                    entity.setBuyPrice(rate.buy());
                    entity.setSellPrice(rate.sell());
                    entity.setSource("TCMB");
                    // Günlük veri olduğu için saat sabah 09:00 gibi sabitlenebilir
                    entity.setTimestamp(startOfDay.withHour(9));

                    repository.save(entity);
                    savedCount++;
                }

                log.info("[BACKFILL] date={} için {} yeni kayıt yazıldı, {} kayıt zaten vardı.",
                        date, savedCount, skippedCount);
            } catch (Exception ex) {
                log.error("[BACKFILL] date={} için backfill başarısız: {}", date, ex.getMessage());
            }
        }

        log.info("[BACKFILL] TCMB FX backfill tamamlandı. days={}", days);
    }
}