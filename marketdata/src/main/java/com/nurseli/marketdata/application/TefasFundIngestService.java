package com.nurseli.marketdata.application;

import com.nurseli.marketdata.config.TefasProperties;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.domain.tefas.TefasFundProfile;
import com.nurseli.marketdata.infrastructure.tefas.TefasApiModels.TefasPriceRow;
import com.nurseli.marketdata.infrastructure.tefas.TefasApiModels.TefasReturnRow;
import com.nurseli.marketdata.infrastructure.tefas.TefasClient;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import com.nurseli.marketdata.repository.TefasFundProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TefasFundIngestService {

    public static final String SOURCE_TEFAS = "TEFAS";
    private static final DateTimeFormatter TEFAS_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final TefasClient tefasClient;
    private final TefasProperties properties;
    private final MarketPriceHistoryRepository priceRepository;
    private final TefasFundProfileRepository profileRepository;

    /**
     * İlk kurulum: tek toplu TEFAS çağrısından yalnızca yapılandırılmış sembollerin profilini yazar.
     * Günlük job bu metodu çağırmaz.
     */
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void syncProfilesFromTefas(List<String> symbols) {
        if (!properties.isEnabled() || symbols == null || symbols.isEmpty()) {
            return;
        }
        Set<String> wanted = normalizeCodes(symbols);
        List<TefasReturnRow> rows = tefasClient.fetchReturnBasedList();
        int saved = 0;
        for (TefasReturnRow row : rows) {
            if (row == null || row.fonKodu() == null || row.fonKodu().isBlank()) {
                continue;
            }
            String code = row.fonKodu().trim().toUpperCase(Locale.ROOT);
            if (!wanted.contains(code)) {
                continue;
            }
            upsertProfileFromReturnRow(row);
            saved++;
        }
        log.info("[TEFAS][PROFILE] Synced {} profile(s) for symbols={}", saved, wanted);
    }

    /** Fon başına tek API: geçmiş NAV → market_price_history (source=TEFAS). */
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public int ingestHistory(String code, int periodMonths) {
        if (!properties.isEnabled() || code == null || code.isBlank()) {
            return 0;
        }
        String symbol = code.trim().toUpperCase(Locale.ROOT);
        List<TefasPriceRow> rows = tefasClient.fetchPriceHistory(symbol, periodMonths);
        if (rows.isEmpty()) {
            log.warn(
                    "[TEFAS][HISTORY] Empty response symbol={} months={} — kod TEFAS YAT listesinde olmayabilir",
                    symbol,
                    periodMonths);
            return 0;
        }
        updateTitleFromPriceRows(symbol, rows);
        int inserted = 0;
        for (TefasPriceRow row : rows) {
            if (row == null || row.tarih() == null || row.fiyat() == null || row.fiyat() <= 0) {
                continue;
            }
            try {
                LocalDate day = LocalDate.parse(row.tarih().trim(), TEFAS_DATE);
                if (priceRepository.existsForDay(symbol, day.atStartOfDay(), day.plusDays(1).atStartOfDay())) {
                    continue;
                }
                saveNav(symbol, day, row.fiyat());
                inserted++;
            } catch (Exception ex) {
                log.debug("[TEFAS][HISTORY] Skip bad row symbol={} tarih={}", symbol, row.tarih());
            }
        }
        log.info("[TEFAS][HISTORY] symbol={} months={} inserted={}", symbol, periodMonths, inserted);
        return inserted;
    }

    /** Günlük job: fon başına 1 kısa periyot çağrısı, son NAV günü DB'ye. */
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void ingestLatestNav(String code) {
        if (!properties.isEnabled() || code == null || code.isBlank()) {
            return;
        }
        String symbol = code.trim().toUpperCase(Locale.ROOT);
        List<TefasPriceRow> rows = tefasClient.fetchPriceHistory(symbol, 1);
        if (rows.isEmpty()) {
            log.warn("[TEFAS][DAILY] No price rows symbol={}", symbol);
            return;
        }
        updateTitleFromPriceRows(symbol, rows);
        TefasPriceRow last = rows.stream()
                .filter(r -> r != null && r.tarih() != null && r.fiyat() != null && r.fiyat() > 0)
                .max(Comparator.comparing(r -> r.tarih()))
                .orElse(null);
        if (last == null) {
            return;
        }
        try {
            LocalDate day = LocalDate.parse(last.tarih().trim(), TEFAS_DATE);
            if (priceRepository.existsForDay(symbol, day.atStartOfDay(), day.plusDays(1).atStartOfDay())) {
                log.debug("[TEFAS][DAILY] Already exists symbol={} day={}", symbol, day);
                return;
            }
            saveNav(symbol, day, last.fiyat());
            log.info("[TEFAS][DAILY] Saved symbol={} day={} nav={}", symbol, day, last.fiyat());
        } catch (Exception ex) {
            log.warn("[TEFAS][DAILY] Failed symbol={} tarih={}", symbol, last.tarih(), ex);
        }
    }

    public long countNavRows(String code) {
        if (code == null || code.isBlank()) {
            return 0;
        }
        return priceRepository.countBySymbolAndSource(code.trim().toUpperCase(Locale.ROOT), SOURCE_TEFAS);
    }

    private void saveNav(String symbol, LocalDate day, double nav) {
        BigDecimal mid = BigDecimal.valueOf(nav);
        MarketPriceHistory entity = new MarketPriceHistory();
        entity.setSymbol(symbol);
        entity.setBuyPrice(SpreadCalculator.buyPrice(mid));
        entity.setSellPrice(SpreadCalculator.sellPrice(mid));
        entity.setSource(SOURCE_TEFAS);
        entity.setTimestamp(LocalDateTime.of(day, java.time.LocalTime.NOON));
        entity.setCurrency("TRY");
        priceRepository.save(entity);
    }

    private void updateTitleFromPriceRows(String code, List<TefasPriceRow> rows) {
        String title = rows.stream()
                .map(TefasPriceRow::fonUnvan)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .map(String::trim)
                .orElse(null);
        if (title == null) {
            return;
        }
        TefasFundProfile profile = profileRepository.findById(code).orElseGet(() -> {
            TefasFundProfile p = new TefasFundProfile();
            p.setCode(code);
            return p;
        });
        if (profile.getTitle() == null || profile.getTitle().isBlank()) {
            profile.setTitle(title);
            profile.setUpdatedAt(Instant.now());
            profileRepository.save(profile);
        }
    }

    private void upsertProfileFromReturnRow(TefasReturnRow row) {
        String code = row.fonKodu().trim().toUpperCase(Locale.ROOT);
        TefasFundProfile profile = profileRepository.findById(code).orElseGet(() -> {
            TefasFundProfile p = new TefasFundProfile();
            p.setCode(code);
            return p;
        });
        profile.setTitle(trim(row.fonUnvan()));
        profile.setFundType(trim(row.fonTurAciklama()));
        profile.setRiskLevel(parseRisk(row.riskDegeri()));
        profile.setTefasListed(Boolean.TRUE.equals(row.tefasDurum()));
        profile.setReturn1m(row.getiri1a());
        profile.setReturn3m(row.getiri3a());
        profile.setReturn6m(row.getiri6a());
        profile.setReturn1y(row.getiri1y());
        profile.setReturnYtd(row.getiriyb());
        profile.setReturn3y(row.getiri3y());
        profile.setReturn5y(row.getiri5y());
        profile.setUpdatedAt(Instant.now());
        profileRepository.save(profile);
    }

    private static Set<String> normalizeCodes(List<String> symbols) {
        Set<String> out = new HashSet<>();
        for (String s : symbols) {
            if (s != null && !s.isBlank()) {
                out.add(s.trim().toUpperCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static String trim(String s) {
        return s != null ? s.trim() : null;
    }

    private static Integer parseRisk(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
