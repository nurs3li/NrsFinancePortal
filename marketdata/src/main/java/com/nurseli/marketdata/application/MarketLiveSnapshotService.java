package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.DebtSnapshotResponse;
import com.nurseli.marketdata.api.dto.MarketLivePayloadResponse;
import com.nurseli.marketdata.api.dto.MarketLiveTickResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.api.dto.ViopSnapshotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MarketLiveSnapshotService {
    private final MarketPriceQueryService marketPriceQueryService;
    private final ViopQueryService viopQueryService;
    private final DebtQueryService debtQueryService;

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
        map.forEach((symbol, row) -> out.add(new MarketLiveTickResponse(
                category,
                symbol,
                nz(row.buyPrice()),
                BigDecimal.ZERO,
                deriveVolume(row)
        )));
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
