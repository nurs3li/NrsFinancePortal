package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.TefasHistoryPoint;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TEFAS fon geçmiş fiyat serisi endpoint'lerini market data client üzerinden sunar.
 */
@RestController
@RequiredArgsConstructor
public class TefasFundController {

    private final MarketDataClient marketDataClient;

    /**
     * {@code history} — Fon kodu ve ay sayısı ile TEFAS geçmiş fiyat noktalarını döner.
     */
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/api/market/tefas/funds/{code}/history")
    public List<TefasHistoryPoint> history(
            @PathVariable String code,
            @RequestParam(defaultValue = "12") int months) {
        return marketDataClient.getTefasFundHistory(code, months);
    }
}
