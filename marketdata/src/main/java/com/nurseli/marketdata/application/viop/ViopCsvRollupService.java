package com.nurseli.marketdata.application.viop;

import com.nurseli.marketdata.domain.derivatives.DerivativeContract;
import com.nurseli.marketdata.domain.derivatives.DerivativeSnapshot;
import com.nurseli.marketdata.infrastructure.persistence.DerivativeContractRepository;
import com.nurseli.marketdata.infrastructure.persistence.DerivativeSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * VIOP yalnız CSV’den beslendiğinde liste % değişimlerini DB’de saklar;
 * {@link ViopBackfillRunner} import tamamlanınca bir kez çalışır, HTTP isteğinde tekrar hesaplanmaz.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ViopCsvRollupService {

    private final DerivativeContractRepository contractRepository;
    private final DerivativeSnapshotRepository snapshotRepository;
    private final ViopContractParser viopContractParser;

    @Transactional
    public void refreshAfterCsvImport() {
        List<DerivativeContract> all = contractRepository.findAll();
        LocalDateTime computedAt = LocalDateTime.now();
        for (DerivativeContract c : all) {
            List<DerivativeSnapshot> asc = snapshotsForContractRow(c);
            c.setViopListPctChange1d(pctChangeOverCalendarDays(asc, 1));
            c.setViopListPctChange7d(pctChangeOverCalendarDays(asc, 7));
            c.setViopListPctChange30d(pctChangeOverCalendarDays(asc, 30));
            c.setViopListPctChange365d(pctChangeOverCalendarDays(asc, 365));
            applySeqMoveFromLastTwoPoints(c, asc);
            c.setViopRollupsComputedAt(computedAt);
            contractRepository.save(c);
        }
        log.info("[VIOP_ROLLUP] list % + seq (last-two) refreshed for {} contracts", all.size());
    }

    private List<DerivativeSnapshot> snapshotsForContractRow(DerivativeContract c) {
        String raw = c.getContractCode();
        List<DerivativeSnapshot> rows = snapshotRepository.findByContractCodeOrderByAsOfAsc(raw);
        if (!rows.isEmpty()) {
            return rows;
        }
        String normalized = viopContractParser.normalizeContractCode(raw);
        rows = snapshotRepository.findByContractCodeOrderByAsOfAsc(normalized);
        if (!rows.isEmpty()) {
            return rows;
        }
        return snapshotRepository.findByContractCodeOrderByAsOfAsc("F_" + normalized);
    }

    /**
     * Son snapshot zamanını referans alır; [tEnd − days, tEnd] aralığındaki ilk ve son fiyatla % değişim.
     */
    private BigDecimal pctChangeOverCalendarDays(List<DerivativeSnapshot> asc, int calendarDays) {
        if (asc == null || asc.size() < 2) {
            return null;
        }
        DerivativeSnapshot last = asc.get(asc.size() - 1);
        LocalDateTime tEnd = last.getAsOf();
        if (tEnd == null) {
            return null;
        }
        LocalDateTime tStart = tEnd.toLocalDate().minusDays(calendarDays).atStartOfDay();
        List<DerivativeSnapshot> inWindow = asc.stream()
                .filter(s -> s.getAsOf() != null
                        && !s.getAsOf().isBefore(tStart)
                        && !s.getAsOf().isAfter(tEnd))
                .toList();
        if (inWindow.size() < 2) {
            return null;
        }
        BigDecimal first = inWindow.get(0).getPrice();
        BigDecimal lastP = inWindow.get(inWindow.size() - 1).getPrice();
        if (first == null || lastP == null || first.signum() == 0 || lastP.signum() == 0) {
            return null;
        }
        return lastP.subtract(first)
                .divide(first, 8, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Zaman sırasındaki son iki fiyat noktası: takvim günü değil, veri setindeki ardışık gözlemler.
     */
    private void applySeqMoveFromLastTwoPoints(DerivativeContract c, List<DerivativeSnapshot> asc) {
        if (asc == null || asc.isEmpty()) {
            c.setViopSeqMovePct(null);
            c.setViopSeqMoveTrend(null);
            return;
        }
        List<DerivativeSnapshot> valid = asc.stream()
                .filter(s -> s.getPrice() != null && s.getPrice().signum() > 0)
                .toList();
        if (valid.size() < 2) {
            c.setViopSeqMovePct(null);
            c.setViopSeqMoveTrend(null);
            return;
        }
        DerivativeSnapshot prev = valid.get(valid.size() - 2);
        DerivativeSnapshot last = valid.get(valid.size() - 1);
        BigDecimal p0 = prev.getPrice();
        BigDecimal p1 = last.getPrice();
        if (p0 == null || p1 == null || p0.signum() == 0 || p1.signum() == 0) {
            c.setViopSeqMovePct(null);
            c.setViopSeqMoveTrend(null);
            return;
        }
        BigDecimal pct = p1.subtract(p0)
                .divide(p0, 8, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(4, RoundingMode.HALF_UP);
        c.setViopSeqMovePct(pct);
        int cmp = p1.compareTo(p0);
        if (cmp > 0) {
            c.setViopSeqMoveTrend("UP");
        } else if (cmp < 0) {
            c.setViopSeqMoveTrend("DOWN");
        } else {
            c.setViopSeqMoveTrend("NEUTRAL");
        }
    }
}
