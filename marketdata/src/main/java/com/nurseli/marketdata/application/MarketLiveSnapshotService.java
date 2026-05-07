package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.DebtSnapshotResponse;
import com.nurseli.marketdata.api.dto.MarketLivePayloadResponse;
import com.nurseli.marketdata.api.dto.MarketLiveTickResponse;
import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.api.dto.ViopSnapshotResponse;
import com.nurseli.marketdata.domain.price.CryptoDailyCandle;
import com.nurseli.marketdata.domain.price.FxDailyCandle;
import com.nurseli.marketdata.repository.CryptoDailyCandleRepository;
import com.nurseli.marketdata.repository.FxDailyCandleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MarketLiveSnapshotService {
    private final MarketPriceQueryService marketPriceQueryService;
    private final ViopQueryService viopQueryService;
    private final DebtQueryService debtQueryService;
    private final FxDailyCandleRepository fxDailyCandleRepository;
    private final CryptoDailyCandleRepository cryptoDailyCandleRepository;

    public MarketLivePayloadResponse snapshot() {
        List<MarketLiveTickResponse> ticks = new ArrayList<>();
        append(ticks, "EQUITY", marketPriceQueryService.getLatestEquity());
        append(ticks, "CRYPTO", marketPriceQueryService.getLatestCrypto());
        append(ticks, "FX", marketPriceQueryService.getLatestFx());
        viopQueryService.latest().forEach(v -> ticks.add(new MarketLiveTickResponse(
                "FUTURES",
                v.contractCode(),
                nz(v.price()),
                pct(v.basis(), v.price()),
                BigDecimal.valueOf(v.openInterest() == null ? 0 : v.openInterest())
        )));
        debtQueryService.latest().forEach(d -> ticks.add(new MarketLiveTickResponse(
                "BOND",
                d.isin(),
                nz(d.dirtyPrice()),
                BigDecimal.ZERO,
                d.dirtyPrice() == null ? BigDecimal.ZERO : d.dirtyPrice().multiply(BigDecimal.valueOf(100L))
        )));
        return new MarketLivePayloadResponse(LocalDateTime.now(), ticks);
    }

    private void append(List<MarketLiveTickResponse> out, String category, Map<String, MarketPriceLatestResponse> map) {
        LocalDate today = LocalDate.now();
        map.forEach((symbol, row) -> {
            BigDecimal price = nz(row.buyPrice());
            BigDecimal changePct = switch (category) {
                case "FX" -> fxDailyChangePct(symbol, price, today);
                case "CRYPTO" -> cryptoDailyChangePct(symbol, price, today);
                default -> BigDecimal.ZERO;
            };
            out.add(new MarketLiveTickResponse(category, symbol, price, changePct, deriveVolume(row)));
        });
    }

    private BigDecimal fxDailyChangePct(String symbol, BigDecimal price, LocalDate today) {
        if (price == null || price.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        String sym = symbol == null ? "" : symbol.trim().toUpperCase();
        if (sym.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Optional<FxDailyCandle> todayM = fxDailyCandleRepository.findBySymbolAndAsOf(sym, today);
        if (todayM.isPresent()) {
            BigDecimal open = todayM.get().getOpenPrice();
            if (open != null && open.signum() > 0) {
                return pctVsRef(price, open);
            }
        }
        Optional<BigDecimal> prevClose = fxDailyCandleRepository
                .findFirstBySymbolAndAsOfLessThanOrderByAsOfDesc(sym, today)
                .map(FxDailyCandle::getClosePrice)
                .filter(b -> b != null && b.signum() > 0);
        if (prevClose.isPresent()) {
            return pctVsRef(price, prevClose.get());
        }
        // TCMB scheduler sık sık market_price_history doldurur; Yahoo günlük mum yoksa buradan % üret.
        return fxChangeFromTcmbHistory(sym, price);
    }

    /** Son ~24 saatteki ilk bucket orta fiyatına göre (TCMB/EVDS ingest). */
    private BigDecimal fxChangeFromTcmbHistory(String symbol, BigDecimal currentPrice) {
        List<MarketPriceHistoryResponse> rows = marketPriceQueryService.getHistory(symbol, 1);
        if (rows == null || rows.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal firstMid = midHistoryRow(rows.getFirst());
        if (firstMid == null || firstMid.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return pctVsRef(currentPrice, firstMid);
    }

    private static BigDecimal midHistoryRow(MarketPriceHistoryResponse d) {
        if (d == null) {
            return null;
        }
        if (d.buyPrice() != null && d.sellPrice() != null) {
            return d.buyPrice().add(d.sellPrice()).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
        }
        if (d.buyPrice() != null) {
            return d.buyPrice();
        }
        return d.sellPrice();
    }

    private BigDecimal cryptoDailyChangePct(String symbol, BigDecimal price, LocalDate today) {
        if (price == null || price.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        String sym = symbol == null ? "" : symbol.trim().toUpperCase();
        if (sym.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Optional<CryptoDailyCandle> todayM = cryptoDailyCandleRepository.findBySymbolAndAsOf(sym, today);
        if (todayM.isPresent()) {
            BigDecimal open = todayM.get().getOpenPrice();
            if (open != null && open.signum() > 0) {
                return pctVsRef(price, open);
            }
        }
        return cryptoDailyCandleRepository
                .findFirstBySymbolAndAsOfLessThanOrderByAsOfDesc(sym, today)
                .map(CryptoDailyCandle::getClosePrice)
                .map(ref -> pctVsRef(price, ref))
                .orElse(BigDecimal.ZERO);
    }

    private static BigDecimal pctVsRef(BigDecimal price, BigDecimal ref) {
        if (ref == null || ref.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return price.subtract(ref).divide(ref, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal pct(BigDecimal basis, BigDecimal price) {
        if (basis == null || price == null || price.signum() == 0) return BigDecimal.ZERO;
        return basis.divide(price, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal deriveVolume(MarketPriceLatestResponse row) {
        if (row == null) {
            return BigDecimal.ZERO;
        }
        if (row.marketCap() != null && row.marketCap().signum() > 0) {
            return row.marketCap();
        }
        BigDecimal ref = nz(row.buyPrice()).max(nz(row.sellPrice()));
        if (ref.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        // Gerçek hacim yoksa sıfıra kilitlemek yerine küçük türetilmiş bir likidite skoru üret.
        return ref.multiply(BigDecimal.valueOf(1_000L)).setScale(2, RoundingMode.HALF_UP);
    }
}
